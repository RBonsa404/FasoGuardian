package bf.fasoguardian.alertes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import bf.fasoguardian.Acteurs;
import bf.fasoguardian.Acteurs.Carte;
import bf.fasoguardian.Acteurs.Parent;
import bf.fasoguardian.Acteurs.ParentAvecEnfant;
import bf.fasoguardian.TestIntegration;
import bf.fasoguardian.alertes.application.Retraits;
import bf.fasoguardian.notifications.infrastructure.SmsBacASable;
import bf.fasoguardian.telemetrie.application.ReceptionMessages;
import bf.fasoguardian.telemetrie.application.ReceptionMessages.Flux;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Alertes, journal d'acquittement et autorisation de retrait (US-ENF-001, US-ENF-002, US-PAR-008, 010, 012). */
class AlertesIT extends TestIntegration {

    private static final AtomicLong SEQUENCE = new AtomicLong(1);

    @Autowired
    MockMvc mvc;

    @Autowired
    SmsBacASable sms;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ReceptionMessages reception;

    @Autowired
    Retraits retraits;

    Acteurs acteurs;

    @BeforeEach
    void preparer() {
        acteurs = new Acteurs(mvc, sms);
    }

    @Test
    void unSosOuvreUneAlerteCritiqueNotifieeParSmsPuisPriseEnChargeEtLevee() throws Exception {
        ParentAvecEnfant famille = acteurs.parentAvecEnfant();
        Carte carte = acteurs.equiper(famille);
        String jeton = famille.parent().jeton();

        evenement(carte, "sos", ",\"lat\":12.3714,\"lon\":-1.5197");
        String id = attendreAlerte(famille, "SOS");
        // Un second appui pendant l'alerte ne la double pas.
        evenement(carte, "sos", "");

        assertThat(acteurs.attendreSms(famille.parent().telephone(), "ALERTE SOS", "Ouvrez l'application"))
                .doesNotContain("12.37");
        avec(get("/api/v1/alertes").param("enCours", "true"), jeton).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].type").value("SOS")).andExpect(jsonPath("$[0].gravite").value("CRITIQUE"))
                .andExpect(jsonPath("$[0].statut").value("OUVERTE")).andExpect(jsonPath("$[0].latitude").value(12.3714))
                .andExpect(jsonPath("$[0].actions[0].type").value("OUVERTURE"))
                .andExpect(jsonPath("$[0].actions[0].auteur").value("SYSTEME"));

