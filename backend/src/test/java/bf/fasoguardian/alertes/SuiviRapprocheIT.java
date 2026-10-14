package bf.fasoguardian.alertes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

import bf.fasoguardian.Acteurs;
import bf.fasoguardian.Acteurs.Carte;
import bf.fasoguardian.Acteurs.ParentAvecEnfant;
import bf.fasoguardian.TestIntegration;
import bf.fasoguardian.alertes.application.SuiviRapproche;
import bf.fasoguardian.notifications.infrastructure.SmsBacASable;
import bf.fasoguardian.telemetrie.application.ReceptionMessages;
import bf.fasoguardian.telemetrie.application.ReceptionMessages.Flux;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Suivi rapproché demandé depuis la carte : mode alerte sans alerte, limité à trente minutes (écran 18). */
class SuiviRapprocheIT extends TestIntegration {

    @Autowired
    MockMvc mvc;

    @Autowired
    SmsBacASable sms;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ReceptionMessages reception;

    @Autowired
    SuiviRapproche suivi;

    Acteurs acteurs;

    @BeforeEach
    void preparer() {
        acteurs = new Acteurs(mvc, sms);
    }

    @Test
    void leTuteurPasseLeBraceletAUnePositionParMinutePourTrenteMinutesPuisLeModeRetombe() throws Exception {
        ParentAvecEnfant famille = acteurs.parentAvecEnfant();
        Carte carte = acteurs.equiper(famille);
        String jeton = famille.parent().jeton();
        String chemin = "/api/v1/enfants/" + famille.enfantId() + "/bracelet/mode-alerte";

        avec(get(chemin), jeton).andExpect(status().isOk()).andExpect(jsonPath("$.actif").value(false));
        avec(post(chemin), jeton).andExpect(status().isOk()).andExpect(jsonPath("$.actif").value(true))
                .andExpect(jsonPath("$.alerteCritique").value(false)).andExpect(jsonPath("$.jusquAu").isNotEmpty());
        assertThat(modes(carte)).containsExactly(1);
        Instant fin = Instant.parse(jdbc.queryForObject("SELECT to_char(jusqu_au AT TIME ZONE 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SS\"Z\"')"
                + " FROM alertes.suivi_rapproche WHERE enfant_id = ?::uuid", String.class, famille.enfantId()));
        assertThat(Duration.between(Instant.now(), fin)).isBetween(Duration.ofMinutes(29), Duration.ofMinutes(30));

        // Avant l'échéance, rien ne se passe ; à l'échéance, le bracelet revient à son rythme ordinaire.
        assertThat(suivi.clore()).isZero();
        jdbc.update("UPDATE alertes.suivi_rapproche SET jusqu_au = now() - INTERVAL '1 second' WHERE enfant_id = ?::uuid", famille.enfantId());
        assertThat(suivi.clore()).isEqualTo(1);
        assertThat(modes(carte)).containsExactly(1, 0);
        avec(get(chemin), jeton).andExpect(jsonPath("$.actif").value(false)).andExpect(jsonPath("$.jusquAu").isEmpty());
        assertThat(jdbc.queryForList("SELECT action FROM audit.entree WHERE action LIKE 'SUIVI_RAPPROCHE%' AND cible_id = ? ORDER BY id",
                String.class, famille.enfantId())).containsExactly("SUIVI_RAPPROCHE_ACTIVE", "SUIVI_RAPPROCHE_ECHU");
    }

    @Test
    void arreterLeSuiviNeCoupeJamaisLeModeAlerteDUneAlerteCritiqueEnCours() throws Exception {
        ParentAvecEnfant famille = acteurs.parentAvecEnfant();
        Carte carte = acteurs.equiper(famille);
        String jeton = famille.parent().jeton();
        String chemin = "/api/v1/enfants/" + famille.enfantId() + "/bracelet/mode-alerte";

        avec(post(chemin), jeton).andExpect(status().isOk());
        reception.recevoir(Flux.ALERT, carte.numeroSerie(),
                ("{\"t\":" + (Instant.now().getEpochSecond() + 1) + ",\"seq\":1,\"ev\":\"sos\"}").getBytes(StandardCharsets.UTF_8));
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(modes(carte)).hasSize(2));

        avec(delete(chemin), jeton).andExpect(status().isOk()).andExpect(jsonPath("$.actif").value(true))
                .andExpect(jsonPath("$.alerteCritique").value(true)).andExpect(jsonPath("$.jusquAu").isEmpty());
        assertThat(modes(carte)).as("le SOS tient le mode alerte").containsExactly(1, 1);

        // Et l'inverse : l'alerte levée pendant un suivi rapproché ne coupe pas ce suivi.
        avec(post(chemin), jeton).andExpect(status().isOk());
        String alerte = jdbc.queryForObject("SELECT id::text FROM alertes.alerte WHERE enfant_id = ?::uuid", String.class, famille.enfantId());
        avec(post("/api/v1/alertes/" + alerte + "/acquittement"), jeton).andExpect(status().isOk());
        avec(post("/api/v1/alertes/" + alerte + "/levee").contentType("application/json").content("{\"motif\":\"Enfant retrouvé\"}"), jeton)
                .andExpect(status().isOk());
        assertThat(modes(carte)).doesNotContain(0);
        avec(get(chemin), jeton).andExpect(jsonPath("$.actif").value(true)).andExpect(jsonPath("$.alerteCritique").value(false));
    }

    @Test
    void seulUnTuteurDeLEnfantEtSeulementAvecUnBracelet() throws Exception {
        ParentAvecEnfant famille = acteurs.parentAvecEnfant();
        String chemin = "/api/v1/enfants/" + famille.enfantId() + "/bracelet/mode-alerte";

        avec(post(chemin), famille.parent().jeton()).andExpect(status().isNotFound());
        acteurs.equiper(famille);
        avec(post(chemin), acteurs.parent().jeton()).andExpect(status().isNotFound());
        avec(post(chemin), acteurs.jetonSav()).andExpect(status().isForbidden());
        avec(post(chemin), null).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM alertes.suivi_rapproche WHERE enfant_id = ?::uuid", Integer.class,
                famille.enfantId())).isZero();
    }

    // -------------------------------------------------------------------- aides

    /** Valeurs de « on » des commandes de mode alerte reçues par le bracelet, dans l'ordre. */
    private List<Integer> modes(Carte carte) {
        return jdbc.queryForList("""
                SELECT c.message FROM dispositifs.commande c JOIN dispositifs.bracelet b ON b.id = c.bracelet_id
                WHERE b.numero_serie = ? AND c.type = 'MODE_ALERTE' ORDER BY c.sequence""", String.class, carte.numeroSerie()).stream()
                .map(message -> new String(Base64.getUrlDecoder().decode(message.substring(0, message.indexOf('.'))), StandardCharsets.UTF_8))
                .map(corps -> corps.contains("\"on\":1") ? 1 : 0).toList();
    }

    private ResultActions avec(MockHttpServletRequestBuilder requete, String jeton) throws Exception {
        return mvc.perform(jeton == null ? requete : requete.header("Authorization", "Bearer " + jeton));
    }
}
