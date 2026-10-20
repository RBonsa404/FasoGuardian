package bf.fasoguardian.identite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import bf.fasoguardian.Acteurs;
import bf.fasoguardian.Acteurs.Parent;
import bf.fasoguardian.TestIntegration;
import bf.fasoguardian.identite.domaine.Totp;
import bf.fasoguardian.notifications.infrastructure.SmsBacASable;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Sessions de la console et des parents sur un même hôte (poste de développement, démonstration) : chacune a son
 * cookie de rafraîchissement, et l'espace qui rafraîchit dit lequel il veut. Une connexion d'agent ne remplace
 * jamais la session d'un parent.
 */
class SessionsParEspaceIT extends TestIntegration {

    @Autowired
    MockMvc mvc;

    @Autowired
    SmsBacASable sms;

    @Test
    void laConnexionDUnAgentLaisseIntacteLaSessionDuParent() throws Exception {
        Acteurs acteurs = new Acteurs(mvc, sms);
        Parent parent = acteurs.parent();
        Cookie cookieParent = mvc.perform(post("/api/v1/auth/connexion").contentType(MediaType.APPLICATION_JSON)
                .content("{\"telephone\":\"" + parent.telephone() + "\",\"motDePasse\":\"" + Acteurs.MOT_DE_PASSE_PARENT + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getCookie("fg_rafraichissement");

        String identifiant = acteurs.creerAgent("[\"KYC\"]");
        // Première connexion : le second facteur s'active avec le premier code valide, et la session s'ouvre.
        String corps = acteurs.connecterAgent(identifiant, Acteurs.MOT_DE_PASSE_AGENT, null).andExpect(status().isForbidden())
                .andReturn().getResponse().getContentAsString();
        byte[] secret = Totp.depuisBase32(Acteurs.extraire(corps, "\"secretTotp\":\"([A-Z2-7]+)\""));
        MvcResult agent = acteurs.connecterAgent(identifiant, Acteurs.MOT_DE_PASSE_AGENT,
                Totp.code(secret, Instant.now().getEpochSecond() / Totp.PAS_SECONDES)).andExpect(status().isOk()).andReturn();
        assertThat(agent.getResponse().getCookie("fg_rafraichissement")).isNull();
        Cookie cookieAgent = agent.getResponse().getCookie("fg_rafraichissement_agent");
        assertThat(cookieAgent).isNotNull();

        // L'espace parents rafraîchit la session du parent, la console celle de l'agent, même avec les deux cookies.
        String jetonParent = extraireJeton(mvc.perform(post("/api/v1/auth/rafraichir").cookie(cookieParent, cookieAgent)
                .header("X-FG-Requete", "parents")).andExpect(status().isOk()).andReturn());
        mvc.perform(get("/api/v1/moi").header("Authorization", "Bearer " + jetonParent))
                .andExpect(jsonPath("$.roles[0]").value("PARENT"));
        String jetonAgent = extraireJeton(mvc.perform(post("/api/v1/auth/rafraichir").cookie(cookieParent, cookieAgent)
                .header("X-FG-Requete", "console")).andExpect(status().isOk()).andReturn());
        mvc.perform(get("/api/v1/moi").header("Authorization", "Bearer " + jetonAgent))
                .andExpect(jsonPath("$.identifiant").value(identifiant));
    }

    private static String extraireJeton(MvcResult resultat) throws Exception {
        return Acteurs.extraire(resultat.getResponse().getContentAsString(), "\"jetonAcces\":\"([^\"]+)\"");
    }
}
