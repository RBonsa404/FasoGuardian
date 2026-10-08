package bf.fasoguardian.identite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.concurrent.atomic.AtomicInteger;

import bf.fasoguardian.Acteurs;
import bf.fasoguardian.Acteurs.Parent;
import bf.fasoguardian.TestIntegration;
import bf.fasoguardian.notifications.infrastructure.SmsBacASable;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Profil du parent, récupération d'accès, second facteur et droit à l'effacement (US-PAR-002). */
class ProfilIT extends TestIntegration {

    private static final AtomicInteger SUITE = new AtomicInteger(100_000);
    private static final String NOUVEAU_MOT_DE_PASSE = "harmattan-de-janvier-2027";

    @Autowired
    MockMvc mvc;

    @Autowired
    SmsBacASable sms;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void unParentAyantPerduSonMotDePasseRetablitSonAccesParCodeEtSesAnciennesSessionsSontFermees() throws Exception {
        Acteurs acteurs = new Acteurs(mvc, sms);
        Parent parent = acteurs.parent();
        Cookie ancienneSession = connecter(parent.telephone(), Acteurs.MOT_DE_PASSE_PARENT).andExpect(status().isOk())
                .andReturn().getResponse().getCookie("fg_rafraichissement");

        json("/api/v1/auth/mot-de-passe/code", "{\"telephone\":\"" + parent.telephone() + "\"}", null)
                .andExpect(status().isAccepted());
        String code = Acteurs.extraire(acteurs.dernierSms(parent.telephone()), "(\\d{6}) est votre code pour changer");

        // Un mot de passe refusé ne consomme pas le code.
        reinitialiser(parent.telephone(), code, "court1").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MOT_DE_PASSE_REFUSE"));
        reinitialiser(parent.telephone(), code, NOUVEAU_MOT_DE_PASSE).andExpect(status().isNoContent());

        connecter(parent.telephone(), Acteurs.MOT_DE_PASSE_PARENT).andExpect(status().isUnauthorized());
        connecter(parent.telephone(), NOUVEAU_MOT_DE_PASSE).andExpect(status().isOk());
        mvc.perform(post("/api/v1/auth/rafraichir").cookie(ancienneSession).header("X-FG-Requete", "1"))
                .andExpect(status().isUnauthorized());
        reinitialiser(parent.telephone(), code, NOUVEAU_MOT_DE_PASSE).andExpect(status().isBadRequest());
    }

    @Test
    void laDemandeDeReinitialisationNeRevelePasSiUnCompteExiste() throws Exception {
        String inconnu = "74" + String.format("%06d", SUITE.incrementAndGet());

        json("/api/v1/auth/mot-de-passe/code", "{\"telephone\":\"" + inconnu + "\"}", null).andExpect(status().isAccepted());

        assertThat(sms.dernierPour("+226" + inconnu)).isEmpty();
        reinitialiser(inconnu, "123456", NOUVEAU_MOT_DE_PASSE).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CODE_INCORRECT"));
    }

    @Test
    void leParentChangeDeNumeroAvecSonMotDePasseEtUnCodeRecuSurLeNouveauNumero() throws Exception {
        Acteurs acteurs = new Acteurs(mvc, sms);
        Parent parent = acteurs.parent();
        Parent autre = acteurs.parent();
        String nouveau = "74" + String.format("%06d", SUITE.incrementAndGet());

        json("/api/v1/moi/telephone/code", "{\"telephone\":\"" + autre.telephone() + "\"}", parent.jeton())
                .andExpect(status().isConflict());
        json("/api/v1/moi/telephone/code", "{\"telephone\":\"" + nouveau + "\"}", parent.jeton())
                .andExpect(status().isAccepted());
        String code = Acteurs.extraire(acteurs.dernierSms(nouveau), "(\\d{6}) est votre code pour confirmer");

        changerTelephone(parent, nouveau, code, "mauvais-mot-de-passe-9").andExpect(status().isUnauthorized());
        changerTelephone(parent, nouveau, code, Acteurs.MOT_DE_PASSE_PARENT).andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/moi").header("Authorization", "Bearer " + parent.jeton()))
                .andExpect(jsonPath("$.telephoneMasque").value("+226 •• •• •• " + nouveau.substring(6)));
        connecter(nouveau, Acteurs.MOT_DE_PASSE_PARENT).andExpect(status().isOk());
        connecter(parent.telephone(), Acteurs.MOT_DE_PASSE_PARENT).andExpect(status().isUnauthorized());
        assertThat(acteurs.dernierSms(parent.telephone())).contains("vient d'être modifié");
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM identite.utilisateur WHERE telephone_modifie_le IS NOT NULL", Long.class)).isPositive();
    }

