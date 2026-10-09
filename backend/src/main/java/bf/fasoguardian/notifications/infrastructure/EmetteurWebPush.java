package bf.fasoguardian.notifications.infrastructure;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import bf.fasoguardian.notifications.application.CanalPush;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Web Push (RFC 8030) : contenu chiffré pour le seul navigateur abonné (RFC 8291, aes128gcm) et requête
 * authentifiée par les clés du serveur d'application (VAPID, RFC 8292). Le serveur n'appelle que des services
 * de push connus : l'adresse d'un abonnement vient du navigateur et ne doit pas servir à atteindre autre chose.
 */
@Component
public class EmetteurWebPush implements CanalPush {

    private static final Logger journalTechnique = LoggerFactory.getLogger(EmetteurWebPush.class);
    private static final Base64.Encoder BASE64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DEBASE64 = Base64.getUrlDecoder();
    private static final int LONGUEUR_POINT = 65;
    private static final int TAILLE_ENREGISTREMENT = 4096;
    /** Un service de push refuse un contenu de plus de 4 096 octets ; le chiffrement en ajoute une centaine. */
    private static final int CONTENU_MAXIMAL = 3800;
    private static final Duration VALIDITE_JETON = Duration.ofHours(12);
    /** Durée pendant laquelle le service de push garde la notification pour un appareil hors ligne. */
    private static final Duration DUREE_DE_VIE = Duration.ofHours(1);

