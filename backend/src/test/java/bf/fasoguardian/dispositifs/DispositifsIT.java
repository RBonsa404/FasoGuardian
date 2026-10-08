package bf.fasoguardian.dispositifs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicInteger;

import bf.fasoguardian.Acteurs;
import bf.fasoguardian.Acteurs.Parent;
import bf.fasoguardian.Acteurs.ParentAvecEnfant;
import bf.fasoguardian.TestIntegration;
import bf.fasoguardian.dispositifs.application.Appairages;
import bf.fasoguardian.notifications.infrastructure.SmsBacASable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Bracelets : appairage, perte, vol, configuration et parc (US-PAR-013, US-PAR-014, US-SAV-002). */
class DispositifsIT extends TestIntegration {

    private static final AtomicInteger SUITE = new AtomicInteger(4000);
    private static final SecureRandom ALEA = new SecureRandom();
    private static String jetonSav;

    /** Ce que l'atelier reçoit à l'enregistrement d'un bracelet. */
    private record Carte(String numeroSerie, String code, String jetonQr) {
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    SmsBacASable sms;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    Appairages appairages;

    Acteurs acteurs;

    @BeforeEach
    void preparer() throws Exception {
        acteurs = new Acteurs(mvc, sms);
        synchronized (DispositifsIT.class) {
            if (jetonSav == null) {
                jetonSav = acteurs.agent("[\"SAV\"]").jeton();
            }
        }
    }

    @Test
    void leCodeDAppairageAssocieLeBraceletALEnfantEtActiveSaPageQr() throws Exception {
        ParentAvecEnfant famille = acteurs.parentAvecEnfant();
        Carte carte = enregistrer();
        String base = "/api/v1/enfants/" + famille.enfantId() + "/bracelet";

        avec(get(base), famille.parent().jeton()).andExpect(status().isNotFound());
        assertThat(pageQr(carte.jetonQr())).contains("Ce code n'est pas reconnu");

        // La saisie tolère les minuscules et l'absence de tiret.
        json(post(base + "/appairage"), "{\"code\":\"" + carte.code().replace("-", "").toLowerCase() + "\"}",
                famille.parent().jeton())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numeroSerie").value(carte.numeroSerie()))
                .andExpect(jsonPath("$.statut").value("ACTIF"))
                .andExpect(jsonPath("$.modeEconomie").value(false))
                .andExpect(jsonPath("$.intervalleS").value(300))
                .andExpect(jsonPath("$.garantieJusquAu").isNotEmpty());

        assertThat(acteurs.dernierSms(famille.parent().telephone())).contains(carte.numeroSerie(), "est associé");
        assertThat(pageQr(carte.jetonQr())).contains(carte.numeroSerie(), "Vous avez trouvé un enfant");
        avec(get(base), famille.parent().jeton()).andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("ACTIF"));
        assertThat(actionsJournalisees(carte)).contains("BRACELET_ENREGISTRE", "BRACELET_APPAIRE");

        // Le code ne sert qu'une fois.
        ParentAvecEnfant autre = acteurs.parentAvecEnfant();
        json(post("/api/v1/enfants/" + autre.enfantId() + "/bracelet/appairage"), "{\"code\":\"" + carte.code() + "\"}",
                autre.parent().jeton())
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CODE_APPAIRAGE_INVALIDE"));
    }

