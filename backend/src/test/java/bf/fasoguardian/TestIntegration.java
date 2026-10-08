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
