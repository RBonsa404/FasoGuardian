package bf.fasoguardian.identite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import bf.fasoguardian.Acteurs;
import bf.fasoguardian.Acteurs.ParentAvecEnfant;
import bf.fasoguardian.TestIntegration;
import bf.fasoguardian.notifications.infrastructure.SmsBacASable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Litige de filiation : gel conservatoire, suspension de la géolocalisation, décision (US-KYC-001). */
class LitigesIT extends TestIntegration {

    private static final String LITIGES = "/api/v1/console/kyc/litiges";
    private static final String CONTACT = "{\"lien\":\"Tante\",\"nom\":\"Fatou Sawadogo\",\"telephone\":\"76 11 22 33\",\"visibleSurQr\":false}";

    @Autowired
    MockMvc mvc;

    @Autowired
    SmsBacASable sms;

    @Autowired
    JdbcTemplate jdbc;

    Acteurs acteurs;
    String kyc;

    @BeforeEach
    void preparer() throws Exception {
        acteurs = new Acteurs(mvc, sms);
        kyc = acteurs.agent("[\"KYC\"]").jeton();
    }

    @Test
    void lOuvertureGeleLesReglagesEtPeutSuspendreLaPositionSansToucherALaSecurite() throws Exception {
        ParentAvecEnfant famille = acteurs.parentAvecEnfant();
        String parent = famille.parent().jeton();
        String enfant = "/api/v1/enfants/" + famille.enfantId();
        acteurs.equiper(famille);
        json(post(enfant + "/contacts"), CONTACT, parent).andExpect(status().isCreated());

        String litigeId = Acteurs.extraire(json(post(LITIGES), "{\"dossierKyc\":\"" + dossier(famille) + "\",\"motif\":\"Jugement de "
                + "garde exclusive produit par l'autre parent\",\"suspendreLaGeolocalisation\":true}", kyc).andExpect(status().isCreated())
                .andExpect(jsonPath("$.statut").value("OUVERT")).andExpect(jsonPath("$.reference").value(org.hamcrest.Matchers.startsWith("LIT-")))
                .andExpect(jsonPath("$.geolocalisationSuspendue").value(true)).andExpect(jsonPath("$.tuteur").isNotEmpty())
                .andExpect(jsonPath("$.echeanceLe").exists()).andReturn().getResponse().getContentAsString(), "\"id\":\"([0-9a-f-]{36})\"");
        acteurs.attendreSms(famille.parent().telephone(), "en cours d'instruction", "Le SOS et la page QR restent actifs");

        // Réglages gelés : rien ne se modifie autour de l'enfant, ni le numéro, ni la clôture du compte.
        json(post(enfant + "/contacts"), CONTACT, parent).andExpect(status().isLocked()).andExpect(jsonPath("$.code").value("COMPTE_GELE"));
        json(put(enfant + "/sante"), "{\"elements\":[]}", parent).andExpect(status().isLocked());
        json(put(enfant + "/bracelet/configuration"), "{\"modeEconomie\":true}", parent).andExpect(status().isLocked());
        json(post("/api/v1/moi/cloture"), "{}", parent).andExpect(status().isLocked()).andExpect(jsonPath("$.code").value("COMPTE_GELE"));
        json(post("/api/v1/moi/telephone/code"), "{\"telephone\":\"70 00 00 01\"}", parent).andExpect(status().isLocked());
        // La consultation reste possible, sauf la position, suspendue à titre conservatoire.
        avec(get(enfant), parent).andExpect(status().isOk());
        avec(get(enfant + "/contacts"), parent).andExpect(status().isOk());
        avec(get(enfant + "/position"), parent).andExpect(status().isLocked()).andExpect(jsonPath("$.code").value("GEOLOCALISATION_SUSPENDUE"));
        avec(get(enfant + "/trajets?jour=2026-10-09"), parent).andExpect(status().isLocked());
        avec(post(enfant + "/bracelet/localisation"), parent).andExpect(status().isLocked());
        // Ce qui protège l'enfant n'est jamais gelé : prise en charge des alertes, paiement de l'abonnement.
        avec(post(enfant + "/alertes/prise-en-charge"), parent).andExpect(status().isOk());
        json(post(enfant + "/abonnement/paiements").header("Idempotency-Key", "litige-" + UUID.randomUUID()), "{\"offre\":\"ESSENTIEL\","
                + "\"moyen\":\"ORANGE_MONEY\",\"numero\":\"70 11 22 33\",\"renouvellementAuto\":false}", parent).andExpect(status().isAccepted());

        // L'agent lève la suspension de la position : le gel des réglages demeure.
        json(put(LITIGES + "/" + litigeId + "/geolocalisation"), "{\"suspendue\":false}", kyc).andExpect(status().isOk())
                .andExpect(jsonPath("$.geolocalisationSuspendue").value(false));
        avec(get(enfant + "/position"), parent).andExpect(status().isOk());
        json(post(enfant + "/contacts"), CONTACT, parent).andExpect(status().isLocked());

        // Un autre parent n'est pas concerné par ce litige.
        ParentAvecEnfant autre = acteurs.parentAvecEnfant();
        json(post("/api/v1/enfants/" + autre.enfantId() + "/contacts"), CONTACT, autre.parent().jeton()).andExpect(status().isCreated());
    }

