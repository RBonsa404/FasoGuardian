package bf.fasoguardian.geolocalisation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import bf.fasoguardian.Acteurs;
import bf.fasoguardian.Acteurs.Carte;
import bf.fasoguardian.Acteurs.ParentAvecEnfant;
import bf.fasoguardian.TestIntegration;
import bf.fasoguardian.notifications.infrastructure.SmsBacASable;
import bf.fasoguardian.telemetrie.application.Ingestion;
import bf.fasoguardian.telemetrie.application.Ingestion.Resultat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Partage temporaire de la position avec un contact d'urgence (US-SEC-001). */
class PartageIT extends TestIntegration {

    private static final String PUBLIC = "/api/v1/public/partages/";

    @Autowired
    MockMvc mvc;

    @Autowired
    SmsBacASable sms;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    Ingestion ingestion;

    Acteurs acteurs;

    @BeforeEach
    void preparer() {
        acteurs = new Acteurs(mvc, sms);
    }

    @Test
    void leContactVoitLaPositionParSonLienPendantLaDureeChoisieSeulement() throws Exception {
        ParentAvecEnfant famille = acteurs.parentAvecEnfant();
        String jeton = famille.parent().jeton();
        String base = "/api/v1/enfants/" + famille.enfantId() + "/partage";
        String contact = contact(famille, "Oncle", "76 11 22 08");
        positionner(famille);

        avec(get(base), jeton).andExpect(status().isNotFound());
        json(post(base), "{\"contactId\":\"" + contact + "\",\"dureeMinutes\":60}", jeton).andExpect(status().isCreated())
                .andExpect(jsonPath("$.lien").value("Oncle")).andExpect(jsonPath("$.destinataire").value("+226 76 •• •• 08"))
                .andExpect(jsonPath("$.ouvertures").value(0));

        // Le contact reçoit un lien personnel ; le SMS ne dit ni le nom de l'enfant ni sa position.
        String texte = sms.dernierPour("+22676112208").orElseThrow().texte();
        assertThat(texte).contains("partage avec vous la position de son enfant jusqu'à").contains("ne le transférez pas")
                .doesNotContain("12.37");
        String lien = Acteurs.extraire(texte, "/p/([A-Za-z0-9_-]{22})");

        mvc.perform(get(PUBLIC + lien)).andExpect(status().isOk()).andExpect(jsonPath("$.position.latitude").value(12.37))
                .andExpect(jsonPath("$.position.longitude").value(-1.52)).andExpect(jsonPath("$.position.precisionM").value(10))
                .andExpect(jsonPath("$.fin").exists()).andExpect(jsonPath("$.partagePar").isNotEmpty())
                .andExpect(jsonPath("$.enfant").doesNotExist()).andExpect(jsonPath("$.prenom").doesNotExist());
        mvc.perform(get(PUBLIC + lien)).andExpect(status().isOk());
        avec(get(base), jeton).andExpect(status().isOk()).andExpect(jsonPath("$.ouvertures").value(2))
                .andExpect(jsonPath("$.derniereOuverture").exists());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit.entree WHERE action = 'POSITION_PARTAGEE_CONSULTEE' AND cible_id = ?",
                Integer.class, famille.enfantId())).isEqualTo(2);
        // La base ne garde ni le lien ni le numéro en clair.
        assertThat(jdbc.queryForObject("SELECT jeton_sha256 || encode(destinataire_chiffre, 'escape') FROM geolocalisation.partage_position"
                + " WHERE enfant_id = ?::uuid", String.class, famille.enfantId())).doesNotContain(lien).doesNotContain("76112208");

        // Passé la durée définie, le lien ne montre plus rien.
        jdbc.update("UPDATE geolocalisation.partage_position SET debut = now() - INTERVAL '2 hours', fin = now() - INTERVAL '1 minute'"
                + " WHERE enfant_id = ?::uuid", famille.enfantId());
        mvc.perform(get(PUBLIC + lien)).andExpect(status().isNotFound()).andExpect(jsonPath("$.detail").value("Ce partage est terminé."));
        avec(get(base), jeton).andExpect(status().isNotFound());
    }

    @Test
    void laRevocationInterromptLAccesAussitotEtUnNouveauPartageRemplaceLAncien() throws Exception {
        ParentAvecEnfant famille = acteurs.parentAvecEnfant();
        String jeton = famille.parent().jeton();
        String base = "/api/v1/enfants/" + famille.enfantId() + "/partage";
        String oncle = contact(famille, "Oncle", "76 11 22 08");
        String tante = contact(famille, "Tante", "70 55 66 77");
        positionner(famille);

        json(post(base), "{\"contactId\":\"" + oncle + "\",\"dureeMinutes\":30}", jeton).andExpect(status().isCreated());
        String premier = Acteurs.extraire(sms.dernierPour("+22676112208").orElseThrow().texte(), "/p/([A-Za-z0-9_-]{22})");
        mvc.perform(get(PUBLIC + premier)).andExpect(status().isOk());

        // Un seul lien vaut à la fois : le partage avec la tante met fin à celui de l'oncle.
        json(post(base), "{\"contactId\":\"" + tante + "\",\"dureeMinutes\":120}", jeton).andExpect(status().isCreated());
        String second = Acteurs.extraire(sms.dernierPour("+22670556677").orElseThrow().texte(), "/p/([A-Za-z0-9_-]{22})");
        mvc.perform(get(PUBLIC + premier)).andExpect(status().isNotFound());
        mvc.perform(get(PUBLIC + second)).andExpect(status().isOk());

        avec(delete(base), jeton).andExpect(status().isNoContent());
        mvc.perform(get(PUBLIC + second)).andExpect(status().isNotFound());
        avec(get(base), jeton).andExpect(status().isNotFound());
        assertThat(jdbc.queryForList("SELECT action FROM audit.entree WHERE acteur_id IS NOT NULL AND cible_id = ? AND action IN"
                + " ('POSITION_PARTAGEE', 'PARTAGE_REVOQUE') ORDER BY id", String.class, famille.enfantId()))
                .containsExactly("POSITION_PARTAGEE", "POSITION_PARTAGEE", "PARTAGE_REVOQUE");
    }

    @Test
    void seulUnTuteurPartageEtSeulementAvecUnContactDeLEnfantPourUneDureeBornee() throws Exception {
        ParentAvecEnfant famille = acteurs.parentAvecEnfant();
        ParentAvecEnfant autre = acteurs.parentAvecEnfant();
        String jeton = famille.parent().jeton();
        String base = "/api/v1/enfants/" + famille.enfantId() + "/partage";
        String contact = contact(famille, "Oncle", "76 99 88 01");
        String contactDUnAutre = contact(autre, "Voisin", "70 00 11 22");

        json(post(base), "{\"contactId\":\"" + contact + "\",\"dureeMinutes\":5}", jeton).andExpect(status().isBadRequest());
        json(post(base), "{\"contactId\":\"" + contact + "\",\"dureeMinutes\":1000}", jeton).andExpect(status().isBadRequest());
        json(post(base), "{\"contactId\":\"" + contactDUnAutre + "\",\"dureeMinutes\":60}", jeton).andExpect(status().isNotFound());
        json(post(base), "{\"contactId\":\"" + UUID.randomUUID() + "\",\"dureeMinutes\":60}", jeton).andExpect(status().isNotFound());

        json(post(base), "{\"contactId\":\"" + contact + "\",\"dureeMinutes\":60}", autre.parent().jeton()).andExpect(status().isNotFound());
        avec(get(base), autre.parent().jeton()).andExpect(status().isNotFound());
        avec(delete(base), autre.parent().jeton()).andExpect(status().isNotFound());
        json(post(base), "{\"contactId\":\"" + contact + "\",\"dureeMinutes\":60}", null).andExpect(status().isUnauthorized());
        json(post(base), "{\"contactId\":\"" + contact + "\",\"dureeMinutes\":60}", acteurs.jetonSav()).andExpect(status().isForbidden());

        mvc.perform(get(PUBLIC + "jeton-inconnu-de-22-car")).andExpect(status().isNotFound());
        mvc.perform(get(PUBLIC + "x")).andExpect(status().isNotFound());
        assertThat(sms.dernierPour("+22676998801")).as("aucun lien n'est parti").isEmpty();
    }

    // -------------------------------------------------------------------- aides

    private String contact(ParentAvecEnfant famille, String lien, String telephone) throws Exception {
        return Acteurs.extraire(json(post("/api/v1/enfants/" + famille.enfantId() + "/contacts"), "{\"lien\":\"" + lien
                + "\",\"nom\":\"Issouf Ouédraogo\",\"telephone\":\"" + telephone + "\",\"visibleSurQr\":false}", famille.parent().jeton())
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "\"id\":\"([0-9a-f-]{36})\"");
    }

    /** Équipe l'enfant et fait émettre une position récente à son bracelet. */
    private void positionner(ParentAvecEnfant famille) throws Exception {
        Carte carte = acteurs.equiper(famille);
        jdbc.update("UPDATE dispositifs.appairage SET debut = now() - INTERVAL '1 day' WHERE enfant_id = ?::uuid", famille.enfantId());
        String mesure = "{\"t\":" + (Instant.now().getEpochSecond() - 60) + ",\"seq\":1,\"lat\":12.37000,\"lon\":-1.52000,\"acc\":10,"
                + "\"src\":\"gnss\",\"bat\":70}";
        assertThat(ingestion.telemetrie(carte.numeroSerie(), mesure.getBytes(StandardCharsets.UTF_8))).isEqualTo(Resultat.ACCEPTE);
    }

    private ResultActions avec(MockHttpServletRequestBuilder requete, String jeton) throws Exception {
        return mvc.perform(jeton == null ? requete : requete.header("Authorization", "Bearer " + jeton));
    }

    private ResultActions json(MockHttpServletRequestBuilder requete, String corps, String jeton) throws Exception {
        return avec(requete.contentType(MediaType.APPLICATION_JSON).content(corps), jeton);
    }
}
