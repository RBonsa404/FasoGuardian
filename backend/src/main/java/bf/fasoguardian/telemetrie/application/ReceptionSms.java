package bf.fasoguardian.telemetrie.application;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import bf.fasoguardian.audit.JournalAudit;
import bf.fasoguardian.audit.JournalAudit.Resultat;
import bf.fasoguardian.dispositifs.Bracelets;
import bf.fasoguardian.plateforme.erreurs.CodeErreur;
import bf.fasoguardian.plateforme.erreurs.ErreurMetier;
import bf.fasoguardian.plateforme.securite.SceauWebhook;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Canal de repli SMS (US-SYS-001, FG-DOC-06 §6.4.3, FG-DOC-08 §8.3). Sans données mobiles, le bracelet envoie
 * son alerte par SMS à la passerelle, qui la remet ici. Le SMS est signé par l'élément sécurisé du bracelet :
 * une signature invalide est rejetée et journalisée, une signature valide donne un événement traité exactement
 * comme un message MQTT.
 *
 * <p>Format : {@code FG1|<bracelet>|ALR|<événement>|<lat>,<lon>|<source>|<batterie>|<secondes Unix>|<signature>}.
 * La signature (ECDSA P-256, R‖S en base64url) porte sur tout ce qui la précède, dernier séparateur compris.
 */
@Service
public class ReceptionSms {

    public enum Issue {
        ACCEPTE,
        /** Déjà reçu, par SMS ou par MQTT. */
        DOUBLON,
        /** Format inconnu, bracelet inconnu ou hors service, signature invalide, date irrecevable. */
        REJETE
    }

    private static final String VERSION = "FG1";
    private static final int CHAMPS = 9;
    /** Événements admis par SMS : ceux qui ne peuvent pas attendre le retour des données. */
    private static final Map<String, String> EVENEMENTS = Map.of("SOS", "sos", "STRAP", "strap", "SKIN", "skin", "FALL", "fall",
            "BATCRIT", "batcrit");

    private final Bracelets bracelets;
    private final Ingestion ingestion;
    private final JournalAudit journal;
    private final JsonMapper json;
    private final MeterRegistry metriques;
    private final Clock horloge;
    private final Optional<SceauWebhook> sceau;

    ReceptionSms(Bracelets bracelets, Ingestion ingestion, JournalAudit journal, JsonMapper json, MeterRegistry metriques,
            Clock horloge, @Value("${fasoguardian.sms.secret-passerelle:}") String secret) {
        this.bracelets = bracelets;
        this.ingestion = ingestion;
        this.journal = journal;
        this.json = json;
        this.metriques = metriques;
        this.horloge = horloge;
        // Sans secret configuré, le canal est fermé : aucun SMS entrant n'est accepté.
        this.sceau = secret.isBlank() ? Optional.empty() : Optional.of(new SceauWebhook(secret, "fasoguardian.sms.secret-passerelle"));
    }

    /**
     * Appel de la passerelle : corps {@code {"de":"+226…","texte":"FG1|…"}}, authentifié par son sceau.
     *
     * @throws ErreurMetier ACCES_REFUSE si l'appel ne vient pas de la passerelle
     */
    @Transactional(noRollbackFor = ErreurMetier.class)
    public Issue recevoir(String entete, byte[] corps) {
        if (sceau.isEmpty() || sceau.get().refus(entete, corps, horloge.instant()) != null) {
            journal.consigner(null, "SYSTEME", "APPEL_PASSERELLE_SMS_REJETE", "PASSERELLE_SMS", null, Resultat.REFUS);
            throw new ErreurMetier(CodeErreur.ACCES_REFUSE, "Appel non authentifié.");
        }
        String texte;
        try {
            JsonNode contenu = json.readTree(corps);
            texte = contenu.path("texte").asString("");
        } catch (JacksonException erreur) {
            texte = "";
        }
        Issue issue = traiter(texte.strip());
        metriques.counter("fasoguardian.sms.entrants", "issue", issue.name()).increment();
        return issue;
    }

    private Issue traiter(String sms) {
        String[] champs = sms.split("\\|", -1);
        if (champs.length != CHAMPS || !VERSION.equals(champs[0]) || !"ALR".equals(champs[2])
                || !EVENEMENTS.containsKey(champs[3]) || !champs[7].matches("\\d{9,11}")) {
            return rejeter(null);
        }
        String bracelet = champs[1];
        byte[] signe = sms.substring(0, sms.lastIndexOf('|') + 1).getBytes(StandardCharsets.UTF_8);
        byte[] signature;
        try {
            signature = Base64.getUrlDecoder().decode(champs[8]);
        } catch (IllegalArgumentException erreur) {
            return rejeter(bracelet);
        }
        if (!bracelets.signatureValide(bracelet, signe, signature)) {
            return rejeter(bracelet);
        }
        // Converti en message d'alerte standard : le numéro de séquence est l'heure de l'événement.
        StringBuilder message = new StringBuilder("{\"t\":").append(champs[7]).append(",\"seq\":").append(champs[7])
                .append(",\"ev\":\"").append(EVENEMENTS.get(champs[3])).append('"');
        String[] position = champs[4].split(",");
        if (position.length == 2 && position[0].matches("-?\\d{1,2}(\\.\\d{1,6})?") && position[1].matches("-?\\d{1,3}(\\.\\d{1,6})?")) {
            message.append(",\"lat\":").append(position[0]).append(",\"lon\":").append(position[1]);
        }
        message.append('}');
        return switch (ingestion.alerte(bracelet, message.toString().getBytes(StandardCharsets.UTF_8))) {
            case ACCEPTE -> {
                journal.consigner(null, "SYSTEME", "ALERTE_RECUE_PAR_SMS", "BRACELET", bracelet.toUpperCase(Locale.ROOT), Resultat.SUCCES);
                yield Issue.ACCEPTE;
            }
            case DOUBLON -> Issue.DOUBLON;
            default -> rejeter(bracelet);
        };
    }

    private Issue rejeter(String bracelet) {
        journal.consigner(null, "SYSTEME", "SMS_BRACELET_REJETE", "BRACELET", bracelet == null || bracelet.length() > 16 ? null : bracelet,
                Resultat.REFUS);
        return Issue.REJETE;
    }
}