        json(post("/api/v1/alertes/" + id + "/levee"), "{\"motif\":\"Trop tôt\"}", jeton).andExpect(status().isConflict());
        avec(post("/api/v1/alertes/" + id + "/acquittement"), jeton).andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("ACQUITTEE")).andExpect(jsonPath("$.actions[1].auteur").value("VOUS"));
        avec(post("/api/v1/alertes/" + id + "/acquittement"), jeton).andExpect(status().isConflict());
        json(post("/api/v1/alertes/" + id + "/levee"), "{\"motif\":\" \"}", jeton).andExpect(status().isBadRequest());
        json(post("/api/v1/alertes/" + id + "/levee"), "{\"motif\":\"Enfant retrouvé à l'école\"}", jeton)
                .andExpect(status().isOk()).andExpect(jsonPath("$.statut").value("LEVEE"))
                .andExpect(jsonPath("$.closeLe").isNotEmpty())
                .andExpect(jsonPath("$.actions[2].motif").value("Enfant retrouvé à l'école"));

        avec(get("/api/v1/alertes").param("enCours", "true"), jeton).andExpect(jsonPath("$.length()").value(0));
        avec(get("/api/v1/enfants/" + famille.enfantId() + "/journal"), jeton).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].actions.length()").value(3));
        assertThat(jdbc.queryForList("SELECT action FROM audit.entree WHERE cible_id = ? ORDER BY id", String.class, id))
                .containsExactly("ALERTE_ACQUITTEE", "ALERTE_LEVEE");
    }

    @Test
    void leJournalDAcquittementNePeutEtreNiModifieNiEfface() throws Exception {
        ParentAvecEnfant famille = acteurs.parentAvecEnfant();
        Carte carte = acteurs.equiper(famille);
        evenement(carte, "sos", "");
        String id = attendreAlerte(famille, "SOS");

        assertThatThrownBy(() -> jdbc.update("UPDATE alertes.action_alerte SET motif = 'réécrit' WHERE alerte_id = ?::uuid", id))
                .hasMessageContaining("ajout seul");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM alertes.action_alerte WHERE alerte_id = ?::uuid", id))
                .hasMessageContaining("ajout seul");
        assertThatThrownBy(() -> jdbc.execute("TRUNCATE alertes.action_alerte")).hasMessageContaining("ajout seul");
    }

    @Test
    void unRetraitHorsFenetreAlerteEtUnRetraitAutoriseNeDeclencheRien() throws Exception {
        ParentAvecEnfant famille = acteurs.parentAvecEnfant();
        Carte carte = acteurs.equiper(famille);
        Parent parent = famille.parent();
        String retrait = "/api/v1/enfants/" + famille.enfantId() + "/bracelet/retrait";

        avec(get(retrait), parent.jeton()).andExpect(status().isNotFound());
        json(post(retrait), "{\"motif\":\"TOILETTE\",\"dureeMinutes\":30}", parent.jeton()).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SECOND_FACTEUR_REQUIS"));
        json(post(retrait), "{\"motif\":\"TOILETTE\",\"dureeMinutes\":20," + code(parent) + "}", parent.jeton())
                .andExpect(status().isBadRequest());
        json(post(retrait), "{\"motif\":\"TOILETTE\",\"dureeMinutes\":30," + code(parent) + "}", parent.jeton())
                .andExpect(status().isCreated()).andExpect(jsonPath("$.motif").value("TOILETTE"))
                .andExpect(jsonPath("$.retire").value(false));
        acteurs.attendreSms(parent.telephone(), "retrait du bracelet", "autorisé pendant 30 min");
        json(post(retrait), "{\"motif\":\"NUIT\",\"dureeMinutes\":600," + code(parent) + "}", parent.jeton())
                .andExpect(status().isConflict());

        // Fermoir ouvert pendant la fenêtre : noté, sans alerte.
        evenement(carte, "strap", "");
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> avec(get(retrait), parent.jeton())
                .andExpect(status().isOk()).andExpect(jsonPath("$.retire").value(true)));
        avec(get("/api/v1/alertes"), parent.jeton()).andExpect(jsonPath("$.length()").value(0));

        // Le bracelet est remis : la fenêtre se referme, et un nouveau retrait alerte aussitôt.
        evenement(carte, "worn", "");
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> avec(get(retrait), parent.jeton()).andExpect(status().isNotFound()));
        evenement(carte, "skin", "");
        attendreAlerte(famille, "RETRAIT");
        acteurs.attendreSms(parent.telephone(), "retiré sans autorisation");
    }

    @Test
    void aLEcheanceUnBraceletNonRemisDeclencheUnRappelPuisUneAlerte() throws Exception {
        ParentAvecEnfant famille = acteurs.parentAvecEnfant();
        Carte carte = acteurs.equiper(famille);
        Parent parent = famille.parent();
        String retrait = "/api/v1/enfants/" + famille.enfantId() + "/bracelet/retrait";
        json(post(retrait), "{\"motif\":\"RECHARGE\",\"dureeMinutes\":120," + code(parent) + "}", parent.jeton())
                .andExpect(status().isCreated());
        evenement(carte, "skin", "");
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> avec(get(retrait), parent.jeton())
                .andExpect(jsonPath("$.retire").value(true)));

        // Prolongation sous second facteur, bornée à 12 heures au total.
        json(post(retrait + "/prolongation"), "{\"minutes\":30}", parent.jeton()).andExpect(status().isForbidden());
        json(post(retrait + "/prolongation"), "{\"minutes\":30," + code(parent) + "}", parent.jeton()).andExpect(status().isOk());
        json(post(retrait + "/prolongation"), "{\"minutes\":600," + code(parent) + "}", parent.jeton())
                .andExpect(status().isBadRequest());

        // Quatre minutes avant la fin : rappel, pas encore d'alerte.
        jdbc.update("UPDATE alertes.autorisation_retrait SET debut = now() - INTERVAL '146 minutes', fin = now() + INTERVAL '4 minutes' WHERE enfant_id = ?::uuid",
                famille.enfantId());
        retraits.surveillerEcheances();
        acteurs.attendreSms(parent.telephone(), "se termine dans quelques minutes", "Remettez-le");
        avec(get("/api/v1/alertes"), parent.jeton()).andExpect(jsonPath("$.length()").value(0));

        jdbc.update("UPDATE alertes.autorisation_retrait SET debut = now() - INTERVAL '151 minutes', fin = now() - INTERVAL '1 minute' WHERE enfant_id = ?::uuid",
                famille.enfantId());
        retraits.surveillerEcheances();
        avec(get("/api/v1/alertes"), parent.jeton()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].type").value("RETRAIT")).andExpect(jsonPath("$[0].gravite").value("CRITIQUE"));
        avec(get(retrait), parent.jeton()).andExpect(status().isNotFound());
        assertThat(jdbc.queryForList("SELECT action FROM audit.entree e JOIN dispositifs.bracelet b ON b.id::text = e.cible_id"
                + " WHERE b.numero_serie = ? AND action LIKE 'RETRAIT%' ORDER BY e.id", String.class, carte.numeroSerie()))
                .containsExactly("RETRAIT_AUTORISE", "RETRAIT_CONSTATE", "RETRAIT_PROLONGE");
    }

    @Test
    void uneAlerteImportanteEstNotifieePuisSeClotDElleMemeQuandSaCauseDisparait() throws Exception {
        ParentAvecEnfant famille = acteurs.parentAvecEnfant();
        Carte carte = acteurs.equiper(famille);
        Parent parent = famille.parent();

        evenement(carte, "batcrit", "");
        String id = attendreAlerte(famille, "BATTERIE_CRITIQUE");
        // Sans abonnement push, le SMS est le seul canal : il part sans attendre (le repli à 60 s est éprouvé par NotificationsIT).
        acteurs.attendreSms(parent.telephone(), "batterie", "critique");

        // Le bracelet est posé sur son chargeur : l'alerte se clôt d'elle-même.
        evenement(carte, "charge", "");
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> avec(get("/api/v1/alertes/" + id), parent.jeton())
                .andExpect(jsonPath("$.statut").value("LEVEE"))
                .andExpect(jsonPath("$.actions[1].type").value("RESOLUTION"))
                .andExpect(jsonPath("$.actions[1].auteur").value("SYSTEME"))
                .andExpect(jsonPath("$.actions[1].motif").value("Bracelet mis en charge")));
    }

    @Test
    void uneSortieDeZoneOuvreUneAlerteQueLeRetourClot() throws Exception {
        ParentAvecEnfant famille = acteurs.parentAvecEnfant();
        Carte carte = acteurs.equiper(famille);
        Parent parent = famille.parent();
        jdbc.update("UPDATE dispositifs.appairage SET debut = now() - INTERVAL '3 days' WHERE enfant_id = ?::uuid", famille.enfantId());
        jdbc.update("DELETE FROM identite.code_usage_unique WHERE finalite = '2F_MODIFIER_SAFE_ZONE'");
        json(post("/api/v1/moi/second-facteur"), "{\"action\":\"MODIFIER_SAFE_ZONE\"}", parent.jeton()).andExpect(status().isAccepted());
        String codeZone = acteurs.dernierCodeDeConfirmation(parent.telephone());
        json(post("/api/v1/enfants/" + famille.enfantId() + "/zones"), "{\"forme\":\"CERCLE\",\"nom\":\"École\",\"categorie\":\"ECOLE\","
                + "\"centre\":{\"latitude\":12.3714,\"longitude\":-1.5197},\"rayonM\":200,\"jours\":[1,2,3,4,5,6,7],\"debut\":\"00:00\","
                + "\"fin\":\"00:00\",\"toleranceS\":0,\"codeSecondFacteur\":\"" + codeZone + "\"}", parent.jeton()).andExpect(status().isCreated());
        long t = Instant.now().getEpochSecond() - 600;

        position(carte, t, 12.3714, -1.5197);
        position(carte, t + 60, 12.3814, -1.5197);
        String id = attendreAlerte(famille, "SORTIE_ZONE");
        avec(get("/api/v1/alertes/" + id), parent.jeton()).andExpect(jsonPath("$.libelle").value("École"))
                .andExpect(jsonPath("$.gravite").value("IMPORTANTE")).andExpect(jsonPath("$.latitude").value(12.3814));

        position(carte, t + 120, 12.3714, -1.5197);
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> avec(get("/api/v1/alertes/" + id), parent.jeton())
                .andExpect(jsonPath("$.statut").value("LEVEE")).andExpect(jsonPath("$.actions[1].motif").value("Retour dans la zone")));
    }

    @Test
    void leParentSignaleLuiMemeEtPrendEnChargeToutesLesAlertesDUnGeste() throws Exception {
        ParentAvecEnfant famille = acteurs.parentAvecEnfant();
        Carte carte = acteurs.equiper(famille);
        String jeton = famille.parent().jeton();
        String enfant = "/api/v1/enfants/" + famille.enfantId();

        avec(post(enfant + "/signalement"), jeton).andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("SIGNALEMENT")).andExpect(jsonPath("$.statut").value("ACQUITTEE"))
                .andExpect(jsonPath("$.actions[0].auteur").value("VOUS")).andExpect(jsonPath("$.actions.length()").value(2));
        avec(post(enfant + "/signalement"), jeton).andExpect(status().isConflict());

        evenement(carte, "sos", "");
        evenement(carte, "fall", "");
        attendreAlerte(famille, "SOS");
        attendreAlerte(famille, "CHUTE");
        avec(post(enfant + "/alertes/prise-en-charge"), jeton).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        avec(get("/api/v1/alertes").param("enCours", "true"), jeton).andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[?(@.statut == 'OUVERTE')]").isEmpty());

        // Fausse alerte, motif obligatoire et journalisé
        String chute = attendreAlerte(famille, "CHUTE");
        json(post("/api/v1/alertes/" + chute + "/fausse-alerte"), "{}", jeton).andExpect(status().isBadRequest());
        json(post("/api/v1/alertes/" + chute + "/fausse-alerte"), "{\"motif\":\"Il jouait au ballon\"}", jeton)
                .andExpect(status().isOk()).andExpect(jsonPath("$.statut").value("FAUSSE_ALERTE"));
        assertThat(jdbc.queryForObject("SELECT motif FROM alertes.action_alerte WHERE alerte_id = ?::uuid AND type = 'FAUSSE_ALERTE'",
                String.class, chute)).isEqualTo("Il jouait au ballon");
    }

    @Test
    void lesAlertesEtLeRetraitSontFermesAuxComptesNonRattaches() throws Exception {
        ParentAvecEnfant famille = acteurs.parentAvecEnfant();
        Carte carte = acteurs.equiper(famille);
        evenement(carte, "sos", "");
        String id = attendreAlerte(famille, "SOS");
        Parent etranger = acteurs.parent();
        String enfant = "/api/v1/enfants/" + famille.enfantId();

        avec(get("/api/v1/alertes"), etranger.jeton()).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        avec(get("/api/v1/alertes/" + id), etranger.jeton()).andExpect(status().isNotFound());
        avec(post("/api/v1/alertes/" + id + "/acquittement"), etranger.jeton()).andExpect(status().isNotFound());
        json(post("/api/v1/alertes/" + id + "/fausse-alerte"), "{\"motif\":\"x\"}", etranger.jeton()).andExpect(status().isNotFound());
        avec(get(enfant + "/journal"), etranger.jeton()).andExpect(status().isNotFound());
        avec(post(enfant + "/signalement"), etranger.jeton()).andExpect(status().isNotFound());
        avec(post(enfant + "/alertes/prise-en-charge"), etranger.jeton()).andExpect(status().isNotFound());
        json(post(enfant + "/bracelet/retrait"), "{\"motif\":\"NUIT\",\"dureeMinutes\":600}", etranger.jeton())
                .andExpect(status().isNotFound());
        avec(delete(enfant + "/bracelet/retrait"), etranger.jeton()).andExpect(status().isNotFound());
        avec(get("/api/v1/alertes"), null).andExpect(status().isUnauthorized());
        avec(get("/api/v1/alertes"), acteurs.jetonSav()).andExpect(status().isForbidden());
        avec(get("/api/v1/alertes/" + id), famille.parent().jeton()).andExpect(jsonPath("$.statut").value("OUVERTE"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit.entree WHERE resultat = 'REFUS' AND cible_id = ?",
                Integer.class, famille.enfantId())).isGreaterThanOrEqualTo(7);
    }

    // -------------------------------------------------------------------- aides

    private void evenement(Carte carte, String code, String suite) {
        String message = "{\"t\":" + (Instant.now().getEpochSecond() + 1) + ",\"seq\":" + SEQUENCE.incrementAndGet() + ",\"ev\":\"" + code
                + "\"" + suite + "}";
        // Par le port des transports, comme le fait l'abonné MQTT : c'est ce chemin qui doit porter la transaction.
        reception.recevoir(Flux.ALERT, carte.numeroSerie(), message.getBytes(StandardCharsets.UTF_8));
    }

    private void position(Carte carte, long t, double latitude, double longitude) {
        String message = String.format(java.util.Locale.ROOT, "{\"t\":%d,\"seq\":%d,\"lat\":%.5f,\"lon\":%.5f,\"acc\":8,\"src\":\"gnss\"}",
                t, SEQUENCE.incrementAndGet(), latitude, longitude);
        reception.recevoir(Flux.TELEMETRY, carte.numeroSerie(), message.getBytes(StandardCharsets.UTF_8));
    }

    /** Attend que l'écouteur asynchrone ait ouvert l'alerte du type donné ; renvoie son identifiant. */
    private String attendreAlerte(ParentAvecEnfant famille, String type) {
        String[] id = new String[1];
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            List<String> ids = jdbc.queryForList("SELECT id::text FROM alertes.alerte WHERE enfant_id = ?::uuid AND type = ?",
                    String.class, famille.enfantId(), type);
            assertThat(ids).hasSize(1);
            id[0] = ids.get(0);
        });
        return id[0];
    }

    private String code(Parent parent) throws Exception {
        jdbc.update("DELETE FROM identite.code_usage_unique WHERE finalite = '2F_AUTORISER_RETRAIT'");
        json(post("/api/v1/moi/second-facteur"), "{\"action\":\"AUTORISER_RETRAIT\"}", parent.jeton()).andExpect(status().isAccepted());
        return "\"codeSecondFacteur\":\"" + acteurs.dernierCodeDeConfirmation(parent.telephone()) + "\"";
    }

    private ResultActions avec(MockHttpServletRequestBuilder requete, String jeton) throws Exception {
        return mvc.perform(jeton == null ? requete : requete.header("Authorization", "Bearer " + jeton));
    }

    private ResultActions json(MockHttpServletRequestBuilder requete, String corps, String jeton) throws Exception {
        return avec(requete.contentType(MediaType.APPLICATION_JSON).content(corps), jeton);
    }
}
