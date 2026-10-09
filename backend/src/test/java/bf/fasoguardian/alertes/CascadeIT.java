package bf.fasoguardian.alertes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import bf.fasoguardian.Acteurs;
import bf.fasoguardian.Acteurs.Carte;
import bf.fasoguardian.Acteurs.ParentAvecEnfant;
import bf.fasoguardian.TestIntegration;
import bf.fasoguardian.alertes.application.Cascade;
import bf.fasoguardian.notifications.infrastructure.SmsBacASable;
import bf.fasoguardian.telemetrie.application.Ingestion;
import bf.fasoguardian.telemetrie.application.Ingestion.Resultat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** Sollicitation en cascade d'une alerte critique restée sans réponse (US-SYS-005). */
class CascadeIT extends TestIntegration {

    /** Point de contact institutionnel fictif, convenu dans la configuration des essais. */
    private static final String INSTITUTION = "+22670990017";

    @Autowired
    MockMvc mvc;

    @Autowired
    SmsBacASable sms;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    Ingestion ingestion;

    @Autowired
    Cascade cascade;

    Acteurs acteurs;

    @BeforeEach
    void preparer() {
        acteurs = new Acteurs(mvc, sms);
        // Les alertes des autres essais ne doivent pas être sollicitées ici : elles sont tenues pour prises en charge.
        jdbc.update("UPDATE alertes.alerte SET statut = 'ACQUITTEE' WHERE statut = 'OUVERTE'");
    }

    @Test
    void sansReponseDesParentsLeContactPuisLInstitutionSontSollicitesChacunUneFois() throws Exception {
        ParentAvecEnfant famille = acteurs.parentAvecEnfant();
        String jeton = famille.parent().jeton();
        contact(famille, "Oncle", "76 44 55 01");
        contact(famille, "Tante", "76 44 55 02");
        String alerte = sos(famille);
        sms.vider();

        // Dans le délai laissé aux parents, personne d'autre n'est sollicité.
        assertThat(cascade.solliciter()).isZero();
        assertThat(sms.tous()).isEmpty();

        // Délai dépassé sans prise en charge : les contacts d'urgence sont prévenus, sans nom ni position.
        vieillir(alerte, "6 minutes");
        assertThat(cascade.solliciter()).isEqualTo(1);
        for (String numero : List.of("+22676445501", "+22676445502")) {
            assertThat(sms.dernierPour(numero).orElseThrow().texte()).contains("contact d'urgence", "ses parents n'ont pas encore répondu")
                    .doesNotContain("Yacouba").doesNotContain("12.37");
        }
        assertThat(sms.dernierPour(INSTITUTION)).isEmpty();
        // Une seule fois : le passage suivant ne renvoie rien.
        sms.vider();
        assertThat(cascade.solliciter()).isZero();
        assertThat(sms.tous()).isEmpty();

        // Toujours sans réponse au délai suivant : le point de contact institutionnel, avec la seule référence.
        jdbc.execute("ALTER TABLE alertes.action_alerte DISABLE TRIGGER action_alerte_ajout_seul");
        jdbc.update("UPDATE alertes.action_alerte SET effectuee_le = now() - INTERVAL '11 minutes' WHERE alerte_id = ?::uuid"
                + " AND type = 'CONTACT_SOLLICITE'", alerte);
        jdbc.execute("ALTER TABLE alertes.action_alerte ENABLE TRIGGER action_alerte_ajout_seul");
        assertThat(cascade.solliciter()).isEqualTo(1);
        assertThat(sms.dernierPour(INSTITUTION).orElseThrow().texte()).contains("reste sans réponse", "Référence ALR-")
                .doesNotContain("Yacouba").doesNotContain("12.37");
        assertThat(cascade.solliciter()).isZero();

        // Le parent voit chaque étape dans le journal de l'alerte.
        mvc.perform(get("/api/v1/alertes/" + alerte).header("Authorization", "Bearer " + jeton)).andExpect(status().isOk())
                .andExpect(jsonPath("$.actions.length()").value(3))
                .andExpect(jsonPath("$.actions[?(@.type == 'CONTACT_SOLLICITE')].motif").value("2 contacts d'urgence prévenus par SMS"))
                .andExpect(jsonPath("$.actions[?(@.type == 'INSTITUTION_SOLLICITEE')].motif").value("Point de contact institutionnel prévenu par SMS"));
        assertThat(jdbc.queryForList("SELECT action FROM audit.entree WHERE action LIKE 'CASCADE%' AND cible_id = ? ORDER BY id",
                String.class, alerte)).containsExactly("CASCADE_CONTACT_SOLLICITE", "CASCADE_INSTITUTION_SOLLICITEE");
    }

