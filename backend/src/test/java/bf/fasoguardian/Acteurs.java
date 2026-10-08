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