    private final PrivateKey clePrivee;
    private final String clePublique;
    private final String sujet;
    private final List<String> hotesAdmis;
    private final boolean httpLocalAdmis;
    private final Clock horloge;
    private final SecureRandom alea = new SecureRandom();
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER).build();

    EmetteurWebPush(@Value("${fasoguardian.push.cle-publique:}") String clePublique,
            @Value("${fasoguardian.push.cle-privee:}") String clePriveePkcs8,
            @Value("${fasoguardian.push.sujet:}") String sujet,
            @Value("${fasoguardian.push.hotes-admis:fcm.googleapis.com,updates.push.services.mozilla.com,web.push.apple.com,.notify.windows.com}") List<String> hotesAdmis,
            @Value("${fasoguardian.push.http-local-admis:false}") boolean httpLocalAdmis, Clock horloge)
            throws GeneralSecurityException {
        this.clePublique = clePublique.strip();
        this.clePrivee = clePriveePkcs8.isBlank() ? null : KeyFactory.getInstance("EC")
                .generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(clePriveePkcs8.strip())));
        if ((this.clePrivee == null) != this.clePublique.isEmpty() || (this.clePrivee != null && sujet.isBlank())) {
            throw new IllegalStateException("fasoguardian.push : la clé publique, la clé privée et le sujet vont ensemble");
        }
        this.sujet = sujet.strip();
        this.hotesAdmis = hotesAdmis.stream().map(hote -> hote.strip().toLowerCase(Locale.ROOT)).filter(hote -> !hote.isEmpty()).toList();
        this.httpLocalAdmis = httpLocalAdmis;
        this.horloge = horloge;
    }

    @Override
    public boolean disponible() {
        return clePrivee != null;
    }

    @Override
    public String clePublique() {
        return clePublique;
    }

    @Override
    public boolean admet(String pointDeLivraison) {
        try {
            URI adresse = URI.create(pointDeLivraison);
            String hote = adresse.getHost() == null ? "" : adresse.getHost().toLowerCase(Locale.ROOT);
            if (adresse.getUserInfo() != null || adresse.getFragment() != null || pointDeLivraison.length() > 1024) {
                return false;
            }
            if (httpLocalAdmis && "http".equals(adresse.getScheme()) && ("localhost".equals(hote) || "127.0.0.1".equals(hote))) {
                return true;
            }
            return "https".equals(adresse.getScheme()) && hotesAdmis.stream()
                    .anyMatch(admis -> admis.startsWith(".") ? hote.endsWith(admis) : hote.equals(admis));
        } catch (IllegalArgumentException erreur) {
            return false;
        }
    }

    @Override
    public Issue pousser(String pointDeLivraison, String cleP256dh, String secretAuth, byte[] contenu, boolean urgent) {
        if (!disponible() || !admet(pointDeLivraison) || contenu.length > CONTENU_MAXIMAL) {
            return Issue.ECHEC;
        }
        try {
            URI adresse = URI.create(pointDeLivraison);
            byte[] corps = chiffrer(contenu, DEBASE64.decode(cleP256dh), DEBASE64.decode(secretAuth));
            HttpRequest requete = HttpRequest.newBuilder(adresse).timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/octet-stream")
                    .header("Content-Encoding", "aes128gcm")
                    .header("TTL", String.valueOf(DUREE_DE_VIE.toSeconds()))
                    .header("Urgency", urgent ? "high" : "normal")
                    .header("Authorization", "vapid t=" + jeton(adresse) + ", k=" + clePublique)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(corps)).build();
            int statut = client.send(requete, HttpResponse.BodyHandlers.discarding()).statusCode();
            if (statut == 404 || statut == 410) {
                return Issue.ABONNEMENT_PERIME;
            }
            if (statut / 100 != 2) {
                journalTechnique.warn("Service de push : réponse {}", statut);
                return Issue.ECHEC;
            }
            return Issue.LIVREE;
        } catch (InterruptedException erreur) {
            Thread.currentThread().interrupt();
            return Issue.ECHEC;
        } catch (IOException | GeneralSecurityException | IllegalArgumentException erreur) {
            journalTechnique.warn("Notification push non livrée : {}", erreur.getClass().getSimpleName());
            return Issue.ECHEC;
        }
    }

    /** Jeton VAPID : JWT ES256 limité au service de push visé et à douze heures. */
    private String jeton(URI adresse) throws GeneralSecurityException {
        String audience = adresse.getScheme() + "://" + adresse.getAuthority();
        String entete = BASE64.encodeToString("{\"typ\":\"JWT\",\"alg\":\"ES256\"}".getBytes(StandardCharsets.UTF_8));
        String charge = BASE64.encodeToString(("{\"aud\":\"" + audience + "\",\"exp\":"
                + horloge.instant().plus(VALIDITE_JETON).getEpochSecond() + ",\"sub\":\"" + sujet + "\"}").getBytes(StandardCharsets.UTF_8));
        Signature signature = Signature.getInstance("SHA256withECDSAinP1363Format");
        signature.initSign(clePrivee);
        signature.update((entete + "." + charge).getBytes(StandardCharsets.US_ASCII));
        return entete + "." + charge + "." + BASE64.encodeToString(signature.sign());
    }

    /**
     * Chiffrement aes128gcm de la RFC 8291 : secret partagé par ECDH entre une clé éphémère et la clé du
     * navigateur, dérivé avec son secret d'authentification, puis un seul enregistrement AES-128-GCM.
     */
    byte[] chiffrer(byte[] contenu, byte[] clePubliqueNavigateur, byte[] secretAuth) throws GeneralSecurityException {
        KeyPairGenerator generateur = KeyPairGenerator.getInstance("EC");
        generateur.initialize(new ECGenParameterSpec("secp256r1"), alea);
        KeyPair ephemere = generateur.generateKeyPair();
        byte[] clePubliqueEphemere = point((ECPublicKey) ephemere.getPublic());

        KeyAgreement accord = KeyAgreement.getInstance("ECDH");
        accord.init(ephemere.getPrivate());
        accord.doPhase(clePublique(clePubliqueNavigateur), true);
        byte[] secretPartage = accord.generateSecret();

        byte[] contexte = concat("WebPush: info\0".getBytes(StandardCharsets.US_ASCII), clePubliqueNavigateur, clePubliqueEphemere);
        byte[] materiel = hkdf(secretAuth, secretPartage, contexte, 32);
        byte[] sel = new byte[16];
        alea.nextBytes(sel);
        byte[] cle = hkdf(sel, materiel, "Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII), 16);
        byte[] nonce = hkdf(sel, materiel, "Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII), 12);

        Cipher chiffre = Cipher.getInstance("AES/GCM/NoPadding");
        chiffre.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(cle, "AES"), new GCMParameterSpec(128, nonce));
        // L'octet 0x02 marque la fin du dernier (et unique) enregistrement.
        byte[] chiffreTexte = chiffre.doFinal(concat(contenu, new byte[] {2}));

        ByteArrayOutputStream corps = new ByteArrayOutputStream();
        corps.writeBytes(sel);
        corps.writeBytes(ByteBuffer.allocate(4).putInt(TAILLE_ENREGISTREMENT).array());
        corps.write(LONGUEUR_POINT);
        corps.writeBytes(clePubliqueEphemere);
        corps.writeBytes(chiffreTexte);
        return corps.toByteArray();
    }

    /** HKDF-SHA-256 (RFC 5869) pour une sortie d'au plus 32 octets : extraction, puis un seul bloc d'expansion. */
    static byte[] hkdf(byte[] sel, byte[] materiel, byte[] information, int longueur) throws GeneralSecurityException {
        Mac hmac = Mac.getInstance("HmacSHA256");
        hmac.init(new SecretKeySpec(sel, "HmacSHA256"));
        byte[] pseudoAleatoire = hmac.doFinal(materiel);
        hmac.init(new SecretKeySpec(pseudoAleatoire, "HmacSHA256"));
        hmac.update(information);
        hmac.update((byte) 1);
        return Arrays.copyOf(hmac.doFinal(), longueur);
    }

    /** Clé publique P-256 à partir de son point non compressé (0x04 ‖ X ‖ Y). */
    static PublicKey clePublique(byte[] point) throws GeneralSecurityException {
        if (point.length != LONGUEUR_POINT || point[0] != 4) {
            throw new GeneralSecurityException("Clé publique P-256 attendue au format non compressé");
        }
        AlgorithmParameters parametres = AlgorithmParameters.getInstance("EC");
        parametres.init(new ECGenParameterSpec("secp256r1"));
        ECPoint coordonnees = new ECPoint(new BigInteger(1, Arrays.copyOfRange(point, 1, 33)),
                new BigInteger(1, Arrays.copyOfRange(point, 33, 65)));
        return KeyFactory.getInstance("EC").generatePublic(
                new ECPublicKeySpec(coordonnees, parametres.getParameterSpec(ECParameterSpec.class)));
    }

    /** Point non compressé (65 octets) d'une clé publique P-256. */
    static byte[] point(ECPublicKey cle) {
        byte[] point = new byte[LONGUEUR_POINT];
        point[0] = 4;
        copier(cle.getW().getAffineX(), point, 1);
        copier(cle.getW().getAffineY(), point, 33);
        return point;
    }

    private static void copier(BigInteger coordonnee, byte[] cible, int position) {
        byte[] octets = coordonnee.toByteArray();
        int debut = Math.max(0, octets.length - 32);
        int longueur = octets.length - debut;
        System.arraycopy(octets, debut, cible, position + 32 - longueur, longueur);
    }

    private static byte[] concat(byte[]... morceaux) {
        ByteArrayOutputStream sortie = new ByteArrayOutputStream();
        for (byte[] morceau : morceaux) {
            sortie.writeBytes(morceau);
        }
        return sortie.toByteArray();
    }
}
