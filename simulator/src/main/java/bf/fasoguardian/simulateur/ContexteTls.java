package bf.fasoguardian.simulateur;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

/** Construit le contexte TLS d'un bracelet : autorité du broker et certificat client (clé PKCS#8 en PEM). */
final class ContexteTls {

    private ContexteTls() {
    }

    static SSLSocketFactory pour(Path autorite, Path certificat, Path cle) throws IOException, GeneralSecurityException {
        CertificateFactory fabrique = CertificateFactory.getInstance("X.509");

        KeyStore confiance = KeyStore.getInstance(KeyStore.getDefaultType());
        confiance.load(null, null);
        confiance.setCertificateEntry("autorite", lireCertificat(fabrique, autorite));
        TrustManagerFactory gestionnaireDeConfiance = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        gestionnaireDeConfiance.init(confiance);

        KeyStore identite = KeyStore.getInstance(KeyStore.getDefaultType());
        identite.load(null, null);
        identite.setKeyEntry("bracelet", lireCle(cle), new char[0],
                new Certificate[] {lireCertificat(fabrique, certificat)});
        KeyManagerFactory gestionnaireDeCles = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        gestionnaireDeCles.init(identite, new char[0]);

        SSLContext contexte = SSLContext.getInstance("TLS");
        contexte.init(gestionnaireDeCles.getKeyManagers(), gestionnaireDeConfiance.getTrustManagers(), null);
        return contexte.getSocketFactory();
    }

    private static Certificate lireCertificat(CertificateFactory fabrique, Path chemin)
            throws IOException, GeneralSecurityException {
        try (InputStream flux = Files.newInputStream(chemin)) {
            return fabrique.generateCertificate(flux);
        }
    }

    static PrivateKey lireCle(Path chemin) throws IOException, GeneralSecurityException {
        String pem = Files.readString(chemin, StandardCharsets.US_ASCII);
        if (!pem.contains("-----BEGIN PRIVATE KEY-----")) {
            throw new GeneralSecurityException("Clé privée attendue au format PKCS#8 (BEGIN PRIVATE KEY) : " + chemin);
        }
        String base64 = pem.replaceAll("-----(BEGIN|END) PRIVATE KEY-----", "").replaceAll("\\s", "");
        return KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64)));
    }
}
