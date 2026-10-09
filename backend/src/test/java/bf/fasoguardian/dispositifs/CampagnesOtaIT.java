package bf.fasoguardian.dispositifs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.security.Signature;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import bf.fasoguardian.Acteurs;
import bf.fasoguardian.Acteurs.Carte;
import bf.fasoguardian.Acteurs.ParentAvecEnfant;
import bf.fasoguardian.TestIntegration;
import bf.fasoguardian.dispositifs.application.CampagnesOta;
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

/** Mise à jour signée du logiciel embarqué, déployée par vagues (US-PAR-013). */
class CampagnesOtaIT extends TestIntegration {

    private static final String OTA = "/api/v1/console/sav/ota";
    private static final String SHA = "9f2c5a1e7b3d4c6f8a0b1c2d3e4f5a6b7c8d9e0f1a2b3c4d5e6f7a8b9c0d1e2f";

    @Autowired
    MockMvc mvc;

    @Autowired
    SmsBacASable sms;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    Ingestion ingestion;

    @Autowired
    CampagnesOta campagnes;

    Acteurs acteurs;

    @BeforeEach
    void preparer() {
        acteurs = new Acteurs(mvc, sms);
        // Le parc porté de l'essai ne compte que les bracelets qu'il équipe lui-même.
        jdbc.update("UPDATE dispositifs.appairage SET fin = now(), motif_fin = 'DESAPPAIRAGE' WHERE fin IS NULL");
        jdbc.update("UPDATE dispositifs.campagne_ota SET statut = 'TERMINEE'");
    }

    @Test
    void seuleUneImageSigneeParLaCleDePublicationEstAcceptee() throws Exception {
        String sav = acteurs.jetonSav();
        String version = version();

        // Signature d'un autre manifeste, signature illisible, image modifiée après signature : refusées et journalisées.
        preparer(sav, version, 421_888, SHA, signer(CampagnesOta.manifeste("9.9.9", 421_888, SHA))).andExpect(status().isBadRequest());
        preparer(sav, version, 421_888, SHA, "pas-une-signature").andExpect(status().isBadRequest());
        preparer(sav, version, 421_889, SHA, signer(CampagnesOta.manifeste(version, 421_888, SHA))).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit.entree WHERE action = 'IMAGE_OTA_REFUSEE' AND cible_id = ?",
                Integer.class, version)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM dispositifs.campagne_ota WHERE version = ?", Integer.class, version)).isZero();

