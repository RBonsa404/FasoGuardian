package bf.fasoguardian.simulateur;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.security.Signature;
import java.util.Base64;
import java.util.Locale;

/**
 * Contrôle d'une demande de mise à jour, tel que le logiciel embarqué doit le faire (US-PAR-013) : l'image
 * n'est installée que si son manifeste, qui lie la version à la taille et à l'empreinte, porte la signature
 * de la clé de publication. La commande de la plateforme ne suffit pas : elle transporte le manifeste, elle
 * ne le remplace pas.
 */
final class ManifesteOta {

    private ManifesteOta() {
    }

    static String texte(String version, long tailleOctets, String sha256) {
        return "FG-OTA|" + version + "|" + tailleOctets + "|" + sha256.toLowerCase(Locale.ROOT);
    }

    /** @param clePublication clé publique de publication du logiciel ; sans elle, aucune image n'est acceptée */
    static boolean valide(PublicKey clePublication, String version, long tailleOctets, String sha256, String signature) {
        if (clePublication == null || version == null || sha256 == null || signature == null || tailleOctets <= 0) {
            return false;
        }
        try {
            Signature verification = Signature.getInstance("SHA256withECDSAinP1363Format");
            verification.initVerify(clePublication);
            verification.update(texte(version, tailleOctets, sha256).getBytes(StandardCharsets.UTF_8));
            return verification.verify(Base64.getUrlDecoder().decode(signature));
        } catch (GeneralSecurityException | IllegalArgumentException erreur) {
            return false;
        }
    }
}
