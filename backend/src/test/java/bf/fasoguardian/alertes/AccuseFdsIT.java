package bf.fasoguardian.alertes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bf.fasoguardian.Acteurs;
import bf.fasoguardian.Acteurs.Agent;
import bf.fasoguardian.Acteurs.Parent;
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

/** Accusé de réception d'un signalement par les forces de sécurité (US-FDS-001). */
class AccuseFdsIT extends TestIntegration {

    private static final String FDS = "/api/v1/console/fds/signalements/";

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
    void lAgentAccuseReceptionLAccuseEstHorodateJournaliseEtNotifieAuParent() throws Exception {
        ParentAvecEnfant famille = acteurs.parentAvecEnfant();
        Parent parent = famille.parent();
        String alerte = signaler(famille);
        String reference = escalader(parent, alerte);
        Agent agent = acteurs.agent("[\"FDS\"]");

        // Le constat ne porte aucune donnée de l'enfant ; remis par le parent, le dossier n'est pas servi ici.
        String constat = avec(get(FDS + reference.toLowerCase()), agent.jeton()).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.reference").value(reference))
                .andExpect(jsonPath("$.nature").value("Disparition signalée par un tuteur"))
                .andExpect(jsonPath("$.accuseLe").isEmpty())
                .andExpect(jsonPath("$.dossierConsultable").value(false))
                .andReturn().getResponse().getContentAsString();
        assertThat(constat).doesNotContain("Yacouba").doesNotContain(famille.enfantId()).doesNotContain(alerte);
        avec(get(FDS + reference + "/dossier"), agent.jeton()).andExpect(status().isNotFound());
        avec(get("/api/v1/alertes/" + alerte + "/signalement"), parent.jeton()).andExpect(status().isOk())
                .andExpect(jsonPath("$.accuseLe").isEmpty());

        avec(post(FDS + reference + "/accuse"), agent.jeton()).andExpect(status().isOk())
                .andExpect(jsonPath("$.accuseLe").isNotEmpty());

        // Le parent voit l'accusé et en est notifié, sans autre détail que la référence.
        avec(get("/api/v1/alertes/" + alerte + "/signalement"), parent.jeton()).andExpect(status().isOk())
                .andExpect(jsonPath("$.accuseLe").isNotEmpty());
        assertThat(jdbc.queryForList("SELECT texte FROM notifications.notification WHERE modele = 'SIGNALEMENT_ACCUSE' AND texte LIKE ?",
                String.class, "%" + reference + "%")).singleElement().asString().contains("ont accusé réception du signalement " + reference);

        // Le premier accusé fait foi : le second ne change ni l'horodatage ni le nombre de notifications.
        String premier = jdbc.queryForObject("SELECT accuse_le::text FROM alertes.signalement_fds WHERE reference = ?", String.class, reference);
        avec(post(FDS + reference + "/accuse"), acteurs.agent("[\"FDS\"]").jeton()).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT accuse_le::text FROM alertes.signalement_fds WHERE reference = ?", String.class, reference))
                .isEqualTo(premier);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications.notification WHERE modele = 'SIGNALEMENT_ACCUSE' AND texte LIKE ?",
                Integer.class, "%" + reference + "%")).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT a.identifiant FROM alertes.signalement_fds s JOIN identite.utilisateur a ON a.id = s.accuse_par
                WHERE s.reference = ?""", String.class, reference)).isEqualTo(agent.identifiant());

        assertThat(jdbc.queryForList("SELECT action FROM audit.entree WHERE cible_id = ? AND role = 'FDS' ORDER BY id", String.class,
                reference)).containsExactly("SIGNALEMENT_CONSULTE_PAR_LES_FDS", "DOSSIER_REFUSE_AUX_FDS", "SIGNALEMENT_ACCUSE");
    }

    @Test
    void transmisParLaPasserelleLeDossierEstTelechargeableEtChaqueTelechargementEstTrace() throws Exception {
        ParentAvecEnfant famille = acteurs.parentAvecEnfant();
        String reference = escalader(famille.parent(), signaler(famille));
        jdbc.update("UPDATE alertes.signalement_fds SET canal = 'PASSERELLE' WHERE reference = ?", reference);
        String jeton = acteurs.agent("[\"FDS\"]").jeton();

        avec(get(FDS + reference), jeton).andExpect(status().isOk()).andExpect(jsonPath("$.dossierConsultable").value(true));
        byte[] pdf = avec(get(FDS + reference + "/dossier"), jeton).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store")).andReturn().getResponse().getContentAsByteArray();

        assertThat(new String(pdf, 0, 5, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit.entree WHERE action = 'DOSSIER_TELECHARGE_PAR_LES_FDS' AND cible_id = ?",
                Integer.class, reference)).isEqualTo(1);
    }

    @Test
    void seulLeRoleFdsAccedeEtSeulementParUneReferenceExacte() throws Exception {
        ParentAvecEnfant famille = acteurs.parentAvecEnfant();
        String reference = escalader(famille.parent(), signaler(famille));
        String fds = acteurs.agent("[\"FDS\"]").jeton();

        avec(get(FDS + reference), null).andExpect(status().isUnauthorized());
        avec(get(FDS + reference), famille.parent().jeton()).andExpect(status().isForbidden());
        avec(get(FDS + reference), acteurs.jetonSav()).andExpect(status().isForbidden());
        avec(post(FDS + reference + "/accuse"), acteurs.agent("[\"SUPPORT\"]").jeton()).andExpect(status().isForbidden());
        avec(get(FDS + reference + "/dossier"), acteurs.agent("[\"KYC\"]").jeton()).andExpect(status().isForbidden());
        // Pas de liste, pas de recherche approchée.
        avec(get("/api/v1/console/fds/signalements"), fds).andExpect(status().is4xxClientError());
        avec(get(FDS + "FG-SIG-999999"), fds).andExpect(status().isNotFound());
        avec(post(FDS + "FG-SIG-999999/accuse"), fds).andExpect(status().isNotFound());

        assertThat(jdbc.queryForObject("SELECT accuse_le IS NULL FROM alertes.signalement_fds WHERE reference = ?", Boolean.class, reference))
                .isTrue();

        // Passé trente jours, la référence n'ouvre plus rien dans cet espace.
        avec(get(FDS + reference), fds).andExpect(status().isOk());
        jdbc.update("UPDATE alertes.signalement_fds SET cree_le = now() - INTERVAL '31 days' WHERE reference = ?", reference);
        avec(get(FDS + reference), fds).andExpect(status().isNotFound());
        avec(post(FDS + reference + "/accuse"), fds).andExpect(status().isNotFound());
    }

    // -------------------------------------------------------------------- aides

    private String signaler(ParentAvecEnfant famille) throws Exception {
        return Acteurs.extraire(avec(post("/api/v1/enfants/" + famille.enfantId() + "/signalement"), famille.parent().jeton())
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "\"id\":\"([0-9a-f-]{36})\"");
    }

    /** Escalade confirmée par second facteur ; rend la référence du dossier. */
    private String escalader(Parent parent, String alerte) throws Exception {
        jdbc.update("DELETE FROM identite.code_usage_unique WHERE finalite = '2F_ESCALADER_FORCES_SECURITE'");
        avec(post("/api/v1/moi/second-facteur").contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"ESCALADER_FORCES_SECURITE\"}"),
                parent.jeton()).andExpect(status().isAccepted());
        String corps = avec(post("/api/v1/alertes/" + alerte + "/escalade").contentType(MediaType.APPLICATION_JSON)
                .content("{\"codeSecondFacteur\":\"" + acteurs.dernierCodeDeConfirmation(parent.telephone()) + "\"}"), parent.jeton())
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return Acteurs.extraire(corps, "\"reference\":\"([A-Z0-9-]+)\"");
    }

    private ResultActions avec(MockHttpServletRequestBuilder requete, String jeton) throws Exception {
        return mvc.perform(jeton == null ? requete : requete.header("Authorization", "Bearer " + jeton));
    }
}
