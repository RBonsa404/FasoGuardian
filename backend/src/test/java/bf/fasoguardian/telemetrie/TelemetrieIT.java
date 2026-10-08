package bf.fasoguardian.telemetrie;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

import bf.fasoguardian.Acteurs;
import bf.fasoguardian.Acteurs.Carte;
import bf.fasoguardian.Acteurs.ParentAvecEnfant;
import bf.fasoguardian.TestIntegration;
import bf.fasoguardian.notifications.infrastructure.SmsBacASable;
import bf.fasoguardian.telemetrie.application.Ingestion;
import bf.fasoguardian.telemetrie.application.Ingestion.Resultat;
import bf.fasoguardian.telemetrie.application.Positions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Télémétrie : réception des messages des bracelets et dernière position (US-PAR-006, US-SYS-002, US-SYS-003). */
@RecordApplicationEvents
class TelemetrieIT extends TestIntegration {

    @Autowired
    MockMvc mvc;

    @Autowired
    SmsBacASable sms;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    Ingestion ingestion;

    @Autowired
    Positions positions;

    @Autowired
    ApplicationEvents evenements;

    Acteurs acteurs;

    @BeforeEach
    void preparer() {
        acteurs = new Acteurs(mvc, sms);
    }

    @Test
    void laDernierePositionEstRestitueeAvecSonHorodatageSaPrecisionEtLEtatDuBracelet() throws Exception {
        ParentAvecEnfant famille = acteurs.parentAvecEnfant();
        Carte carte = equiper(famille);
        long maintenant = Instant.now().getEpochSecond();

        position(famille).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.numeroSerie").value(carte.numeroSerie()))
                .andExpect(jsonPath("$.position").doesNotExist()).andExpect(jsonPath("$.etat").doesNotExist());

        assertThat(recevoir(carte, mesure(maintenant - 60, 41, 12.3714, -1.5197, "\"bat\":76,\"rssi\":-79,\"net\":\"4g\",\"op\":\"Orange BF\",\"mv\":1")))
                .isEqualTo(Resultat.ACCEPTE);

        position(famille).andExpect(status().isOk())
                .andExpect(jsonPath("$.position.latitude").value(12.3714))
                .andExpect(jsonPath("$.position.longitude").value(-1.5197))
                .andExpect(jsonPath("$.position.precisionM").value(8))
                .andExpect(jsonPath("$.position.source").value("GNSS"))
                .andExpect(jsonPath("$.position.mesureeLe").value(Instant.ofEpochSecond(maintenant - 60).toString()))
                .andExpect(jsonPath("$.etat.batterie").value(76))
                .andExpect(jsonPath("$.etat.signalDbm").value(-79))
                .andExpect(jsonPath("$.etat.reseau").value("4G"))
                .andExpect(jsonPath("$.etat.operateur").value("Orange BF"))
                .andExpect(jsonPath("$.etat.enLigne").value(true));

        PositionRecue recue = evenements.stream(PositionRecue.class).reduce((a, b) -> b).orElseThrow();
        assertThat(recue.enfantId()).isEqualTo(UUID.fromString(famille.enfantId()));
        assertThat(recue.latitude()).isCloseTo(12.3714, within(1e-6));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit.entree WHERE action = 'POSITION_CONSULTEE' AND cible_id = ?",
                Integer.class, famille.enfantId())).isEqualTo(2);
    }

    @Test
    void unMessageRejoueEstIgnoreEtUnePositionTamponneeGardeSonHeureDOrigine() throws Exception {
        ParentAvecEnfant famille = acteurs.parentAvecEnfant();
        Carte carte = equiper(famille);
        long maintenant = Instant.now().getEpochSecond();
        String recente = mesure(maintenant - 30, 12, 12.40, -1.50, "\"bat\":60");

        assertThat(recevoir(carte, recente)).isEqualTo(Resultat.ACCEPTE);
        assertThat(recevoir(carte, recente)).isEqualTo(Resultat.DOUBLON);
        // Au retour du réseau, le bracelet vide son tampon : des positions plus anciennes arrivent après.
        assertThat(recevoir(carte, mesure(maintenant - 3600, 10, 12.30, -1.60, "\"bat\":64"))).isEqualTo(Resultat.ACCEPTE);
        assertThat(recevoir(carte, mesure(maintenant - 1800, 11, 12.35, -1.55, "\"bat\":62"))).isEqualTo(Resultat.ACCEPTE);

        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM telemetrie.position p JOIN dispositifs.bracelet b ON b.id = p.bracelet_id
                WHERE b.numero_serie = ? AND p.recue_le > p.mesuree_le""", Integer.class, carte.numeroSerie())).isEqualTo(3);
        position(famille).andExpect(jsonPath("$.position.latitude").value(12.4))
                .andExpect(jsonPath("$.position.mesureeLe").value(Instant.ofEpochSecond(maintenant - 30).toString()));
        assertThat(evenements.stream(PositionRecue.class).filter(e -> e.enfantId().toString().equals(famille.enfantId())))
                .hasSize(3);
    }

    @Test
    void lesMessagesDUnAppareilNonAutoriseOuMalFormesSontEcartes() throws Exception {
        ParentAvecEnfant famille = acteurs.parentAvecEnfant();
        Carte carte = equiper(famille);
        long maintenant = Instant.now().getEpochSecond();
        String valide = mesure(maintenant, 1, 12.37, -1.52, "\"bat\":50");

        assertThat(ingestion.telemetrie("FG-0000", octets(valide))).isEqualTo(Resultat.APPAREIL_REFUSE);
        assertThat(recevoir(acteurs.braceletAuParc(), valide)).as("bracelet en stock, non appairé").isEqualTo(Resultat.APPAREIL_REFUSE);

        assertThat(recevoir(carte, "pas du json")).isEqualTo(Resultat.INVALIDE);
        assertThat(recevoir(carte, "[1,2]")).isEqualTo(Resultat.INVALIDE);
        assertThat(recevoir(carte, mesure(maintenant, 2, 91.0, -1.52, "\"bat\":50"))).isEqualTo(Resultat.INVALIDE);
        assertThat(recevoir(carte, mesure(maintenant, 2, 0.0, 0.0, "\"bat\":50"))).isEqualTo(Resultat.INVALIDE);
        assertThat(recevoir(carte, mesure(maintenant + 3600, 2, 12.37, -1.52, "\"bat\":50"))).isEqualTo(Resultat.INVALIDE);
        assertThat(recevoir(carte, mesure(maintenant - 40L * 86_400, 2, 12.37, -1.52, "\"bat\":50"))).isEqualTo(Resultat.INVALIDE);
        assertThat(recevoir(carte, mesure(maintenant, 2, 12.37, -1.52, "\"bat\":50,\"x\":\"" + "z".repeat(600) + "\""))).isEqualTo(Resultat.INVALIDE);
        assertThat(recevoir(carte, "{\"t\":" + maintenant + ",\"seq\":3,\"lat\":12.37,\"lon\":-1.52,\"src\":\"radar\"}"))
                .isEqualTo(Resultat.INVALIDE);
        position(famille).andExpect(jsonPath("$.position").doesNotExist());

        // Volé : le certificat est révoqué, plus rien n'est accepté de cet appareil.
        mvc.perform(post("/api/v1/moi/second-facteur").header("Authorization", "Bearer " + famille.parent().jeton())
                .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"DECLARER_BRACELET\"}")).andExpect(status().isAccepted());
        String code = Acteurs.extraire(acteurs.dernierSms(famille.parent().telephone()), "(\\d{6}) est votre code de confirmation");
        mvc.perform(post("/api/v1/enfants/" + famille.enfantId() + "/bracelet/declaration")
                .header("Authorization", "Bearer " + famille.parent().jeton()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"motif\":\"VOLE\",\"codeSecondFacteur\":\"" + code + "\"}")).andExpect(status().isOk());
        assertThat(recevoir(carte, valide)).isEqualTo(Resultat.APPAREIL_REFUSE);
        assertThat(ingestion.alerte(carte.numeroSerie(), octets("{\"t\":" + maintenant + ",\"seq\":9,\"ev\":\"sos\"}")))
                .isEqualTo(Resultat.APPAREIL_REFUSE);
    }

    @Test
    void lesEvenementsEtLEtatDuBraceletSontEnregistres() throws Exception {
        ParentAvecEnfant famille = acteurs.parentAvecEnfant();
        Carte carte = equiper(famille);
        long maintenant = Instant.now().getEpochSecond();
        String sos = "{\"t\":" + maintenant + ",\"seq\":77,\"ev\":\"sos\",\"lat\":12.3714,\"lon\":-1.5197,\"acc\":15}";

        assertThat(ingestion.alerte(carte.numeroSerie(), octets(sos))).isEqualTo(Resultat.ACCEPTE);
        assertThat(ingestion.alerte(carte.numeroSerie(), octets(sos))).isEqualTo(Resultat.DOUBLON);
        assertThat(ingestion.alerte(carte.numeroSerie(), octets("{\"t\":" + maintenant + ",\"seq\":78,\"ev\":\"strap\"}")))
                .as("événement sans position").isEqualTo(Resultat.ACCEPTE);
        assertThat(ingestion.alerte(carte.numeroSerie(), octets("{\"t\":" + maintenant + ",\"seq\":79,\"ev\":\"inconnu\"}")))
                .isEqualTo(Resultat.INVALIDE);

        assertThat(evenements.stream(EvenementBraceletRecu.class)
                .filter(e -> e.enfantId().toString().equals(famille.enfantId())).map(EvenementBraceletRecu::type))
                .containsExactly(EvenementBraceletRecu.Type.SOS, EvenementBraceletRecu.Type.COUPURE_BOUCLE);

        assertThat(ingestion.etat(carte.numeroSerie(), octets("{\"online\":false,\"fw\":\"2.4.1\"}"))).isEqualTo(Resultat.ACCEPTE);
        assertThat(ingestion.etat(carte.numeroSerie(), octets("{\"fw\":\"2.4.1\"}"))).isEqualTo(Resultat.INVALIDE);
        position(famille).andExpect(jsonPath("$.etat.enLigne").value(false))
                .andExpect(jsonPath("$.etat.versionLogiciel").value("2.4.1"));
    }

    @Test
    void laPositionEstFermeeAuxComptesNonRattachesEtLeRefusEstJournalise() throws Exception {
        ParentAvecEnfant famille = acteurs.parentAvecEnfant();
        equiper(famille);
        String chemin = "/api/v1/enfants/" + famille.enfantId() + "/position";

        mvc.perform(get(chemin).header("Authorization", "Bearer " + acteurs.parent().jeton())).andExpect(status().isNotFound());
        mvc.perform(get(chemin)).andExpect(status().isUnauthorized());
        mvc.perform(get(chemin).header("Authorization", "Bearer " + acteurs.jetonSav())).andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit.entree WHERE resultat = 'REFUS' AND cible_id = ?",
                Integer.class, famille.enfantId())).isEqualTo(1);
        // Sans bracelet, il n'y a pas de position à montrer.
        ParentAvecEnfant sansBracelet = acteurs.parentAvecEnfant();
        position(sansBracelet).andExpect(status().isNotFound());
    }

    @Test
    void unBraceletReconditionneNeMontreJamaisLesPositionsDeLEnfantPrecedent() throws Exception {
        ParentAvecEnfant premiere = acteurs.parentAvecEnfant();
        Carte carte = equiper(premiere);
        long maintenant = Instant.now().getEpochSecond();
        assertThat(recevoir(carte, mesure(maintenant - 120, 5, 12.31, -1.61, "\"bat\":80"))).isEqualTo(Resultat.ACCEPTE);
        position(premiere).andExpect(jsonPath("$.position.latitude").value(12.31));

        mvc.perform(delete("/api/v1/enfants/" + premiere.enfantId() + "/bracelet")
                .header("Authorization", "Bearer " + premiere.parent().jeton())).andExpect(status().isNoContent());
        String corps = mvc.perform(post("/api/v1/console/parc/" + carte.numeroSerie() + "/remise-en-stock")
                .header("Authorization", "Bearer " + acteurs.jetonSav()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        ParentAvecEnfant seconde = acteurs.parentAvecEnfant();
        mvc.perform(post("/api/v1/enfants/" + seconde.enfantId() + "/bracelet/appairage")
                .header("Authorization", "Bearer " + seconde.parent().jeton()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + Acteurs.extraire(corps, "\"codeAppairage\":\"([A-Z0-9-]{8})\"") + "\"}"))
                .andExpect(status().isOk());

        position(seconde).andExpect(status().isOk()).andExpect(jsonPath("$.position").doesNotExist())
                .andExpect(jsonPath("$.etat").doesNotExist());
        // Une mesure antérieure au nouvel appairage, restée dans le tampon du bracelet, n'est pas enregistrée.
        assertThat(recevoir(carte, mesure(maintenant - 60, 6, 12.32, -1.62, "\"bat\":79"))).isEqualTo(Resultat.INVALIDE);
        assertThat(recevoir(carte, mesure(Instant.now().getEpochSecond() + 1, 7, 12.33, -1.63, "\"bat\":78")))
                .isEqualTo(Resultat.ACCEPTE);
        position(seconde).andExpect(jsonPath("$.position.latitude").value(12.33));
        position(premiere).andExpect(status().isNotFound());
    }

    @Test
    void lEntretienPrepareLesPartitionsEtEffaceLesPositionsAuDelaDeLaConservation() throws Exception {
        ParentAvecEnfant famille = acteurs.parentAvecEnfant();
        Carte carte = equiper(famille);
        long maintenant = Instant.now().getEpochSecond();
        assertThat(recevoir(carte, mesure(maintenant - 86_400, 1, 12.37, -1.52, "\"bat\":50"))).isEqualTo(Resultat.ACCEPTE);
        // Position vieille de 31 jours, écrite directement : l'ingestion ne l'accepterait plus.
        jdbc.update("""
                INSERT INTO telemetrie.position (id, mesuree_le, bracelet_id, point, precision_m, source, sequence, recue_le)
                SELECT gen_random_uuid(), now() - INTERVAL '31 days', id, ST_SetSRID(ST_MakePoint(-1.5, 12.3), 4326)::geography,
                       10, 'GNSS', 0, now() - INTERVAL '31 days'
                FROM dispositifs.bracelet WHERE numero_serie = ?""", carte.numeroSerie());

        positions.entretenir();

        assertThat(jdbc.queryForList("""
                SELECT p.sequence FROM telemetrie.position p JOIN dispositifs.bracelet b ON b.id = p.bracelet_id
                WHERE b.numero_serie = ?""", Long.class, carte.numeroSerie())).containsExactly(1L);
        String dansDeuxMois = "position_" + LocalDate.now(ZoneOffset.UTC).plusMonths(2).format(DateTimeFormatter.ofPattern("yyyy_MM"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_tables WHERE schemaname = 'telemetrie' AND tablename = ?",
                Integer.class, dansDeuxMois)).isEqualTo(1);
    }

    // -------------------------------------------------------------------- aides

    /** Bracelet appairé depuis plus d'un mois : les mesures passées des essais appartiennent bien à l'enfant. */
    private Carte equiper(ParentAvecEnfant famille) throws Exception {
        Carte carte = acteurs.equiper(famille);
        jdbc.update("UPDATE dispositifs.appairage SET debut = now() - INTERVAL '35 days' WHERE enfant_id = ?::uuid",
                famille.enfantId());
        return carte;
    }

    private Resultat recevoir(Carte carte, String message) {
        return ingestion.telemetrie(carte.numeroSerie(), octets(message));
    }

    private ResultActions position(ParentAvecEnfant famille) throws Exception {
        return mvc.perform(get("/api/v1/enfants/" + famille.enfantId() + "/position")
                .header("Authorization", "Bearer " + famille.parent().jeton()));
    }

    /** Message de télémétrie au format compact de FG-DOC-08 §8.2. */
    private static String mesure(long t, long sequence, double latitude, double longitude, String suite) {
        return String.format(Locale.ROOT, "{\"t\":%d,\"seq\":%d,\"lat\":%.5f,\"lon\":%.5f,\"acc\":8,\"src\":\"gnss\",%s}", t,
                sequence, latitude, longitude, suite);
    }

    private static byte[] octets(String message) {
        return message.getBytes(StandardCharsets.UTF_8);
    }
}
