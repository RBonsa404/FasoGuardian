package bf.fasoguardian.telemetrie.infrastructure;

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

import bf.fasoguardian.telemetrie.application.ReceptionMessages;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

/**
 * Branche le serveur sur le broker MQTT lorsque son adresse est configurée. Hors des profils dev et test,
 * seule une connexion TLS à authentification mutuelle est admise (REQ-SYS : échanges bracelets–plateforme).
 */
@Configuration
@EnableConfigurationProperties(ProprietesMqtt.class)
// La variable FG_MQTT_URL existe toujours, vide par défaut : seule une adresse renseignée active l'abonné.
@ConditionalOnExpression("!'${fasoguardian.mqtt.url:}'.isBlank()")
class ConfigurationMqtt {

    @Bean(initMethod = "demarrer", destroyMethod = "arreter")
    AbonneMqtt abonneMqtt(ProprietesMqtt proprietes, ReceptionMessages reception, Environment environnement)
            throws IOException, GeneralSecurityException {
        if (!proprietes.chiffre() && !environnement.acceptsProfiles(Profiles.of("dev", "test"))) {
            throw new IllegalStateException("fasoguardian.mqtt.url doit être une adresse ssl:// hors des profils dev et test");
        }
        return new AbonneMqtt(proprietes, proprietes.chiffre() ? tls(proprietes) : null, reception);
    }

    @Bean
    HealthIndicator mqtt(AbonneMqtt abonne) {
        return () -> abonne.connecte() ? Health.up().build() : Health.down().withDetail("broker", "injoignable").build();
    }

    private static SSLSocketFactory tls(ProprietesMqtt proprietes) throws IOException, GeneralSecurityException {
        CertificateFactory fabrique = CertificateFactory.getInstance("X.509");
        KeyStore confiance = KeyStore.getInstance(KeyStore.getDefaultType());
        confiance.load(null, null);
        confiance.setCertificateEntry("autorite", certificat(fabrique, proprietes.autorite()));
        TrustManagerFactory gestionConfiance = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        gestionConfiance.init(confiance);

        KeyStore identite = KeyStore.getInstance(KeyStore.getDefaultType());
        identite.load(null, null);
        identite.setKeyEntry("serveur", cle(proprietes.cle()), new char[0],
                new Certificate[] {certificat(fabrique, proprietes.certificat())});
        KeyManagerFactory gestionCles = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        gestionCles.init(identite, new char[0]);

        SSLContext contexte = SSLContext.getInstance("TLS");
        contexte.init(gestionCles.getKeyManagers(), gestionConfiance.getTrustManagers(), null);
        return contexte.getSocketFactory();
    }

    private static Certificate certificat(CertificateFactory fabrique, Path chemin)
            throws IOException, GeneralSecurityException {
        try (InputStream flux = Files.newInputStream(chemin)) {
            return fabrique.generateCertificate(flux);
        }
    }

    private static PrivateKey cle(Path chemin) throws IOException, GeneralSecurityException {
        String pem = Files.readString(chemin, StandardCharsets.US_ASCII);
        if (!pem.contains("-----BEGIN PRIVATE KEY-----")) {
            throw new GeneralSecurityException("Clé privée attendue au format PKCS#8 (BEGIN PRIVATE KEY)");
        }
        String base64 = pem.replaceAll("-----(BEGIN|END) PRIVATE KEY-----", "").replaceAll("\\s", "");
        return KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64)));
    }
}
