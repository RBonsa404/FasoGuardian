package bf.fasoguardian.dispositifs.infrastructure;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

import bf.fasoguardian.dispositifs.application.Signataire;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Signature des commandes par la clé de la plateforme (ECDSA P-256), fournie par le coffre de secrets. La clé
 * publique correspondante est inscrite dans le logiciel embarqué. Un serveur relié au broker ne démarre pas
 * sans cette clé : il ne pourrait commander aucun bracelet.
 */
@Component
class SignataireEcdsa implements Signataire {

    private final PrivateKey cle;

    SignataireEcdsa(@Value("${fasoguardian.commandes.cle-privee:}") String clePkcs8Base64,
            @Value("${fasoguardian.mqtt.url:}") String adresseBroker) throws GeneralSecurityException {
        if (clePkcs8Base64.isBlank()) {
            if (!adresseBroker.isBlank()) {
                throw new IllegalStateException(
                        "fasoguardian.commandes.cle-privee (FG_CLE_COMMANDES) est requise dès que le serveur est relié au broker");
            }
            this.cle = null;
        } else {
            this.cle = KeyFactory.getInstance("EC")
                    .generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(clePkcs8Base64.strip())));
        }
    }

    @Override
    public byte[] signer(byte[] contenu) {
        if (cle == null) {
            throw new IllegalStateException("Clé de signature des commandes absente");
        }
        try {
            Signature signature = Signature.getInstance("SHA256withECDSAinP1363Format");
            signature.initSign(cle);
            signature.update(contenu);
            return signature.sign();
        } catch (GeneralSecurityException erreur) {
            throw new IllegalStateException("Signature de commande impossible", erreur);
        }
    }
}
