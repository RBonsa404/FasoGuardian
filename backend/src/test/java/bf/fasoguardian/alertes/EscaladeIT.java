package bf.fasoguardian.alertes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;

import bf.fasoguardian.Acteurs;
import bf.fasoguardian.Acteurs.Carte;
import bf.fasoguardian.Acteurs.Parent;
import bf.fasoguardian.Acteurs.ParentAvecEnfant;
import bf.fasoguardian.TestIntegration;
import bf.fasoguardian.alertes.application.Escalades;
import bf.fasoguardian.notifications.infrastructure.SmsBacASable;
import bf.fasoguardian.telemetrie.application.ReceptionMessages;
import bf.fasoguardian.telemetrie.application.ReceptionMessages.Flux;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Escalade vers les forces de sécurité et dossier de signalement (US-PAR-010, REQ-SYS-022). */
class EscaladeIT extends TestIntegration {

    @Autowired
    MockMvc mvc;

    @Autowired
    SmsBacASable sms;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ReceptionMessages reception;

    @Autowired
    Escalades escalades;

    Acteurs acteurs;

    @BeforeEach
    void preparer() {
        acteurs = new Acteurs(mvc, sms);
    }

    @Test
    void sansConventionLEscaladeConfirmeeGenereUnDossierCompletQueLeParentRemet() throws Exception {
        ParentAvecEnfant famille = acteurs.parentAvecEnfant();
        Parent parent = famille.parent();
        Carte carte = acteurs.equiper(famille);
        String enfant = "/api/v1/enfants/" + famille.enfantId();
        json(patch(enfant), "{\"profil\":{\"ecole\":\"Les Manguiers\",\"quartier\":\"Tampouy\",\"tailleCm\":128,"
                + "\"signesDistinctifs\":\"tresses courtes, cicatrice au genou droit\"}}", parent.jeton()).andExpect(status().isOk());
        json(put(enfant + "/sante"), "{\"groupeSanguin\":\"O+\",\"groupeSanguinSurQr\":false,\"elements\":["
                + "{\"type\":\"ALLERGIE\",\"libelle\":\"Arachide\",\"critique\":true},"
                + "{\"type\":\"TRAITEMENT\",\"libelle\":\"Suivi orthophonique\",\"critique\":false}]}", parent.jeton())
                .andExpect(status().isOk());
        jdbc.update("UPDATE dispositifs.appairage SET debut = now() - INTERVAL '1 day' WHERE enfant_id = ?::uuid", famille.enfantId());
        long t = Instant.now().getEpochSecond();
        position(carte, t - 9000, 1, 12.36000, -1.53000);
        position(carte, t - 3600, 2, 12.36500, -1.52500);
        position(carte, t - 300, 3, 12.37140, -1.51970);

        String alerte = Acteurs.extraire(avec(post(enfant + "/signalement"), parent.jeton()).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "\"id\":\"([0-9a-f-]{36})\"");
        String base = "/api/v1/alertes/" + alerte;

        avec(get(base + "/signalement/apercu"), parent.jeton()).andExpect(status().isOk())
                .andExpect(jsonPath("$.enfant.nom").value("Ouédraogo")).andExpect(jsonPath("$.enfant.tailleCm").value(128))
                .andExpect(jsonPath("$.enfant.informationsMedicales.length()").value(1))
                .andExpect(jsonPath("$.enfant.informationsMedicales[0]").value("Allergie : Arachide"))
                .andExpect(jsonPath("$.positionsDuTrajet").value(2))
                .andExpect(jsonPath("$.dernierePosition.latitude").value(12.3714))
                .andExpect(jsonPath("$.conventionActive").value(false));

        json(post(base + "/escalade"), "{}", parent.jeton()).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SECOND_FACTEUR_REQUIS"));
        avec(get(base + "/signalement"), parent.jeton()).andExpect(status().isNotFound());

        String corps = json(post(base + "/escalade"), "{" + code(parent) + "}", parent.jeton()).andExpect(status().isCreated())
                .andExpect(jsonPath("$.canal").value("REMISE_PAR_LE_PARENT")).andExpect(jsonPath("$.dossierDisponible").value(true))
                .andReturn().getResponse().getContentAsString();
        String reference = Acteurs.extraire(corps, "\"reference\":\"(FG-SIG-\\d{6})\"");
        String empreinte = Acteurs.extraire(corps, "\"empreinteDossier\":\"([0-9a-f]{64})\"");
        avec(get(base), parent.jeton()).andExpect(jsonPath("$.statut").value("ESCALADEE"))
                .andExpect(jsonPath("$.actions[2].type").value("ESCALADE"));
        json(post(base + "/escalade"), "{" + code(parent) + "}", parent.jeton()).andExpect(status().isConflict());

        byte[] pdf = avec(get(base + "/signalement/dossier"), parent.jeton()).andExpect(status().isOk())
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_PDF_VALUE))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"" + reference + ".pdf\""))
                .andExpect(header().string("Cache-Control", "no-store")).andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        assertThat(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(pdf))).as("empreinte du dossier remis")
                .isEqualTo(empreinte);
        String texte = texteDe(pdf);
        assertThat(texte).contains(reference, "OUÉDRAOGO Awa", "14/03/2018", "128 cm", "tresses courtes, cicatrice au genou droit",
                "Les Manguiers", "Tampouy", "Bracelet porté : " + carte.numeroSerie(), "Allergie : Arachide", "Signalement par un tuteur", "12.37140, -1.51970",
                "Trajet des deux dernières heures (2 positions)", "12.36500, -1.52500");
        assertThat(texte).as("ni information médicale non critique, ni position hors des deux heures")
                .doesNotContain("orthophonique", "12.36000");

        // Le dossier est chiffré en base ; l'escalade, la génération et le téléchargement sont journalisés.
        byte[] stocke = jdbc.queryForObject("SELECT dossier_chiffre FROM alertes.signalement_fds WHERE alerte_id = ?::uuid", byte[].class, alerte);
        assertThat(new String(stocke, StandardCharsets.ISO_8859_1)).doesNotContain("%PDF", "Arachide");
        assertThat(jdbc.queryForList("SELECT action FROM audit.entree WHERE cible_id = ? ORDER BY id", String.class, alerte))
                .containsSubsequence("APERCU_SIGNALEMENT_CONSULTE", "ALERTE_ESCALADEE", "DOSSIER_SIGNALEMENT_GENERE",
                        "DOSSIER_SIGNALEMENT_TELECHARGE");

        // Une alerte escaladée se lève encore, avec son motif.
        json(post(base + "/levee"), "{\"motif\":\"Enfant retrouvé par la Police\"}", parent.jeton()).andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("LEVEE"));
    }

    @Test
    void leDossierEstEffaceAuBoutDeTrenteJoursMaisSaReferenceEtSonEmpreinteRestent() throws Exception {
        ParentAvecEnfant famille = acteurs.parentAvecEnfant();
        Parent parent = famille.parent();
        String alerte = Acteurs.extraire(avec(post("/api/v1/enfants/" + famille.enfantId() + "/signalement"), parent.jeton())
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "\"id\":\"([0-9a-f-]{36})\"");
        String base = "/api/v1/alertes/" + alerte;
        json(post(base + "/escalade"), "{" + code(parent) + "}", parent.jeton()).andExpect(status().isCreated());
        // Sans bracelet ni fiche médicale, le dossier reste lisible et le dit.
        assertThat(texteDe(avec(get(base + "/signalement/dossier"), parent.jeton()).andReturn().getResponse().getContentAsByteArray()))
                .contains("Aucune information médicale critique", "aucune position au cours des deux dernières heures", "non renseigné",
                        "aucun bracelet associé");

        escalades.effacerLesDossiersEchus();
        avec(get(base + "/signalement/dossier"), parent.jeton()).andExpect(status().isOk());

        jdbc.update("UPDATE alertes.signalement_fds SET cree_le = now() - INTERVAL '31 days' WHERE alerte_id = ?::uuid", alerte);
        escalades.effacerLesDossiersEchus();
        avec(get(base + "/signalement/dossier"), parent.jeton()).andExpect(status().isNotFound());
        avec(get(base + "/signalement"), parent.jeton()).andExpect(status().isOk()).andExpect(jsonPath("$.dossierDisponible").value(false))
                .andExpect(jsonPath("$.reference").isNotEmpty()).andExpect(jsonPath("$.empreinteDossier").isNotEmpty());
        assertThat(jdbc.queryForObject("SELECT dossier_chiffre IS NULL AND efface_le IS NOT NULL FROM alertes.signalement_fds WHERE alerte_id = ?::uuid",
                Boolean.class, alerte)).isTrue();
    }

    @Test
    void seuleUneAlertePriseEnChargeParUnTuteurDeLEnfantPeutEtreEscaladee() throws Exception {
        ParentAvecEnfant famille = acteurs.parentAvecEnfant();
        Parent parent = famille.parent();
        Carte carte = acteurs.equiper(famille);
        reception.recevoir(Flux.ALERT, carte.numeroSerie(), ("{\"t\":" + (Instant.now().getEpochSecond() + 1) + ",\"seq\":1,\"ev\":\"sos\"}")
                .getBytes(StandardCharsets.UTF_8));
        String[] alerte = new String[1];
        org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(10)).untilAsserted(() -> {
            alerte[0] = jdbc.queryForList("SELECT id::text FROM alertes.alerte WHERE enfant_id = ?::uuid", String.class, famille.enfantId())
                    .stream().findFirst().orElse(null);
            assertThat(alerte[0]).isNotNull();
        });
        String base = "/api/v1/alertes/" + alerte[0];

        // Pas encore prise en charge : l'escalade est refusée avant même de consommer un code.
        json(post(base + "/escalade"), "{\"codeSecondFacteur\":\"000000\"}", parent.jeton()).andExpect(status().isConflict());

        Parent etranger = acteurs.parent();
        avec(get(base + "/signalement/apercu"), etranger.jeton()).andExpect(status().isNotFound());
        json(post(base + "/escalade"), "{\"codeSecondFacteur\":\"000000\"}", etranger.jeton()).andExpect(status().isNotFound());
        avec(get(base + "/signalement"), etranger.jeton()).andExpect(status().isNotFound());
        avec(get(base + "/signalement/dossier"), etranger.jeton()).andExpect(status().isNotFound());
        avec(get(base + "/signalement/apercu"), null).andExpect(status().isUnauthorized());
        avec(get(base + "/signalement/dossier"), acteurs.jetonSav()).andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM alertes.signalement_fds WHERE alerte_id = ?::uuid", Integer.class, alerte[0]))
                .isZero();
    }

    // -------------------------------------------------------------------- aides

    private void position(Carte carte, long t, long sequence, double latitude, double longitude) {
        reception.recevoir(Flux.TELEMETRY, carte.numeroSerie(), String.format(Locale.ROOT,
                "{\"t\":%d,\"seq\":%d,\"lat\":%.5f,\"lon\":%.5f,\"acc\":9,\"src\":\"gnss\"}", t, sequence, latitude, longitude)
                .getBytes(StandardCharsets.UTF_8));
    }

    private String code(Parent parent) throws Exception {
        jdbc.update("DELETE FROM identite.code_usage_unique WHERE finalite = '2F_ESCALADER_FORCES_SECURITE'");
        json(post("/api/v1/moi/second-facteur"), "{\"action\":\"ESCALADER_FORCES_SECURITE\"}", parent.jeton()).andExpect(status().isAccepted());
        return "\"codeSecondFacteur\":\"" + acteurs.dernierCodeDeConfirmation(parent.telephone()) + "\"";
    }

    private static String texteDe(byte[] pdf) throws Exception {
        PdfReader lecteur = new PdfReader(pdf);
        StringBuilder texte = new StringBuilder();
        PdfTextExtractor extracteur = new PdfTextExtractor(lecteur);
        for (int page = 1; page <= lecteur.getNumberOfPages(); page++) {
            texte.append(extracteur.getTextFromPage(page)).append('\n');
        }
        lecteur.close();
        return texte.toString().replaceAll("\\s+", " ");
    }

    private ResultActions avec(MockHttpServletRequestBuilder requete, String jeton) throws Exception {
        return mvc.perform(jeton == null ? requete : requete.header("Authorization", "Bearer " + jeton));
    }

    private ResultActions json(MockHttpServletRequestBuilder requete, String corps, String jeton) throws Exception {
        return avec(requete.contentType(MediaType.APPLICATION_JSON).content(corps), jeton);
    }
}
