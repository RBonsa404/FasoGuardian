package bf.fasoguardian.identite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import bf.fasoguardian.Acteurs;
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

/** Périmètres des agents : changement de rôles, suspension, rétablissement (US-ADM-001). */
class GestionAgentsIT extends TestIntegration {

    private static final String AGENTS = "/api/v1/admin/agents";

    @Autowired
    MockMvc mvc;

    @Autowired
    SmsBacASable sms;

    @Autowired
    JdbcTemplate jdbc;

    Acteurs acteurs;

    @BeforeEach
    void preparer() {
        acteurs = new Acteurs(mvc, sms);
    }

    @Test
    void unChangementDeRolesFermeLesSessionsEtVautDesLaConnexionSuivante() throws Exception {
        String admin = acteurs.jetonAdmin();
        String identifiant = "agent.perimetre." + UUID.randomUUID().toString().substring(0, 8);
        String agentId = Acteurs.extraire(acteurs.creerAgent(identifiant, Acteurs.MOT_DE_PASSE_AGENT, "[\"KYC\"]")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "\"id\":\"([0-9a-f-]{36})\"");
        String jetonKyc = acteurs.activerEtConnecter(identifiant, Acteurs.MOT_DE_PASSE_AGENT);
        avec(get("/api/v1/console/kyc/dossiers"), jetonKyc).andExpect(status().isOk());
        avec(get("/api/v1/console/parc"), jetonKyc).andExpect(status().isForbidden());

        json(put(AGENTS + "/" + agentId + "/roles"), "{\"roles\":[]}", admin).andExpect(status().isBadRequest());
        json(put(AGENTS + "/" + agentId + "/roles"), "{\"roles\":[\"SAV\"]}", admin).andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0]").value("SAV")).andExpect(jsonPath("$.roles.length()").value(1));

        // Ses sessions sont fermées : il ne peut pas prolonger l'ancien périmètre.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM identite.jeton_rafraichissement WHERE utilisateur_id = ?::uuid"
                + " AND revoque_le IS NULL AND utilise_le IS NULL", Integer.class, agentId)).isZero();
        assertThat(jdbc.queryForList("SELECT action FROM audit.entree WHERE type_cible = 'AGENT' AND action LIKE 'AGENT_%' AND cible_id = ? ORDER BY id",
                String.class, agentId)).containsExactly("AGENT_CREE", "AGENT_ROLES_MODIFIES");
        avec(get(AGENTS), admin).andExpect(jsonPath("$[?(@.id == '" + agentId + "')].roles[0]").value("SAV"));
    }

    @Test
    void unAgentSuspenduNePeutPlusSeConnecterJusquASonRetablissement() throws Exception {
        String admin = acteurs.jetonAdmin();
        String identifiant = "agent.suspendu." + UUID.randomUUID().toString().substring(0, 8);
        String agentId = Acteurs.extraire(acteurs.creerAgent(identifiant, Acteurs.MOT_DE_PASSE_AGENT, "[\"SUPPORT\"]")
                .andReturn().getResponse().getContentAsString(), "\"id\":\"([0-9a-f-]{36})\"");

        avec(post(AGENTS + "/" + agentId + "/suspension"), admin).andExpect(status().isOk())
                .andExpect(jsonPath("$.suspendu").value(true));
        acteurs.connecterAgent(identifiant, Acteurs.MOT_DE_PASSE_AGENT, null).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("IDENTIFIANTS_INVALIDES"));

        avec(post(AGENTS + "/" + agentId + "/retablissement"), admin).andExpect(status().isOk())
                .andExpect(jsonPath("$.suspendu").value(false));
        // De nouveau admis : la première connexion demande d'activer le second facteur.
        acteurs.connecterAgent(identifiant, Acteurs.MOT_DE_PASSE_AGENT, null).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TOTP_A_ACTIVER"));
        assertThat(jdbc.queryForList("SELECT action FROM audit.entree WHERE type_cible = 'AGENT' AND action LIKE 'AGENT_%' AND cible_id = ? ORDER BY id",
                String.class, agentId)).containsExactly("AGENT_CREE", "AGENT_SUSPENDU", "AGENT_RETABLI");
    }

    @Test
    void nulNeModifieSonPropreCompteEtSeulUnAdministrateurGereLesAgents() throws Exception {
        String admin = acteurs.jetonAdmin();
        String moi = Acteurs.extraire(new String(Base64.getUrlDecoder().decode(admin.split("\\.")[1]), StandardCharsets.UTF_8),
                "\"sub\":\"([0-9a-f-]{36})\"");

        json(put(AGENTS + "/" + moi + "/roles"), "{\"roles\":[\"KYC\"]}", admin).andExpect(status().isConflict());
        avec(post(AGENTS + "/" + moi + "/suspension"), admin).andExpect(status().isConflict());
        avec(post(AGENTS + "/" + UUID.randomUUID() + "/suspension"), admin).andExpect(status().isNotFound());

        String kyc = acteurs.agent("[\"KYC\"]").jeton();
        json(put(AGENTS + "/" + moi + "/roles"), "{\"roles\":[\"KYC\"]}", kyc).andExpect(status().isForbidden());
        avec(post(AGENTS + "/" + moi + "/suspension"), kyc).andExpect(status().isForbidden());
        avec(post(AGENTS + "/" + moi + "/retablissement"), null).andExpect(status().isUnauthorized());
        // Un parent ne peut pas être visé comme s'il était un agent.
        String parent = Acteurs.extraire(new String(Base64.getUrlDecoder().decode(acteurs.parent().jeton().split("\\.")[1]),
                StandardCharsets.UTF_8), "\"sub\":\"([0-9a-f-]{36})\"");
        avec(post(AGENTS + "/" + parent + "/suspension"), admin).andExpect(status().isNotFound());
    }

    private ResultActions avec(MockHttpServletRequestBuilder requete, String jeton) throws Exception {
        return mvc.perform(jeton == null ? requete : requete.header("Authorization", "Bearer " + jeton));
    }

    private ResultActions json(MockHttpServletRequestBuilder requete, String corps, String jeton) throws Exception {
        return avec(requete.contentType(MediaType.APPLICATION_JSON).content(corps), jeton);
    }
}
