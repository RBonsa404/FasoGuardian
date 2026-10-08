package bf.fasoguardian;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import bf.fasoguardian.identite.domaine.Totp;
import bf.fasoguardian.notifications.infrastructure.SmsBacASable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Fabrique les acteurs des tests d'intégration en passant par les vraies API : parents inscrits,
 * agents créés par l'administrateur et connectés avec leur second facteur.
 */
public final class Acteurs {

    public static final String MOT_DE_PASSE_PARENT = "soleil-de-ouaga-2026";
    public static final String MOT_DE_PASSE_AGENT = "phrase-de-passe-agent-2026";
    private static final AtomicInteger SUITE = new AtomicInteger(500_000);
    private static final java.security.SecureRandom ALEA = new java.security.SecureRandom();
    // L'administrateur de test ne se connecte qu'une fois : un code TOTP ne se rejoue pas.
    private static String jetonAdmin;

    public record Parent(String telephone, String jeton) {
    }

    public record Agent(String identifiant, String jeton) {
    }

    private final MockMvc mvc;
    private final SmsBacASable sms;

    public Acteurs(MockMvc mvc, SmsBacASable sms) {
        this.mvc = mvc;
        this.sms = sms;
    }

    public Parent parent() throws Exception {
        String telephone = "75" + String.format("%06d", SUITE.incrementAndGet());
        json("/api/v1/auth/inscription/numero", "{\"telephone\":\"" + telephone + "\"}").andExpect(status().isAccepted());
        String code = extraire(dernierSms(telephone), "code est (\\d{6})");
        String preuve = extraire(json("/api/v1/auth/inscription/code",
                "{\"telephone\":\"" + telephone + "\",\"code\":\"" + code + "\"}").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "\"preuve\":\"([^\"]+)\"");
        String session = json("/api/v1/auth/inscription/terminer", "{\"preuve\":\"" + preuve + "\",\"motDePasse\":\""
                + MOT_DE_PASSE_PARENT + "\",\"consentements\":[\"CONDITIONS_GENERALES\",\"DONNEES_ENFANT\"]}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return new Parent(telephone, extraire(session, "\"jetonAcces\":\"([^\"]+)\""));
    }

    public record ParentAvecEnfant(Parent parent, String enfantId) {
    }

    private static String jetonKyc;

    /**
     * Parent dont le dossier KYC (en point d'inscription, enfant « Awa Ouédraogo ») vient d'être approuvé :
     * la fiche de l'enfant existe et le lien de tutelle est actif.
     */
    public ParentAvecEnfant parentAvecEnfant() throws Exception {
        Parent parent = parent();
        String dossier = extraire(mvc.perform(post("/api/v1/kyc/dossiers").header("Authorization", "Bearer " + parent.jeton())
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"canal":"POINT_INSCRIPTION","natureLien":"PARENT",
                         "demandeur":{"nom":"Ouédraogo","prenoms":"Mariam","typePiece":"CNIB","numeroPiece":"B12345678"},
                         "enfant":{"prenom":"Awa","nom":"Ouédraogo","dateNaissance":"2018-03-14"}}
                        """)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(),
                "\"id\":\"([0-9a-f-]{36})\"");
        mvc.perform(post("/api/v1/kyc/dossiers/" + dossier + "/depot").header("Authorization", "Bearer " + parent.jeton()))
                .andExpect(status().isOk());
        String kyc;
        synchronized (Acteurs.class) {
            if (jetonKyc == null) {
                jetonKyc = agent("[\"KYC\"]").jeton();
            }
            kyc = jetonKyc;
        }
        String base = "/api/v1/console/kyc/dossiers/" + dossier;
        mvc.perform(post(base + "/prise-en-charge").header("Authorization", "Bearer " + kyc)).andExpect(status().isOk());
        mvc.perform(post(base + "/decision").header("Authorization", "Bearer " + kyc)
                .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"APPROUVER\"}")).andExpect(status().isOk());
        // La fiche est créée par un écouteur asynchrone de l'événement DossierKycApprouve.
        String[] enfantId = new String[1];
        org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(10)).untilAsserted(() -> {
            String corps = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                    .get("/api/v1/enfants").header("Authorization", "Bearer " + parent.jeton()))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            enfantId[0] = extraire(corps, "\"id\":\"([0-9a-f-]{36})\"");
        });
        return new ParentAvecEnfant(parent, enfantId[0]);
    }

    /** Carte d'activation d'un bracelet enregistré au parc. */
    public record Carte(String numeroSerie, String code, String jetonQr) {
    }

    private static String jetonSav;

    public String jetonSav() throws Exception {
        synchronized (Acteurs.class) {
            if (jetonSav == null) {
                jetonSav = agent("[\"SAV\"]").jeton();
            }
            return jetonSav;
        }
    }

    /** Enregistre au parc un bracelet neuf (IMEI et certificat fictifs). */
    public Carte braceletAuParc() throws Exception {
        String numero = "FG-" + (70_000 + SUITE.incrementAndGet() % 10_000_000);
        byte[] empreinte = new byte[32];
        ALEA.nextBytes(empreinte);
        String corps = mvc.perform(post("/api/v1/console/parc").header("Authorization", "Bearer " + jetonSav())
                .contentType(MediaType.APPLICATION_JSON).content("{\"numeroSerie\":\"" + numero + "\",\"imei\":\""
                        + imeiFictif() + "\",\"revisionMaterielle\":\"V1\",\"versionLogiciel\":\"2.4.1\","
                        + "\"empreinteCertificat\":\"" + java.util.HexFormat.of().formatHex(empreinte) + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return new Carte(numero, extraire(corps, "\"codeAppairage\":\"([A-Z0-9-]{8})\""),
                extraire(corps, "\"jetonQr\":\"([A-Za-z0-9_-]{22})\""));
    }

    /** Appaire un bracelet neuf à l'enfant de la famille ; renvoie sa carte d'activation. */
    public Carte equiper(ParentAvecEnfant famille) throws Exception {
        Carte carte = braceletAuParc();
        mvc.perform(post("/api/v1/enfants/" + famille.enfantId() + "/bracelet/appairage")
                .header("Authorization", "Bearer " + famille.parent().jeton())
                .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"" + carte.code() + "\"}"))
                .andExpect(status().isOk());
        return carte;
    }

    /** IMEI fictif de 15 chiffres à clé de Luhn valide. */
    private static String imeiFictif() {
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

    /** Crée un agent aux rôles donnés (tableau JSON, par exemple {@code ["KYC"]}) sans le connecter. */
    public String creerAgent(String roles) throws Exception {
        String identifiant = "agent.essai." + SUITE.incrementAndGet();
        creerAgent(identifiant, MOT_DE_PASSE_AGENT, roles).andExpect(status().isCreated());
        return identifiant;
    }

    public ResultActions creerAgent(String identifiant, String motDePasse, String roles) throws Exception {
        return mvc.perform(post("/api/v1/admin/agents").header("Authorization", "Bearer " + jetonAdmin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"identifiant\":\"" + identifiant + "\",\"motDePasseProvisoire\":\"" + motDePasse
                        + "\",\"roles\":" + roles + "}"));
    }

    /** Agent créé, second facteur activé, connecté. */
    public Agent agent(String roles) throws Exception {
        String identifiant = creerAgent(roles);
        return new Agent(identifiant, activerEtConnecter(identifiant, MOT_DE_PASSE_AGENT));
    }

    public ResultActions connecterAgent(String identifiant, String motDePasse, String code) throws Exception {
        return json("/api/v1/auth/agents/connexion", "{\"identifiant\":\"" + identifiant + "\",\"motDePasse\":\""
                + motDePasse + "\"" + (code == null ? "" : ",\"codeTotp\":\"" + code + "\"") + "}");
    }

    public String activerEtConnecter(String identifiant, String motDePasse) throws Exception {
        String corps = connecterAgent(identifiant, motDePasse, null).andExpect(status().isForbidden())
                .andReturn().getResponse().getContentAsString();
        byte[] secret = Totp.depuisBase32(extraire(corps, "\"secretTotp\":\"([A-Z2-7]+)\""));
        String session = connecterAgent(identifiant, motDePasse,
                Totp.code(secret, Instant.now().getEpochSecond() / Totp.PAS_SECONDES))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return extraire(session, "\"jetonAcces\":\"([^\"]+)\"");
    }

    public String jetonAdmin() throws Exception {
        synchronized (Acteurs.class) {
            if (jetonAdmin == null) {
                jetonAdmin = activerEtConnecter(TestIntegration.ADMIN_IDENTIFIANT, TestIntegration.ADMIN_MOT_DE_PASSE);
            }
            return jetonAdmin;
        }
    }

    public String dernierSms(String telephone) {
        return sms.dernierPour("+226" + telephone).orElseThrow().texte();
    }

    private ResultActions json(String chemin, String corps) throws Exception {
        return mvc.perform(post(chemin).contentType(MediaType.APPLICATION_JSON).content(corps));
    }

    public static String extraire(String texte, String motif) {
        Matcher correspondance = Pattern.compile(motif).matcher(texte);
        assertThat(correspondance.find()).as("motif %s dans %s", motif, texte).isTrue();
        return correspondance.group(1);
    }
}
