package bf.fasoguardian.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

import bf.fasoguardian.Acteurs;
import bf.fasoguardian.Acteurs.Carte;
import bf.fasoguardian.Acteurs.ParentAvecEnfant;
import bf.fasoguardian.TestIntegration;
import bf.fasoguardian.audit.application.SupervisionPlateforme;
import bf.fasoguardian.notifications.infrastructure.SmsBacASable;
import bf.fasoguardian.telemetrie.application.Ingestion;
import bf.fasoguardian.telemetrie.application.Ingestion.Resultat;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** Disponibilité mesurée, délai de notification suivi et alerte d'exploitation (US-ADM-004). */
class SupervisionIT extends TestIntegration {

    private static final String SUPERVISION = "/api/v1/console/supervision";

    @Autowired
    MockMvc mvc;

    @Autowired
    SmsBacASable sms;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    SupervisionPlateforme supervision;

    @Autowired
    Ingestion ingestion;

    @Autowired
    MeterRegistry metriques;

    Acteurs acteurs;

    @BeforeEach
    void preparer() {
        acteurs = new Acteurs(mvc, sms);
        jdbc.update("DELETE FROM audit.sonde_disponibilite");
    }

    @Test
    void laDisponibiliteEstLaPartDesMinutesOuLeServeurARepondu() throws Exception {
        // 200 minutes mesurées, dont trois sans réponse.
        jdbc.update("INSERT INTO audit.sonde_disponibilite (minute) SELECT date_trunc('minute', now()) - make_interval(mins => n)"
                + " FROM generate_series(0, 199) AS n WHERE n NOT IN (50, 51, 120)");

        mvc.perform(get(SUPERVISION).header("Authorization", "Bearer " + acteurs.jetonAdmin())).andExpect(status().isOk())
                .andExpect(jsonPath("$.minutesMesurees").value(200)).andExpect(jsonPath("$.minutesIndisponibles").value(3))
                .andExpect(jsonPath("$.disponibilite").value(98.5)).andExpect(jsonPath("$.objectifDeDisponibilite").value(99.5))
                .andExpect(jsonPath("$.seuilDeDelaiS").value(45));

        // La sonde note la minute en cours, une seule fois même appelée deux fois.
        jdbc.update("DELETE FROM audit.sonde_disponibilite");
        supervision.sonder();
        supervision.sonder();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit.sonde_disponibilite", Integer.class)).isEqualTo(1);
        mvc.perform(get(SUPERVISION).header("Authorization", "Bearer " + acteurs.jetonAdmin()))
                .andExpect(jsonPath("$.disponibilite").value(100.0));
    }

    @Test
    void unDelaiDeNotificationAuDessusDe45SecondesEmetUneAlerteDExploitationUneSeuleFoisParHeure() throws Exception {
        ParentAvecEnfant famille = acteurs.parentAvecEnfant();
        Carte carte = acteurs.equiper(famille);
        jdbc.update("UPDATE dispositifs.appairage SET debut = now() - INTERVAL '1 hour' WHERE enfant_id = ?::uuid", famille.enfantId());
        Timer delai = metriques.find("fasoguardian.alertes.delai.notification").timer();
        assertThat(delai).as("le délai est mesuré dès le démarrage").isNotNull();
        long avant = delai.count();

        // Un SOS mesuré il y a deux minutes : son délai jusqu'à la notification est pris en compte.
        String sos = "{\"t\":" + (Instant.now().getEpochSecond() - 120) + ",\"seq\":1,\"ev\":\"sos\",\"lat\":12.37,\"lon\":-1.52}";
        assertThat(ingestion.alerte(carte.numeroSerie(), sos.getBytes(StandardCharsets.UTF_8))).isEqualTo(Resultat.ACCEPTE);
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(delai.count()).isEqualTo(avant + 1));
        assertThat(delai.max(java.util.concurrent.TimeUnit.SECONDS)).isGreaterThanOrEqualTo(120);

        // Une série d'alertes lentes fait passer le 95e centile au-dessus du seuil.
        for (int i = 0; i < 500; i++) {
            delai.record(Duration.ofSeconds(60));
        }
        int alertesAvant = alertesDExploitation();

        assertThat(supervision.controlerLeDelai()).isTrue();
        assertThat(supervision.controlerLeDelai()).as("une alerte au plus par heure").isFalse();

        assertThat(alertesDExploitation()).isEqualTo(alertesAvant + 1);
        mvc.perform(get(SUPERVISION).header("Authorization", "Bearer " + acteurs.jetonAdmin())).andExpect(status().isOk())
                .andExpect(jsonPath("$.delaiHorsSeuil").value(true)).andExpect(jsonPath("$.derniereAlerte").exists())
                .andExpect(jsonPath("$.delaiP95S").value(org.hamcrest.Matchers.greaterThan(45.0)));
    }

    @Test
    void seulUnAdministrateurVoitLaSupervision() throws Exception {
        mvc.perform(get(SUPERVISION)).andExpect(status().isUnauthorized());
        mvc.perform(get(SUPERVISION).header("Authorization", "Bearer " + acteurs.jetonSav())).andExpect(status().isForbidden());
        mvc.perform(get(SUPERVISION).header("Authorization", "Bearer " + acteurs.parent().jeton())).andExpect(status().isForbidden());
    }

    private int alertesDExploitation() {
        return jdbc.queryForObject("SELECT count(*) FROM audit.entree WHERE action = 'DELAI_DE_NOTIFICATION_ELEVE'", Integer.class);
    }
}
