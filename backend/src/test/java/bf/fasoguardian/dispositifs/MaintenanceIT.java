package bf.fasoguardian.dispositifs;

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
import java.util.Base64;
import java.util.List;

import bf.fasoguardian.Acteurs;
import bf.fasoguardian.Acteurs.Carte;
import bf.fasoguardian.Acteurs.ParentAvecEnfant;
import bf.fasoguardian.TestIntegration;
import bf.fasoguardian.notifications.infrastructure.SmsBacASable;
import bf.fasoguardian.telemetrie.application.Ingestion;
import bf.fasoguardian.telemetrie.application.Ingestion.Resultat;
import bf.fasoguardian.telemetrie.application.Supervision;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Bracelets muets, tickets de maintenance et seuils de batterie (US-SAV-001, US-SYS-007). */
class MaintenanceIT extends TestIntegration {

    private static final String TICKETS = "/api/v1/console/sav/tickets";

    @Autowired
    MockMvc mvc;

    @Autowired
    SmsBacASable sms;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    Ingestion ingestion;

    @Autowired
    Supervision supervision;

    Acteurs acteurs;

    @BeforeEach
    void preparer() {
        acteurs = new Acteurs(mvc, sms);
        // Chaque essai part d'un parc sans ticket en cours : la supervision parcourt tous les bracelets en service.
        jdbc.update("UPDATE dispositifs.ticket_maintenance SET statut = 'RESOLU', resolution = 'SANS_SUITE', resolu_le = now()"
                + " WHERE statut <> 'RESOLU'");
        jdbc.update("UPDATE dispositifs.appairage SET fin = now(), motif_fin = 'DESAPPAIRAGE' WHERE fin IS NULL");
    }

