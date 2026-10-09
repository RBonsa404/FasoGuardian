package bf.fasoguardian.dispositifs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.security.Signature;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

import bf.fasoguardian.Acteurs;
import bf.fasoguardian.Acteurs.Carte;
import bf.fasoguardian.Acteurs.ParentAvecEnfant;
import bf.fasoguardian.TestIntegration;
import bf.fasoguardian.dispositifs.application.CommandesBracelet;
import bf.fasoguardian.notifications.infrastructure.SmsBacASable;
import bf.fasoguardian.telemetrie.application.ReceptionMessages;
import bf.fasoguardian.telemetrie.application.ReceptionMessages.Flux;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Commandes signées vers le bracelet et leurs accusés (US-SYS-011, US-ENF-002, US-PAR-012, US-PAR-013). */
@RecordApplicationEvents
class CommandesIT extends TestIntegration {

    @Autowired
    MockMvc mvc;

    @Autowired
    SmsBacASable sms;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ReceptionMessages reception;

    @Autowired
    CommandesBracelet commandes;

    @Autowired
    JsonMapper json;

    @Autowired
    ApplicationEvents evenements;

    Acteurs acteurs;

    @BeforeEach
    void preparer() {
        acteurs = new Acteurs(mvc, sms);
    }

    @Test
    void unSosMetLeBraceletEnModeAlerteParUneCommandeSigneeEtLaLeveeLEnSort() throws Exception {
        ParentAvecEnfant famille = acteurs.parentAvecEnfant();
        Carte carte = acteurs.equiper(famille);
        String jeton = famille.parent().jeton();

        reception.recevoir(Flux.ALERT, carte.numeroSerie(), octets("{\"t\":" + (Instant.now().getEpochSecond() + 1) + ",\"seq\":1,\"ev\":\"sos\"}"));
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(messages(carte)).hasSize(1));

        JsonNode entree = corpsVerifie(messages(carte).get(0));
        assertThat(entree.path("dev").asString()).isEqualTo(carte.numeroSerie());
        assertThat(entree.path("cmd").asString()).isEqualTo("alert");
        assertThat(entree.path("p").path("on").asInt()).isEqualTo(1);
        assertThat(entree.path("exp").asLong()).isBetween(Instant.now().getEpochSecond() + 800, Instant.now().getEpochSecond() + 901);

        String alerte = jdbc.queryForObject("SELECT id::text FROM alertes.alerte WHERE enfant_id = ?::uuid", String.class, famille.enfantId());
        mvc.perform(post("/api/v1/alertes/" + alerte + "/acquittement").header("Authorization", "Bearer " + jeton)).andExpect(status().isOk());
        assertThat(messages(carte)).as("la prise en charge ne change pas le mode du bracelet").hasSize(1);
        mvc.perform(post("/api/v1/alertes/" + alerte + "/levee").header("Authorization", "Bearer " + jeton)
                .contentType(MediaType.APPLICATION_JSON).content("{\"motif\":\"Enfant retrouvé\"}")).andExpect(status().isOk());

