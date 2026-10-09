package bf.fasoguardian.geolocalisation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;

import bf.fasoguardian.Acteurs;
import bf.fasoguardian.Acteurs.Carte;
import bf.fasoguardian.Acteurs.Parent;
import bf.fasoguardian.Acteurs.ParentAvecEnfant;
import bf.fasoguardian.TestIntegration;
import bf.fasoguardian.notifications.infrastructure.SmsBacASable;
import bf.fasoguardian.telemetrie.application.Ingestion;
import bf.fasoguardian.telemetrie.application.Ingestion.Resultat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Safe Zones, détection des sorties et trajets (US-PAR-007, US-PAR-008, US-ENF-001). */
class GeolocalisationIT extends TestIntegration {

    /** Zone surveillée tous les jours, toute la journée : les essais ne dépendent pas de l'heure d'exécution. */
    private static final String PLAGE_PERMANENTE = "\"jours\":[1,2,3,4,5,6,7],\"debut\":\"00:00\",\"fin\":\"00:00\"";
    private static final double LAT = 12.3714;
    private static final double LON = -1.5197;

    @Autowired
    MockMvc mvc;

    @Autowired
    SmsBacASable sms;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    Ingestion ingestion;

    Acteurs acteurs;

    @BeforeEach
    void preparer() {
        acteurs = new Acteurs(mvc, sms);
    }

