package bf.fasoguardian.plateforme.securite;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Authentification d'un appel entrant par secret partagé : en-tête {@code t=<secondes Unix>,v1=<HMAC-SHA-256
 * hexadécimal>}, le sceau portant sur {@code <t>.<corps>}. Un appel daté de plus de cinq minutes est refusé :
 * un appel intercepté ne peut pas être rejoué plus tard.
 */
public final class SceauWebhook {

    private static final Duration TOLERANCE = Duration.ofMinutes(5);
    private static final int LONGUEUR_MINIMALE_DU_SECRET = 32;

    private final SecretKeySpec secret;

    /** @param propriete nom de la propriété de configuration, pour le message d'erreur au démarrage */
    public SceauWebhook(String secret, String propriete) {
        if (secret == null || secret.length() < LONGUEUR_MINIMALE_DU_SECRET) {
            throw new IllegalStateException("Secret absent ou trop court : " + LONGUEUR_MINIMALE_DU_SECRET
                    + " caractères au moins (" + propriete + ")");
        }
        this.secret = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    /** En-tête à joindre à un corps émis à l'instant donné. */
    public String sceller(byte[] corps, Instant maintenant) {
        String horodatage = Long.toString(maintenant.getEpochSecond());
        return "t=" + horodatage + ",v1=" + calculer(horodatage, corps);
    }

    /** @return {@code null} si l'en-tête authentifie le corps, sinon la raison du refus */
    public String refus(String entete, byte[] corps, Instant maintenant) {
        if (entete == null || corps == null) {
            return "signature absente";
        }
        String horodatage = null;
        String sceau = null;
        for (String partie : entete.split(",")) {
            if (partie.startsWith("t=")) {
                horodatage = partie.substring(2);
            } else if (partie.startsWith("v1=")) {
                sceau = partie.substring(3);
            }
        }
        if (horodatage == null || sceau == null || !horodatage.matches("\\d{1,12}")) {
            return "signature mal formée";
        }
        if (Duration.between(Instant.ofEpochSecond(Long.parseLong(horodatage)), maintenant).abs().compareTo(TOLERANCE) > 0) {
            return "appel trop ancien";
        }
        // Comparaison en temps constant : la durée de la réponse ne renseigne pas sur le sceau attendu.
        return MessageDigest.isEqual(calculer(horodatage, corps).getBytes(StandardCharsets.US_ASCII),
                sceau.getBytes(StandardCharsets.US_ASCII)) ? null : "signature fausse";
    }

    private String calculer(String horodatage, byte[] corps) {
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
