package bf.fasoguardian.telemetrie;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.concurrent.ThreadLocalRandom;

import bf.fasoguardian.Acteurs;
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

/** Repli SMS du bracelet : alerte signée, signature invalide rejetée et journalisée (US-SYS-001). */
class SmsDeRepliIT extends TestIntegration {

    private static final String ENTRANT = "/api/v1/public/sms/entrant";

    @Autowired
    MockMvc mvc;

    @Autowired
    SmsBacASable sms;

    @Autowired
    JdbcTemplate jdbc;

    Acteurs acteurs;
    KeyPair cleDuBracelet;

    @BeforeEach
    void preparer() throws Exception {
        acteurs = new Acteurs(mvc, sms);
        KeyPairGenerator generateur = KeyPairGenerator.getInstance("EC");
        generateur.initialize(new ECGenParameterSpec("secp256r1"));
        cleDuBracelet = generateur.generateKeyPair();
    }

    @Test
    void uneAlerteEnvoyeeParSmsSigneEstTraiteeCommeUnMessageDuBracelet() throws Exception {
        ParentAvecEnfant famille = acteurs.parentAvecEnfant();
        String bracelet = equiper(famille, true);
        long t = Instant.now().getEpochSecond() - 30;
        String texte = signer("FG1|" + bracelet + "|ALR|SOS|12.37140,-1.51970|G|76|" + t + "|");

        remettre(texte).andExpect(status().isNoContent());

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> mvc.perform(get("/api/v1/alertes?enCours=true")
                .header("Authorization", "Bearer " + famille.parent().jeton())).andExpect(jsonPath("$[0].type").value("SOS"))
                .andExpect(jsonPath("$[0].latitude").value(12.3714)));
        acteurs.attendreSms(famille.parent().telephone(), "SOS");
        assertThat(evenements(bracelet)).isEqualTo(1);
        assertThat(journal("ALERTE_RECUE_PAR_SMS", bracelet)).isEqualTo(1);

