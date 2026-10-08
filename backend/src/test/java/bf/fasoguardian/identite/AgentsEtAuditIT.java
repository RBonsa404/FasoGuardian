package bf.fasoguardian.identite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import bf.fasoguardian.Acteurs;
import bf.fasoguardian.TestIntegration;
import bf.fasoguardian.audit.JournalAudit;
import bf.fasoguardian.identite.domaine.Totp;
import bf.fasoguardian.notifications.infrastructure.SmsBacASable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** Agents internes, second facteur obligatoire, cloisonnement des rôles et journal d'audit (US-ADM-001, US-ADM-002). */
class AgentsEtAuditIT extends TestIntegration {

    private static final String MOT_DE_PASSE_AGENT = Acteurs.MOT_DE_PASSE_AGENT;

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    JournalAudit journal;

    @Autowired
    SmsBacASable sms;

    @Test
    void laPremiereConnexionImposeLActivationDuSecondFacteurPuisChaqueConnexionExigeUnCodeNeuf() throws Exception {
        String identifiant = acteurs().creerAgent("[\"KYC\"]");

        String corps = acteurs().connecterAgent(identifiant, MOT_DE_PASSE_AGENT, null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TOTP_A_ACTIVER"))
                .andExpect(jsonPath("$.uriTotp").value(org.hamcrest.Matchers.startsWith("otpauth://totp/FasoGuardian:")))
                .andReturn().getResponse().getContentAsString();
        byte[] secret = Totp.depuisBase32(Acteurs.extraire(corps, "\"secretTotp\":\"([A-Z2-7]+)\""));
        long pas = Instant.now().getEpochSecond() / Totp.PAS_SECONDES;

        acteurs().connecterAgent(identifiant, MOT_DE_PASSE_AGENT, "000000").andExpect(status().isUnauthorized());
        acteurs().connecterAgent(identifiant, MOT_DE_PASSE_AGENT, Totp.code(secret, pas - 1))
                .andExpect(status().isOk()).andExpect(jsonPath("$.expireDansSecondes").value(600));

        acteurs().connecterAgent(identifiant, MOT_DE_PASSE_AGENT, null)
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("CODE_TOTP_REQUIS"));
        acteurs().connecterAgent(identifiant, MOT_DE_PASSE_AGENT, Totp.code(secret, pas - 1))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("IDENTIFIANTS_INVALIDES"));
        acteurs().connecterAgent(identifiant, MOT_DE_PASSE_AGENT, Totp.code(secret, pas)).andExpect(status().isOk());
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM audit.entree WHERE action = 'CONNEXION_AGENT' AND role = 'KYC'", Long.class))
                .isGreaterThanOrEqualTo(2);
    }

    @Test
    void unAgentKycNePeutPasAdministrerLesComptesEtSonRefusEstJournalise() throws Exception {
        String identifiant = acteurs().creerAgent("[\"KYC\"]");
        String jetonKyc = acteurs().activerEtConnecter(identifiant, MOT_DE_PASSE_AGENT);

        mvc.perform(get("/api/v1/admin/agents").header("Authorization", "Bearer " + jetonKyc))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCES_REFUSE"));

        assertThat(jdbc.queryForList("""
                SELECT role, type_cible, cible_id, resultat FROM audit.entree
                WHERE action = 'ACCES_REFUSE' AND role = 'KYC' ORDER BY id DESC LIMIT 1
                """)).singleElement().satisfies(ligne -> {
            assertThat(ligne.get("cible_id")).isEqualTo("GET /api/v1/admin/agents");
            assertThat(ligne.get("resultat")).isEqualTo("REFUS");
        });
    }

    @Test
    void unParentNAccedePasALAdministrationEtLAdministrateurVoitLesAgents() throws Exception {
        mvc.perform(get("/api/v1/admin/agents").header("Authorization", "Bearer " + acteurs().parent().jeton()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/agents")).andExpect(status().isUnauthorized());

        mvc.perform(get("/api/v1/admin/agents").header("Authorization", "Bearer " + acteurs().jetonAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.identifiant == '" + ADMIN_IDENTIFIANT + "')].roles[0]").value("ADMIN"));
    }

    @Test
    void laCreationDUnAgentEstValideeEtJournalisee() throws Exception {
        String identifiant = acteurs().creerAgent("[\"SUPPORT\",\"SAV\"]");

        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM audit.entree e JOIN identite.utilisateur u ON u.id::text = e.cible_id
                WHERE e.action = 'AGENT_CREE' AND e.role = 'ADMIN' AND u.identifiant = ?
                """, Long.class, identifiant)).isEqualTo(1);
        acteurs().creerAgent(identifiant, MOT_DE_PASSE_AGENT, "[\"KYC\"]").andExpect(status().isConflict());
        acteurs().creerAgent("agent.court", "trop-court", "[\"KYC\"]")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MOT_DE_PASSE_REFUSE"));
        acteurs().creerAgent("agent.sans.role", MOT_DE_PASSE_AGENT, "[]").andExpect(status().isBadRequest());
    }

    @Test
    void leJournalEstEnAjoutSeulEtSonAlterationEstDetectee() {
        journal.consigner(null, "SYSTEME", "ESSAI", "TEST", "a", JournalAudit.Resultat.SUCCES);
        journal.consigner(null, "SYSTEME", "ESSAI", "TEST", "b", JournalAudit.Resultat.SUCCES);
        assertThat(journal.premiereEntreeAlteree()).isEmpty();

        assertThatThrownBy(() -> jdbc.update("UPDATE audit.entree SET role = 'ADMIN' WHERE action = 'ESSAI'"))
                .hasMessageContaining("ajout seul");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM audit.entree")).hasMessageContaining("ajout seul");
        assertThatThrownBy(() -> jdbc.execute("TRUNCATE audit.entree")).hasMessageContaining("ajout seul");

        // Altération par un accès privilégié à la base (protection désactivée) : la chaîne la révèle.
        Long cible = jdbc.queryForObject("SELECT max(id) - 1 FROM audit.entree", Long.class);
        String origine = jdbc.queryForObject("SELECT cible_id FROM audit.entree WHERE id = ?", String.class, cible);
        jdbc.execute("ALTER TABLE audit.entree DISABLE TRIGGER entree_ajout_seul");
        try {
            jdbc.update("UPDATE audit.entree SET cible_id = 'falsifie' WHERE id = ?", cible);
            assertThat(journal.premiereEntreeAlteree()).hasValue(cible);
            jdbc.update("UPDATE audit.entree SET cible_id = ? WHERE id = ?", origine, cible);
        } finally {
            jdbc.execute("ALTER TABLE audit.entree ENABLE TRIGGER entree_ajout_seul");
        }
        assertThat(journal.premiereEntreeAlteree()).isEmpty();
    }

    private Acteurs acteurs() {
        return new Acteurs(mvc, sms);
    }
}
