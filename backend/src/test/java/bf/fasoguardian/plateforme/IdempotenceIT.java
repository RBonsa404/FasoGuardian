package bf.fasoguardian.plateforme;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import bf.fasoguardian.Acteurs;
import bf.fasoguardian.Acteurs.ParentAvecEnfant;
import bf.fasoguardian.TestIntegration;
import bf.fasoguardian.notifications.infrastructure.SmsBacASable;
import bf.fasoguardian.plateforme.idempotence.DepotIdempotence;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Actions rejouées hors ligne (FG-DOC-06 §5.4, US-SYS-002) : une même clé n'applique l'écriture qu'une fois. */
class IdempotenceIT extends TestIntegration {

    private static final String CONTACT = "{\"lien\":\"Tante\",\"nom\":\"Fatou\",\"telephone\":\"76 11 22 33\",\"visibleSurQr\":false}";

    @Autowired
    MockMvc mvc;

    @Autowired
    SmsBacASable sms;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    DepotIdempotence depot;

    @Test
    void uneEcritureRejoueeAvecLaMemeCleNeSAppliqueQuUneFois() throws Exception {
        ParentAvecEnfant famille = new Acteurs(mvc, sms).parentAvecEnfant();
        String base = "/api/v1/enfants/" + famille.enfantId() + "/contacts";
        String cle = "a1b2c3d4-e5f6-4711-8899-aabbccddeeff";

        creer(base, famille, cle, CONTACT).andExpect(status().isCreated()).andExpect(header().doesNotExist("Idempotent-Replayed"));
        // Le téléphone n'a pas reçu la réponse : il renvoie la même action, avec la même clé.
        creer(base, famille, cle, CONTACT).andExpect(status().isCreated()).andExpect(header().string("Idempotent-Replayed", "true"));
        assertThat(contacts(famille)).isEqualTo(1);

        // Une autre action ne peut pas réutiliser la clé ; une clé neuve crée bien un second contact.
        creer(base, famille, cle, CONTACT.replace("Fatou", "Awa")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLIT"));
        creer(base, famille, "f0e1d2c3-b4a5-4697-8877-665544332211", CONTACT).andExpect(status().isCreated());
        assertThat(contacts(famille)).isEqualTo(2);

        creer(base, famille, "trop-courte", CONTACT).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUETE_INVALIDE"));
        // Rien du corps n'est gardé : seuls le statut et l'empreinte de la requête.
        assertThat(jdbc.queryForObject("SELECT statut FROM plateforme.requete_idempotente WHERE cle = ?", Integer.class, cle))
                .isEqualTo(201);
    }

    @Test
    void lesClesSontRangeesParCompteEtOublieesAuBoutDeVingtQuatreHeures() throws Exception {
        Acteurs acteurs = new Acteurs(mvc, sms);
        ParentAvecEnfant premiere = acteurs.parentAvecEnfant();
        ParentAvecEnfant seconde = acteurs.parentAvecEnfant();
        String cle = "0123456789abcdef-partagee";

        creer("/api/v1/enfants/" + premiere.enfantId() + "/contacts", premiere, cle, CONTACT).andExpect(status().isCreated());
        // La même clé, choisie par un autre compte, ne rejoue rien : chaque compte a ses clés.
        creer("/api/v1/enfants/" + seconde.enfantId() + "/contacts", seconde, cle, CONTACT).andExpect(status().isCreated())
                .andExpect(header().doesNotExist("Idempotent-Replayed"));
        assertThat(contacts(seconde)).isEqualTo(1);

        jdbc.update("UPDATE plateforme.requete_idempotente SET creee_le = ? WHERE cle = ?",
                Timestamp.from(Instant.now().minus(25, ChronoUnit.HOURS)), cle);
        depot.purger();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM plateforme.requete_idempotente WHERE cle = ?", Long.class, cle))
                .isZero();
    }

    private ResultActions creer(String base, ParentAvecEnfant famille, String cle, String corps) throws Exception {
        return mvc.perform(post(base).header("Authorization", "Bearer " + famille.parent().jeton())
                .header("Idempotency-Key", cle).contentType(MediaType.APPLICATION_JSON).content(corps));
    }

    private long contacts(ParentAvecEnfant famille) {
        return jdbc.queryForObject("SELECT count(*) FROM famille.contact_urgence WHERE enfant_id = ?::uuid", Long.class,
                famille.enfantId());
    }
}