    @Test
    void leParentCreeModifieSuspendReactiveEtSupprimeUneZoneAvecSecondFacteur() throws Exception {
        ParentAvecEnfant famille = acteurs.parentAvecEnfant();
        Parent parent = famille.parent();
        String base = "/api/v1/enfants/" + famille.enfantId() + "/zones";
        String ecole = "\"forme\":\"CERCLE\",\"nom\":\"École Les Manguiers\",\"categorie\":\"ECOLE\",\"centre\":{\"latitude\":"
                + LAT + ",\"longitude\":" + LON + "},\"rayonM\":220,\"jours\":[1,2,3,4,5],\"debut\":\"07:00\",\"fin\":\"17:30\",\"toleranceS\":300";

        json(post(base), "{" + ecole + "}", parent.jeton()).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SECOND_FACTEUR_REQUIS"));
        String id = Acteurs.extraire(json(post(base), "{" + ecole + "," + code(parent) + "}", parent.jeton())
                .andExpect(status().isCreated()).andExpect(jsonPath("$.statut").value("ACTIVE"))
                .andExpect(jsonPath("$.rayonM").value(220)).andExpect(jsonPath("$.centre.latitude").value(LAT))
                .andReturn().getResponse().getContentAsString(), "\"id\":\"([0-9a-f-]{36})\"");

        // Polygone, plage chevauchant minuit
        json(post(base), "{\"forme\":\"POLYGONE\",\"nom\":\"Maison\",\"categorie\":\"MAISON\",\"sommets\":["
                + "{\"latitude\":12.300,\"longitude\":-1.500},{\"latitude\":12.300,\"longitude\":-1.498},"
                + "{\"latitude\":12.302,\"longitude\":-1.498},{\"latitude\":12.302,\"longitude\":-1.500}],"
                + "\"jours\":[1,2,3,4,5,6,7],\"debut\":\"19:00\",\"fin\":\"06:30\",\"toleranceS\":0," + code(parent) + "}", parent.jeton())
                .andExpect(status().isCreated()).andExpect(jsonPath("$.sommets.length()").value(4))
                .andExpect(jsonPath("$.sommets[2].latitude").value(12.302));

        avec(get(base), parent.jeton()).andExpect(status().isOk()).andExpect(jsonPath("$.maximum").value(3))
                .andExpect(jsonPath("$.zones.length()").value(2)).andExpect(jsonPath("$.zones[0].nom").value("École Les Manguiers"))
                .andExpect(jsonPath("$.zones[0].jours.length()").value(5)).andExpect(jsonPath("$.zones[0].debut").value("07:00"));

        // Modification : rayon et tolérance
        json(put(base + "/" + id), "{" + ecole.replace("\"rayonM\":220", "\"rayonM\":300").replace("\"toleranceS\":300", "\"toleranceS\":600")
                + "," + code(parent) + "}", parent.jeton()).andExpect(status().isOk())
                .andExpect(jsonPath("$.rayonM").value(300)).andExpect(jsonPath("$.toleranceS").value(600));

        // Suspension avec second facteur, réactivation sans
        json(post(base + "/" + id + "/suspension"), "{}", parent.jeton()).andExpect(status().isForbidden());
        json(post(base + "/" + id + "/suspension"), "{" + code(parent) + "}", parent.jeton()).andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("SUSPENDUE")).andExpect(jsonPath("$.rayonM").value(300))
                .andExpect(jsonPath("$.dansLaPlage").value(false));
        json(post(base + "/" + id + "/reactivation"), "{}", parent.jeton()).andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("ACTIVE"));
        json(post(base + "/" + id + "/reactivation"), "{}", parent.jeton()).andExpect(status().isConflict());

        json(post(base + "/" + id + "/suppression"), "{}", parent.jeton()).andExpect(status().isForbidden());
        json(post(base + "/" + id + "/suppression"), "{" + code(parent) + "}", parent.jeton()).andExpect(status().isNoContent());
        avec(get(base), parent.jeton()).andExpect(jsonPath("$.zones.length()").value(1));
        assertThat(jdbc.queryForList("SELECT action FROM audit.entree WHERE cible_id = ? ORDER BY id", String.class, id))
                .containsExactly("SAFE_ZONE_CREEE", "SAFE_ZONE_MODIFIEE", "SAFE_ZONE_SUSPENDUE", "SAFE_ZONE_REACTIVEE",
                        "SAFE_ZONE_SUPPRIMEE");
    }

    @Test
    void lesSaisiesInvalidesLeQuotaEtLesComptesNonRattachesSontRefuses() throws Exception {
        ParentAvecEnfant famille = acteurs.parentAvecEnfant();
        Parent parent = famille.parent();
        String base = "/api/v1/enfants/" + famille.enfantId() + "/zones";

        json(post(base), "{" + cercle("Trop petite", 20, 0) + "," + code(parent) + "}", parent.jeton())
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("REQUETE_INVALIDE"));
        json(post(base), "{" + cercle("Heure", 100, 0).replace("\"debut\":\"00:00\"", "\"debut\":\"25:00\"") + "," + code(parent)
                + "}", parent.jeton()).andExpect(status().isBadRequest());
        json(post(base), "{" + cercle("Jour", 100, 0).replace("[1,2,3,4,5,6,7]", "[8]") + "," + code(parent) + "}",
                parent.jeton()).andExpect(status().isBadRequest());
        json(post(base), "{\"forme\":\"POLYGONE\",\"nom\":\"Sablier\",\"categorie\":\"AUTRE\",\"sommets\":["
                + "{\"latitude\":12.300,\"longitude\":-1.500},{\"latitude\":12.302,\"longitude\":-1.498},"
                + "{\"latitude\":12.302,\"longitude\":-1.500},{\"latitude\":12.300,\"longitude\":-1.498}]," + PLAGE_PERMANENTE
                + ",\"toleranceS\":0," + code(parent) + "}", parent.jeton()).andExpect(status().isBadRequest());

        for (int i = 1; i <= 3; i++) {
            creer(famille, cercle("Zone " + i, 100, 0));
        }
        json(post(base), "{" + cercle("Zone 4", 100, 0) + "," + code(parent) + "}", parent.jeton())
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ZONES_MAXIMUM_ATTEINT"));

        Parent etranger = acteurs.parent();
        avec(get(base), etranger.jeton()).andExpect(status().isNotFound());
        json(post(base), "{" + cercle("Intrus", 100, 0) + "}", etranger.jeton()).andExpect(status().isNotFound());
        avec(get(base), null).andExpect(status().isUnauthorized());
        avec(get(base), acteurs.jetonSav()).andExpect(status().isForbidden());
        // La zone d'un autre enfant n'est pas atteignable par l'adresse de son propre enfant.
        ParentAvecEnfant autre = acteurs.parentAvecEnfant();
        String zoneDAutrui = creer(autre, cercle("Chez autrui", 100, 0));
        json(post(base + "/" + zoneDAutrui + "/reactivation"), "{}", parent.jeton()).andExpect(status().isNotFound());
    }

    @Test
    void uneSortieEstSignaleeApresLeDelaiDeToleranceEtLeRetourLaLeve() throws Exception {
        ParentAvecEnfant famille = acteurs.parentAvecEnfant();
        Carte carte = equiper(famille);
        String zone = creer(famille, cercle("École", 220, 300));
        long t = Instant.now().getEpochSecond() - 3000;

        recevoir(carte, t, 1, LAT, LON, 8);
        recevoir(carte, t + 60, 2, LAT + 0.01, LON, 8);
        recevoir(carte, t + 240, 3, LAT + 0.01, LON, 8);
        attendreSuivi(zone, t + 240);
        assertThat(franchissements(zone)).as("moins de 5 minutes dehors").isEmpty();
        zones(famille).andExpect(jsonPath("$.zones[0].dansLaPlage").value(true))
                .andExpect(jsonPath("$.zones[0].sortieEnCours").value(false));

        recevoir(carte, t + 420, 4, LAT + 0.01, LON, 8);
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(franchissements(zone)).containsExactly("SORTIE"));
        zones(famille).andExpect(jsonPath("$.zones[0].sortieEnCours").value(true));

        recevoir(carte, t + 720, 5, LAT + 0.01, LON, 8);
        attendreSuivi(zone, t + 720);
        assertThat(franchissements(zone)).as("une seule fois").containsExactly("SORTIE");

        recevoir(carte, t + 900, 6, LAT, LON, 8);
        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(franchissements(zone)).containsExactly("SORTIE", "RETOUR"));
        zones(famille).andExpect(jsonPath("$.zones[0].sortieEnCours").value(false));
    }

    @Test
    void uneZoneSuspendueUnePositionImpreciseOuUnEnfantPasEncoreArriveNeSignalentRien() throws Exception {
        ParentAvecEnfant famille = acteurs.parentAvecEnfant();
        Carte carte = equiper(famille);
        String zone = creer(famille, cercle("Maison", 150, 0));
        long t = Instant.now().getEpochSecond() - 3000;

        // Jamais vu dans la zone : être dehors n'est pas une sortie.
        recevoir(carte, t, 1, LAT + 0.01, LON, 8);
        attendreSuivi(zone, t);
        // Vu dedans, puis une position par antenne relais (± 900 m) dehors : ignorée.
        recevoir(carte, t + 60, 2, LAT, LON, 8);
        recevoir(carte, t + 120, 3, LAT + 0.01, LON, 900);
        attendreSuivi(zone, t + 60);
        assertThat(franchissements(zone)).isEmpty();

        // Suspendue : la même sortie, précise cette fois, ne déclenche rien.
        json(post("/api/v1/enfants/" + famille.enfantId() + "/zones/" + zone + "/suspension"), "{" + code(famille.parent()) + "}",
                famille.parent().jeton()).andExpect(status().isOk());
        recevoir(carte, t + 180, 4, LAT + 0.01, LON, 8);
        await().pollDelay(Duration.ofMillis(800)).atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(
                jdbc.queryForObject("SELECT count(*) FROM geolocalisation.suivi_zone WHERE zone_id = ?::uuid", Integer.class, zone))
                .isZero());
        assertThat(franchissements(zone)).isEmpty();

        // Réactivée : le suivi repart de zéro, il faut revoir l'enfant dans la zone avant de signaler une sortie.
        json(post("/api/v1/enfants/" + famille.enfantId() + "/zones/" + zone + "/reactivation"), "{}", famille.parent().jeton())
                .andExpect(status().isOk());
        recevoir(carte, t + 240, 5, LAT + 0.01, LON, 8);
        recevoir(carte, t + 300, 6, LAT, LON, 8);
        recevoir(carte, t + 360, 7, LAT + 0.01, LON, 8);
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(franchissements(zone)).containsExactly("SORTIE"));
    }

    @Test
    void leTrajetDUneJourneeEstRestitueDansLOrdreEtFermeAuxAutresComptes() throws Exception {
        ParentAvecEnfant famille = acteurs.parentAvecEnfant();
        Carte carte = equiper(famille);
        LocalDate aujourdhui = LocalDate.now(ZoneOffset.UTC);
        long minuit = aujourdhui.atStartOfDay(ZoneOffset.UTC).toEpochSecond();
        long maintenant = Instant.now().getEpochSecond();
        // Deux points d'aujourd'hui reçus dans le désordre, un point d'hier.
        long recent = Math.max(minuit, maintenant - 30);
        long ancien = Math.max(minuit, maintenant - 600) == recent ? recent - 1 : Math.max(minuit, maintenant - 600);
        recevoir(carte, recent, 2, 12.38, -1.51, 8);
        recevoir(carte, minuit - 3600, 1, 12.36, -1.53, 8);
        boolean deuxPoints = ancien >= minuit && ancien < recent;
        if (deuxPoints) {
            recevoir(carte, ancien, 3, 12.37, -1.52, 8);
        }
        String chemin = "/api/v1/enfants/" + famille.enfantId() + "/trajets";

        avec(get(chemin).param("jour", aujourdhui.toString()), famille.parent().jeton()).andExpect(status().isOk())
                .andExpect(jsonPath("$.joursConserves").value(30))
                .andExpect(jsonPath("$.points.length()").value(deuxPoints ? 2 : 1))
                .andExpect(jsonPath("$.points[" + (deuxPoints ? 1 : 0) + "].latitude").value(12.38));
        avec(get(chemin).param("jour", aujourdhui.minusDays(1).toString()), famille.parent().jeton())
                .andExpect(jsonPath("$.points.length()").value(1)).andExpect(jsonPath("$.points[0].latitude").value(12.36));
        avec(get(chemin).param("jour", aujourdhui.minusDays(60).toString()), famille.parent().jeton())
                .andExpect(jsonPath("$.points.length()").value(0));
        avec(get(chemin).param("jour", "hier"), famille.parent().jeton()).andExpect(status().isBadRequest());
        avec(get(chemin).param("jour", aujourdhui.toString()), acteurs.parent().jeton()).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit.entree WHERE action = 'TRAJET_CONSULTE' AND cible_id = ?",
                Integer.class, famille.enfantId())).isEqualTo(3);
    }

    // -------------------------------------------------------------------- aides

    private static String cercle(String nom, int rayonM, int toleranceS) {
        return "\"forme\":\"CERCLE\",\"nom\":\"" + nom + "\",\"categorie\":\"AUTRE\",\"centre\":{\"latitude\":" + LAT
                + ",\"longitude\":" + LON + "},\"rayonM\":" + rayonM + "," + PLAGE_PERMANENTE + ",\"toleranceS\":" + toleranceS;
    }

    private String creer(ParentAvecEnfant famille, String zone) throws Exception {
        return Acteurs.extraire(json(post("/api/v1/enfants/" + famille.enfantId() + "/zones"),
                "{" + zone + "," + code(famille.parent()) + "}", famille.parent().jeton()).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "\"id\":\"([0-9a-f-]{36})\"");
    }

    /** Champ JSON portant un code de second facteur fraîchement émis pour ce parent. */
    private String code(Parent parent) throws Exception {
        // Deux codes de même finalité ne peuvent pas être demandés à moins d'une minute d'intervalle.
        jdbc.update("DELETE FROM identite.code_usage_unique WHERE finalite = '2F_MODIFIER_SAFE_ZONE'");
        json(post("/api/v1/moi/second-facteur"), "{\"action\":\"MODIFIER_SAFE_ZONE\"}", parent.jeton())
                .andExpect(status().isAccepted());
        return "\"codeSecondFacteur\":\"" + acteurs.dernierCodeDeConfirmation(parent.telephone()) + "\"";
    }

    private Carte equiper(ParentAvecEnfant famille) throws Exception {
        Carte carte = acteurs.equiper(famille);
        jdbc.update("UPDATE dispositifs.appairage SET debut = now() - INTERVAL '3 days' WHERE enfant_id = ?::uuid",
                famille.enfantId());
        return carte;
    }

    private void recevoir(Carte carte, long t, long sequence, double latitude, double longitude, int precision) {
        String message = String.format(Locale.ROOT,
                "{\"t\":%d,\"seq\":%d,\"lat\":%.5f,\"lon\":%.5f,\"acc\":%d,\"src\":\"gnss\",\"bat\":70}", t, sequence, latitude,
                longitude, precision);
        assertThat(ingestion.telemetrie(carte.numeroSerie(), message.getBytes(StandardCharsets.UTF_8))).isEqualTo(Resultat.ACCEPTE);
    }

    /** Attend que l'écouteur asynchrone ait traité la position mesurée à l'instant donné. */
    private void attendreSuivi(String zone, long mesure) {
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(jdbc.queryForList(
                "SELECT extract(epoch FROM derniere_mesure)::bigint FROM geolocalisation.suivi_zone WHERE zone_id = ?::uuid",
                Long.class, zone)).containsExactly(mesure));
    }

    private List<String> franchissements(String zone) {
        return jdbc.queryForList("SELECT type FROM geolocalisation.franchissement WHERE zone_id = ?::uuid ORDER BY mesure_le",
                String.class, zone);
    }

    private ResultActions zones(ParentAvecEnfant famille) throws Exception {
        return avec(get("/api/v1/enfants/" + famille.enfantId() + "/zones"), famille.parent().jeton());
    }

    private ResultActions avec(MockHttpServletRequestBuilder requete, String jeton) throws Exception {
        return mvc.perform(jeton == null ? requete : requete.header("Authorization", "Bearer " + jeton));
    }

    private ResultActions json(MockHttpServletRequestBuilder requete, String corps, String jeton) throws Exception {
        return avec(requete.contentType(MediaType.APPLICATION_JSON).content(corps), jeton);
    }
}