        assertThat(messages(carte)).hasSize(2);
        JsonNode sortie = corpsVerifie(messages(carte).get(1));
        assertThat(sortie.path("p").path("on").asInt()).isZero();
        assertThat(sortie.path("n").asLong()).as("numéro strictement croissant").isGreaterThan(entree.path("n").asLong());
        assertThat(sortie.path("id").asString()).isNotEqualTo(entree.path("id").asString());
        assertThat(jdbc.queryForList("SELECT e.action FROM audit.entree e JOIN dispositifs.bracelet b ON b.id::text = e.cible_id"
                + " WHERE b.numero_serie = ? AND e.action LIKE 'COMMANDE_%'", String.class, carte.numeroSerie()))
                .containsExactly("COMMANDE_MODE_ALERTE", "COMMANDE_MODE_ALERTE");
    }

    @Test
    void leBraceletAccuseSesCommandesEtSignaleCellesQuIlNePeutPasAuthentifier() throws Exception {
        ParentAvecEnfant famille = acteurs.parentAvecEnfant();
        Carte carte = acteurs.equiper(famille);
        Carte voisin = acteurs.equiper(acteurs.parentAvecEnfant());
        String jeton = famille.parent().jeton();
        String base = "/api/v1/enfants/" + famille.enfantId() + "/bracelet";

        // Localiser maintenant : une commande, une fois par minute
        mvc.perform(post(base + "/localisation").header("Authorization", "Bearer " + jeton)).andExpect(status().isAccepted());
        mvc.perform(post(base + "/localisation").header("Authorization", "Bearer " + jeton)).andExpect(status().isTooManyRequests());
        mvc.perform(post(base + "/localisation").header("Authorization", "Bearer " + acteurs.parent().jeton()))
                .andExpect(status().isNotFound());
        String id = corpsVerifie(messages(carte).get(0)).path("id").asString();
        assertThat(corpsVerifie(messages(carte).get(0)).path("cmd").asString()).isEqualTo("loc");

        // L'accusé d'un autre bracelet ne vaut rien ; celui du destinataire clôt la commande.
        reception.recevoir(Flux.ACK, voisin.numeroSerie(), octets("{\"id\":\"" + id + "\",\"ok\":true}"));
        assertThat(statut(id)).isEqualTo("EMISE");
        reception.recevoir(Flux.ACK, carte.numeroSerie(), octets("{\"id\":\"" + id + "\",\"ok\":true}"));
        assertThat(statut(id)).isEqualTo("ACCUSEE");
        reception.recevoir(Flux.ACK, carte.numeroSerie(), octets("{\"id\":\"" + id + "\",\"ok\":false}"));
        assertThat(statut(id)).as("un accusé ne se rejoue pas").isEqualTo("ACCUSEE");

        // Commande que la plateforme n'a pas émise et que le bracelet a rejetée : tentative journalisée.
        reception.recevoir(Flux.ACK, carte.numeroSerie(), octets("{\"id\":\"" + java.util.UUID.randomUUID() + "\",\"ok\":false}"));
        reception.recevoir(Flux.ACK, carte.numeroSerie(), octets("{\"id\":42}"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit.entree e JOIN dispositifs.bracelet b ON b.id::text = e.cible_id"
                + " WHERE b.numero_serie = ? AND e.action = 'COMMANDE_ETRANGERE_REJETEE' AND e.resultat = 'REFUS'", Integer.class,
                carte.numeroSerie())).isEqualTo(1);
    }

    @Test
    void uneCommandeSansAccuseEstReemiseALIdentiquePuisExpire() throws Exception {
        ParentAvecEnfant famille = acteurs.parentAvecEnfant();
        Carte carte = acteurs.equiper(famille);
        String jeton = famille.parent().jeton();

        // Mode économie : la configuration part vers le bracelet
        mvc.perform(put("/api/v1/enfants/" + famille.enfantId() + "/bracelet/configuration").header("Authorization", "Bearer " + jeton)
                .contentType(MediaType.APPLICATION_JSON).content("{\"modeEconomie\":true}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.modeEconomie").value(true));
        String message = messages(carte).get(0);
        JsonNode corps = corpsVerifie(message);
        assertThat(corps.path("cmd").asString()).isEqualTo("cfg");
        assertThat(corps.path("p").path("eco").asInt()).isEqualTo(1);
        assertThat(corps.path("p").path("int").asInt()).isEqualTo(900);
        assertThat(corps.path("p").path("alr").asInt()).isEqualTo(60);

        commandes.reemettre();
        assertThat(reemissions(carte)).as("moins de 30 secondes sans accusé").isZero();

        jdbc.update("UPDATE dispositifs.commande SET emise_le = now() - INTERVAL '31 seconds' WHERE id = ?::uuid", corps.path("id").asString());
        commandes.reemettre();
        assertThat(evenements.stream(CommandeAEnvoyer.class).filter(e -> e.numeroSerie().equals(carte.numeroSerie()))
                .map(CommandeAEnvoyer::message)).as("même message, même signature").containsOnly(message);
        assertThat(reemissions(carte)).isEqualTo(1);

        jdbc.update("UPDATE dispositifs.commande SET emise_le = now() - INTERVAL '16 minutes', expire_le = now() - INTERVAL '1 minute' WHERE id = ?::uuid",
                corps.path("id").asString());
        commandes.reemettre();
        assertThat(statut(corps.path("id").asString())).isEqualTo("EXPIREE");
        assertThat(reemissions(carte)).as("une commande expirée n'est plus émise").isEqualTo(1);
    }

    @Test
    void lAutorisationDeRetraitEstTransmiseAuBraceletPuisRefermee() throws Exception {
        ParentAvecEnfant famille = acteurs.parentAvecEnfant();
        Carte carte = acteurs.equiper(famille);
        String jeton = famille.parent().jeton();
        String retrait = "/api/v1/enfants/" + famille.enfantId() + "/bracelet/retrait";
        jdbc.update("DELETE FROM identite.code_usage_unique WHERE finalite = '2F_AUTORISER_RETRAIT'");
        mvc.perform(post("/api/v1/moi/second-facteur").header("Authorization", "Bearer " + jeton).contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"AUTORISER_RETRAIT\"}")).andExpect(status().isAccepted());
        String code = Acteurs.extraire(acteurs.dernierSms(famille.parent().telephone()), "(\\d{6}) est votre code de confirmation");

        mvc.perform(post(retrait).header("Authorization", "Bearer " + jeton).contentType(MediaType.APPLICATION_JSON)
                .content("{\"motif\":\"TOILETTE\",\"dureeMinutes\":30,\"codeSecondFacteur\":\"" + code + "\"}")).andExpect(status().isCreated());
        JsonNode ouverture = corpsVerifie(messages(carte).get(0));
        assertThat(ouverture.path("cmd").asString()).isEqualTo("rm");
        assertThat(ouverture.path("p").path("until").asLong()).isBetween(Instant.now().getEpochSecond() + 1700, Instant.now().getEpochSecond() + 1801);

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(retrait)
                .header("Authorization", "Bearer " + jeton)).andExpect(status().isNoContent());
        assertThat(corpsVerifie(messages(carte).get(1)).path("p").path("until").asLong()).isZero();
    }

    // -------------------------------------------------------------------- aides

    /** Messages signés émis vers ce bracelet, dans l'ordre. */
    private List<String> messages(Carte carte) {
        return jdbc.queryForList("SELECT c.message FROM dispositifs.commande c JOIN dispositifs.bracelet b ON b.id = c.bracelet_id"
                + " WHERE b.numero_serie = ? ORDER BY c.sequence", String.class, carte.numeroSerie());
    }

    /** Vérifie la signature comme le fera le bracelet, avec la seule clé publique, puis rend le corps. */
    private JsonNode corpsVerifie(String message) throws Exception {
        String[] parties = message.split("\\.");
        assertThat(parties).hasSize(2);
        byte[] contenu = Base64.getUrlDecoder().decode(parties[0]);
        byte[] signature = Base64.getUrlDecoder().decode(parties[1]);
        assertThat(signature).as("signature brute R‖S").hasSize(64);
        Signature verification = Signature.getInstance("SHA256withECDSAinP1363Format");
        verification.initVerify(CLE_COMMANDES.getPublic());
        verification.update(contenu);
        assertThat(verification.verify(signature)).as("signature de la plateforme").isTrue();
        // Un seul octet modifié invalide la commande.
        contenu[contenu.length - 2] ^= 1;
        verification.initVerify(CLE_COMMANDES.getPublic());
        verification.update(contenu);
        assertThat(verification.verify(signature)).isFalse();
        contenu[contenu.length - 2] ^= 1;
        return json.readTree(contenu);
    }

    private String statut(String id) {
        return jdbc.queryForObject("SELECT statut FROM dispositifs.commande WHERE id = ?::uuid", String.class, id);
    }

    /** Nombre de réémissions vues par les tests : événements publiés hors de l'émission initiale. */
    private long reemissions(Carte carte) {
        return evenements.stream(CommandeAEnvoyer.class).filter(e -> e.numeroSerie().equals(carte.numeroSerie())).count() - 1;
    }

    private static byte[] octets(String message) {
        return message.getBytes(StandardCharsets.UTF_8);
    }
}
