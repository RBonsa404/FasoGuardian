package bf.fasoguardian.identite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import bf.fasoguardian.TestIntegration;
import bf.fasoguardian.notifications.infrastructure.SmsBacASable;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/** Inscription d'un parent, connexion et rotation des sessions (US-PAR-001 en partie, US-PAR-019). */
class InscriptionEtSessionsIT extends TestIntegration {

    private static final AtomicInteger SUITE = new AtomicInteger(10_000);
    private static final String MOT_DE_PASSE = "soleil-de-ouaga-2026";
    private static final String CONSENTEMENTS = "[\"CONDITIONS_GENERALES\",\"DONNEES_ENFANT\"]";

    @Autowired
    MockMvc mvc;

    @Autowired
    SmsBacASable sms;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void unParentSInscritEnTroisEtapesEtSonCompteResteEnInstruction() throws Exception {
        String telephone = nouveauNumero();

        MvcResult fin = inscrire(telephone).andExpect(status().isCreated()).andReturn();

        Cookie cookie = fin.getResponse().getCookie("fg_rafraichissement");
        assertThat(cookie).isNotNull();
        assertThat(fin.getResponse().getHeader("Set-Cookie"))
                .contains("HttpOnly", "Secure", "SameSite=Strict", "Path=/api/v1/auth");
        mvc.perform(get("/api/v1/moi").header("Authorization", "Bearer " + jetonAcces(fin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("EN_INSTRUCTION"))
                .andExpect(jsonPath("$.roles[0]").value("PARENT"))
                .andExpect(jsonPath("$.telephoneMasque").value("+226 •• •• •• " + telephone.substring(6)));
    }

    @Test
    void leTelephoneEstChiffreEnBaseEtLeMotDePasseHacheEnArgon2id() throws Exception {
        String telephone = nouveauNumero();
        inscrire(telephone).andExpect(status().isCreated());

        var lignes = jdbc.queryForList(
                "SELECT telephone_chiffre, telephone_hash, mdp_argon2id FROM identite.utilisateur");
        assertThat(lignes).isNotEmpty().allSatisfy(ligne -> {
            assertThat(new String((byte[]) ligne.get("telephone_chiffre"), StandardCharsets.ISO_8859_1))
                    .doesNotContain(telephone);
            assertThat((String) ligne.get("telephone_hash")).hasSize(64).doesNotContain(telephone);
            assertThat((String) ligne.get("mdp_argon2id")).startsWith("$argon2id$");
        });
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM identite.consentement WHERE type = 'CONDITIONS_GENERALES' AND accorde", Long.class))
                .isPositive();
    }

    @Test
    void unCodeErroneEstRefusePuisEpuiseApresTroisEssais() throws Exception {
        String telephone = nouveauNumero();
        demanderCode(telephone).andExpect(status().isAccepted());
        String faux = codeRecu(telephone).equals("000000") ? "111111" : "000000";

        verifier(telephone, faux).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CODE_INCORRECT"));
        verifier(telephone, faux).andExpect(jsonPath("$.code").value("CODE_INCORRECT"));
        verifier(telephone, faux).andExpect(jsonPath("$.code").value("CODE_EPUISE"));
        verifier(telephone, codeRecu(telephone)).andExpect(jsonPath("$.code").value("CODE_EPUISE"));
    }

    @Test
    void unSecondCodeNePeutPasEtreDemandeAvantUneMinute() throws Exception {
        String telephone = nouveauNumero();
        demanderCode(telephone).andExpect(status().isAccepted());

        demanderCode(telephone).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TROP_DE_REQUETES"));
    }

    @Test
    void leCompteNEstPasCreeSansConsentementObligatoireNiAvecUnMotDePasseFaible() throws Exception {
        String telephone = nouveauNumero();
        demanderCode(telephone);
        String preuve = preuve(telephone);

        terminer(preuve, MOT_DE_PASSE, "[\"CONDITIONS_GENERALES\"]")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CONSENTEMENT_REQUIS"));
        terminer(preuve, "court1", CONSENTEMENTS)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MOT_DE_PASSE_REFUSE"));
        terminer("jeton-forge", MOT_DE_PASSE, CONSENTEMENTS)
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("SESSION_EXPIREE"));
    }

    @Test
    void unNumeroDejaInscritRecoitLaMemeReponseEtUnSmsDInformation() throws Exception {
        String telephone = nouveauNumero();
        inscrire(telephone).andExpect(status().isCreated());
        jdbc.update("UPDATE identite.code_usage_unique SET emis_le = emis_le - interval '2 minutes'");

        demanderCode(telephone).andExpect(status().isAccepted());

        assertThat(sms.dernierPour("+226" + telephone).orElseThrow().texte()).contains("un compte existe déjà");
    }

    @Test
    void laConnexionRepondDeLaMemeFaconPourUnMauvaisMotDePasseEtUnNumeroInconnu() throws Exception {
        String telephone = nouveauNumero();
        inscrire(telephone).andExpect(status().isCreated());

        connecter(telephone, MOT_DE_PASSE).andExpect(status().isOk()).andExpect(jsonPath("$.jetonAcces").isNotEmpty());
        connecter(telephone, "mauvais-mot-de-passe-1")
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("IDENTIFIANTS_INVALIDES"));
        connecter(nouveauNumero(), MOT_DE_PASSE)
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("IDENTIFIANTS_INVALIDES"));
    }

    @Test
    void cinqEchecsDeConnexionVerrouillentLeCompte() throws Exception {
        String telephone = nouveauNumero();
        inscrire(telephone).andExpect(status().isCreated());

        for (int essai = 0; essai < 5; essai++) {
            connecter(telephone, "mauvais-mot-de-passe-1").andExpect(status().isUnauthorized());
        }

        connecter(telephone, MOT_DE_PASSE).andExpect(status().isLocked())
                .andExpect(jsonPath("$.code").value("COMPTE_VERROUILLE"));
    }

    @Test
    void leRafraichissementFaitTournerLeJetonEtSaReutilisationRevoqueLaSession() throws Exception {
        MvcResult fin = inscrire(nouveauNumero()).andExpect(status().isCreated()).andReturn();
        Cookie premier = fin.getResponse().getCookie("fg_rafraichissement");

        MvcResult echange = rafraichir(premier).andExpect(status().isOk()).andReturn();
        Cookie second = echange.getResponse().getCookie("fg_rafraichissement");
        assertThat(second.getValue()).isNotEqualTo(premier.getValue());
        mvc.perform(get("/api/v1/moi").header("Authorization", "Bearer " + jetonAcces(echange))).andExpect(status().isOk());

        rafraichir(premier).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("SESSION_EXPIREE"));
        rafraichir(second).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("SESSION_EXPIREE"));
    }

    @Test
    void leRafraichissementExigeLEnteteAntiCsrfEtLaDeconnexionRevoqueLaSession() throws Exception {
        MvcResult fin = inscrire(nouveauNumero()).andExpect(status().isCreated()).andReturn();
        Cookie cookie = fin.getResponse().getCookie("fg_rafraichissement");

        mvc.perform(post("/api/v1/auth/rafraichir").cookie(cookie)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/auth/deconnexion").cookie(cookie).header("X-FG-Requete", "1"))
                .andExpect(status().isNoContent());
        rafraichir(cookie).andExpect(status().isUnauthorized());
    }

    @Test
    void uneApiProtegeeRefuseLAbsenceDeJetonEtUnJetonDUnAutreUsage() throws Exception {
        String telephone = nouveauNumero();
        demanderCode(telephone);
        String preuve = preuve(telephone);

        mvc.perform(get("/api/v1/moi")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("NON_AUTHENTIFIE"));
        mvc.perform(get("/api/v1/moi").header("Authorization", "Bearer " + preuve)).andExpect(status().isUnauthorized());
    }

    private ResultActions inscrire(String telephone) throws Exception {
        demanderCode(telephone).andExpect(status().isAccepted());
        return terminer(preuve(telephone), MOT_DE_PASSE, CONSENTEMENTS);
    }

    private ResultActions demanderCode(String telephone) throws Exception {
        return json("/api/v1/auth/inscription/numero", "{\"telephone\":\"" + telephone + "\"}");
    }

    private ResultActions verifier(String telephone, String code) throws Exception {
        return json("/api/v1/auth/inscription/code", "{\"telephone\":\"" + telephone + "\",\"code\":\"" + code + "\"}");
    }

    private String preuve(String telephone) throws Exception {
        String corps = verifier(telephone, codeRecu(telephone)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return extraire(corps, "\"preuve\":\"([^\"]+)\"");
    }

    private ResultActions terminer(String preuve, String motDePasse, String consentements) throws Exception {
        return json("/api/v1/auth/inscription/terminer", "{\"preuve\":\"" + preuve + "\",\"motDePasse\":\"" + motDePasse
                + "\",\"consentements\":" + consentements + "}");
    }

    private ResultActions connecter(String telephone, String motDePasse) throws Exception {
        return json("/api/v1/auth/connexion",
                "{\"telephone\":\"" + telephone + "\",\"motDePasse\":\"" + motDePasse + "\"}");
    }

    private ResultActions rafraichir(Cookie cookie) throws Exception {
        return mvc.perform(post("/api/v1/auth/rafraichir").cookie(cookie).header("X-FG-Requete", "1"));
    }

    private ResultActions json(String chemin, String corps) throws Exception {
        return mvc.perform(post(chemin).contentType(MediaType.APPLICATION_JSON).content(corps));
    }

    private String codeRecu(String telephone) {
        return extraire(sms.dernierPour("+226" + telephone).orElseThrow().texte(), "code est (\\d{6})");
    }

    private static String jetonAcces(MvcResult resultat) throws Exception {
        return extraire(resultat.getResponse().getContentAsString(), "\"jetonAcces\":\"([^\"]+)\"");
    }

    private static String extraire(String texte, String motif) {
        Matcher correspondance = Pattern.compile(motif).matcher(texte);
        assertThat(correspondance.find()).as("motif %s dans %s", motif, texte).isTrue();
        return correspondance.group(1);
    }

    private static String nouveauNumero() {
        return "70" + String.format("%06d", SUITE.incrementAndGet());
    }
}
