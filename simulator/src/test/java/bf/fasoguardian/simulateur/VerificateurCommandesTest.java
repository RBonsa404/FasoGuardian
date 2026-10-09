package bf.fasoguardian.simulateur;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.util.Base64;

import bf.fasoguardian.simulateur.VerificateurCommandes.Rejet;
import bf.fasoguardian.simulateur.VerificateurCommandes.Resultat;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Le bracelet n'exécute qu'une commande signée par la plateforme, pour lui, récente et jamais vue (US-SYS-011). */
class VerificateurCommandesTest {

    private static final Instant MAINTENANT = Instant.parse("2026-10-09T10:00:00Z");
    private static final String ID = "3f0c2b9e-4f55-4c0e-9d3a-0e8a1b2c3d4e";
    private static KeyPair plateforme;
    private static KeyPair intrus;

    @BeforeAll
    static void genererLesCles() throws Exception {
        KeyPairGenerator generateur = KeyPairGenerator.getInstance("EC");
        generateur.initialize(new ECGenParameterSpec("secp256r1"));
        plateforme = generateur.generateKeyPair();
        intrus = generateur.generateKeyPair();
    }

    @Test
    void uneCommandeSigneePourCeBraceletEstAccepteeEtSesParametresLus() throws Exception {
        VerificateurCommandes verificateur = new VerificateurCommandes("FG-2291", plateforme.getPublic());

        Resultat resultat = verificateur.verifier(message(corps(ID, "FG-2291", "rm", 100, 600, "{\"until\":1791540000}"), plateforme.getPrivate()), MAINTENANT);

        assertThat(resultat.acceptee()).isTrue();
        assertThat(resultat.commande().id()).isEqualTo(ID);
        assertThat(resultat.commande().code()).isEqualTo("rm");
        assertThat(resultat.commande().parametre("until", 0)).isEqualTo(1_791_540_000L);
        assertThat(resultat.commande().parametre("absent", -1)).isEqualTo(-1);
    }

    @Test
    void uneCommandeNonSigneeParLaPlateformeEstRejeteeEtSonIdentifiantSignale() throws Exception {
        VerificateurCommandes verificateur = new VerificateurCommandes("FG-2291", plateforme.getPublic());
        String corps = corps(ID, "FG-2291", "alert", 100, 600, "{\"on\":0}");

        Resultat parUnIntrus = verificateur.verifier(message(corps, intrus.getPrivate()), MAINTENANT);
        assertThat(parUnIntrus.rejet()).isEqualTo(Rejet.SIGNATURE);
        assertThat(parUnIntrus.idLu()).isEqualTo(ID);

        // Corps modifié après signature : couper le mode alerte devient « on » à 1.
        String signature = message(corps, plateforme.getPrivate()).split("\\.")[1];
        String falsifie = base64(corps.replace("\"on\":0", "\"on\":1")) + "." + signature;
        assertThat(verificateur.verifier(falsifie, MAINTENANT).rejet()).isEqualTo(Rejet.SIGNATURE);

        assertThat(new VerificateurCommandes("FG-2291", null).verifier(message(corps, plateforme.getPrivate()), MAINTENANT).rejet())
                .as("sans clé de la plateforme, rien n'est authentifiable").isEqualTo(Rejet.SIGNATURE);
    }

    @Test
    void uneCommandeDestineeAUnAutreBraceletExpireeOuRejoueeEstRejetee() throws Exception {
        VerificateurCommandes verificateur = new VerificateurCommandes("FG-2291", plateforme.getPublic());

        assertThat(verificateur.verifier(message(corps(ID, "FG-9999", "loc", 100, 600, "{}"), plateforme.getPrivate()), MAINTENANT).rejet())
                .isEqualTo(Rejet.DESTINATAIRE);
        assertThat(verificateur.verifier(message(corps(ID, "FG-2291", "loc", 100, 0, "{}"), plateforme.getPrivate()), MAINTENANT).rejet())
                .isEqualTo(Rejet.EXPIREE);

        String valide = message(corps(ID, "FG-2291", "loc", 100, 600, "{}"), plateforme.getPrivate());
        assertThat(verificateur.verifier(valide, MAINTENANT).acceptee()).isTrue();
        Resultat reemise = verificateur.verifier(valide, MAINTENANT);
        assertThat(reemise.acceptee()).as("même message : jamais exécuté deux fois").isFalse();
        assertThat(reemise.dejaExecutee()).isTrue();
        assertThat(reemise.accusePositif()).as("réémission après un accusé perdu : accusée de nouveau").isTrue();
        assertThat(verificateur.verifier(message(corps(ID, "FG-2291", "loc", 99, 600, "{}"), plateforme.getPrivate()), MAINTENANT).rejet())
                .as("numéro antérieur").isEqualTo(Rejet.REJEU);
        assertThat(verificateur.verifier(message(corps("11111111-2222-3333-4444-555555555555", "FG-2291", "loc", 100, 600, "{}"),
                plateforme.getPrivate()), MAINTENANT).accusePositif()).as("numéro déjà vu sous un autre identifiant").isFalse();
        assertThat(verificateur.verifier(message(corps(ID, "FG-2291", "loc", 101, 600, "{}"), plateforme.getPrivate()), MAINTENANT).acceptee())
                .isTrue();
    }

    @Test
    void unMessageMalFormeEstRejeteSansErreur() {
        VerificateurCommandes verificateur = new VerificateurCommandes("FG-2291", plateforme.getPublic());

        assertThat(verificateur.verifier(null, MAINTENANT).rejet()).isEqualTo(Rejet.FORMAT);
        assertThat(verificateur.verifier("{\"cmd\":\"loc\"}", MAINTENANT).rejet()).isEqualTo(Rejet.FORMAT);
        assertThat(verificateur.verifier("pas*du*base64.zzz", MAINTENANT).rejet()).isEqualTo(Rejet.FORMAT);
    }

    private static String corps(String id, String destinataire, String code, long numero, long validiteS, String parametres) {
        return "{\"id\":\"" + id + "\",\"dev\":\"" + destinataire + "\",\"cmd\":\"" + code + "\",\"n\":" + numero + ",\"exp\":"
                + (MAINTENANT.getEpochSecond() + validiteS) + ",\"p\":" + parametres + "}";
    }

    private static String message(String corps, PrivateKey cle) throws Exception {
        Signature signature = Signature.getInstance("SHA256withECDSAinP1363Format");
        signature.initSign(cle);
        signature.update(corps.getBytes(StandardCharsets.UTF_8));
        return base64(corps) + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(signature.sign());
    }

    private static String base64(String texte) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(texte.getBytes(StandardCharsets.UTF_8));
    }
}
