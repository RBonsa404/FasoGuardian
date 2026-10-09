package bf.fasoguardian.notifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import bf.fasoguardian.Acteurs;
import bf.fasoguardian.Acteurs.Carte;
import bf.fasoguardian.Acteurs.Parent;
import bf.fasoguardian.Acteurs.ParentAvecEnfant;
import bf.fasoguardian.TestIntegration;
import bf.fasoguardian.notifications.application.ServiceNotifications;
import bf.fasoguardian.notifications.infrastructure.SmsBacASable;
import bf.fasoguardian.telemetrie.application.ReceptionMessages;
import bf.fasoguardian.telemetrie.application.ReceptionMessages.Flux;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Notifications push et repli SMS (US-ENF-001, US-ENF-002, REQ-MUST-11). Le service de push est un serveur
 * HTTP local, et le navigateur est joué par le test : il déchiffre le contenu avec sa seule clé privée
 * (RFC 8291) et vérifie le jeton du serveur d'application (RFC 8292).
 */
class NotificationsIT extends TestIntegration {

    /** Requête reçue par le service de push. */
    private record Livraison(String chemin, String encodage, String urgence, String dureeDeVie, String autorisation, byte[] corps) {
    }

    /** Navigateur abonné : ses clés ne quittent pas le test. */
    private record Navigateur(String chemin, KeyPair cles, byte[] secret) {
    }

    private static final Base64.Encoder BASE64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DEBASE64 = Base64.getUrlDecoder();
    private static final AtomicInteger SEQUENCE = new AtomicInteger(1);
    private static final List<Livraison> livraisons = new CopyOnWriteArrayList<>();
    /** Chemins pour lesquels le service répond « abonnement inconnu ». */
    private static final List<String> perimes = new CopyOnWriteArrayList<>();
    private static HttpServer servicePush;

    @Autowired
    MockMvc mvc;

    @Autowired
    SmsBacASable sms;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ReceptionMessages reception;

    @Autowired
    ServiceNotifications notifications;

    @Autowired
    JsonMapper json;

    Acteurs acteurs;

    @BeforeAll
    static void demarrerLeServiceDePush() throws IOException {
        servicePush = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servicePush.createContext("/push/", echange -> {
            String chemin = echange.getRequestURI().getPath();
            livraisons.add(new Livraison(chemin, echange.getRequestHeaders().getFirst("Content-Encoding"),
                    echange.getRequestHeaders().getFirst("Urgency"), echange.getRequestHeaders().getFirst("TTL"),
                    echange.getRequestHeaders().getFirst("Authorization"), echange.getRequestBody().readAllBytes()));
            echange.sendResponseHeaders(perimes.contains(chemin) ? 410 : 201, -1);
            echange.close();
        });
        servicePush.start();
    }

    @AfterAll
    static void arreterLeServiceDePush() {
        servicePush.stop(0);
    }

    @BeforeEach
    void preparer() {
        acteurs = new Acteurs(mvc, sms);
    }

