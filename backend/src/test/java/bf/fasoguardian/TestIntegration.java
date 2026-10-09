package bf.fasoguardian;

import java.security.SecureRandom;
import java.util.Base64;

import bf.fasoguardian.plateforme.chiffrement.CategorieDonnee;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base des tests d'intégration : serveur complet sur un PostgreSQL 17 / PostGIS 3.5 jetable,
 * partagé par toutes les classes de test et supprimé par Testcontainers en fin d'exécution.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class TestIntegration {

    protected static final String ADMIN_IDENTIFIANT = "admin.test";
    protected static final String ADMIN_MOT_DE_PASSE = "mot-de-passe-de-test-" + java.util.UUID.randomUUID();

    /** Paire de clés jetable de la plateforme : la clé publique sert aux tests à vérifier les commandes signées. */
    protected static final java.security.KeyPair CLE_COMMANDES = cleDeSignature();

    private static java.security.KeyPair cleDeSignature() {
        try {
            java.security.KeyPairGenerator generateur = java.security.KeyPairGenerator.getInstance("EC");
            generateur.initialize(new java.security.spec.ECGenParameterSpec("secp256r1"));
            return generateur.generateKeyPair();
        } catch (java.security.GeneralSecurityException erreur) {
            throw new IllegalStateException(erreur);
        }
    }

    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("postgis/postgis:17-3.5").asCompatibleSubstituteFor("postgres"));

    static {
        postgres.start();
    }

    /** Clés de chiffrement jetables, tirées au hasard à chaque exécution des tests. */
    @DynamicPropertySource
    static void clesDeChiffrement(DynamicPropertyRegistry registre) {
        registre.add("fasoguardian.chiffrement.cle-empreinte", TestIntegration::cleAleatoire);
        registre.add("fasoguardian.jetons.secret", TestIntegration::cleAleatoire);
        registre.add("fasoguardian.sms.adaptateur", () -> "bac-a-sable");
        registre.add("fasoguardian.commandes.cle-privee",
                () -> Base64.getEncoder().encodeToString(CLE_COMMANDES.getPrivate().getEncoded()));
        // Délai plancher de la page publique QR réduit pour les tests.
        registre.add("fasoguardian.qr.delai-minimal", () -> "PT0.08S");
        registre.add("fasoguardian.amorcage.admin.identifiant", () -> ADMIN_IDENTIFIANT);
        registre.add("fasoguardian.amorcage.admin.mot-de-passe", () -> ADMIN_MOT_DE_PASSE);
        for (CategorieDonnee categorie : CategorieDonnee.values()) {
            String cle = cleAleatoire();
            registre.add("fasoguardian.chiffrement.cles." + categorie + ".1", () -> cle);
        }
    }

    private static String cleAleatoire() {
        byte[] octets = new byte[32];
        new SecureRandom().nextBytes(octets);
        return Base64.getEncoder().encodeToString(octets);
    }
}