    @Test
    void uneAlertePriseEnChargeNEstJamaisTransmiseAUnTiers() throws Exception {
        ParentAvecEnfant famille = acteurs.parentAvecEnfant();
        contact(famille, "Oncle", "76 44 55 03");
        String alerte = sos(famille);
        mvc.perform(post("/api/v1/alertes/" + alerte + "/acquittement").header("Authorization", "Bearer " + famille.parent().jeton()))
                .andExpect(status().isOk());
        vieillir(alerte, "30 minutes");
        sms.vider();

        assertThat(cascade.solliciter()).isZero();

        assertThat(sms.tous()).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM alertes.action_alerte WHERE alerte_id = ?::uuid AND type LIKE '%SOLLICITE%'",
                Integer.class, alerte)).isZero();
    }

    @Test
    void sansContactDUrgenceLeJournalLeDitEtUneAlerteNonCritiqueNeDeclencheRien() throws Exception {
        ParentAvecEnfant famille = acteurs.parentAvecEnfant();
        Carte carte = acteurs.equiper(famille);
        jdbc.update("UPDATE dispositifs.appairage SET debut = now() - INTERVAL '1 hour' WHERE enfant_id = ?::uuid", famille.enfantId());
        // Une chute est une alerte importante, non critique : elle ne passe pas par la cascade.
        emettre(carte, 1, "fall");
        String chute = attendreAlerte(famille, "CHUTE");
        vieillir(chute, "30 minutes");
        assertThat(cascade.solliciter()).isZero();

        emettre(carte, 2, "sos");
        String sos = attendreAlerte(famille, "SOS");
        vieillir(sos, "6 minutes");
        assertThat(cascade.solliciter()).isEqualTo(1);

        assertThat(jdbc.queryForObject("SELECT motif FROM alertes.action_alerte WHERE alerte_id = ?::uuid AND type = 'CONTACT_SOLLICITE'",
                String.class, sos)).isEqualTo("Aucun contact d'urgence enregistré");
    }

    // -------------------------------------------------------------------- aides

    private void contact(ParentAvecEnfant famille, String lien, String telephone) throws Exception {
        mvc.perform(post("/api/v1/enfants/" + famille.enfantId() + "/contacts").header("Authorization", "Bearer " + famille.parent().jeton())
                .contentType(MediaType.APPLICATION_JSON).content("{\"lien\":\"" + lien + "\",\"nom\":\"Issouf Ouédraogo\",\"telephone\":\""
                        + telephone + "\",\"visibleSurQr\":false}")).andExpect(status().isCreated());
    }

    /** Équipe l'enfant, fait déclencher le SOS de son bracelet et rend l'identifiant de l'alerte. */
    private String sos(ParentAvecEnfant famille) throws Exception {
        Carte carte = acteurs.equiper(famille);
        jdbc.update("UPDATE dispositifs.appairage SET debut = now() - INTERVAL '1 hour' WHERE enfant_id = ?::uuid", famille.enfantId());
        emettre(carte, 1, "sos");
        return attendreAlerte(famille, "SOS");
    }

    private void emettre(Carte carte, long sequence, String evenement) {
        String message = "{\"t\":" + (Instant.now().getEpochSecond() - 30) + ",\"seq\":" + sequence + ",\"ev\":\"" + evenement
                + "\",\"lat\":12.37,\"lon\":-1.52}";
        assertThat(ingestion.alerte(carte.numeroSerie(), message.getBytes(StandardCharsets.UTF_8))).isEqualTo(Resultat.ACCEPTE);
    }

    private String attendreAlerte(ParentAvecEnfant famille, String type) {
        String[] id = new String[1];
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            id[0] = jdbc.queryForList("SELECT id::text FROM alertes.alerte WHERE enfant_id = ?::uuid AND type = ?", String.class,
                    famille.enfantId(), type).stream().findFirst().orElse(null);
            assertThat(id[0]).isNotNull();
        });
        return id[0];
    }

    private void vieillir(String alerte, String duree) {
        jdbc.update("UPDATE alertes.alerte SET ouverte_le = now() - ?::interval WHERE id = ?::uuid", duree, alerte);
    }
}
