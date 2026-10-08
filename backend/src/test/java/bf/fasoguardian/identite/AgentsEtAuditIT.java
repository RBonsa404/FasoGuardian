package bf.fasoguardian.identite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import bf.fasoguardian.TestIntegration;
import bf.fasoguardian.audit.JournalAudit;
import bf.fasoguardian.identite.domaine.Totp;
import bf.fasoguardian.notifications.infrastructure.SmsBacASable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Agents internes, second facteur obligatoire, cloisonnement des rôles et journal d'audit (US-ADM-001, US-ADM-002). */
class AgentsEtAuditIT extends TestIntegration {

    private static final AtomicInteger SUITE = new AtomicInteger();
    private static final String MOT_DE_PASSE_AGENT = "phrase-de-passe-agent-2026";
    private static String jetonAdmin;

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
        String identifiant = creerAgent("[\"KYC\"]");

        String corps = connecter(identifiant, MOT_DE_PASSE_AGENT, null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TOTP_A_ACTIVER"))
                .andExpect(jsonPath("$.uriTotp").value(org.hamcrest.Matchers.startsWith("otpauth://totp/FasoGuardian:")))
                .andReturn().getResponse().getContentAsString();
        byte[] secret = Totp.depuisBase32(extraire(corps, "\"secretTotp\":\"([A-Z2-7]+)\""));
        long pas = Instant.now().getEpochSecond() / Totp.PAS_SECONDES;

        connecter(identifiant, MOT_DE_PASSE_AGENT, "000000").andExpect(status().isUnauthorized());
        connecter(identifiant, MOT_DE_PASSE_AGENT, Totp.code(secret, pas - 1))
                .andExpect(status().isOk()).andExpect(jsonPath("$.expireDansSecondes").value(600));

        connecter(identifiant, MOT_DE_PASSE_AGENT, null)
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("CODE_TOTP_REQUIS"));
        connecter(identifiant, MOT_DE_PASSE_AGENT, Totp.code(secret, pas - 1))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("IDENTIFIANTS_INVALIDES"));
        connecter(identifiant, MOT_DE_PASSE_AGENT, Totp.code(secret, pas)).andExpect(status().isOk());
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM audit.entree WHERE action = 'CONNEXION_AGENT' AND role = 'KYC'", Long.class))
                .isGreaterThanOrEqualTo(2);
    }

    @Test
    void unAgentKycNePeutPasAdministrerLesComptesEtSonRefusEstJournalise() throws Exception {
        String identifiant = creerAgent("[\"KYC\"]");
        String jetonKyc = activerEtConnecter(identifiant);

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
        mvc.perform(get("/api/v1/admin/agents").header("Authorization", "Bearer " + jetonParent()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/agents")).andExpect(status().isUnauthorized());

        mvc.perform(get("/api/v1/admin/agents").header("Authorization", "Bearer " + jetonAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.identifiant == '" + ADMIN_IDENTIFIANT + "')].roles[0]").value("ADMIN"));
    }

    @Test
    void laCreationDUnAgentEstValideeEtJournalisee() throws Exception {
        String identifiant = creerAgent("[\"SUPPORT\",\"SAV\"]");

        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM audit.entree e JOIN identite.utilisateur u ON u.id::text = e.cible_id
                WHERE e.action = 'AGENT_CREE' AND e.role = 'ADMIN' AND u.identifiant = ?
                """, Long.class, identifiant)).isEqualTo(1);
        creer(identifiant, MOT_DE_PASSE_AGENT, "[\"KYC\"]").andExpect(status().isConflict());
        creer("agent.court", "trop-court", "[\"KYC\"]")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MOT_DE_PASSE_REFUSE"));
        creer("agent.sans.role", MOT_DE_PASSE_AGENT, "[]").andExpect(status().isBadRequest());
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

    private String creerAgent(String roles) throws Exception {
        String identifiant = "agent.essai." + SUITE.incrementAndGet();
        creer(identifiant, MOT_DE_PASSE_AGENT, roles).andExpect(status().isCreated())
                .andExpect(jsonPath("$.secondFacteurActif").value(false));
        return identifiant;
    }

    private ResultActions creer(String identifiant, String motDePasse, String roles) throws Exception {
        return mvc.perform(post("/api/v1/admin/agents").header("Authorization", "Bearer " + jetonAdmin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"identifiant\":\"" + identifiant + "\",\"motDePasseProvisoire\":\"" + motDePasse
                        + "\",\"roles\":" + roles + "}"));
    }

    private ResultActions connecter(String identifiant, String motDePasse, String code) throws Exception {
        return mvc.perform(post("/api/v1/auth/agents/connexion").contentType(MediaType.APPLICATION_JSON)
                .content("{\"identifiant\":\"" + identifiant + "\",\"motDePasse\":\"" + motDePasse + "\""
                        + (code == null ? "" : ",\"codeTotp\":\"" + code + "\"") + "}"));
    }

    private String activerEtConnecter(String identifiant) throws Exception {
        return activerEtConnecter(identifiant, MOT_DE_PASSE_AGENT);
    }

    private String activerEtConnecter(String identifiant, String motDePasse) throws Exception {
        String corps = connecter(identifiant, motDePasse, null).andExpect(status().isForbidden())
                .andReturn().getResponse().getContentAsString();
        byte[] secret = Totp.depuisBase32(extraire(corps, "\"secretTotp\":\"([A-Z2-7]+)\""));
        String session = connecter(identifiant, motDePasse, Totp.code(secret, Instant.now().getEpochSecond() / 30))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return extraire(session, "\"jetonAcces\":\"([^\"]+)\"");
    }

    private synchronized String jetonAdmin() throws Exception {
        if (jetonAdmin == null) {
            jetonAdmin = activerEtConnecter(ADMIN_IDENTIFIANT, ADMIN_MOT_DE_PASSE);
        }
        return jetonAdmin;
    }

    private String jetonParent() throws Exception {
        String telephone = "76" + String.format("%06d", SUITE.incrementAndGet());
        mvc.perform(post("/api/v1/auth/inscription/numero").contentType(MediaType.APPLICATION_JSON)
                .content("{\"telephone\":\"" + telephone + "\"}")).andExpect(status().isAccepted());
        String code = extraire(sms.dernierPour("+226" + telephone).orElseThrow().texte(), "code est (\\d{6})");
        String preuve = extraire(mvc.perform(post("/api/v1/auth/inscription/code").contentType(MediaType.APPLICATION_JSON)
                .content("{\"telephone\":\"" + telephone + "\",\"code\":\"" + code + "\"}"))
                .andReturn().getResponse().getContentAsString(), "\"preuve\":\"([^\"]+)\"");
        return extraire(mvc.perform(post("/api/v1/auth/inscription/terminer").contentType(MediaType.APPLICATION_JSON)
                .content("{\"preuve\":\"" + preuve + "\",\"motDePasse\":\"soleil-de-ouaga-2026\","
                        + "\"consentements\":[\"CONDITIONS_GENERALES\",\"DONNEES_ENFANT\"]}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(),
                "\"jetonAcces\":\"([^\"]+)\"");
    }

    private static String extraire(String texte, String motif) {
        Matcher correspondance = Pattern.compile(motif).matcher(texte);
        assertThat(correspondance.find()).as("motif %s dans %s", motif, texte).isTrue();
        return correspondance.group(1);
    }
}
