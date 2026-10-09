package bf.fasoguardian.identite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import bf.fasoguardian.Acteurs;
import bf.fasoguardian.Acteurs.Parent;
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

/** Demandes de support suivies par le parent et base de connaissances (US-PAR-017, US-SUP-001). */
class SupportIT extends TestIntegration {

    private static final String MES_DEMANDES = "/api/v1/support/demandes";
    private static final String DEMANDES = "/api/v1/console/support/demandes";
    private static final String ARTICLES = "/api/v1/console/support/articles";

    @Autowired
    MockMvc mvc;

    @Autowired
    SmsBacASable sms;

    @Autowired
    JdbcTemplate jdbc;

    Acteurs acteurs;
    String operateur;

    @BeforeEach
    void preparer() throws Exception {
        acteurs = new Acteurs(mvc, sms);
        operateur = acteurs.agent("[\"SUPPORT\"]").jeton();
    }

    @Test
    void leParentSuitSaDemandeEtVoitLeStatutEtLesReponses() throws Exception {
        Parent parent = acteurs.parent();
        String reponse = json(post(MES_DEMANDES), "{\"objet\":\"La sangle se détache toute seule\",\"message\":\"Depuis hier la sangle "
                + "s'ouvre quand elle joue. Est-ce normal ?\"}", parent.jeton()).andExpect(status().isCreated())
                .andExpect(jsonPath("$.statut").value("OUVERTE")).andExpect(jsonPath("$.reference").value(org.hamcrest.Matchers.startsWith("SUP-")))
                .andExpect(jsonPath("$.messages[0].deMoi").value(true)).andExpect(jsonPath("$.telephone").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        String id = Acteurs.extraire(reponse, "\"id\":\"([0-9a-f-]{36})\"");
        String reference = Acteurs.extraire(reponse, "\"reference\":\"(SUP-\\d{6})\"");

        // L'opérateur voit la demande et les coordonnées du parent ; sa consultation est journalisée.
        avec(get(DEMANDES + "/" + id), operateur).andExpect(status().isOk()).andExpect(jsonPath("$.objet").value("La sangle se détache toute seule"))
                .andExpect(jsonPath("$.telephone").value("+226" + parent.telephone()));
        avec(get(DEMANDES + "?statut=OUVERTE"), operateur).andExpect(jsonPath("$[?(@.id == '" + id + "')].reference").value(reference));

        json(post(DEMANDES + "/" + id + "/reponse"), "{\"message\":\"Ce n'est pas normal.\",\"suite\":\"OUVERTE\"}", operateur)
                .andExpect(status().isBadRequest());
        json(post(DEMANDES + "/" + id + "/reponse"), "{\"message\":\"Ce n'est pas normal. Échange gratuit de sangle au point relais.\","
                + "\"suite\":\"EN_ATTENTE_PARENT\"}", operateur).andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("EN_ATTENTE_PARENT")).andExpect(jsonPath("$.messages.length()").value(2));
        // Le parent est prévenu, sans que le SMS reprenne le contenu de l'échange.
        assertThat(acteurs.attendreSms(parent.telephone(), "le support a répondu", reference)).doesNotContain("sangle");

        avec(get(MES_DEMANDES + "/" + id), parent.jeton()).andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("EN_ATTENTE_PARENT")).andExpect(jsonPath("$.messages[1].duSupport").value(true))
                .andExpect(jsonPath("$.messages[1].texte").value("Ce n'est pas normal. Échange gratuit de sangle au point relais."));
        avec(get(MES_DEMANDES), parent.jeton()).andExpect(jsonPath("$.length()").value(1));