    @Test
    void uneAlerteImportanteEstPousseeChiffreePuisDoubleeParSmsApres60SecondesSansAccuse() throws Exception {
        ParentAvecEnfant famille = acteurs.parentAvecEnfant();
        Carte carte = acteurs.equiper(famille);
        Parent parent = famille.parent();
        Navigateur navigateur = abonner(parent);
        int smsAvant = smsDe(parent).size();

        evenement(carte, "batcrit");
        Livraison livraison = attendreLivraison(navigateur, 1);

        assertThat(livraison.encodage()).isEqualTo("aes128gcm");
        assertThat(livraison.urgence()).isEqualTo("high");
        assertThat(Integer.parseInt(livraison.dureeDeVie())).isPositive();
        verifierJetonVapid(livraison.autorisation());
        JsonNode contenu = json.readTree(dechiffrer(livraison.corps(), navigateur));
        assertThat(contenu.path("titre").asString()).isEqualTo("Batterie critique");
        assertThat(contenu.path("texte").asString()).startsWith("La batterie du bracelet de votre enfant est critique.");
        assertThat(contenu.path("lien").asString()).matches("/alertes/[0-9a-f-]{36}");
        assertThat(contenu.path("critique").asBoolean()).isFalse();
        // Le contenu circule chiffré : le service de push ne peut pas le lire.
        assertThat(new String(livraison.corps(), StandardCharsets.ISO_8859_1)).doesNotContain("batterie", "alertes");

        notifications.replierParSms();
        assertThat(smsDe(parent)).as("moins de 60 secondes : pas encore de SMS").hasSize(smsAvant);

        jdbc.update("UPDATE notifications.notification SET creee_le = now() - INTERVAL '61 seconds' WHERE id = ?::uuid",
                contenu.path("id").asString());
        notifications.replierParSms();
        assertThat(smsDe(parent)).hasSize(smsAvant + 1);
        assertThat(smsDe(parent).get(smsAvant)).isEqualTo("FasoGuardian : la batterie du bracelet de votre enfant est critique. Ouvrez l'application.");
        notifications.replierParSms();
        assertThat(smsDe(parent)).as("un seul SMS de repli").hasSize(smsAvant + 1);
    }

    @Test
    void lAccuseDuNavigateurOuLaPriseEnChargeDeLAlerteAnnuleLeRepliSms() throws Exception {
        ParentAvecEnfant famille = acteurs.parentAvecEnfant();
        Carte carte = acteurs.equiper(famille);
        Parent parent = famille.parent();
        Navigateur navigateur = abonner(parent);

        // Le navigateur accuse réception, sans session, par l'identifiant reçu dans le push.
        evenement(carte, "batcrit");
        String batterie = json.readTree(dechiffrer(attendreLivraison(navigateur, 1).corps(), navigateur)).path("id").asString();
        mvc.perform(post("/api/v1/public/notifications/" + batterie + "/accuse")).andExpect(status().isNoContent());
        // Même réponse pour un identifiant inconnu : l'adresse ne renseigne pas sur l'existence d'une notification.
        mvc.perform(post("/api/v1/public/notifications/" + UUID.randomUUID() + "/accuse")).andExpect(status().isNoContent());

        // Une chute : le parent la prend en charge dans l'application avant le délai.
        evenement(carte, "fall");
        JsonNode chute = json.readTree(dechiffrer(attendreLivraison(navigateur, 2).corps(), navigateur));
        mvc.perform(post(chute.path("lien").asString().replace("/alertes/", "/api/v1/alertes/") + "/acquittement")
                .header("Authorization", "Bearer " + parent.jeton())).andExpect(status().isOk());

        int smsAvant = smsDe(parent).size();
        jdbc.update("UPDATE notifications.notification SET creee_le = now() - INTERVAL '5 minutes' WHERE id IN (?::uuid, ?::uuid)",
                batterie, chute.path("id").asString());
        notifications.replierParSms();
        assertThat(smsDe(parent)).hasSize(smsAvant);
    }

    @Test
    void uneAlerteCritiquePartParPushEtParSmsEtUneInformationParPushSeulement() throws Exception {
        ParentAvecEnfant famille = acteurs.parentAvecEnfant();
        Parent parent = famille.parent();
        Navigateur navigateur = abonner(parent);
        int smsAvant = smsDe(parent).size();

        // Information : le bracelet est associé.
        Carte carte = acteurs.equiper(famille);
        JsonNode association = json.readTree(dechiffrer(attendreLivraison(navigateur, 1).corps(), navigateur));
        assertThat(association.path("titre").asString()).isEqualTo("Bracelet associé");
        assertThat(association.path("lien").asString()).isEqualTo("/enfants/" + famille.enfantId() + "/bracelet");
        assertThat(livraisonsVers(navigateur).get(0).urgence()).isEqualTo("normal");
        jdbc.update("UPDATE notifications.notification SET creee_le = now() - INTERVAL '5 minutes' WHERE id = ?::uuid",
                association.path("id").asString());
        notifications.replierParSms();
        assertThat(smsDe(parent)).as("une information poussée n'appelle aucun SMS").hasSize(smsAvant);

        // Critique : SOS.
        evenement(carte, "sos");
        JsonNode sos = json.readTree(dechiffrer(attendreLivraison(navigateur, 2).corps(), navigateur));
        assertThat(sos.path("titre").asString()).isEqualTo("Alerte SOS");
        assertThat(sos.path("critique").asBoolean()).isTrue();
        acteurs.attendreSms(parent.telephone(), "ALERTE SOS");
    }

