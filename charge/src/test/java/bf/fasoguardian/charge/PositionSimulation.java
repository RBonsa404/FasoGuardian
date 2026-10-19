package bf.fasoguardian.charge;

import static io.gatling.javaapi.core.CoreDsl.StringBody;
import static io.gatling.javaapi.core.CoreDsl.constantUsersPerSec;
import static io.gatling.javaapi.core.CoreDsl.details;
import static io.gatling.javaapi.core.CoreDsl.exec;
import static io.gatling.javaapi.core.CoreDsl.global;
import static io.gatling.javaapi.core.CoreDsl.jsonPath;
import static io.gatling.javaapi.core.CoreDsl.rampUsersPerSec;
import static io.gatling.javaapi.core.CoreDsl.repeat;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

import java.time.Duration;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;

/**
 * Parcours le plus sollicité de l'application Parents : ouvrir l'accueil, c'est lire la dernière position de
 * l'enfant, puis la relire à chaque rafraîchissement. Seuil de FG-DOC-06 (tableau 9) : réponse de l'API position
 * sous 500 ms au 95e centile, côté serveur. La simulation échoue si le seuil n'est pas tenu.
 *
 * <p>Compte utilisé : celui de la démonstration (données fictives, {@code sh frontend/e2e/pile.sh demo}), ou un
 * autre compte de test passé par les propriétés {@code telephone}, {@code motDePasse} et {@code base}. Charge :
 * {@code parents} parents par seconde au plateau, pendant {@code duree} secondes.
 */
public class PositionSimulation extends Simulation {

    private static final String BASE = System.getProperty("base", "http://localhost:8080");
    private static final String TELEPHONE = System.getProperty("telephone", "70000001");
    private static final String MOT_DE_PASSE = System.getProperty("motDePasse", "soleil-de-ouaga-2026");
    private static final double PARENTS_PAR_SECONDE = Double.parseDouble(System.getProperty("parents", "5"));
    private static final long DUREE_S = Long.parseLong(System.getProperty("duree", "120"));
    /** Rafraîchissements de la position par visite, toutes les 3 secondes comme un parent qui suit la carte. */
    private static final int RAFRAICHISSEMENTS = 10;

    private final HttpProtocolBuilder protocole = http.baseUrl(BASE)
            .acceptHeader("application/json")
            .contentTypeHeader("application/json")
            .userAgentHeader("FasoGuardian-charge");

    private final ScenarioBuilder parent = scenario("Parent qui suit son enfant")
            .exec(http("connexion").post("/api/v1/auth/connexion")
                    .body(StringBody("{\"telephone\":\"" + TELEPHONE + "\",\"motDePasse\":\"" + MOT_DE_PASSE + "\"}"))
                    .check(status().is(200), jsonPath("$.jetonAcces").saveAs("jeton")))
            .exec(http("mes enfants").get("/api/v1/enfants")
                    .header("Authorization", "Bearer #{jeton}")
                    .check(status().is(200), jsonPath("$[0].id").saveAs("enfant")))
            .exec(repeat(RAFRAICHISSEMENTS).on(exec(http("position").get("/api/v1/enfants/#{enfant}/position")
                    .header("Authorization", "Bearer #{jeton}")
                    .check(status().is(200)))
                    .pause(Duration.ofSeconds(3))));

    {
        setUp(parent.injectOpen(
                rampUsersPerSec(0.5).to(PARENTS_PAR_SECONDE).during(Duration.ofSeconds(30)),
                constantUsersPerSec(PARENTS_PAR_SECONDE).during(Duration.ofSeconds(DUREE_S))))
                .protocols(protocole)
                .assertions(
                        details("position").responseTime().percentile(95.0).lt(500),
                        global().failedRequests().percent().lt(1.0));
    }
}