        // Le parent répond : la demande revient à l'opérateur, qui la résout.
        json(post(MES_DEMANDES + "/" + id + "/messages"), "{\"message\":\"Merci, j'y passe demain.\"}", parent.jeton())
                .andExpect(status().isOk()).andExpect(jsonPath("$.statut").value("OUVERTE"));
        json(post(DEMANDES + "/" + id + "/reponse"), "{\"message\":\"Bonne journée.\",\"suite\":\"RESOLUE\"}", operateur)
                .andExpect(status().isOk()).andExpect(jsonPath("$.statut").value("RESOLUE")).andExpect(jsonPath("$.messages.length()").value(4));
        assertThat(jdbc.queryForList("SELECT action FROM audit.entree WHERE type_cible = 'DEMANDE_SUPPORT' AND cible_id = ? ORDER BY id",
                String.class, reference)).containsExactly("DEMANDE_SUPPORT_CONSULTEE", "DEMANDE_SUPPORT_REPONDUE", "DEMANDE_SUPPORT_REPONDUE");
    }

    @Test
    void uneDemandeNEstVisibleQueDeSonAuteurEtDesOperateurs() throws Exception {
        Parent parent = acteurs.parent();
        String id = Acteurs.extraire(json(post(MES_DEMANDES), "{\"objet\":\"Facture\",\"message\":\"Je n'ai pas reçu mon reçu.\"}",
                parent.jeton()).andReturn().getResponse().getContentAsString(), "\"id\":\"([0-9a-f-]{36})\"");
        String intrus = acteurs.parent().jeton();

        avec(get(MES_DEMANDES + "/" + id), intrus).andExpect(status().isNotFound());
        json(post(MES_DEMANDES + "/" + id + "/messages"), "{\"message\":\"Bonjour\"}", intrus).andExpect(status().isNotFound());
        avec(get(MES_DEMANDES), intrus).andExpect(jsonPath("$.length()").value(0));
        avec(get(MES_DEMANDES), null).andExpect(status().isUnauthorized());
        avec(get(DEMANDES + "/" + id), parent.jeton()).andExpect(status().isForbidden());
        avec(get(DEMANDES + "/" + id), acteurs.agent("[\"KYC\"]").jeton()).andExpect(status().isForbidden());
        avec(get(DEMANDES + "/" + UUID.randomUUID()), operateur).andExpect(status().isNotFound());
        json(post(MES_DEMANDES), "{\"objet\":\" \",\"message\":\"x\"}", parent.jeton()).andExpect(status().isBadRequest());
        // Un opérateur support n'ouvre pas de demande au nom d'un parent.
        json(post(MES_DEMANDES), "{\"objet\":\"Test\",\"message\":\"x\"}", operateur).andExpect(status().isForbidden());
    }

    @Test
    void unArticleNEstConsultableParLesParentsQuUneFoisPublie() throws Exception {
        String parent = acteurs.parent().jeton();
        String titre = "Que faire si la sangle s'ouvre seule ? " + UUID.randomUUID().toString().substring(0, 6);
        String article = "{\"categorie\":\"BRACELET\",\"titre\":\"" + titre + "\",\"contenu\":\"Le fermoir de sécurité doit produire un clic "
                + "net.\\n\\nRendez-vous dans un point relais : la sangle est échangée gratuitement.\"}";

        String cree = json(post(ARTICLES), article, operateur).andExpect(status().isCreated()).andExpect(jsonPath("$.publie").value(false))
                .andReturn().getResponse().getContentAsString();
        String id = Acteurs.extraire(cree, "\"id\":\"([0-9a-f-]{36})\"");
        String slug = Acteurs.extraire(cree, "\"slug\":\"([a-z0-9-]+)\"");
        assertThat(slug).startsWith("que-faire-si-la-sangle-s-ouvre-seule");
        json(post(ARTICLES), article, operateur).andExpect(status().isConflict());

        // Brouillon : invisible des parents.
        avec(get("/api/v1/aide/articles/" + slug), parent).andExpect(status().isNotFound());
        avec(get("/api/v1/aide/articles?q=fermoir"), parent).andExpect(jsonPath("$[?(@.slug == '" + slug + "')]").isEmpty());

        avec(post(ARTICLES + "/" + id + "/publication"), operateur).andExpect(status().isOk()).andExpect(jsonPath("$.publie").value(true));

        avec(get("/api/v1/aide/articles?q=FERMOIR"), parent).andExpect(jsonPath("$[?(@.slug == '" + slug + "')].titre").value(titre));
        avec(get("/api/v1/aide/articles?categorie=PAIEMENT"), parent).andExpect(jsonPath("$[?(@.slug == '" + slug + "')]").isEmpty());
        avec(get("/api/v1/aide/categories"), parent).andExpect(jsonPath("$[?(@.categorie == 'BRACELET')].articles").exists());
        avec(get("/api/v1/aide/articles/" + slug), parent).andExpect(status().isOk())
                .andExpect(jsonPath("$.contenu").value(org.hamcrest.Matchers.containsString("point relais")));
        avec(get("/api/v1/aide/articles/" + slug), parent).andExpect(jsonPath("$.lectures").value(2));

        // Modification puis retrait : l'effet est immédiat.
        json(put(ARTICLES + "/" + id), article.replace("clic net", "clic franc"), operateur).andExpect(status().isOk());
        avec(get("/api/v1/aide/articles/" + slug), parent).andExpect(jsonPath("$.contenu").value(org.hamcrest.Matchers.containsString("clic franc")));
        avec(post(ARTICLES + "/" + id + "/retrait"), operateur).andExpect(status().isOk());
        avec(get("/api/v1/aide/articles/" + slug), parent).andExpect(status().isNotFound());

        // Seul l'opérateur support gère la base de connaissances.
        json(post(ARTICLES), article, parent).andExpect(status().isForbidden());
        avec(get(ARTICLES), acteurs.jetonSav()).andExpect(status().isForbidden());
        avec(get(ARTICLES), null).andExpect(status().isUnauthorized());
        avec(get("/api/v1/aide/articles"), null).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForList("SELECT action FROM audit.entree WHERE type_cible = 'ARTICLE_AIDE' AND cible_id = ? ORDER BY id",
                String.class, slug)).containsExactly("ARTICLE_REDIGE", "ARTICLE_PUBLIE", "ARTICLE_MODIFIE", "ARTICLE_RETIRE");
    }

    private ResultActions avec(MockHttpServletRequestBuilder requete, String jeton) throws Exception {
        return mvc.perform(jeton == null ? requete : requete.header("Authorization", "Bearer " + jeton));
    }

    private ResultActions json(MockHttpServletRequestBuilder requete, String corps, String jeton) throws Exception {
        return avec(requete.contentType(MediaType.APPLICATION_JSON).content(corps), jeton);
    }
}
