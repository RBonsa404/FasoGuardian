package bf.fasoguardian.abonnements.infrastructure;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import bf.fasoguardian.abonnements.application.AgregateurPaiement;
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
 * <p>Signature d'une notification : en-tête {@code t=<secondes Unix>,v1=<HMAC-SHA-256 hexadécimal>}, le sceau
 * portant sur {@code <t>.<corps>} avec le secret partagé. Une notification datée de plus de cinq minutes est
 * refusée : une notification interceptée ne peut pas être rejouée plus tard.
 */
@Component
@ConditionalOnProperty(name = "fasoguardian.paiements.adaptateur", havingValue = "bac-a-sable")
public class AgregateurBacASable implements AgregateurPaiement {

    private static final Duration TOLERANCE = Duration.ofMinutes(5);
    private static final int CAPACITE = 500;
    private static final int LONGUEUR_MINIMALE_DU_SECRET = 32;

    /** @param refusee le portefeuille refusera la demande (numéro de test se terminant par 00) */
    public record DemandeRecue(String reference, int montantFcfa, boolean refusee, Instant recueLe) {
    }

    public record NotificationSignee(String signature, byte[] corps) {
    }

    private final Map<String, DemandeRecue> enAttente = new LinkedHashMap<>();
    private final SecureRandom alea = new SecureRandom();
    private final SecretKeySpec secret;
    private final JsonMapper json;
    private final Clock horloge;

    AgregateurBacASable(@Value("${fasoguardian.paiements.secret-webhook:}") String secret, JsonMapper json, Clock horloge) {
        if (secret.length() < LONGUEUR_MINIMALE_DU_SECRET) {
            throw new IllegalStateException("Secret des notifications de paiement absent ou trop court : "
                    + LONGUEUR_MINIMALE_DU_SECRET + " caractères au moins (fasoguardian.paiements.secret-webhook)");
        }
        this.secret = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
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
        if (signature == null || corps == null) {
            throw new NotificationRejetee("signature absente");
        }
        String horodatage = null;
        String sceau = null;
        for (String partie : signature.split(",")) {
            if (partie.startsWith("t=")) {
                horodatage = partie.substring(2);
            } else if (partie.startsWith("v1=")) {
                sceau = partie.substring(3);
            }
        }
        if (horodatage == null || sceau == null || !horodatage.matches("\\d{1,12}")) {
            throw new NotificationRejetee("signature mal formée");
        }
        if (Duration.between(Instant.ofEpochSecond(Long.parseLong(horodatage)), horloge.instant()).abs().compareTo(TOLERANCE) > 0) {
            throw new NotificationRejetee("notification trop ancienne");
        }
        // Comparaison en temps constant : la durée de la réponse ne renseigne pas sur le sceau attendu.
        if (!MessageDigest.isEqual(sceller(horodatage, corps).getBytes(StandardCharsets.US_ASCII),
                sceau.getBytes(StandardCharsets.US_ASCII))) {
            throw new NotificationRejetee("signature fausse");
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
        String horodatage = Long.toString(horloge.instant().getEpochSecond());
        return new NotificationSignee("t=" + horodatage + ",v1=" + sceller(horodatage, corps), corps);
    }

    private String sceller(String horodatage, byte[] corps) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(secret);
            mac.update((horodatage + ".").getBytes(StandardCharsets.US_ASCII));
            return HexFormat.of().formatHex(mac.doFinal(corps));
        } catch (GeneralSecurityException erreur) {
            throw new IllegalStateException("Calcul du sceau impossible", erreur);
        }
    }
}