        preparer(sav, version, 421_888, SHA.toUpperCase(), signer(CampagnesOta.manifeste(version, 421_888, SHA)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.statut").value("PREPAREE"))
                .andExpect(jsonPath("$.vagues.length()").value(4)).andExpect(jsonPath("$.vagues[0].etat").value("PRETE"))
                .andExpect(jsonPath("$.vagues[1].etat").value("PLANIFIEE"));
        // Une version ne se publie qu'une fois.
        preparer(sav, version, 421_888, SHA, signer(CampagnesOta.manifeste(version, 421_888, SHA))).andExpect(status().isConflict());

        // Service après-vente et administrateur seulement.
        avec(get(OTA), acteurs.jetonAdmin()).andExpect(status().isOk());
        avec(get(OTA), acteurs.agent("[\"KYC\"]").jeton()).andExpect(status().isForbidden());
        avec(get(OTA), acteurs.parent().jeton()).andExpect(status().isForbidden());
        avec(get(OTA), null).andExpect(status().isUnauthorized());
    }

    @Test
    void lesVaguesVisentLeParcPeuAPeuEtLesParentsSontInformesAvantLInstallation() throws Exception {
        String sav = acteurs.jetonSav();
        List<ParentAvecEnfant> familles = new ArrayList<>();
        List<Carte> cartes = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            ParentAvecEnfant famille = acteurs.parentAvecEnfant();
            familles.add(famille);
            cartes.add(acteurs.equiper(famille));
        }
        String version = version();
        String id = Acteurs.extraire(preparer(sav, version, 421_888, SHA, signer(CampagnesOta.manifeste(version, 421_888, SHA)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "\"id\":\"([0-9a-f-]{36})\"");

        // Vague 1 : un seul bracelet, le premier par numéro ; sa demande signée part, son parent est informé.
        avec(post(OTA + "/" + id + "/vagues"), sav).andExpect(status().isOk()).andExpect(jsonPath("$.statut").value("EN_COURS"))
                .andExpect(jsonPath("$.vagues[0].cibles").value(1)).andExpect(jsonPath("$.vagues[0].etat").value("EN_COURS"))
                .andExpect(jsonPath("$.vagues[1].etat").value("PRETE"));
        String premier = jdbc.queryForObject("""
                SELECT b.numero_serie FROM dispositifs.cible_ota c JOIN dispositifs.bracelet b ON b.id = c.bracelet_id
                WHERE c.campagne_id = ?::uuid""", String.class, id);
        assertThat(premier).isEqualTo(cartes.stream().map(Carte::numeroSerie).sorted().findFirst().orElseThrow());
        String message = jdbc.queryForObject("""
                SELECT c.message FROM dispositifs.commande c JOIN dispositifs.bracelet b ON b.id = c.bracelet_id
                WHERE b.numero_serie = ? AND c.type = 'MISE_A_JOUR'""", String.class, premier);
        String corps = new String(Base64.getUrlDecoder().decode(message.substring(0, message.indexOf('.'))), StandardCharsets.UTF_8);
        assertThat(corps).contains("\"cmd\":\"ota\"", "\"v\":\"" + version + "\"", "\"sha\":\"" + SHA + "\"", "\"size\":421888", "\"sig\":\"");
        assertThat(jdbc.queryForList("SELECT texte FROM notifications.notification WHERE modele = 'MISE_A_JOUR_BRACELET' AND texte LIKE ?",
                String.class, "%(" + version + ")%")).singleElement().asString().contains("va être installée sur le bracelet " + premier);

        // La notification se relit dans l'application, par son destinataire seulement, même sans push.
        ParentAvecEnfant vise = familles.get(cartes.indexOf(cartes.stream().filter(c -> c.numeroSerie().equals(premier)).findFirst()
                .orElseThrow()));
        avec(get("/api/v1/notifications"), vise.parent().jeton()).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].modele").value("MISE_A_JOUR_BRACELET"))
                .andExpect(jsonPath("$[0].lien").value("/enfants/" + vise.enfantId() + "/bracelet"));
        avec(get("/api/v1/notifications"), acteurs.parent().jeton()).andExpect(jsonPath("$.length()").value(0));
        avec(get("/api/v1/notifications"), null).andExpect(status().isUnauthorized());

        // Le bracelet redémarre sur la nouvelle version : le parc est à jour, la vague est terminée.
        assertThat(ingestion.etat(premier, ("{\"online\":true,\"fw\":\"" + version + "\"}").getBytes(StandardCharsets.UTF_8)))
                .isEqualTo(Resultat.ACCEPTE);
        assertThat(jdbc.queryForObject("SELECT version_logiciel FROM dispositifs.bracelet WHERE numero_serie = ?", String.class, premier))
                .isEqualTo(version);
        avec(get(OTA), sav).andExpect(jsonPath("$[0].vagues[0].installes").value(1)).andExpect(jsonPath("$[0].vagues[0].etat").value("TERMINEE"));

        // Vagues 2 et 3 : sur un parc de trois, la part visée est déjà atteinte. Vague 4 : tout le reste.
        avec(post(OTA + "/" + id + "/vagues"), sav).andExpect(jsonPath("$.vagues[1].cibles").value(0));
        avec(post(OTA + "/" + id + "/vagues"), sav).andExpect(jsonPath("$.vagues[2].cibles").value(0));
        avec(post(OTA + "/" + id + "/vagues"), sav).andExpect(jsonPath("$.vagues[3].cibles").value(2))
                .andExpect(jsonPath("$.vagues[3].etat").value("EN_COURS")).andExpect(jsonPath("$.statut").value("EN_COURS"));
        avec(post(OTA + "/" + id + "/vagues"), sav).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications.notification WHERE modele = 'MISE_A_JOUR_BRACELET' AND texte LIKE ?",
                Integer.class, "%(" + version + ")%")).isEqualTo(3);

