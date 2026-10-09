package bf.fasoguardian.telemetrie.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import bf.fasoguardian.audit.JournalAudit;
import bf.fasoguardian.audit.JournalAudit.Resultat;
import bf.fasoguardian.plateforme.erreurs.CodeErreur;
import bf.fasoguardian.plateforme.erreurs.ErreurMetier;
import bf.fasoguardian.plateforme.securite.SceauWebhook;
import bf.fasoguardian.telemetrie.domaine.Passerelle;
import bf.fasoguardian.telemetrie.infrastructure.DepotPasserelles;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Passerelles LoRaWAN en zone pilote (US-SYS-004, REQ-COULD-06). L'administrateur tient le registre des
 * passerelles et de l'enceinte que chacune couvre. Le serveur de réseau LoRaWAN, qui authentifie les trames
 * des bracelets, remet ici chaque trame entendue : elle confirme la présence du bracelet dans l'enceinte,
 * sans réseau cellulaire.
 *
 * <p>Appel du serveur de réseau, authentifié par son sceau :
 * {@code {"passerelle":"<EUI>","bracelet":"<numéro de série>","compteur":<n° de trame>,"t":<secondes Unix>}}.
 * Sans {@code bracelet}, c'est un simple signe de vie de la passerelle.
 */
@Service
public class PasserellesLorawan {

    public enum Issue {
        ACCEPTE,
        /** Trame déjà remise. */
        DOUBLON,
        /** Passerelle inconnue ou retirée, bracelet inconnu ou hors service, contenu irrecevable. */
        REJETE
    }

    /** @param enLigne une trame ou un signe de vie a été reçu depuis moins d'un quart d'heure */
    public record Vue(UUID id, String eui, String etablissement, double latitude, double longitude, int rayonM, Instant creeeLe,
            Instant vueLe, boolean enLigne) {
    }

    private static final String ROLE = "ADMIN";

    private final DepotPasserelles depot;
    private final Ingestion ingestion;
    private final JournalAudit journal;
    private final JsonMapper json;
    private final MeterRegistry metriques;
    private final Clock horloge;
    private final Optional<SceauWebhook> sceau;

    PasserellesLorawan(DepotPasserelles depot, Ingestion ingestion, JournalAudit journal, JsonMapper json, MeterRegistry metriques,
            Clock horloge, @Value("${fasoguardian.lorawan.secret-serveur-reseau:}") String secret) {
        this.depot = depot;
        this.ingestion = ingestion;
        this.journal = journal;
        this.json = json;
        this.metriques = metriques;
        this.horloge = horloge;
        // Sans secret configuré, le canal est fermé : aucune trame n'est acceptée.
        this.sceau = secret.isBlank() ? Optional.empty()
                : Optional.of(new SceauWebhook(secret, "fasoguardian.lorawan.secret-serveur-reseau"));
    }

    @Transactional(readOnly = true)
    public List<Vue> enService() {
        Instant maintenant = horloge.instant();
        return depot.enService().stream().map(p -> vue(p, maintenant)).toList();
    }

    @Transactional
    public Vue installer(UUID agentId, String eui, String etablissement, double latitude, double longitude, int rayonM) {
        String identifiant = eui.strip().toUpperCase(Locale.ROOT);
        if (depot.existe(identifiant)) {
            throw new ErreurMetier(CodeErreur.CONFLIT, "Une passerelle porte déjà cet identifiant.");
        }
        Passerelle passerelle = new Passerelle(UUID.randomUUID(), identifiant, etablissement.strip(), latitude, longitude, rayonM,
                horloge.instant(), null);
        depot.ajouter(passerelle);
        journal.consigner(agentId, ROLE, "PASSERELLE_LORAWAN_INSTALLEE", "PASSERELLE_LORAWAN", identifiant, Resultat.SUCCES);
        return vue(passerelle, horloge.instant());
    }

    @Transactional
    public void retirer(UUID agentId, UUID id) {
        Passerelle passerelle = depot.parId(id)
                .orElseThrow(() -> new ErreurMetier(CodeErreur.RESSOURCE_INTROUVABLE, "Passerelle introuvable."));
        depot.retirer(id, horloge.instant());
        journal.consigner(agentId, ROLE, "PASSERELLE_LORAWAN_RETIREE", "PASSERELLE_LORAWAN", passerelle.eui(), Resultat.SUCCES);
    }

    /**
     * Trame remise par le serveur de réseau.
     *
     * @throws ErreurMetier ACCES_REFUSE si l'appel ne porte pas le sceau du serveur de réseau
     */
    @Transactional(noRollbackFor = ErreurMetier.class)
    public Issue recevoir(String entete, byte[] corps) {
        Instant maintenant = horloge.instant();
        if (sceau.isEmpty() || sceau.get().refus(entete, corps, maintenant) != null) {
            journal.consigner(null, "SYSTEME", "APPEL_SERVEUR_LORAWAN_REJETE", "PASSERELLE_LORAWAN", null, Resultat.REFUS);
            throw new ErreurMetier(CodeErreur.ACCES_REFUSE, "Appel non authentifié.");
        }
        Issue issue = traiter(corps, maintenant);
        metriques.counter("fasoguardian.lorawan.trames", "issue", issue.name()).increment();
        return issue;
    }

    private Issue traiter(byte[] corps, Instant maintenant) {
        JsonNode trame;
        try {
            trame = json.readTree(corps);
        } catch (JacksonException erreur) {
            return Issue.REJETE;
        }
        if (trame == null || !trame.path("passerelle").isString()) {
            return Issue.REJETE;
        }
        Optional<Passerelle> connue = depot.parEui(trame.path("passerelle").asString().strip().toUpperCase(Locale.ROOT));
        if (connue.isEmpty()) {
            return Issue.REJETE;
        }
        Passerelle passerelle = connue.get();
        depot.noterVue(passerelle.id(), maintenant);
        if (trame.path("bracelet").isMissingNode() || trame.path("bracelet").isNull()) {
            return Issue.ACCEPTE;
        }
        if (!trame.path("bracelet").isString() || !trame.path("compteur").isIntegralNumber() || !trame.path("t").isIntegralNumber()) {
            return Issue.REJETE;
        }
        return switch (ingestion.presence(trame.path("bracelet").asString(), Instant.ofEpochSecond(trame.path("t").asLong()),
                trame.path("compteur").asLong(), passerelle.latitude(), passerelle.longitude(), passerelle.rayonM())) {
            case ACCEPTE -> Issue.ACCEPTE;
            case DOUBLON -> Issue.DOUBLON;
            default -> Issue.REJETE;
        };
    }

    private static Vue vue(Passerelle p, Instant maintenant) {
        return new Vue(p.id(), p.eui(), p.etablissement(), p.latitude(), p.longitude(), p.rayonM(), p.creeeLe(), p.vueLe(),
                p.enLigne(maintenant));
    }
}
