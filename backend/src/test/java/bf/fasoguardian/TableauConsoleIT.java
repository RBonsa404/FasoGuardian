package bf.fasoguardian;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bf.fasoguardian.notifications.infrastructure.SmsBacASable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Tableau de bord de la console (écran 57) : chacun ne voit que les indicateurs de ses propres files. */
class TableauConsoleIT extends TestIntegration {

    private static final String TABLEAU = "/api/v1/console/tableau";

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
    void chaqueAgentNeVoitQueLesIndicateursDeSesRoles() throws Exception {
        avec(acteurs.agent("[\"KYC\"]").jeton()).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].role").value("KYC"))
                .andExpect(jsonPath("$[0].indicateurs[*].code").value(org.hamcrest.Matchers.contains(
                        "kyc.enFile", "kyc.prioritaires", "kyc.delaiMedian", "kyc.mesDecisions")))
                .andExpect(jsonPath("$[0].indicateurs[1].repere").value(36))
                .andExpect(jsonPath("$[0].indicateurs[2].repere").value(48));

        // Plusieurs rôles : un groupe par rôle, dans l'ordre de la barre latérale.
        avec(acteurs.agent("[\"SAV\", \"SUPPORT\"]").jeton()).andExpect(status().isOk())
                .andExpect(jsonPath("$[*].role").value(org.hamcrest.Matchers.contains("SUPPORT", "SAV")))
                .andExpect(jsonPath("$[1].indicateurs[*].code").value(org.hamcrest.Matchers.contains(
                        "sav.muets", "sav.sansAgent", "sav.auSav", "sav.enStock")));

        avec(acteurs.agent("[\"ADMIN\"]").jeton()).andExpect(status().isOk()).andExpect(jsonPath("$[0].role").value("ADMIN"))
                .andExpect(jsonPath("$[0].indicateurs[*].code").value(org.hamcrest.Matchers.contains(
                        "admin.refus24h", "admin.demandesDroits", "admin.echeancesProches")));
    }

    @Test
    void lesForcesDeSecuriteNeVoientQueLeursPropresAccuses() throws Exception {
        String reponse = avec(acteurs.agent("[\"FDS\"]").jeton()).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].indicateurs[*].code").value(org.hamcrest.Matchers.contains("fds.mesAccusesJour", "fds.mesAccuses30j")))
                .andExpect(jsonPath("$[0].indicateurs[0].valeur").value(0))
                .andReturn().getResponse().getContentAsString();
        assertThat(reponse).doesNotContain("signalement");
    }

    @Test
    void lesDecomptesSuiventLaFile() throws Exception {
        String sav = acteurs.agent("[\"SAV\"]").jeton();
        long enStock = jdbc.queryForObject("SELECT count(*) FROM dispositifs.bracelet WHERE statut = 'EN_STOCK' AND certificat_revoque_le IS NULL",
                Long.class);
        avec(sav).andExpect(jsonPath("$[0].indicateurs[3].valeur").value(enStock));
        acteurs.braceletAuParc();
        avec(sav).andExpect(jsonPath("$[0].indicateurs[3].valeur").value(enStock + 1));
    }

    @Test
    void reserveAuxAgents() throws Exception {
        mvc.perform(get(TABLEAU)).andExpect(status().isUnauthorized());
        avec(acteurs.parent().jeton()).andExpect(status().isForbidden());
    }

    private ResultActions avec(String jeton) throws Exception {
        return mvc.perform(get(TABLEAU).header("Authorization", "Bearer " + jeton));
    }
}