        // Le même SMS remis deux fois par la passerelle ne crée pas un second événement.
        remettre(texte).andExpect(status().isNoContent());
        assertThat(evenements(bracelet)).isEqualTo(1);
    }

    @Test
    void unSmsDontLaSignatureEstInvalideEstRejeteEtJournalise() throws Exception {
        ParentAvecEnfant famille = acteurs.parentAvecEnfant();
        String bracelet = equiper(famille, true);
        long t = Instant.now().getEpochSecond() - 30;
        String valide = signer("FG1|" + bracelet + "|ALR|SOS|12.37140,-1.51970|G|76|" + t + "|");

        // Contenu modifié après signature, signature d'une autre clé, signature absente, format inconnu.
        remettre(valide.replace("12.37140", "12.99999")).andExpect(status().isNoContent());
        KeyPair cleDuBon = cleDuBracelet;
        preparer();
        remettre(signer("FG1|" + bracelet + "|ALR|SOS|12.37140,-1.51970|G|76|" + t + "|")).andExpect(status().isNoContent());
        cleDuBracelet = cleDuBon;
        remettre("FG1|" + bracelet + "|ALR|SOS|12.37140,-1.51970|G|76|" + t + "|").andExpect(status().isNoContent());
        remettre("Bonjour, rappelle-moi").andExpect(status().isNoContent());

        assertThat(evenements(bracelet)).isZero();
        assertThat(journal("SMS_BRACELET_REJETE", bracelet)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM alertes.alerte WHERE enfant_id = ?::uuid", Integer.class,
                famille.enfantId())).isZero();
    }

    @Test
    void sansCleEnregistreeOuSansSceauDeLaPasserelleRienNEstAccepte() throws Exception {
        ParentAvecEnfant famille = acteurs.parentAvecEnfant();
        String sansCle = equiper(famille, false);
        long t = Instant.now().getEpochSecond() - 30;
        String texte = signer("FG1|" + sansCle + "|ALR|SOS|12.37140,-1.51970|G|76|" + t + "|");

        remettre(texte).andExpect(status().isNoContent());
        assertThat(evenements(sansCle)).isZero();
        assertThat(journal("SMS_BRACELET_REJETE", sansCle)).isEqualTo(1);

        // L'appel doit venir de la passerelle : sans sceau, ou avec le sceau d'un autre corps, il est refusé.
        byte[] corps = corps(texte);
        mvc.perform(post(ENTRANT).contentType(MediaType.APPLICATION_JSON).content(corps)).andExpect(status().isForbidden());
        String autre = new SceauWebhook(SECRET_PASSERELLE_SMS, "essai").sceller(corps("autre"), Instant.now());
        mvc.perform(post(ENTRANT).contentType(MediaType.APPLICATION_JSON).content(corps).header("X-FG-Signature", autre))
                .andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit.entree WHERE action = 'APPEL_PASSERELLE_SMS_REJETE'",
                Integer.class)).isGreaterThanOrEqualTo(2);
    }

    // -------------------------------------------------------------------- aides

    /** Enregistre un bracelet au parc, avec ou sans sa clé publique, et l'appaire à l'enfant ; rend son numéro. */
    private String equiper(ParentAvecEnfant famille, boolean avecCle) throws Exception {
        String numero = "FG-" + ThreadLocalRandom.current().nextInt(20_000_000, 60_000_000);
        byte[] empreinte = new byte[32];
        ThreadLocalRandom.current().nextBytes(empreinte);
        String cle = avecCle ? ",\"clePublique\":\"" + Base64.getEncoder().encodeToString(cleDuBracelet.getPublic().getEncoded()) + "\"" : "";
        String carte = mvc.perform(post("/api/v1/console/parc").header("Authorization", "Bearer " + acteurs.jetonSav())
                .contentType(MediaType.APPLICATION_JSON).content("{\"numeroSerie\":\"" + numero + "\",\"imei\":\"" + imei()
                        + "\",\"revisionMaterielle\":\"V1\",\"versionLogiciel\":\"2.4.1\",\"empreinteCertificat\":\""
                        + HexFormat.of().formatHex(empreinte) + "\"" + cle + "}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        mvc.perform(post("/api/v1/enfants/" + famille.enfantId() + "/bracelet/appairage")
                .header("Authorization", "Bearer " + famille.parent().jeton()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + Acteurs.extraire(carte, "\"codeAppairage\":\"([A-Z0-9-]{8})\"") + "\"}"))
                .andExpect(status().isOk());
        jdbc.update("UPDATE dispositifs.appairage SET debut = now() - INTERVAL '1 hour' WHERE enfant_id = ?::uuid", famille.enfantId());
        return numero;
    }

    /** Signe comme l'élément sécurisé du bracelet : ECDSA P-256, R‖S, sur tout le texte qui précède la signature. */
    private String signer(String texte) throws Exception {
        Signature signature = Signature.getInstance("SHA256withECDSAinP1363Format");
        signature.initSign(cleDuBracelet.getPrivate());
        signature.update(texte.getBytes(StandardCharsets.UTF_8));
        return texte + Base64.getUrlEncoder().withoutPadding().encodeToString(signature.sign());
    }

    /** Remise par la passerelle, avec son sceau. */
    private ResultActions remettre(String texte) throws Exception {
        byte[] corps = corps(texte);
        return mvc.perform(post(ENTRANT).contentType(MediaType.APPLICATION_JSON).content(corps)
                .header("X-FG-Signature", new SceauWebhook(SECRET_PASSERELLE_SMS, "essai").sceller(corps, Instant.now())));
    }

    private static byte[] corps(String texte) {
        return ("{\"de\":\"+22670000000\",\"texte\":\"" + texte + "\"}").getBytes(StandardCharsets.UTF_8);
    }

    private int evenements(String bracelet) {
        return jdbc.queryForObject("SELECT count(*) FROM telemetrie.evenement e JOIN dispositifs.bracelet b ON b.id = e.bracelet_id"
                + " WHERE b.numero_serie = ?", Integer.class, bracelet);
    }

    private int journal(String action, String bracelet) {
        return jdbc.queryForObject("SELECT count(*) FROM audit.entree WHERE action = ? AND cible_id = ?", Integer.class, action, bracelet);
    }

    /** IMEI fictif de 15 chiffres à clé de Luhn valide. */
    private static String imei() {
        int[] chiffres = new int[14];
        chiffres[0] = 3;
        chiffres[1] = 5;
        for (int i = 2; i < 14; i++) {
            chiffres[i] = ThreadLocalRandom.current().nextInt(10);
        }
        int somme = 0;
        StringBuilder imei = new StringBuilder();
        for (int i = 0; i < 14; i++) {
            int valeur = i % 2 == 1 ? chiffres[i] * 2 : chiffres[i];
            somme += valeur > 9 ? valeur - 9 : valeur;
            imei.append(chiffres[i]);
        }
        return imei.append((10 - somme % 10) % 10).toString();
    }
}