    @Test
    void unBraceletMuetDepuisPlusDeTroisIntervallesOuvreUnTicketEtLeParentEstInforme() throws Exception {
        ParentAvecEnfant famille = acteurs.parentAvecEnfant();
        Carte carte = equiper(famille, "50 minutes");
        emettre(carte, 1200, 1, 64);

        // Vingt minutes de silence pour un intervalle de cinq : muet.
        supervision.superviser();
        supervision.superviser();

        assertThat(ticketsDe(carte)).containsExactly("OUVERT");
        String texte = acteurs.attendreSms(famille.parent().telephone(), "ne donne plus de nouvelles", carte.numeroSerie());
        assertThat(texte).contains("Le service après-vente est prévenu (SAV-");

        String sav = acteurs.jetonSav();
        String reponse = avec(get(TICKETS), sav).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(reponse).contains(carte.numeroSerie()).contains("\"batterie\":64").contains("\"motif\":\"MUET\"")
                .doesNotContain(famille.enfantId()).doesNotContain(famille.parent().telephone());
        String ticketId = jdbc.queryForObject("SELECT t.id::text FROM dispositifs.ticket_maintenance t JOIN dispositifs.bracelet b"
                + " ON b.id = t.bracelet_id WHERE b.numero_serie = ?", String.class, carte.numeroSerie());

        // Le parent suit le ticket depuis la fiche du bracelet : ni l'agent ni ses notes ne lui sont montrés.
        String suivi = "/api/v1/enfants/" + famille.enfantId() + "/bracelet/maintenance";
        avec(get(suivi), famille.parent().jeton()).andExpect(status().isOk())
                .andExpect(jsonPath("$.reference").value(org.hamcrest.Matchers.startsWith("SAV-")))
                .andExpect(jsonPath("$.numeroSerie").value(carte.numeroSerie()))
                .andExpect(jsonPath("$.statut").value("OUVERT")).andExpect(jsonPath("$.batterie").value(64))
                .andExpect(jsonPath("$.dernierContact").isNotEmpty()).andExpect(jsonPath("$.prisEnChargeLe").isEmpty())
                .andExpect(jsonPath("$.agentId").doesNotExist()).andExpect(jsonPath("$.note").doesNotExist());
        avec(get(suivi), acteurs.parent().jeton()).andExpect(status().isNotFound());
        avec(get(suivi), sav).andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT lien FROM notifications.notification WHERE modele = 'BRACELET_MUET' AND lien LIKE ?",
                String.class, "%" + famille.enfantId() + "%")).isEqualTo("/enfants/" + famille.enfantId() + "/bracelet/maintenance");

        avec(post(TICKETS + "/" + ticketId + "/prise-en-charge"), sav).andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("EN_COURS")).andExpect(jsonPath("$.prisEnChargeParMoi").value(true));
        avec(get(suivi), famille.parent().jeton()).andExpect(jsonPath("$.statut").value("EN_COURS"))
                .andExpect(jsonPath("$.prisEnChargeLe").isNotEmpty());
        avec(post(TICKETS + "/" + ticketId + "/prise-en-charge"), sav).andExpect(status().isConflict());

        // Le bracelet redonne des nouvelles : la supervision clôt le ticket et le dit au parent.
        emettre(carte, 10, 2, 60);
        supervision.superviser();

        assertThat(ticketsDe(carte)).containsExactly("RESOLU");
        avec(get(suivi), famille.parent().jeton()).andExpect(status().isNotFound());
        acteurs.attendreSms(famille.parent().telephone(), "donne de nouveau des nouvelles");
        avec(get(TICKETS + "?resolus=true"), sav).andExpect(jsonPath("$[0].resolution").value("REPRISE_SPONTANEE"));
        assertThat(jdbc.queryForList("SELECT action FROM audit.entree WHERE action LIKE 'TICKET_%' AND cible_id IN (?, ("
                + "SELECT reference FROM dispositifs.ticket_maintenance WHERE id = ?::uuid)) ORDER BY id", String.class,
                braceletId(carte), ticketId)).containsExactly("TICKET_OUVERT", "TICKET_PRIS_EN_CHARGE", "TICKET_RESOLU");
    }

    @Test
    void lAgentSavResoutUnTicketEtSeulLeSavYAAcces() throws Exception {
        ParentAvecEnfant famille = acteurs.parentAvecEnfant();
        Carte carte = equiper(famille, "2 hours");
        supervision.superviser();
        String ticketId = jdbc.queryForObject("SELECT t.id::text FROM dispositifs.ticket_maintenance t JOIN dispositifs.bracelet b"
                + " ON b.id = t.bracelet_id WHERE b.numero_serie = ? AND t.statut = 'OUVERT'", String.class, carte.numeroSerie());
        String sav = acteurs.jetonSav();

        // Un bracelet qui n'a jamais rien émis depuis l'appairage est muet lui aussi, sans dernier contact.
        avec(get(TICKETS), sav).andExpect(jsonPath("$[0].dernierContact").doesNotExist());
        json(post(TICKETS + "/" + ticketId + "/resolution"), "{}", sav).andExpect(status().isBadRequest());
        json(post(TICKETS + "/" + ticketId + "/resolution"), "{\"resolution\":\"REPRISE_SPONTANEE\"}", sav).andExpect(status().isBadRequest());
        json(post(TICKETS + "/" + ticketId + "/resolution"), "{\"resolution\":\"ECHANGE\",\"note\":\"Sangle et module remplacés\"}", sav)
                .andExpect(status().isOk()).andExpect(jsonPath("$.statut").value("RESOLU"))
                .andExpect(jsonPath("$.note").value("Sangle et module remplacés"));
        json(post(TICKETS + "/" + ticketId + "/resolution"), "{\"resolution\":\"ECHANGE\"}", sav).andExpect(status().isConflict());

        avec(get(TICKETS), null).andExpect(status().isUnauthorized());
        avec(get(TICKETS), famille.parent().jeton()).andExpect(status().isForbidden());
        avec(get(TICKETS), acteurs.agent("[\"KYC\"]").jeton()).andExpect(status().isForbidden());
        avec(post(TICKETS + "/" + ticketId + "/prise-en-charge"), acteurs.agent("[\"SUPPORT\"]").jeton()).andExpect(status().isForbidden());
    }

    @Test
    void lAgentSavMeneLeTicketJusquALEchangeEnPointRelaisSansConnaitreLaFamille() throws Exception {
        ParentAvecEnfant famille = acteurs.parentAvecEnfant();
        Carte carte = equiper(famille, "2 hours");
        supervision.superviser();
        String ticket = TICKETS + "/" + ticketOuvert(carte);
        String suivi = "/api/v1/enfants/" + famille.enfantId() + "/bracelet/maintenance";
        String sav = acteurs.jetonSav();

        // Rien ne se fait sur un ticket que personne n'a pris en charge.
        avec(post(ticket + "/diagnostic"), sav).andExpect(status().isConflict());
        avec(post(ticket + "/prise-en-charge"), sav).andExpect(status().isOk());

        // Diagnostic : une localisation signée part vers le bracelet ; tant qu'il ne l'accuse pas, elle reste émise.
        String diagnostic = avec(post(ticket + "/diagnostic"), sav).andExpect(status().isOk())
                .andExpect(jsonPath("$.diagnosticLe").isNotEmpty()).andExpect(jsonPath("$.diagnostic").value("EMISE"))
                .andExpect(jsonPath("$.autresMuets").value(0)).andReturn().getResponse().getContentAsString();
        assertThat(diagnostic).doesNotContain(famille.enfantId()).doesNotContain(famille.parent().telephone());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM dispositifs.commande c JOIN dispositifs.bracelet b ON b.id = c.bracelet_id"
                + " WHERE b.numero_serie = ? AND c.type = 'LOCALISER'", Long.class, carte.numeroSerie())).isEqualTo(1);

        // Rendez-vous : à venir et sous trente jours ; la plateforme le transmet au parent, que l'agent ne connaît pas.
        json(post(ticket + "/rendez-vous"), "{\"pointRelais\":\"Dassasgho\",\"le\":\"2020-01-01T10:00:00Z\"}", sav)
                .andExpect(status().isBadRequest());
        json(post(ticket + "/rendez-vous"), "{\"pointRelais\":\"Dassasgho\",\"le\":\"" + Instant.now().plus(Duration.ofDays(40)) + "\"}", sav)
                .andExpect(status().isBadRequest());
        json(post(ticket + "/rendez-vous"), "{\"pointRelais\":\"Dassasgho\",\"le\":\"" + Instant.now().plus(Duration.ofDays(2)) + "\"}", sav)
                .andExpect(status().isOk()).andExpect(jsonPath("$.pointRelais").value("Dassasgho"));
        acteurs.attendreSms(famille.parent().telephone(), "au point relais « Dassasgho »", carte.numeroSerie(), "saisirez le code");
        avec(get(suivi), famille.parent().jeton()).andExpect(jsonPath("$.pointRelais").value("Dassasgho"))
                .andExpect(jsonPath("$.rendezVousLe").isNotEmpty());

        // Sans réservation, une autre unité ne remplace pas le bracelet de l'enfant.
        Carte autre = acteurs.braceletAuParc();
        json(post("/api/v1/enfants/" + famille.enfantId() + "/bracelet/appairage"), "{\"code\":\"" + autre.code() + "\"}",
                famille.parent().jeton()).andExpect(status().is4xxClientError());

        // Remplacement : une unité en stock, réservée pour un seul ticket.
        json(post(ticket + "/remplacement"), "{\"numeroSerie\":\"" + carte.numeroSerie() + "\"}", sav).andExpect(status().isConflict());
        json(post(ticket + "/remplacement"), "{\"numeroSerie\":\"FG-INCONNU\"}", sav).andExpect(status().isNotFound());
        Carte neuve = acteurs.braceletAuParc();
        json(post(ticket + "/remplacement"), "{\"numeroSerie\":\"" + neuve.numeroSerie().toLowerCase() + "\"}", sav)
                .andExpect(status().isOk()).andExpect(jsonPath("$.remplacement").value(neuve.numeroSerie()));
        ParentAvecEnfant voisins = acteurs.parentAvecEnfant();
        Carte autreMuet = equiper(voisins, "2 hours");
        supervision.superviser();
        String autreTicket = TICKETS + "/" + ticketOuvert(autreMuet);
        avec(post(autreTicket + "/prise-en-charge"), sav).andExpect(status().isOk()).andExpect(jsonPath("$.autresMuets").value(1));
        json(post(autreTicket + "/remplacement"), "{\"numeroSerie\":\"" + neuve.numeroSerie() + "\"}", sav).andExpect(status().isConflict());

        // Au point relais, le parent associe l'unité remise avec son code : échange, ticket clos, ancienne unité au SAV.
        json(post("/api/v1/enfants/" + famille.enfantId() + "/bracelet/appairage"), "{\"code\":\"" + neuve.code() + "\"}",
                famille.parent().jeton()).andExpect(status().isOk()).andExpect(jsonPath("$.numeroSerie").value(neuve.numeroSerie()));
        assertThat(ticketsDe(carte)).containsExactly("RESOLU");
        assertThat(jdbc.queryForObject("SELECT resolution FROM dispositifs.ticket_maintenance t JOIN dispositifs.bracelet b"
                + " ON b.id = t.bracelet_id WHERE b.numero_serie = ?", String.class, carte.numeroSerie())).isEqualTo("ECHANGE");
        assertThat(jdbc.queryForObject("SELECT statut FROM dispositifs.bracelet WHERE numero_serie = ?", String.class, carte.numeroSerie()))
                .isEqualTo("EN_SAV");
        avec(get(suivi), famille.parent().jeton()).andExpect(status().isNotFound());
        avec(post(ticket + "/diagnostic"), sav).andExpect(status().isConflict());
        assertThat(jdbc.queryForList("SELECT action FROM audit.entree WHERE action IN ('TICKET_DIAGNOSTIC', 'TICKET_RENDEZ_VOUS',"
                + " 'TICKET_REMPLACEMENT_RESERVE', 'BRACELET_ECHANGE') AND cible_id IN (?, (SELECT reference FROM"
                + " dispositifs.ticket_maintenance t JOIN dispositifs.bracelet b ON b.id = t.bracelet_id WHERE b.numero_serie = ?))"
                + " ORDER BY id", String.class, braceletId(carte), carte.numeroSerie()))
                .containsExactly("TICKET_DIAGNOSTIC", "TICKET_RENDEZ_VOUS", "TICKET_REMPLACEMENT_RESERVE", "BRACELET_ECHANGE");

        // Ces actions sont réservées au service après-vente.
        avec(post(autreTicket + "/diagnostic"), voisins.parent().jeton()).andExpect(status().isForbidden());
        json(post(autreTicket + "/remplacement"), "{\"numeroSerie\":\"FG-1\"}", acteurs.agent("[\"SUPPORT\"]").jeton())
                .andExpect(status().isForbidden());
        json(post(autreTicket + "/rendez-vous"), "{\"pointRelais\":\"Pissy\",\"le\":\"" + Instant.now().plus(Duration.ofDays(1)) + "\"}",
                acteurs.agent("[\"KYC\"]").jeton()).andExpect(status().isForbidden());
    }

    @Test
    void sousVingtPourCentLeBraceletPasseEnModeEconomieEtLeParentEstAverti() throws Exception {
        ParentAvecEnfant famille = acteurs.parentAvecEnfant();
        Carte carte = equiper(famille, "3 hours");
        String bracelet = "/api/v1/enfants/" + famille.enfantId() + "/bracelet";

        emettre(carte, 600, 1, 50);
        emettre(carte, 500, 2, 18);

        acteurs.attendreSms(famille.parent().telephone(), "est à 18 %", "Le mode économie est activé");
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(configurations(carte)).containsExactly("900/1"));
        avec(get(bracelet), famille.parent().jeton()).andExpect(jsonPath("$.modeEconomie").value(true))
                .andExpect(jsonPath("$.intervalleS").value(900));

        // Sous le seuil, pas de nouvel avertissement à chaque mesure.
        sms.vider();
        emettre(carte, 400, 3, 15);
        assertThat(sms.tous()).isEmpty();

        // Rechargé : le mode économie activé d'office est levé.
        emettre(carte, 300, 4, 35);
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(configurations(carte)).containsExactly("900/1", "300/0"));
        avec(get(bracelet), famille.parent().jeton()).andExpect(jsonPath("$.modeEconomie").value(false));
    }

    @Test
    void unModeEconomieChoisiParLeParentNEstPasLeveALaRecharge() throws Exception {
        ParentAvecEnfant famille = acteurs.parentAvecEnfant();
        Carte carte = equiper(famille, "3 hours");
        String bracelet = "/api/v1/enfants/" + famille.enfantId() + "/bracelet";
        json(put(bracelet + "/configuration"), "{\"modeEconomie\":true}", famille.parent().jeton()).andExpect(status().isOk());

        emettre(carte, 600, 1, 50);
        emettre(carte, 500, 2, 17);
        acteurs.attendreSms(famille.parent().telephone(), "est à 17 %", "Pensez à le recharger");
        emettre(carte, 400, 3, 40);

        avec(get(bracelet), famille.parent().jeton()).andExpect(jsonPath("$.modeEconomie").value(true));
        assertThat(configurations(carte)).containsExactly("900/1");
    }

    // -------------------------------------------------------------------- aides

    private Carte equiper(ParentAvecEnfant famille, String anciennete) throws Exception {
        Carte carte = acteurs.equiper(famille);
        jdbc.update("UPDATE dispositifs.appairage SET debut = now() - ?::interval WHERE enfant_id = ?::uuid", anciennete, famille.enfantId());
        return carte;
    }

    /** Télémétrie mesurée il y a {@code ilYA} secondes, avec le niveau de batterie donné. */
    private void emettre(Carte carte, long ilYA, long sequence, int batterie) {
        String message = "{\"t\":" + (Instant.now().getEpochSecond() - ilYA) + ",\"seq\":" + sequence
                + ",\"lat\":12.37000,\"lon\":-1.52000,\"acc\":10,\"src\":\"gnss\",\"bat\":" + batterie + "}";
        assertThat(ingestion.telemetrie(carte.numeroSerie(), message.getBytes(StandardCharsets.UTF_8))).isEqualTo(Resultat.ACCEPTE);
        // Le dernier contact est celui de la réception ; l'essai le ramène à l'heure de la mesure.
        jdbc.update("UPDATE telemetrie.etat_bracelet SET dernier_contact = now() - make_interval(secs => ?) WHERE bracelet_id = ?::uuid",
                ilYA, braceletId(carte));
    }

    private String ticketOuvert(Carte carte) {
        return jdbc.queryForObject("SELECT t.id::text FROM dispositifs.ticket_maintenance t JOIN dispositifs.bracelet b"
                + " ON b.id = t.bracelet_id WHERE b.numero_serie = ? AND t.statut = 'OUVERT'", String.class, carte.numeroSerie());
    }

    private String braceletId(Carte carte) {
        return jdbc.queryForObject("SELECT id::text FROM dispositifs.bracelet WHERE numero_serie = ?", String.class, carte.numeroSerie());
    }

    private List<String> ticketsDe(Carte carte) {
        return jdbc.queryForList("SELECT t.statut FROM dispositifs.ticket_maintenance t JOIN dispositifs.bracelet b ON b.id = t.bracelet_id"
                + " WHERE b.numero_serie = ? ORDER BY t.ouvert_le", String.class, carte.numeroSerie());
    }

    /** Commandes de configuration émises vers le bracelet, sous la forme « intervalle/économie ». */
    private List<String> configurations(Carte carte) {
        return jdbc.queryForList("SELECT c.message FROM dispositifs.commande c JOIN dispositifs.bracelet b ON b.id = c.bracelet_id"
                + " WHERE b.numero_serie = ? AND c.type = 'CONFIGURATION' ORDER BY c.sequence", String.class, carte.numeroSerie())
                .stream().map(message -> new String(Base64.getUrlDecoder().decode(message.split("\\.")[0]), StandardCharsets.UTF_8))
                .map(corps -> Acteurs.extraire(corps, "\"int\":(\\d+)") + "/" + Acteurs.extraire(corps, "\"eco\":(\\d)")).toList();
    }

    private ResultActions avec(MockHttpServletRequestBuilder requete, String jeton) throws Exception {
        return mvc.perform(jeton == null ? requete : requete.header("Authorization", "Bearer " + jeton));
    }

    private ResultActions json(MockHttpServletRequestBuilder requete, String corps, String jeton) throws Exception {
        return avec(requete.contentType(MediaType.APPLICATION_JSON).content(corps), jeton);
    }
}