    @Test
    void laDecisionEstAppliqueeJournaliseeEtNotifieeAuxParties() throws Exception {
        ParentAvecEnfant conteste = acteurs.parentAvecEnfant();
        String enfant = "/api/v1/enfants/" + conteste.enfantId();
        String litigeId = ouvrir(conteste);
        String reference = jdbc.queryForObject("SELECT reference FROM identite.litige WHERE id = ?::uuid", String.class, litigeId);

        json(post(LITIGES + "/" + litigeId + "/decision"), "{\"decision\":\"LIEN_RETIRE\"}", kyc).andExpect(status().isBadRequest());
        json(post(LITIGES + "/" + litigeId + "/decision"), "{\"decision\":\"LIEN_RETIRE\",\"fondement\":\"DECISION_DE_JUSTICE\","
                + "\"referenceDuFondement\":\"TGI Bobo-Dioulasso, jugement 2026-412\"}", kyc).andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("CLOS")).andExpect(jsonPath("$.decision").value("LIEN_RETIRE"))
                .andExpect(jsonPath("$.decideLe").exists());
        json(post(LITIGES + "/" + litigeId + "/decision"), "{\"decision\":\"LIEN_MAINTENU\",\"fondement\":\"ACCORD_ECRIT\"}", kyc)
                .andExpect(status().isConflict());

        // Appliquée : le tuteur dont le lien est retiré n'a plus accès à l'enfant.
        avec(get(enfant), conteste.parent().jeton()).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT statut FROM identite.lien_tutelle WHERE enfant_id = ?::uuid", String.class,
                conteste.enfantId())).isEqualTo("SUSPENDU");
        // Notifiée, y compris à celui qui perd l'accès ; journalisée.
        acteurs.attendreSms(conteste.parent().telephone(), reference, "est clos", "L'accès contesté à l'enfant est retiré");
        assertThat(jdbc.queryForList("SELECT action FROM audit.entree WHERE type_cible = 'LITIGE' AND cible_id = ? ORDER BY id",
                String.class, reference)).containsExactly("LITIGE_OUVERT", "LITIGE_DECIDE_LIEN_RETIRE");

        // Lien maintenu : les réglages redeviennent modifiables.
        ParentAvecEnfant maintenu = acteurs.parentAvecEnfant();
        String second = ouvrir(maintenu);
        json(post("/api/v1/enfants/" + maintenu.enfantId() + "/contacts"), CONTACT, maintenu.parent().jeton()).andExpect(status().isLocked());
        json(post(LITIGES + "/" + second + "/decision"), "{\"decision\":\"LIEN_MAINTENU\",\"fondement\":\"ACCORD_ECRIT\"}", kyc)
                .andExpect(status().isOk());
        json(post("/api/v1/enfants/" + maintenu.enfantId() + "/contacts"), CONTACT, maintenu.parent().jeton()).andExpect(status().isCreated());
        acteurs.attendreSms(maintenu.parent().telephone(), "est clos", "de nouveau modifiables");
    }

    @Test
    void seulUnAgentKycInstruitUnLitigeSurUnDossierApprouve() throws Exception {
        ParentAvecEnfant famille = acteurs.parentAvecEnfant();
        String ouverture = "{\"dossierKyc\":\"" + dossier(famille) + "\",\"motif\":\"Contestation\",\"suspendreLaGeolocalisation\":false}";

        json(post(LITIGES), ouverture, acteurs.jetonSav()).andExpect(status().isForbidden());
        json(post(LITIGES), ouverture, acteurs.jetonAdmin()).andExpect(status().isForbidden());
        json(post(LITIGES), ouverture, famille.parent().jeton()).andExpect(status().isForbidden());
        json(post(LITIGES), ouverture, null).andExpect(status().isUnauthorized());
        json(post(LITIGES), ouverture.replace(dossier(famille), "KYC-INCONNU"), kyc).andExpect(status().isNotFound());
        json(post(LITIGES), ouverture.replace("Contestation", " "), kyc).andExpect(status().isBadRequest());

        String id = Acteurs.extraire(json(post(LITIGES), ouverture, kyc).andExpect(status().isCreated()).andReturn().getResponse()
                .getContentAsString(), "\"id\":\"([0-9a-f-]{36})\"");
        json(post(LITIGES), ouverture, kyc).andExpect(status().isConflict());
        avec(get(LITIGES), kyc).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(id));
        avec(get(LITIGES + "/" + id), kyc).andExpect(status().isOk()).andExpect(jsonPath("$.motif").value("Contestation"));
        avec(get(LITIGES + "/" + id), acteurs.agent("[\"SUPPORT\"]").jeton()).andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit.entree WHERE action = 'LITIGE_CONSULTE' AND cible_id = ("
                + "SELECT reference FROM identite.litige WHERE id = ?::uuid)", Integer.class, id)).isEqualTo(1);
    }

    // -------------------------------------------------------------------- aides

    private String dossier(ParentAvecEnfant famille) {
        return jdbc.queryForObject("SELECT reference FROM identite.dossier_kyc WHERE enfant_id = ?::uuid", String.class, famille.enfantId());
    }

    private String ouvrir(ParentAvecEnfant famille) throws Exception {
        return Acteurs.extraire(json(post(LITIGES), "{\"dossierKyc\":\"" + dossier(famille) + "\",\"motif\":\"Contestation de la "
                + "filiation\",\"suspendreLaGeolocalisation\":false}", kyc).andExpect(status().isCreated()).andReturn().getResponse()
                .getContentAsString(), "\"id\":\"([0-9a-f-]{36})\"");
    }

    private ResultActions avec(MockHttpServletRequestBuilder requete, String jeton) throws Exception {
        return mvc.perform(jeton == null ? requete : requete.header("Authorization", "Bearer " + jeton));
    }

    private ResultActions json(MockHttpServletRequestBuilder requete, String corps, String jeton) throws Exception {
        return avec(requete.contentType(MediaType.APPLICATION_JSON).content(corps), jeton);
    }
}
