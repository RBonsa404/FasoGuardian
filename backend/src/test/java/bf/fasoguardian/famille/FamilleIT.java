package bf.fasoguardian.famille;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import bf.fasoguardian.Acteurs;
import bf.fasoguardian.Acteurs.Parent;
import bf.fasoguardian.Acteurs.ParentAvecEnfant;
import bf.fasoguardian.TestIntegration;
import bf.fasoguardian.notifications.infrastructure.SmsBacASable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Fiche enfant, fiche médicale et contacts d'urgence (US-PAR-003, US-PAR-004, US-PAR-011). */
class FamilleIT extends TestIntegration {

    @Autowired
    MockMvc mvc;

    @Autowired
    SmsBacASable sms;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void laFicheEnfantNaitDeLApprobationDuKycEtSesCorrectionsSontHistorisees() throws Exception {
        Acteurs acteurs = new Acteurs(mvc, sms);
        Parent sansEnfant = acteurs.parent();
        appel(get("/api/v1/enfants"), sansEnfant.jeton()).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));

        ParentAvecEnfant famille = acteurs.parentAvecEnfant();
        String jeton = famille.parent().jeton();
        String base = "/api/v1/enfants/" + famille.enfantId();

        appel(get(base), jeton).andExpect(status().isOk())
                .andExpect(jsonPath("$.prenom").value("Awa")).andExpect(jsonPath("$.nom").value("Ouédraogo"))
                .andExpect(jsonPath("$.dateNaissance").value("2018-03-14"))
                .andExpect(header().string("Cache-Control", "no-store"));

        appel(patch(base).contentType(MediaType.APPLICATION_JSON).content("{\"prenom\":\"Hawa\"}"), jeton)
                .andExpect(status().isOk()).andExpect(jsonPath("$.prenom").value("Hawa"))
                .andExpect(jsonPath("$.nom").value("Ouédraogo"));
        appel(get(base + "/historique"), jeton).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].champ").value("prenom")).andExpect(jsonPath("$[0].modifieLe").isNotEmpty());

        // Profil descriptif : chiffré en base, historisé sans valeur, jamais projeté sur la page publique.
        appel(patch(base).contentType(MediaType.APPLICATION_JSON).content("""
                {"profil":{"ecole":"Les Manguiers MARQUEUR-PROFIL","quartier":"Ouaga 2000","tailleCm":128,
                           "signesDistinctifs":"Petite cicatrice au menton"}}
                """), jeton).andExpect(status().isOk())
                .andExpect(jsonPath("$.profil.ecole").value("Les Manguiers MARQUEUR-PROFIL"))
                .andExpect(jsonPath("$.profil.tailleCm").value(128));
        assertThat(new String(jdbc.queryForObject("SELECT profil_chiffre FROM famille.enfant WHERE id = ?::uuid",
                byte[].class, famille.enfantId()), StandardCharsets.ISO_8859_1)).doesNotContain("MARQUEUR", "cicatrice");
        appel(get(base + "/historique"), jeton).andExpect(jsonPath("$[0].champ").value("profil"));
        appel(patch(base).contentType(MediaType.APPLICATION_JSON).content("{\"profil\":{\"tailleCm\":400}}"), jeton)
                .andExpect(status().isBadRequest());
    }

    @Test
    void unAutreParentOuUnAgentNAccedePasALaFicheNiALaSanteEtLeRefusEstJournalise() throws Exception {
        Acteurs acteurs = new Acteurs(mvc, sms);
        ParentAvecEnfant famille = acteurs.parentAvecEnfant();
        Parent etranger = acteurs.parent();
        String base = "/api/v1/enfants/" + famille.enfantId();

        appel(get(base), etranger.jeton()).andExpect(status().isNotFound());
        appel(get(base + "/sante"), etranger.jeton()).andExpect(status().isNotFound());
        appel(put(base + "/sante").contentType(MediaType.APPLICATION_JSON).content("{\"elements\":[]}"), etranger.jeton())
                .andExpect(status().isNotFound());
        appel(get(base + "/contacts"), etranger.jeton()).andExpect(status().isNotFound());
        appel(get(base + "/sante"), acteurs.agent("[\"KYC\"]").jeton()).andExpect(status().isForbidden());
        mvc.perform(get(base)).andExpect(status().isUnauthorized());

        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM audit.entree
                WHERE action = 'ACCES_REFUSE' AND type_cible = 'ENFANT' AND cible_id = ? AND resultat = 'REFUS'
                """, Long.class, famille.enfantId())).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM famille.fiche_sante WHERE enfant_id = ?::uuid", Long.class,
                famille.enfantId())).isZero();
    }

    @Test
    void laFicheMedicaleEstChiffreeSesModificationsSontJournaliseesEtSonJournalEstNonModifiable() throws Exception {
        ParentAvecEnfant famille = new Acteurs(mvc, sms).parentAvecEnfant();
        String jeton = famille.parent().jeton();
        String base = "/api/v1/enfants/" + famille.enfantId() + "/sante";

        appel(get(base), jeton).andExpect(status().isOk()).andExpect(jsonPath("$.elements.length()").value(0));
        appel(put(base).contentType(MediaType.APPLICATION_JSON).content("""
                {"groupeSanguin":"O+","elements":[
                  {"type":"ALLERGIE","libelle":"Arachide MARQUEUR-SANTE","critique":true},
                  {"type":"TRAITEMENT","libelle":"Ventoline au besoin","critique":false}]}
                """), jeton).andExpect(status().isOk());

        appel(get(base), jeton).andExpect(jsonPath("$.groupeSanguin").value("O+"))
                .andExpect(jsonPath("$.elements[0].libelle").value("Arachide MARQUEUR-SANTE"))
                .andExpect(jsonPath("$.elements[0].critique").value(true));
        assertThat(new String(jdbc.queryForObject("SELECT contenu_chiffre FROM famille.fiche_sante WHERE enfant_id = ?::uuid",
                byte[].class, famille.enfantId()), StandardCharsets.ISO_8859_1)).doesNotContain("MARQUEUR", "O+", "Ventoline");

        appel(get(base + "/revisions"), jeton).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].nombreElements").value(2)).andExpect(jsonPath("$[0].nombreCritiques").value(1));
        assertThat(jdbc.queryForList("SELECT action FROM audit.entree WHERE type_cible = 'ENFANT' AND cible_id = ?",
                String.class, famille.enfantId())).contains("CONSULTATION_FICHE_SANTE", "MODIFICATION_FICHE_SANTE");
        assertThatThrownBy(() -> jdbc.update("UPDATE famille.revision_sante SET nombre_critiques = 0"))
                .hasMessageContaining("ajout seul");

        appel(put(base).contentType(MediaType.APPLICATION_JSON).content("{\"groupeSanguin\":\"Z\",\"elements\":[]}"), jeton)
                .andExpect(status().isBadRequest());
    }

    @Test
    void lesContactsDUrgenceSontChiffresNormalisesEtLimitesACinq() throws Exception {
        ParentAvecEnfant famille = new Acteurs(mvc, sms).parentAvecEnfant();
        String jeton = famille.parent().jeton();
        String base = "/api/v1/enfants/" + famille.enfantId() + "/contacts";

        String premier = Acteurs.extraire(contact(base, jeton, "Tante", "Fatou MARQUEUR-CONTACT", "76 11 22 33", true)
                .andExpect(status().isCreated()).andExpect(jsonPath("$.telephone").value("+22676112233"))
                .andExpect(jsonPath("$.rang").value(1)).andReturn().getResponse().getContentAsString(),
                "\"id\":\"([0-9a-f-]{36})\"");
        contact(base, jeton, "Voisin", "Paul", "25 30 60 70", false).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TELEPHONE_INVALIDE"));

        assertThat(jdbc.queryForList("""
                SELECT encode(nom_chiffre || telephone_chiffre, 'escape') FROM famille.contact_urgence WHERE enfant_id = ?::uuid
                """, String.class, famille.enfantId())).singleElement().asString().doesNotContain("MARQUEUR", "76112233");

        appel(put(base + "/" + premier).contentType(MediaType.APPLICATION_JSON)
                .content("{\"lien\":\"Tante\",\"nom\":\"Fatou\",\"telephone\":\"76112233\",\"visibleSurQr\":false}"), jeton)
                .andExpect(status().isOk()).andExpect(jsonPath("$.visibleSurQr").value(false));
        for (int i = 2; i <= 5; i++) {
            contact(base, jeton, "Proche " + i, "Contact " + i, "7011000" + i, false).andExpect(status().isCreated());
        }
        contact(base, jeton, "Proche 6", "Contact 6", "70110006", false).andExpect(status().isConflict());

        appel(delete(base + "/" + premier), jeton).andExpect(status().isNoContent());
        appel(get(base), jeton).andExpect(jsonPath("$.length()").value(4));
    }

    private ResultActions contact(String base, String jeton, String lien, String nom, String telephone, boolean visible)
            throws Exception {
        return appel(post(base).contentType(MediaType.APPLICATION_JSON).content("{\"lien\":\"" + lien + "\",\"nom\":\"" + nom
                + "\",\"telephone\":\"" + telephone + "\",\"visibleSurQr\":" + visible + "}"), jeton);
    }

    private ResultActions appel(MockHttpServletRequestBuilder requete, String jeton) throws Exception {
        return mvc.perform(requete.header("Authorization", "Bearer " + jeton));
    }
}