        // Une seule campagne déploie à la fois : une autre ne se lance pas tant que celle-ci est en cours.
        String autre = version();
        String autreId = Acteurs.extraire(preparer(sav, autre, 421_888, SHA, signer(CampagnesOta.manifeste(autre, 421_888, SHA)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "\"id\":\"([0-9a-f-]{36})\"");
        avec(post(OTA + "/" + autreId + "/vagues"), sav).andExpect(status().isConflict());
        jdbc.update("UPDATE dispositifs.campagne_ota SET statut = 'TERMINEE' WHERE id = ?::uuid", autreId);

        // En pause, plus rien ne part ; reprise, les bracelets restés muets reçoivent de nouveau la demande.
        jdbc.update("UPDATE dispositifs.cible_ota SET emise_le = now() - INTERVAL '2 hours' WHERE campagne_id = ?::uuid", id);
        avec(post(OTA + "/" + id + "/pause"), sav).andExpect(jsonPath("$.statut").value("EN_PAUSE"));
        assertThat(campagnes.relancer()).isZero();
        avec(post(OTA + "/" + id + "/vagues"), sav).andExpect(status().isConflict());
        avec(post(OTA + "/" + id + "/reprise"), sav).andExpect(jsonPath("$.statut").value("EN_COURS"));
        assertThat(campagnes.relancer()).isEqualTo(2);
        assertThat(campagnes.relancer()).isZero();

        // Tous les bracelets visés ont changé de version : la campagne est terminée.
        for (Carte carte : cartes) {
            ingestion.etat(carte.numeroSerie(), ("{\"online\":true,\"fw\":\"" + version + "\"}").getBytes(StandardCharsets.UTF_8));
        }
        avec(get(OTA), sav).andExpect(jsonPath("$[?(@.id == '" + id + "')].statut").value("TERMINEE"))
                .andExpect(jsonPath("$[?(@.id == '" + id + "')].vagues[3].installes").value(2));
        assertThat(jdbc.queryForList("SELECT action FROM audit.entree WHERE action LIKE '%OTA%' AND cible_id LIKE ? ORDER BY id",
                String.class, version + "%")).containsExactly("CAMPAGNE_OTA_PREPAREE", "VAGUE_OTA_LANCEE", "VAGUE_OTA_LANCEE",
                        "VAGUE_OTA_LANCEE", "VAGUE_OTA_LANCEE", "CAMPAGNE_OTA_EN_PAUSE", "CAMPAGNE_OTA_REPRISE", "CAMPAGNE_OTA_TERMINEE");
        assertThat(familles).hasSize(3);
    }

    // -------------------------------------------------------------------- aides

    private static String version() {
        ThreadLocalRandom hasard = ThreadLocalRandom.current();
        return "7." + hasard.nextInt(1000) + "." + hasard.nextInt(1000);
    }

    /** Signe le manifeste comme le ferait le poste de publication du logiciel embarqué. */
    private static String signer(String manifeste) throws Exception {
        Signature signature = Signature.getInstance("SHA256withECDSAinP1363Format");
        signature.initSign(CLE_OTA.getPrivate());
        signature.update(manifeste.getBytes(StandardCharsets.UTF_8));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(signature.sign());
    }

    private ResultActions preparer(String jeton, String version, long taille, String sha256, String signature) throws Exception {
        return mvc.perform(post(OTA).header("Authorization", "Bearer " + jeton).contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":\"" + version + "\",\"urlImage\":\"https://mises-a-jour.fasoguardian.test/fg-" + version
                        + ".bin\",\"tailleOctets\":" + taille + ",\"sha256\":\"" + sha256 + "\",\"signature\":\"" + signature
                        + "\",\"note\":\"Correctif d'autonomie en 2G\"}"));
    }

    private ResultActions avec(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder requete, String jeton)
            throws Exception {
        return mvc.perform(jeton == null ? requete : requete.header("Authorization", "Bearer " + jeton));
    }
}
