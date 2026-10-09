package bf.fasoguardian.telemetrie;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

import bf.fasoguardian.Acteurs;
import bf.fasoguardian.Acteurs.Carte;
import bf.fasoguardian.Acteurs.ParentAvecEnfant;
import bf.fasoguardian.TestIntegration;
import bf.fasoguardian.notifications.infrastructure.SmsBacASable;
import bf.fasoguardian.plateforme.securite.SceauWebhook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Passerelles LoRaWAN en zone pilote : registre et présence confirmée sans réseau cellulaire (US-SYS-004). */
class PasserellesLorawanIT extends TestIntegration {

    private static final String REGISTRE = "/api/v1/console/passerelles";
    private static final String TRAMES = "/api/v1/public/lorawan/trames";

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
    void uneTrameEntendueParLaPasserelleConfirmeLaPresenceDansLEnceinteSansReseauCellulaire() throws Exception {
        ParentAvecEnfant famille = acteurs.parentAvecEnfant();
        Carte carte = acteurs.equiper(famille);
        jdbc.update("UPDATE dispositifs.appairage SET debut = now() - INTERVAL '1 hour' WHERE enfant_id = ?::uuid", famille.enfantId());
        String eui = eui();
        installer(eui, "École Les Manguiers", 12.36420, -1.53310, 150).andExpect(status().isCreated())
                .andExpect(jsonPath("$.eui").value(eui)).andExpect(jsonPath("$.enLigne").value(false));

        // Le bracelet n'a rien émis par le réseau cellulaire : seule la passerelle l'a entendu.
        long t = Instant.now().getEpochSecond() - 20;
        remettre(trame(eui, carte.numeroSerie(), 41, t)).andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/enfants/" + famille.enfantId() + "/position").header("Authorization", "Bearer " + famille.parent().jeton()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.position.latitude").value(12.3642))
                .andExpect(jsonPath("$.position.longitude").value(-1.5331))
                .andExpect(jsonPath("$.position.precisionM").value(150))
                .andExpect(jsonPath("$.position.source").value("LORA"));
        // La même trame remise deux fois ne compte qu'une fois ; la passerelle est vue en ligne.
        remettre(trame(eui, carte.numeroSerie(), 41, t)).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM telemetrie.position p JOIN dispositifs.bracelet b ON b.id = p.bracelet_id
                WHERE b.numero_serie = ? AND p.source = 'LORA'""", Integer.class, carte.numeroSerie())).isEqualTo(1);
        mvc.perform(get(REGISTRE).header("Authorization", "Bearer " + acteurs.jetonAdmin())).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.eui == '" + eui + "')].enLigne").value(true))
                .andExpect(jsonPath("$[?(@.eui == '" + eui + "')].etablissement").value("École Les Manguiers"));
    }

    @Test
    void sansSceauOuDepuisUnePasserelleInconnueOuRetireeRienNEstEnregistre() throws Exception {
        ParentAvecEnfant famille = acteurs.parentAvecEnfant();
        Carte carte = acteurs.equiper(famille);
        jdbc.update("UPDATE dispositifs.appairage SET debut = now() - INTERVAL '1 hour' WHERE enfant_id = ?::uuid", famille.enfantId());
        String eui = eui();
        String id = Acteurs.extraire(installer(eui, "École Wend-Kuuni", 12.40110, -1.49870, 200).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "\"id\":\"([0-9a-f-]{36})\"");
        long t = Instant.now().getEpochSecond() - 20;
        byte[] corps = trame(eui, carte.numeroSerie(), 7, t);

        // L'appel doit porter le sceau du serveur de réseau, calculé sur ce corps précis.
        mvc.perform(post(TRAMES).contentType(MediaType.APPLICATION_JSON).content(corps)).andExpect(status().isForbidden());
        mvc.perform(post(TRAMES).contentType(MediaType.APPLICATION_JSON).content(corps).header("X-FG-Signature",
                new SceauWebhook(SECRET_SERVEUR_LORAWAN, "essai").sceller(trame(eui, carte.numeroSerie(), 8, t), Instant.now())))
                .andExpect(status().isForbidden());
        // Passerelle inconnue, bracelet inconnu, trame datée du futur : même réponse, rien d'enregistré.
        remettre(trame(eui(), carte.numeroSerie(), 7, t)).andExpect(status().isNoContent());
        remettre(trame(eui, "FG-INCONNU-0001", 7, t)).andExpect(status().isNoContent());
        remettre(trame(eui, carte.numeroSerie(), 7, t + 3600)).andExpect(status().isNoContent());
        assertThat(positionsLora(carte)).isZero();

        // Un signe de vie sans bracelet suffit à tenir la passerelle pour en ligne.
        remettre(("{\"passerelle\":\"" + eui + "\"}").getBytes(StandardCharsets.UTF_8)).andExpect(status().isNoContent());
        mvc.perform(get(REGISTRE).header("Authorization", "Bearer " + acteurs.jetonAdmin()))
                .andExpect(jsonPath("$[?(@.eui == '" + eui + "')].enLigne").value(true));

        // Retirée, la passerelle disparaît du registre et ses trames ne sont plus acceptées.
        mvc.perform(delete(REGISTRE + "/" + id).header("Authorization", "Bearer " + acteurs.jetonAdmin())).andExpect(status().isNoContent());
        remettre(corps).andExpect(status().isNoContent());
        assertThat(positionsLora(carte)).isZero();
        mvc.perform(get(REGISTRE).header("Authorization", "Bearer " + acteurs.jetonAdmin()))
                .andExpect(jsonPath("$[?(@.eui == '" + eui + "')]").isEmpty());
        assertThat(jdbc.queryForList("SELECT action FROM audit.entree WHERE cible_id = ? ORDER BY id", String.class, eui))
                .containsExactly("PASSERELLE_LORAWAN_INSTALLEE", "PASSERELLE_LORAWAN_RETIREE");
    }

    @Test
    void seulLAdministrateurTientLeRegistreEtUnIdentifiantNeSertQuUneFois() throws Exception {
        String eui = eui();
        installer(eui, "École A", 12.37, -1.52, 100).andExpect(status().isCreated());
        installer(eui.toLowerCase(Locale.ROOT), "École B", 12.38, -1.51, 100).andExpect(status().isConflict());
        installer("PAS-UN-EUI", "École C", 12.38, -1.51, 100).andExpect(status().isBadRequest());
        installer(eui(), "École D", 12.38, -1.51, 5).andExpect(status().isBadRequest());

        // L'écran de paramétrage lit aussi les tarifs : l'administrateur y a accès, pas les autres rôles internes.
        mvc.perform(get("/api/v1/offres").header("Authorization", "Bearer " + acteurs.jetonAdmin())).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].prixFcfa").isNumber());
        mvc.perform(get("/api/v1/offres").header("Authorization", "Bearer " + acteurs.jetonSav())).andExpect(status().isForbidden());

        mvc.perform(get(REGISTRE)).andExpect(status().isUnauthorized());
        mvc.perform(get(REGISTRE).header("Authorization", "Bearer " + acteurs.jetonSav())).andExpect(status().isForbidden());
        mvc.perform(get(REGISTRE).header("Authorization", "Bearer " + acteurs.parent().jeton())).andExpect(status().isForbidden());
        mvc.perform(post(REGISTRE).header("Authorization", "Bearer " + acteurs.agent("[\"SUPPORT\"]").jeton())
                .contentType(MediaType.APPLICATION_JSON).content(installation(eui(), "École E", 12.38, -1.51, 100)))
                .andExpect(status().isForbidden());
    }

    // -------------------------------------------------------------------- aides

    private static String eui() {
        return String.format("%016X", ThreadLocalRandom.current().nextLong());
    }

    private static String installation(String eui, String etablissement, double latitude, double longitude, int rayonM) {
        return String.format(Locale.ROOT, "{\"eui\":\"%s\",\"etablissement\":\"%s\",\"latitude\":%.5f,\"longitude\":%.5f,\"rayonM\":%d}",
                eui, etablissement, latitude, longitude, rayonM);
    }

    private ResultActions installer(String eui, String etablissement, double latitude, double longitude, int rayonM) throws Exception {
        return mvc.perform(post(REGISTRE).header("Authorization", "Bearer " + acteurs.jetonAdmin())
                .contentType(MediaType.APPLICATION_JSON).content(installation(eui, etablissement, latitude, longitude, rayonM)));
    }

    private static byte[] trame(String eui, String bracelet, long compteur, long t) {
        return ("{\"passerelle\":\"" + eui + "\",\"bracelet\":\"" + bracelet + "\",\"compteur\":" + compteur + ",\"t\":" + t + "}")
                .getBytes(StandardCharsets.UTF_8);
    }

    /** Remise par le serveur de réseau, avec son sceau. */
    private ResultActions remettre(byte[] corps) throws Exception {
        return mvc.perform(post(TRAMES).contentType(MediaType.APPLICATION_JSON).content(corps)
                .header("X-FG-Signature", new SceauWebhook(SECRET_SERVEUR_LORAWAN, "essai").sceller(corps, Instant.now())));
    }

    private Integer positionsLora(Carte carte) {
        return jdbc.queryForObject("""
                SELECT count(*) FROM telemetrie.position p JOIN dispositifs.bracelet b ON b.id = p.bracelet_id
                WHERE b.numero_serie = ? AND p.source = 'LORA'""", Integer.class, carte.numeroSerie());
    }
}