    @Test
    void leParentChangeSonMotDePasseEnFournissantLActuel() throws Exception {
        Parent parent = new Acteurs(mvc, sms).parent();

        json("/api/v1/moi/mot-de-passe", "{\"actuel\":\"faux-mot-de-passe-1\",\"nouveau\":\"" + NOUVEAU_MOT_DE_PASSE + "\"}",
                parent.jeton()).andExpect(status().isUnauthorized());
        json("/api/v1/moi/mot-de-passe", "{\"actuel\":\"" + Acteurs.MOT_DE_PASSE_PARENT + "\",\"nouveau\":\""
                + NOUVEAU_MOT_DE_PASSE + "\"}", parent.jeton()).andExpect(status().isNoContent());

        connecter(parent.telephone(), NOUVEAU_MOT_DE_PASSE).andExpect(status().isOk());
    }

    @Test
    void laClotureExigeUnSecondFacteurPropreACetteActionPuisLeCompteNePeutPlusSAuthentifier() throws Exception {
        Acteurs acteurs = new Acteurs(mvc, sms);
        Parent parent = acteurs.parent();

        json("/api/v1/moi/cloture", "{}", parent.jeton()).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SECOND_FACTEUR_REQUIS"));

        // Un code émis pour une autre action sensible ne vaut pas pour la clôture.
        json("/api/v1/moi/second-facteur", "{\"action\":\"AUTORISER_RETRAIT\"}", parent.jeton())
                .andExpect(status().isAccepted());
        String codeRetrait = Acteurs.extraire(acteurs.dernierSms(parent.telephone()), "(\\d{6}) est votre code de confirmation");
        json("/api/v1/moi/cloture", "{\"codeSecondFacteur\":\"" + codeRetrait + "\"}", parent.jeton())
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CODE_INCORRECT"));

        json("/api/v1/moi/second-facteur", "{\"action\":\"CLORE_COMPTE\"}", parent.jeton()).andExpect(status().isAccepted());
        String code = Acteurs.extraire(acteurs.dernierSms(parent.telephone()), "(\\d{6}) est votre code de confirmation");
        json("/api/v1/moi/cloture", "{\"codeSecondFacteur\":\"" + code + "\"}", parent.jeton())
                .andExpect(status().isNoContent());

        assertThat(acteurs.dernierSms(parent.telephone())).contains("supprimées sous 30 jours");
        connecter(parent.telephone(), Acteurs.MOT_DE_PASSE_PARENT).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/moi").header("Authorization", "Bearer " + parent.jeton()))
                .andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM audit.entree e JOIN identite.utilisateur u ON u.id = e.acteur_id
                WHERE e.action = 'COMPTE_CLOS' AND u.statut = 'CLOS' AND u.clos_le IS NOT NULL
                """, Long.class)).isPositive();
    }

    @Test
    void lesActionsDuProfilSontReserveesAuParentAuthentifie() throws Exception {
        json("/api/v1/moi/second-facteur", "{\"action\":\"CLORE_COMPTE\"}", null).andExpect(status().isUnauthorized());
        json("/api/v1/moi/cloture", "{}", new Acteurs(mvc, sms).agent("[\"SUPPORT\"]").jeton())
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCES_REFUSE"));
    }

    private ResultActions reinitialiser(String telephone, String code, String motDePasse) throws Exception {
        return json("/api/v1/auth/mot-de-passe/reinitialiser", "{\"telephone\":\"" + telephone + "\",\"code\":\"" + code
                + "\",\"motDePasse\":\"" + motDePasse + "\"}", null);
    }

    private ResultActions changerTelephone(Parent parent, String nouveau, String code, String motDePasse) throws Exception {
        return json("/api/v1/moi/telephone", "{\"telephone\":\"" + nouveau + "\",\"code\":\"" + code
                + "\",\"motDePasse\":\"" + motDePasse + "\"}", parent.jeton());
    }

    private ResultActions connecter(String telephone, String motDePasse) throws Exception {
        return json("/api/v1/auth/connexion", "{\"telephone\":\"" + telephone + "\",\"motDePasse\":\"" + motDePasse + "\"}",
                null);
    }

    private ResultActions json(String chemin, String corps, String jeton) throws Exception {
        var requete = post(chemin).contentType(MediaType.APPLICATION_JSON).content(corps);
        return mvc.perform(jeton == null ? requete : requete.header("Authorization", "Bearer " + jeton));
    }
}
