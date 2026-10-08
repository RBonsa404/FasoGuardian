package bf.fasoguardian.telemetrie.infrastructure;

import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Connexion du serveur au broker MQTT. Sans adresse, l'abonné n'est pas démarré (tests, poste sans broker).
 *
 * @param url               adresse du broker, {@code ssl://hôte:8883}
 * @param autorite          certificat de l'autorité du broker (PEM)
 * @param certificat        certificat client du serveur (PEM)
 * @param cle               clé privée du serveur (PEM, PKCS#8)
 * @param identifiantClient identifiant de session, propre à chaque instance du serveur
 * @param groupe            groupe d'abonnement partagé, qui répartit les messages entre les instances
 */
@ConfigurationProperties("fasoguardian.mqtt")
public record ProprietesMqtt(String url, Path autorite, Path certificat, Path cle, String identifiantClient,
        String groupe) {

    public boolean chiffre() {
        return url != null && url.startsWith("ssl://");
    }
}
