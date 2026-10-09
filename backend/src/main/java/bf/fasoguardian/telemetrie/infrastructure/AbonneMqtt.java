package bf.fasoguardian.telemetrie.infrastructure;

import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.SSLSocketFactory;

import bf.fasoguardian.telemetrie.application.ReceptionMessages;
import bf.fasoguardian.telemetrie.application.ReceptionMessages.Flux;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abonné MQTT du serveur (FG-DOC-06 §6.4) : il reçoit les flux montants de tous les bracelets en QoS 1 et les
 * remet au port de réception. L'identité d'un bracelet est celle que le broker a authentifiée : l'ACL ne le
 * laisse publier que sous son propre identifiant, repris ici du nom du sujet.
 */
public class AbonneMqtt implements MqttCallbackExtended {

    private static final Logger journalTechnique = LoggerFactory.getLogger(AbonneMqtt.class);
    private static final Pattern SUJET = Pattern.compile("fg/([A-Za-z0-9-]{1,32})/(telemetry|status|alert|ack)");
    private static final Map<String, Flux> FLUX =
            Map.of("telemetry", Flux.TELEMETRY, "status", Flux.STATUS, "alert", Flux.ALERT, "ack", Flux.ACK);
    private static final int QOS = 1;

    private final ProprietesMqtt proprietes;
    private final SSLSocketFactory tls;
    private final ReceptionMessages reception;
    private final ScheduledExecutorService relance = Executors.newSingleThreadScheduledExecutor(tache -> {
        Thread fil = new Thread(tache, "mqtt-connexion");
        fil.setDaemon(true);
        return fil;
    });
    private MqttAsyncClient client;

    /** @param tls fabrique de connexions TLS à authentification mutuelle, ou {@code null} pour un broker d'essai */
    public AbonneMqtt(ProprietesMqtt proprietes, SSLSocketFactory tls, ReceptionMessages reception) {
        this.proprietes = proprietes;
        this.tls = tls;
        this.reception = reception;
    }

    /** Ouvre la connexion sans bloquer le démarrage du serveur : un broker absent est réessayé toutes les 5 s. */
    public synchronized void demarrer() throws MqttException {
        client = new MqttAsyncClient(proprietes.url(), proprietes.identifiantClient(), new MemoryPersistence());
        client.setCallback(this);
        relance.execute(this::connecter);
    }

    public synchronized void arreter() {
        relance.shutdownNow();
        try {
            if (client != null) {
                if (client.isConnected()) {
                    client.disconnect().waitForCompletion(2_000);
                }
                client.close(true);
            }
        } catch (MqttException erreur) {
            journalTechnique.warn("Fermeture de la connexion MQTT : {}", erreur.getMessage());
        }
    }

    /**
     * Publie une commande signée vers un bracelet, en QoS 1 : le broker la garde pour un bracelet hors ligne.
     *
     * @throws MqttException si le broker est injoignable ; la commande sera réémise
     */
    public void publierCommande(String numeroSerie, byte[] message) throws MqttException {
        client.publish("fg/" + numeroSerie + "/cmd", message, QOS, false);
    }

    public boolean connecte() {
        MqttAsyncClient courant = client;
        return courant != null && courant.isConnected();
    }

    private void connecter() {
        MqttConnectOptions options = new MqttConnectOptions();
        // Session conservée : le broker garde les messages QoS 1 reçus pendant un redémarrage du serveur.
        options.setCleanSession(false);
        options.setAutomaticReconnect(true);
        options.setConnectionTimeout(10);
        options.setKeepAliveInterval(30);
        if (tls != null) {
            options.setSocketFactory(tls);
            options.setHttpsHostnameVerificationEnabled(true);
        }
        try {
            client.connect(options).waitForCompletion(15_000);
        } catch (MqttException erreur) {
            journalTechnique.warn("Broker MQTT injoignable ({}), nouvel essai dans 5 s", erreur.getMessage());
            relance.schedule(this::connecter, 5, TimeUnit.SECONDS);
        }
    }

    @Override
    public void connectComplete(boolean reconnexion, String adresse) {
        try {
            for (String flux : FLUX.keySet()) {
                client.subscribe(prefixe() + "fg/+/" + flux, QOS);
            }
            journalTechnique.info("Abonné aux flux des bracelets{}", reconnexion ? " (reconnexion)" : "");
        } catch (MqttException erreur) {
            journalTechnique.error("Abonnement MQTT refusé : {}", erreur.getMessage());
        }
    }

    @Override
    public void messageArrived(String sujet, MqttMessage message) {
        Matcher correspondance = SUJET.matcher(sujet);
        if (!correspondance.matches()) {
            return;
        }
        try {
            reception.recevoir(FLUX.get(correspondance.group(2)), correspondance.group(1), message.getPayload());
        } catch (RuntimeException erreur) {
            // Une erreur de traitement ne doit ni fermer la connexion ni bloquer les messages suivants.
            journalTechnique.error("Message {} non traité : {}", correspondance.group(2), erreur.toString());
        }
    }

    @Override
    public void connectionLost(Throwable cause) {
        journalTechnique.warn("Connexion MQTT perdue : {}", cause == null ? "cause inconnue" : cause.getMessage());
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken jeton) {
        // Rien à faire : une commande n'est tenue pour reçue qu'à l'accusé du bracelet.
    }

    private String prefixe() {
        String groupe = proprietes.groupe();
        return groupe == null || groupe.isBlank() ? "" : "$share/" + groupe + "/";
    }
}
