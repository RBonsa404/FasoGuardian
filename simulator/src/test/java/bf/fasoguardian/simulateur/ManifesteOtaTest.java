package bf.fasoguardian.simulateur;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Le bracelet n'installe qu'une image dont le manifeste est signé par la clé de publication (US-PAR-013). */
class ManifesteOtaTest {

    private static final String SHA = "9f2c5a1e7b3d4c6f8a0b1c2d3e4f5a6b7c8d9e0f1a2b3c4d5e6f7a8b9c0d1e2f";

    static KeyPair publication;
    static KeyPair autre;

    @BeforeAll
    static void cles() throws Exception {
        KeyPairGenerator generateur = KeyPairGenerator.getInstance("EC");
        generateur.initialize(new ECGenParameterSpec("secp256r1"));
        publication = generateur.generateKeyPair();
        autre = generateur.generateKeyPair();
    }

    private static String signer(KeyPair cle, String texte) throws Exception {
        Signature signature = Signature.getInstance("SHA256withECDSAinP1363Format");
        signature.initSign(cle.getPrivate());
        signature.update(texte.getBytes(StandardCharsets.UTF_8));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(signature.sign());
    }

    @Test
    void accepteUnManifesteSigneParLaCleDePublication() throws Exception {
        String signature = signer(publication, ManifesteOta.texte("2.4.2", 421_888, SHA));

        assertThat(ManifesteOta.valide(publication.getPublic(), "2.4.2", 421_888, SHA, signature)).isTrue();
        // L'empreinte se compare sans tenir compte de la casse.
        assertThat(ManifesteOta.valide(publication.getPublic(), "2.4.2", 421_888, SHA.toUpperCase(), signature)).isTrue();
    }

    @Test
    void refuseUneImageDontLaVersionLaTailleOuLEmpreinteNeSontPasCellesQuiOntEteSignees() throws Exception {
        String signature = signer(publication, ManifesteOta.texte("2.4.2", 421_888, SHA));

        assertThat(ManifesteOta.valide(publication.getPublic(), "2.4.3", 421_888, SHA, signature)).isFalse();
        assertThat(ManifesteOta.valide(publication.getPublic(), "2.4.2", 421_889, SHA, signature)).isFalse();
        assertThat(ManifesteOta.valide(publication.getPublic(), "2.4.2", 421_888, SHA.replace('9', '8'), signature)).isFalse();
    }

    @Test
    void refuseUneAutreCleUneSignatureIllisibleOuLAbsenceDeCle() throws Exception {
        String texte = ManifesteOta.texte("2.4.2", 421_888, SHA);

        assertThat(ManifesteOta.valide(publication.getPublic(), "2.4.2", 421_888, SHA, signer(autre, texte))).isFalse();
        assertThat(ManifesteOta.valide(publication.getPublic(), "2.4.2", 421_888, SHA, "pas-une-signature")).isFalse();
        assertThat(ManifesteOta.valide(publication.getPublic(), "2.4.2", 421_888, SHA, null)).isFalse();
        assertThat(ManifesteOta.valide(null, "2.4.2", 421_888, SHA, signer(publication, texte))).isFalse();
    }

    @Test
    void leBraceletAnnonceLaVersionInstalleeApresRedemarrage() {
        BraceletSimule bracelet = new BraceletSimule("FG-DEV-0001", 1);

        bracelet.installer("2.4.2");

        assertThat(bracelet.etat(true)).isEqualTo("{\"online\":true,\"fw\":\"2.4.2\"}");
    }
}