    @Test
    void unEnfantNePorteQuUnBraceletEtLesEssaisDeCodeSontLimites() throws Exception {
        ParentAvecEnfant famille = acteurs.parentAvecEnfant();
        String base = "/api/v1/enfants/" + famille.enfantId() + "/bracelet";
        associer(famille, enregistrer());

        json(post(base + "/appairage"), "{\"code\":\"" + enregistrer().code() + "\"}", famille.parent().jeton())
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ENFANT_DEJA_EQUIPE"));

        for (int i = 0; i < Appairages.ESSAIS_PAR_QUART_HEURE; i++) {
            json(post(base + "/appairage"), "{\"code\":\"ZZZZ-ZZ" + (2 + i) + "\"}", famille.parent().jeton())
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CODE_APPAIRAGE_INVALIDE"));
        }
        json(post(base + "/appairage"), "{\"code\":\"ZZZZ-ZZ9\"}", famille.parent().jeton())
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void leVolDesactiveLaPagePubliqueEtRevoqueLeCertificat() throws Exception {
        ParentAvecEnfant famille = acteurs.parentAvecEnfant();
        Carte carte = enregistrer();
        associer(famille, carte);
        String base = "/api/v1/enfants/" + famille.enfantId() + "/bracelet";

        json(post(base + "/declaration"), "{\"motif\":\"VOLE\"}", famille.parent().jeton())
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("SECOND_FACTEUR_REQUIS"));
        assertThat(pageQr(carte.jetonQr())).contains("Vous avez trouvé un enfant");

        json(post(base + "/declaration"), "{\"motif\":\"VOLE\",\"codeSecondFacteur\":\"" + codeSms(famille.parent()) + "\"}",
                famille.parent().jeton())
                .andExpect(status().isOk()).andExpect(jsonPath("$.statut").value("VOLE"));

        assertThat(pageQr(carte.jetonQr())).contains("Ce bracelet n'est plus actif").doesNotContain("Vous avez trouvé un enfant");
        avec(get("/api/v1/console/parc/certificats-revoques"), jetonSav).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.numeroSerie == '" + carte.numeroSerie() + "')]").isNotEmpty());
        avec(get(base), famille.parent().jeton()).andExpect(status().isNotFound());
        assertThat(acteurs.dernierSms(famille.parent().telephone())).contains("déclaré volé");
        assertThat(actionsJournalisees(carte)).contains("BRACELET_DECLARE_VOLE");
    }

    @Test
    void laPerteMaintientLeSuivi72HeuresPuisRevoqueLeCertificat() throws Exception {
        ParentAvecEnfant famille = acteurs.parentAvecEnfant();
        Carte carte = enregistrer();
        associer(famille, carte);
        String base = "/api/v1/enfants/" + famille.enfantId() + "/bracelet";

        declarer(famille, "PERDU").andExpect(status().isOk()).andExpect(jsonPath("$.statut").value("PERDU"))
                .andExpect(jsonPath("$.suiviJusquAu").isNotEmpty());
        assertThat(pageQr(carte.jetonQr())).contains("Ce bracelet n'est plus actif");
        assertThat(certificatRevoque(carte)).isFalse();

        // Retrouvé pendant le suivi : tout redevient actif.
        avec(post(base + "/retrouve"), famille.parent().jeton()).andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("ACTIF"));
        assertThat(pageQr(carte.jetonQr())).contains("Vous avez trouvé un enfant");

        declarer(famille, "PERDU").andExpect(status().isOk());
        jdbc.update("UPDATE dispositifs.bracelet SET suivi_jusqu_au = now() - INTERVAL '1 minute' WHERE numero_serie = ?",
                carte.numeroSerie());
        appairages.cloreSuivisEchus();

        assertThat(certificatRevoque(carte)).isTrue();
        avec(get(base), famille.parent().jeton()).andExpect(status().isNotFound());
        assertThat(pageQr(carte.jetonQr())).contains("Ce bracelet n'est plus actif");

        // L'enfant peut alors recevoir un bracelet de remplacement.
        Carte remplacement = enregistrer();
        associer(famille, remplacement);
        assertThat(pageQr(remplacement.jetonQr())).contains(remplacement.numeroSerie());
    }

    @Test
    void uneUniteRetourneeApparaitEnSavPuisRepartEnStockAvecUnNouveauCode() throws Exception {
        ParentAvecEnfant famille = acteurs.parentAvecEnfant();
        Carte carte = enregistrer();
        associer(famille, carte);
        String unite = "/api/v1/console/parc/" + carte.numeroSerie();

        avec(post(unite + "/retour"), jetonSav).andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("EN_SAV")).andExpect(jsonPath("$.appaire").value(false));
        avec(get("/api/v1/console/parc").param("statut", "EN_SAV"), jetonSav).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.numeroSerie == '" + carte.numeroSerie() + "')]").isNotEmpty());
        avec(get("/api/v1/enfants/" + famille.enfantId() + "/bracelet"), famille.parent().jeton())
                .andExpect(status().isNotFound());
        assertThat(pageQr(carte.jetonQr())).contains("Ce code n'est pas reconnu");

        String corps = json(post(unite + "/remise-en-stock"), "{}", jetonSav).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String nouveauCode = Acteurs.extraire(corps, "\"codeAppairage\":\"([A-Z0-9-]{8})\"");
        assertThat(nouveauCode).isNotEqualTo(carte.code());

        // L'unité reconditionnée équipe un autre enfant : le même QR gravé ouvre désormais sa page.
        ParentAvecEnfant suivante = acteurs.parentAvecEnfant();
        associer(suivante, new Carte(carte.numeroSerie(), nouveauCode, carte.jetonQr()));
        assertThat(pageQr(carte.jetonQr())).contains(carte.numeroSerie(), "Vous avez trouvé un enfant");

        avec(get(unite), jetonSav).andExpect(status().isOk())
                .andExpect(jsonPath("$.appairages.length()").value(2))
                .andExpect(jsonPath("$.appairages[1].motifFin").value("PANNE"))
                .andExpect(jsonPath("$.imeiMasque").value(org.hamcrest.Matchers.startsWith("•••••••••••")))
                .andExpect(jsonPath("$.appairages[0].enfantId").doesNotExist());
        avec(post(unite + "/reforme"), jetonSav).andExpect(status().isConflict());
    }

    @Test
    void leModeEconomieEtLeDesappairageSontNotifiesAuxTuteurs() throws Exception {
        ParentAvecEnfant famille = acteurs.parentAvecEnfant();
        Carte carte = enregistrer();
        associer(famille, carte);
        String base = "/api/v1/enfants/" + famille.enfantId() + "/bracelet";

        json(put(base + "/configuration"), "{\"modeEconomie\":true}", famille.parent().jeton())
                .andExpect(status().isOk()).andExpect(jsonPath("$.modeEconomie").value(true))
                .andExpect(jsonPath("$.intervalleS").value(900));
        assertThat(acteurs.dernierSms(famille.parent().telephone())).contains("mode économie", "activé");

        avec(delete(base), famille.parent().jeton()).andExpect(status().isNoContent());
        assertThat(acteurs.dernierSms(famille.parent().telephone())).contains("n'est plus associé");
        avec(get(base), famille.parent().jeton()).andExpect(status().isNotFound());
        assertThat(pageQr(carte.jetonQr())).contains("Ce code n'est pas reconnu");
        avec(get("/api/v1/console/parc/" + carte.numeroSerie()), jetonSav)
                .andExpect(jsonPath("$.bracelet.statut").value("EN_SAV"));
    }

    @Test
    void leBraceletDUnEnfantEtLeParcSontFermesAuxAutres() throws Exception {
        ParentAvecEnfant famille = acteurs.parentAvecEnfant();
        Carte carte = enregistrer();
        associer(famille, carte);
        Parent etranger = acteurs.parent();
        String base = "/api/v1/enfants/" + famille.enfantId() + "/bracelet";

        avec(get(base), etranger.jeton()).andExpect(status().isNotFound());
        json(post(base + "/appairage"), "{\"code\":\"" + enregistrer().code() + "\"}", etranger.jeton())
                .andExpect(status().isNotFound());
        json(post(base + "/declaration"), "{\"motif\":\"VOLE\"}", etranger.jeton()).andExpect(status().isNotFound());
        avec(delete(base), etranger.jeton()).andExpect(status().isNotFound());
        avec(get(base), null).andExpect(status().isUnauthorized());
        avec(get(base), jetonSav).andExpect(status().isForbidden());

        avec(get("/api/v1/console/parc"), famille.parent().jeton()).andExpect(status().isForbidden());
        avec(get("/api/v1/console/parc"), acteurs.agent("[\"KYC\"]").jeton()).andExpect(status().isForbidden());
        avec(get("/api/v1/console/parc"), null).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit.entree WHERE resultat = 'REFUS' AND cible_id = ?",
                Integer.class, famille.enfantId())).isGreaterThanOrEqualTo(4);
    }

    @Test
    void lEnregistrementRefuseLesSaisiesInvalidesEtLesDoublons() throws Exception {
        Carte carte = enregistrer();
        String certificat = empreinteAleatoire();

        enregistrer(carte.numeroSerie(), imeiAleatoire(), certificat).andExpect(status().isConflict());
        enregistrer("2291", imeiAleatoire(), certificat).andExpect(status().isBadRequest());
        enregistrer("FG-" + SUITE.incrementAndGet(), "123456789012345", certificat).andExpect(status().isBadRequest());
        enregistrer("FG-" + SUITE.incrementAndGet(), imeiAleatoire(), "abc").andExpect(status().isBadRequest());
        // Ni l'IMEI ni le code d'appairage ne sont conservés en clair.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM dispositifs.bracelet WHERE code_appairage_empreinte = ?",
                Integer.class, carte.code().replace("-", ""))).isZero();
    }

    // -------------------------------------------------------------------- aides

    private Carte enregistrer() throws Exception {
        String numero = "FG-" + SUITE.incrementAndGet();
        String corps = enregistrer(numero, imeiAleatoire(), empreinteAleatoire()).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return new Carte(numero, Acteurs.extraire(corps, "\"codeAppairage\":\"([A-Z0-9-]{8})\""),
                Acteurs.extraire(corps, "\"jetonQr\":\"([A-Za-z0-9_-]{22})\""));
    }

    private ResultActions enregistrer(String numero, String imei, String certificat) throws Exception {
        return json(post("/api/v1/console/parc"), "{\"numeroSerie\":\"" + numero + "\",\"imei\":\"" + imei
                + "\",\"revisionMaterielle\":\"V1\",\"versionLogiciel\":\"2.4.1\",\"empreinteCertificat\":\"" + certificat
                + "\"}", jetonSav);
    }

    private void associer(ParentAvecEnfant famille, Carte carte) throws Exception {
        json(post("/api/v1/enfants/" + famille.enfantId() + "/bracelet/appairage"), "{\"code\":\"" + carte.code() + "\"}",
                famille.parent().jeton()).andExpect(status().isOk());
    }

    private ResultActions declarer(ParentAvecEnfant famille, String motif) throws Exception {
        return json(post("/api/v1/enfants/" + famille.enfantId() + "/bracelet/declaration"),
                "{\"motif\":\"" + motif + "\",\"codeSecondFacteur\":\"" + codeSms(famille.parent()) + "\"}",
                famille.parent().jeton());
    }

    private String codeSms(Parent parent) throws Exception {
        // Deux codes de même finalité ne peuvent pas être demandés à moins d'une minute d'intervalle.
        jdbc.update("DELETE FROM identite.code_usage_unique WHERE finalite = '2F_DECLARER_BRACELET'");
        json(post("/api/v1/moi/second-facteur"), "{\"action\":\"DECLARER_BRACELET\"}", parent.jeton())
                .andExpect(status().isAccepted());
        return Acteurs.extraire(acteurs.dernierSms(parent.telephone()), "(\\d{6}) est votre code de confirmation");
    }

    private String pageQr(String jeton) throws Exception {
        return mvc.perform(get("/q/" + jeton).with(requete -> {
            requete.setRemoteAddr("198.51.100." + (1 + ALEA.nextInt(250)));
            return requete;
        })).andReturn().getResponse().getContentAsString();
    }

    private boolean certificatRevoque(Carte carte) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT certificat_revoque_le IS NOT NULL FROM dispositifs.bracelet WHERE numero_serie = ?", Boolean.class,
                carte.numeroSerie()));
    }

    private java.util.List<String> actionsJournalisees(Carte carte) {
        return jdbc.queryForList("SELECT e.action FROM audit.entree e JOIN dispositifs.bracelet b ON b.id::text = e.cible_id"
                + " WHERE b.numero_serie = ?", String.class, carte.numeroSerie());
    }

    private ResultActions avec(MockHttpServletRequestBuilder requete, String jeton) throws Exception {
        return mvc.perform(jeton == null ? requete : requete.header("Authorization", "Bearer " + jeton));
    }

    private ResultActions json(MockHttpServletRequestBuilder requete, String corps, String jeton) throws Exception {
        return avec(requete.contentType(MediaType.APPLICATION_JSON).content(corps), jeton);
    }

    private static String empreinteAleatoire() {
        byte[] octets = new byte[32];
        ALEA.nextBytes(octets);
        return HexFormat.of().formatHex(octets);
    }

    /** IMEI fictif de 15 chiffres à clé de Luhn valide (préfixe 35 réservé aux essais de ce dépôt). */
    private static String imeiAleatoire() {
        StringBuilder chiffres = new StringBuilder("35");
        for (int i = 0; i < 12; i++) {
            chiffres.append(ALEA.nextInt(10));
        }
        int somme = 0;
        for (int i = 0; i < 14; i++) {
            int chiffre = chiffres.charAt(13 - i) - '0';
            if (i % 2 == 0) {
                chiffre *= 2;
                if (chiffre > 9) {
                    chiffre -= 9;
                }
            }
            somme += chiffre;
        }
        return chiffres.append((10 - somme % 10) % 10).toString();
    }
}
