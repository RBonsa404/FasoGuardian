package bf.fasoguardian.abonnements.infrastructure;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import bf.fasoguardian.abonnements.application.AgregateurPaiement;
import bf.fasoguardian.plateforme.securite.SceauWebhook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Agrégateur de paiement « bac à sable » : ne débite aucun portefeuille. Il garde en mémoire les demandes
 * reçues et fabrique, pour chacune, la notification signée qu'un agrégateur réel enverrait. Activé par
 * {@code fasoguardian.paiements.adaptateur=bac-a-sable} ; sans adaptateur configuré, le serveur ne démarre pas.
 *
 * <p>Chaque notification est authentifiée par un {@link SceauWebhook} calculé avec le secret partagé.
 */
@Component
@ConditionalOnProperty(name = "fasoguardian.paiements.adaptateur", havingValue = "bac-a-sable")
public class AgregateurBacASable implements AgregateurPaiement {

    private static final int CAPACITE = 500;

    /** @param refusee le portefeuille refusera la demande (numéro de test se terminant par 00) */
    public record DemandeRecue(String reference, int montantFcfa, boolean refusee, Instant recueLe) {
    }

    public record NotificationSignee(String signature, byte[] corps) {
    }

    private final Map<String, DemandeRecue> enAttente = new LinkedHashMap<>();
    private final SecureRandom alea = new SecureRandom();
    private final SceauWebhook sceau;
    private final JsonMapper json;
    private final Clock horloge;

    AgregateurBacASable(@Value("${fasoguardian.paiements.secret-webhook:}") String secret, JsonMapper json, Clock horloge) {
        this.sceau = new SceauWebhook(secret, "fasoguardian.paiements.secret-webhook");
        this.json = json;
        this.horloge = horloge;
    }

    @Override
    public synchronized String initier(Demande demande) {
        byte[] octets = new byte[6];
        alea.nextBytes(octets);
        String reference = "BAS-" + HexFormat.of().withUpperCase().formatHex(octets);
        if (enAttente.size() == CAPACITE) {
            enAttente.remove(enAttente.keySet().iterator().next());
        }
        // Le numéro du portefeuille n'est pas gardé : seule la convention de test « se termine par 00 » en est tirée.
        enAttente.put(reference, new DemandeRecue(reference, demande.montantFcfa(), demande.numeroE164().endsWith("00"),
                horloge.instant()));
        return reference;
    }

    @Override
    public Notification lire(String signature, byte[] corps) {
        String refus = sceau.refus(signature, corps, horloge.instant());
        if (refus != null) {
            throw new NotificationRejetee(refus);
        }
        try {
            JsonNode contenu = json.readTree(corps);
            String reference = contenu.path("reference").asString("");
            String statut = contenu.path("statut").asString("");
            if (reference.isEmpty() || !(statut.equals("SUCCES") || statut.equals("ECHEC"))) {
                throw new NotificationRejetee("contenu incomplet");
            }
            return new Notification(reference, statut.equals("SUCCES"), contenu.path("montant").asInt(0),
                    contenu.path("motif").asString(null));
        } catch (JacksonException erreur) {
            throw new NotificationRejetee("contenu illisible");
        }
    }

    /** Demandes auxquelles l'opérateur simulé n'a pas encore répondu, les plus anciennes d'abord. */
    public synchronized List<DemandeRecue> enAttente() {
        return List.copyOf(enAttente.values());
    }

    /**
     * La notification signée que l'agrégateur enverrait pour cette demande, qui cesse alors d'être en attente.
     *
     * @param montantFcfa montant annoncé, pour simuler aussi un écart avec le montant demandé
     */
    public synchronized NotificationSignee notification(String reference, boolean succes, int montantFcfa, String motif) {
        enAttente.remove(reference);
        Map<String, Object> contenu = new LinkedHashMap<>();
        contenu.put("reference", reference);
        contenu.put("statut", succes ? "SUCCES" : "ECHEC");
        contenu.put("montant", montantFcfa);
        if (motif != null) {
            contenu.put("motif", motif);
        }
        byte[] corps = json.writeValueAsBytes(contenu);
        return new NotificationSignee(sceau.sceller(corps, horloge.instant()), corps);
    }
}