    @Test
    void unAbonnementPerimeEstRetireEtLeSmsPrendLeRelais() throws Exception {
        ParentAvecEnfant famille = acteurs.parentAvecEnfant();
        Parent parent = famille.parent();
        Navigateur navigateur = abonner(parent);
        perimes.add(navigateur.chemin());

        acteurs.equiper(famille);

        attendreLivraison(navigateur, 1);
        acteurs.attendreSms(parent.telephone(), "est associé à votre enfant");
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM notifications.abonnement_push WHERE point_de_livraison LIKE ?", Integer.class,
                "%" + navigateur.chemin())).isZero());
    }

    @Test
    void lAbonnementNAccepteQuUnServiceDePushAdmisEtResteProprieteDeSonCompte() throws Exception {
        Parent parent = acteurs.parent();
        mvc.perform(get("/api/v1/notifications/cle-publique").header("Authorization", "Bearer " + parent.jeton()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.clePublique").value(clePubliquePush()));
        mvc.perform(get("/api/v1/notifications/cle-publique")).andExpect(status().isUnauthorized());

        // Le serveur ne doit pas pouvoir être envoyé vers une adresse arbitraire.
        for (String adresse : List.of("https://exemple.test/push/1", "http://169.254.169.254/latest/meta-data",
                "http://localhost.exemple.test/push/1", "https://utilisateur@fcm.googleapis.com/x", "ftp://localhost/push/1", "pas une adresse")) {
            abonnement(parent, adresse, "BAAA", "AAAA").andExpect(status().isBadRequest());
        }
        abonnement(null, "https://fcm.googleapis.com/fcm/send/abc", "BAAA", "AAAA").andExpect(status().isUnauthorized());
        abonnement(parent, "https://fcm.googleapis.com/fcm/send/abc", "BAAA", "AAAA").andExpect(status().isNoContent());
        abonnement(parent, "https://db5p.notify.windows.com/w/?token=abc", "BAAA", "AAAA").andExpect(status().isNoContent());

        // Six navigateurs : le plus ancien abonnement du compte est retiré.
        for (int i = 0; i < 4; i++) {
            abonner(parent);
        }
        assertThat(jdbc.queryForList("""
                SELECT point_de_livraison FROM notifications.abonnement_push a JOIN identite.utilisateur u ON u.id = a.destinataire_id
                WHERE u.telephone_hash IS NOT NULL AND a.destinataire_id = (SELECT destinataire_id FROM notifications.abonnement_push
                WHERE point_de_livraison = 'https://db5p.notify.windows.com/w/?token=abc')""", String.class))
                .hasSize(5).doesNotContain("https://fcm.googleapis.com/fcm/send/abc");

        // Un autre compte ne peut pas désabonner ce navigateur ; son propriétaire, si.
        Parent autre = acteurs.parent();
        desabonnement(autre, "https://db5p.notify.windows.com/w/?token=abc").andExpect(status().isNoContent());
        assertThat(nombre("https://db5p.notify.windows.com/w/?token=abc")).isEqualTo(1);
        desabonnement(parent, "https://db5p.notify.windows.com/w/?token=abc").andExpect(status().isNoContent());
        assertThat(nombre("https://db5p.notify.windows.com/w/?token=abc")).isZero();
    }

    // -------------------------------------------------------------------- aides

    private Navigateur abonner(Parent parent) throws Exception {
        KeyPairGenerator generateur = KeyPairGenerator.getInstance("EC");
        generateur.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair cles = generateur.generateKeyPair();
        byte[] secret = new byte[16];
        new SecureRandom().nextBytes(secret);
        String chemin = "/push/" + UUID.randomUUID();
        abonnement(parent, "http://127.0.0.1:" + servicePush.getAddress().getPort() + chemin,
                BASE64.encodeToString(point((ECPublicKey) cles.getPublic())), BASE64.encodeToString(secret)).andExpect(status().isNoContent());
        return new Navigateur(chemin, cles, secret);
    }

    private ResultActions abonnement(Parent parent, String adresse, String p256dh, String auth) throws Exception {
        var requete = post("/api/v1/notifications/abonnements").contentType(MediaType.APPLICATION_JSON)
                .content("{\"endpoint\":\"" + adresse + "\",\"keys\":{\"p256dh\":\"" + p256dh + "\",\"auth\":\"" + auth + "\"}}");
        return mvc.perform(parent == null ? requete : requete.header("Authorization", "Bearer " + parent.jeton()));
    }

    private ResultActions desabonnement(Parent parent, String adresse) throws Exception {
        return mvc.perform(post("/api/v1/notifications/desabonnement").header("Authorization", "Bearer " + parent.jeton())
                .contentType(MediaType.APPLICATION_JSON).content("{\"endpoint\":\"" + adresse + "\"}"));
    }

    private int nombre(String adresse) {
        return jdbc.queryForObject("SELECT count(*) FROM notifications.abonnement_push WHERE point_de_livraison = ?", Integer.class, adresse);
    }

    private void evenement(Carte carte, String code) {
        reception.recevoir(Flux.ALERT, carte.numeroSerie(), ("{\"t\":" + (Instant.now().getEpochSecond() + 1) + ",\"seq\":"
                + SEQUENCE.incrementAndGet() + ",\"ev\":\"" + code + "\"}").getBytes(StandardCharsets.UTF_8));
    }

    private List<String> smsDe(Parent parent) {
        return sms.tous().stream().filter(message -> message.destinataire().equals("+226" + parent.telephone()))
                .map(SmsBacASable.SmsEnvoye::texte).toList();
    }

    private List<Livraison> livraisonsVers(Navigateur navigateur) {
        return livraisons.stream().filter(livraison -> livraison.chemin().equals(navigateur.chemin())).toList();
    }

    /** Attend la n-ième notification poussée vers ce navigateur. */
    private Livraison attendreLivraison(Navigateur navigateur, int rang) {
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(livraisonsVers(navigateur)).hasSizeGreaterThanOrEqualTo(rang));
        return livraisonsVers(navigateur).get(rang - 1);
    }

    /** « vapid t=<JWT ES256>, k=<clé publique> » : jeton signé par le serveur, pour ce service, non expiré. */
    private void verifierJetonVapid(String autorisation) throws Exception {
        assertThat(autorisation).startsWith("vapid t=").endsWith(", k=" + clePubliquePush());
        String[] jeton = autorisation.substring("vapid t=".length(), autorisation.indexOf(", k=")).split("\\.");
        assertThat(jeton).hasSize(3);
        Signature verification = Signature.getInstance("SHA256withECDSAinP1363Format");
        verification.initVerify(CLE_PUSH.getPublic());
        verification.update((jeton[0] + "." + jeton[1]).getBytes(StandardCharsets.US_ASCII));
        assertThat(verification.verify(DEBASE64.decode(jeton[2]))).as("signature du jeton").isTrue();
        assertThat(json.readTree(DEBASE64.decode(jeton[0])).path("alg").asString()).isEqualTo("ES256");
        JsonNode charge = json.readTree(DEBASE64.decode(jeton[1]));
        assertThat(charge.path("aud").asString()).isEqualTo("http://127.0.0.1:" + servicePush.getAddress().getPort());
        assertThat(charge.path("sub").asString()).isEqualTo("mailto:essais@fasoguardian.test");
        assertThat(charge.path("exp").asLong()).isBetween(Instant.now().getEpochSecond(), Instant.now().getEpochSecond() + 24 * 3600);
    }

    /** Déchiffrement côté navigateur (RFC 8291, aes128gcm), écrit indépendamment de l'émetteur. */
    private static byte[] dechiffrer(byte[] corps, Navigateur navigateur) throws Exception {
        byte[] sel = Arrays.copyOfRange(corps, 0, 16);
        assertThat(ByteBuffer.wrap(corps, 16, 4).getInt()).as("taille d'enregistrement").isEqualTo(4096);
        int longueurCle = corps[20] & 0xff;
        byte[] cleServeur = Arrays.copyOfRange(corps, 21, 21 + longueurCle);
        byte[] chiffre = Arrays.copyOfRange(corps, 21 + longueurCle, corps.length);
        byte[] cleNavigateur = point((ECPublicKey) navigateur.cles().getPublic());

        ECPublicKey modele = (ECPublicKey) navigateur.cles().getPublic();
        ECPoint coordonnees = new ECPoint(new java.math.BigInteger(1, Arrays.copyOfRange(cleServeur, 1, 33)),
                new java.math.BigInteger(1, Arrays.copyOfRange(cleServeur, 33, 65)));
        KeyAgreement accord = KeyAgreement.getInstance("ECDH");
        accord.init(navigateur.cles().getPrivate());
        accord.doPhase(java.security.KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(coordonnees, modele.getParams())), true);
        byte[] secretPartage = accord.generateSecret();

        byte[] contexte = concat("WebPush: info\0".getBytes(StandardCharsets.US_ASCII), cleNavigateur, cleServeur);
        byte[] materiel = hkdf(navigateur.secret(), secretPartage, contexte, 32);
        byte[] cle = hkdf(sel, materiel, "Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII), 16);
        byte[] nonce = hkdf(sel, materiel, "Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII), 12);
        Cipher dechiffre = Cipher.getInstance("AES/GCM/NoPadding");
        dechiffre.init(Cipher.DECRYPT_MODE, new SecretKeySpec(cle, "AES"), new GCMParameterSpec(128, nonce));
        byte[] clair = dechiffre.doFinal(chiffre);
        assertThat(clair[clair.length - 1]).as("délimiteur du dernier enregistrement").isEqualTo((byte) 2);
        return Arrays.copyOf(clair, clair.length - 1);
    }

    private static byte[] hkdf(byte[] sel, byte[] materiel, byte[] information, int longueur) throws Exception {
        Mac hmac = Mac.getInstance("HmacSHA256");
        hmac.init(new SecretKeySpec(sel, "HmacSHA256"));
        byte[] pseudoAleatoire = hmac.doFinal(materiel);
        hmac.init(new SecretKeySpec(pseudoAleatoire, "HmacSHA256"));
        hmac.update(information);
        hmac.update((byte) 1);
        return Arrays.copyOf(hmac.doFinal(), longueur);
    }

    private static byte[] point(ECPublicKey cle) {
        byte[] point = new byte[65];
        point[0] = 4;
        copier(cle.getW().getAffineX(), point, 1);
        copier(cle.getW().getAffineY(), point, 33);
        return point;
    }

    private static byte[] concat(byte[]... morceaux) {
        java.io.ByteArrayOutputStream sortie = new java.io.ByteArrayOutputStream();
        for (byte[] morceau : morceaux) {
            sortie.writeBytes(morceau);
        }
        return sortie.toByteArray();
    }
}
