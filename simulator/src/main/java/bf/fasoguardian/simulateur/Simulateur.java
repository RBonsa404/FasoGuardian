package bf.fasoguardian.simulateur;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

/**
 * Simulateur de bracelets FasoGuardian : chaque bracelet se connecte au broker en TLS mutuel avec son
 * propre certificat et publie sa télémétrie sur fg/{deviceId}/telemetry (QoS 1).
 *
 * <p>Version minimale du socle : télémétrie périodique et état en ligne / hors ligne (dernière volonté).
 * Les alertes, le repli SMS, les coupures et la montée à 15 000 bracelets (client asynchrone) arrivent
 * avec le module telemetrie et les tests de charge.
 *
 * <pre>
 * java -jar fasoguardian-simulateur.jar --broker=ssl://localhost:8883 --certificats=infra/certs \
 *      --bracelets=FG-DEV-0001,FG-DEV-0002 --intervalle=PT5M [--duree=PT10M]
 * </pre>
 */
public final class Simulateur {

    private static final int QOS = 1;

    private Simulateur() {
    }

    public static void main(String[] arguments) throws Exception {
        Map<String, String> options = lireOptions(arguments);
        String broker = options.getOrDefault("broker", "ssl://localhost:8883");
        Path certificats = Path.of(options.getOrDefault("certificats", "infra/certs"));
        String[] identifiants = options.getOrDefault("bracelets", "FG-DEV-0001").split(",");
        Duration intervalle = Duration.parse(options.getOrDefault("intervalle", "PT5M"));
        Duration duree = options.containsKey("duree") ? Duration.parse(options.get("duree")) : null;
        long graine = Long.parseLong(options.getOrDefault("graine", "20261007"));

        ScheduledExecutorService planificateur = Executors.newScheduledThreadPool(2);
        List<MqttClient> clients = new ArrayList<>();
        AtomicLong publies = new AtomicLong();

        for (String identifiant : identifiants) {
            BraceletSimule bracelet = new BraceletSimule(identifiant.trim(), graine);
            MqttClient client = connecter(broker, certificats, bracelet);
            clients.add(client);
            planificateur.scheduleAtFixedRate(() -> publier(client, bracelet, publies),
                    0, intervalle.toMillis(), TimeUnit.MILLISECONDS);
        }
        System.out.printf("%d bracelet(s) connecté(s) à %s, télémétrie toutes les %s%n",
                clients.size(), broker, intervalle);

        Runnable arreter = () -> {
            planificateur.shutdownNow();
            for (MqttClient client : clients) {
                try {
                    client.disconnectForcibly(1000, 1000);
                    client.close();
                } catch (MqttException erreur) {
                    System.err.println("Fermeture : " + erreur.getMessage());
                }
            }
            System.out.printf("Simulation terminée : %d message(s) publié(s)%n", publies.get());
        };

        if (duree == null) {
            Runtime.getRuntime().addShutdownHook(new Thread(arreter));
            Thread.currentThread().join();
        } else {
            Thread.sleep(duree.toMillis());
            arreter.run();
        }
    }

    private static MqttClient connecter(String broker, Path certificats, BraceletSimule bracelet) throws Exception {
        String id = bracelet.identifiant();
        MqttConnectOptions options = new MqttConnectOptions();
        options.setCleanSession(false);
        options.setAutomaticReconnect(true);
        options.setKeepAliveInterval(120);
        options.setSocketFactory(ContexteTls.pour(certificats.resolve("ca.crt"),
                certificats.resolve("bracelet-" + id + ".crt"), certificats.resolve("bracelet-" + id + ".key")));
        // Dernière volonté : le broker annonce la déconnexion du bracelet (REQ-SYS-012).
        options.setWill(topic(id, "status"), bracelet.etat(false).getBytes(StandardCharsets.UTF_8), QOS, true);

        MqttClient client = new MqttClient(broker, id, new MemoryPersistence());
        client.connect(options);
        client.publish(topic(id, "status"), bracelet.etat(true).getBytes(StandardCharsets.UTF_8), QOS, true);
        return client;
    }

    private static void publier(MqttClient client, BraceletSimule bracelet, AtomicLong publies) {
        try {
            String message = bracelet.prochaineTelemetrie(Instant.now());
            client.publish(topic(bracelet.identifiant(), "telemetry"), message.getBytes(StandardCharsets.UTF_8), QOS, false);
            publies.incrementAndGet();
        } catch (MqttException erreur) {
            System.err.printf("%s : publication impossible (%s)%n", bracelet.identifiant(), erreur.getMessage());
        }
    }

    private static String topic(String identifiant, String flux) {
        return "fg/" + identifiant + "/" + flux;
    }

    static Map<String, String> lireOptions(String[] arguments) {
        Map<String, String> options = new HashMap<>();
        for (String argument : arguments) {
            int egal = argument.indexOf('=');
            if (!argument.startsWith("--") || egal < 3) {
                throw new IllegalArgumentException("Option attendue sous la forme --cle=valeur : " + argument);
            }
            options.put(argument.substring(2, egal), argument.substring(egal + 1));
        }
        return options;
    }
}
