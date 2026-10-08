package bf.fasoguardian.famille;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import bf.fasoguardian.Acteurs;
import bf.fasoguardian.Acteurs.ParentAvecEnfant;
import bf.fasoguardian.TestIntegration;
import bf.fasoguardian.famille.application.PagePubliqueQr;
import bf.fasoguardian.notifications.infrastructure.SmsBacASable;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Page publique QR (US-TRS-001, US-SYS-010, REQ-SYS-014 ; minimisation renforcée de l'ADR 0002). */
class PagePubliqueQrIT extends TestIntegration {

    private static final AtomicInteger SOURCES = new AtomicInteger(10);

    @Autowired
    MockMvc mvc;

    @Autowired
    SmsBacASable sms;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ProfilsQr profils;

    @Test
    void laPageNAfficheQueLeNumeroDuBraceletLesInformationsCritiquesEtLesContactsVisibles() throws Exception {
        Acteurs acteurs = new Acteurs(mvc, sms);
        ParentAvecEnfant famille = acteurs.parentAvecEnfant();
        String jeton = equiper(famille, "FG-2291");
        String ip = nouvelleSource();

        String page = scanner(jeton, ip).andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/html;charset=UTF-8"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Content-Security-Policy", org.hamcrest.Matchers.containsString("default-src 'none'")))
                .andReturn().getResponse().getContentAsString();

        assertThat(page).contains("FG-2291", "Vous avez trouvé un enfant", "Arachide", "Allergie", "Groupe sanguin", "O+",
                "tel:+22676112233", "Tante", "tel:17", "tel:18", "/q/" + jeton + "/prevenir");
        // Jamais de nom, de prénom, de date de naissance, d'élément non critique, de contact non visible, d'identifiant.
        assertThat(page).doesNotContain("Awa", "Ouédraogo", "2018", "Ventoline", "Fatou", "70990011", "Voisin",
                famille.enfantId(), "<script", "[[", "<fg-etat", "<fg-si", "<fg-pour", "<base", "n'est pas reconnu", "n'est plus actif");

        assertThat(jdbc.queryForMap("""
                SELECT resultat, ip_pseudonymisee FROM famille.consultation_qr WHERE enfant_id = ?::uuid
                """, famille.enfantId())).satisfies(ligne -> {
            assertThat(ligne.get("resultat")).isEqualTo("TROUVE");
            assertThat((String) ligne.get("ip_pseudonymisee")).hasSize(32).doesNotContain(ip);
        });
        Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(acteurs.dernierSms(famille.parent().telephone())).contains("FG-2291 vient d'être scanné")
                        .doesNotContain("Awa"));
    }

    @Test
    void unJetonInconnuOuMalFormeRecoitLaMemePageGeneriqueDansLeMemeDelai() throws Exception {
        String ip = nouvelleSource();

        long debut = System.nanoTime();
        String inconnu = scanner(nouveauJeton(), ip).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long dureeInconnu = System.nanoTime() - debut;
        String malForme = scanner("abc", ip).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(inconnu).contains("Ce code n'est pas reconnu", "tel:17").doesNotContain("Informations médicales", "Prévenir la famille", "[[", "<fg-etat", "<fg-si")
                .doesNotContainPattern("FG-[0-9]");
        assertThat(malForme).isEqualTo(inconnu);
        assertThat(Duration.ofNanos(dureeInconnu)).isGreaterThanOrEqualTo(Duration.ofMillis(80));
    }

    @Test
    void unBraceletDeclarePerduAfficheLaPageDesactiveeSansInformationDeLEnfant() throws Exception {
        ParentAvecEnfant famille = new Acteurs(mvc, sms).parentAvecEnfant();
        String jeton = equiper(famille, "FG-3105");

        profils.suspendre(UUID.fromString(famille.enfantId()));
        String page = scanner(jeton, nouvelleSource()).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(page).contains("Ce bracelet n'est plus actif", "FG-3105", "tel:17")
                .doesNotContain("Arachide", "tel:+226", "Prévenir la famille");

        profils.reactiver(UUID.fromString(famille.enfantId()));
        assertThat(scanner(jeton, nouvelleSource()).andReturn().getResponse().getContentAsString()).contains("Arachide");
    }

    @Test
    void auDelaDeVingtJetonsInvalidesParMinuteLaSourceEstBloqueeEtLAdministrateurAverti() throws Exception {
        ParentAvecEnfant famille = new Acteurs(mvc, sms).parentAvecEnfant();
        String jetonValide = equiper(famille, "FG-4410");
        String attaquant = nouvelleSource();

        for (int essai = 0; essai < PagePubliqueQr.INVALIDES_PAR_MINUTE; essai++) {
            scanner(nouveauJeton(), attaquant).andExpect(status().isOk());
        }
        scanner(nouveauJeton(), attaquant).andExpect(status().isOk());

        // Bloquée : même un jeton valide ne répond plus à cette source.
        String page = scanner(jetonValide, attaquant).andExpect(status().isTooManyRequests())
                .andReturn().getResponse().getContentAsString();
        assertThat(page).doesNotContain("FG-4410", "Arachide");
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM audit.entree WHERE action = 'ENUMERATION_QR' AND resultat = 'REFUS'", Long.class))
                .isEqualTo(1);

        // Une autre source n'est pas affectée.
        scanner(jetonValide, nouvelleSource()).andExpect(status().isOk());
    }

    @Test
    void leTiersLaisseUnMessageChiffreQueLaFamilleLiraDansLApplication() throws Exception {
        Acteurs acteurs = new Acteurs(mvc, sms);
        ParentAvecEnfant famille = acteurs.parentAvecEnfant();
        String jeton = equiper(famille, "FG-5520");
        String ip = nouvelleSource();

        String erreur = prevenir(jeton, ip, "12", "Marché").andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(erreur).contains("Saisissez un numéro mobile à 8 chiffres.", "Vous avez trouvé un enfant");

        String succes = prevenir(jeton, ip, "70 44 55 66", "Devant la pharmacie MARQUEUR-LIEU <b>")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(succes).contains("La famille a été prévenue", "FG-5520", "tel:+22676112233")
                .doesNotContain("70445566", "MARQUEUR-LIEU");

        assertThat(jdbc.queryForObject("""
                SELECT encode(telephone_chiffre || lieu_chiffre, 'escape') FROM famille.signalement_tiers WHERE enfant_id = ?::uuid
                """, String.class, famille.enfantId())).doesNotContain("70445566", "MARQUEUR");
        Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(acteurs.dernierSms(famille.parent().telephone())).contains("vous a laissé un message")
                        .doesNotContain("70445566", "pharmacie"));

        prevenir(jeton, ip, "70445566", null).andExpect(status().isOk());
        prevenir(jeton, ip, "70445566", null).andExpect(status().isOk());
        prevenir(jeton, ip, "70445566", null).andExpect(status().isTooManyRequests());
        prevenir(nouveauJeton(), ip, "70445566", null).andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString()).contains("Ce code n'est pas reconnu"));
    }

    /** Renseigne la fiche médicale et les contacts, puis rattache un jeton de bracelet ; renvoie le jeton en clair. */
    private String equiper(ParentAvecEnfant famille, String numero) throws Exception {
        String jetonAcces = famille.parent().jeton();
        String base = "/api/v1/enfants/" + famille.enfantId();
        mvc.perform(put(base + "/sante").header("Authorization", "Bearer " + jetonAcces)
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"groupeSanguin":"O+","groupeSanguinSurQr":true,"elements":[
                          {"type":"ALLERGIE","libelle":"Arachide","critique":true},
                          {"type":"TRAITEMENT","libelle":"Ventoline au besoin","critique":false}]}
                        """)).andExpect(status().isOk());
        contact(base, jetonAcces, "Tante", "Fatou", "76112233", true);
        contact(base, jetonAcces, "Voisin", "Paul", "70990011", false);
        String jeton = nouveauJeton();
        profils.associer(UUID.fromString(famille.enfantId()), PagePubliqueQr.sha256(jeton), numero);
        return jeton;
    }

    private void contact(String base, String jetonAcces, String lien, String nom, String telephone, boolean visible)
            throws Exception {
        mvc.perform(post(base + "/contacts").header("Authorization", "Bearer " + jetonAcces)
                .contentType(MediaType.APPLICATION_JSON).content("{\"lien\":\"" + lien + "\",\"nom\":\"" + nom
                        + "\",\"telephone\":\"" + telephone + "\",\"visibleSurQr\":" + visible + "}"))
                .andExpect(status().isCreated());
    }

    private ResultActions scanner(String jeton, String ip) throws Exception {
        return mvc.perform(depuis(get("/q/" + jeton), ip));
    }

    private ResultActions prevenir(String jeton, String ip, String telephone, String lieu) throws Exception {
        MockHttpServletRequestBuilder requete = post("/q/" + jeton + "/prevenir")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED).param("telephone", telephone);
        return mvc.perform(depuis(lieu == null ? requete : requete.param("lieu", lieu), ip));
    }

    private static MockHttpServletRequestBuilder depuis(MockHttpServletRequestBuilder requete, String ip) {
        return requete.with(servlet -> {
            servlet.setRemoteAddr(ip);
            return servlet;
        });
    }

    private static String nouvelleSource() {
        return "203.0.113." + SOURCES.incrementAndGet();
    }

    private static String nouveauJeton() {
        byte[] octets = new byte[16];
        new SecureRandom().nextBytes(octets);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(octets);
    }
}
