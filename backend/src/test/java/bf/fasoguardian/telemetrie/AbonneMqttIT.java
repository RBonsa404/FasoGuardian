package bf.fasoguardian.telemetrie;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import bf.fasoguardian.telemetrie.application.ReceptionMessages.Flux;
import bf.fasoguardian.telemetrie.infrastructure.AbonneMqtt;
import bf.fasoguardian.telemetrie.infrastructure.ProprietesMqtt;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.utility.DockerImageName;

/**
 * Abonné MQTT contre un vrai broker Mosquitto (sans TLS : le chiffrement et l'ACL sont éprouvés par la pile
 * Compose et le simulateur). Vérifie l'abonnement partagé, le routage par sujet et la tenue aux erreurs.
 */
class AbonneMqttIT {

    private record Recu(Flux flux, String appareil, String message) {
    }

    @SuppressWarnings("resource")
    private static final GenericContainer<?> broker = new GenericContainer<>(DockerImageName.parse("eclipse-mosquitto:2.0"))
            .withExposedPorts(1883)
            .withCopyToContainer(Transferable.of("listener 1883\nallow_anonymous true\n"), "/mosquitto/config/mosquitto.conf");

    private static final List<Recu> recus = new CopyOnWriteArrayList<>();
    private static AbonneMqtt abonne;
    private static MqttClient bracelet;

    @BeforeAll
    static void demarrer() throws MqttException {
        broker.start();
        String url = "tcp://" + broker.getHost() + ":" + broker.getMappedPort(1883);
        abonne = new AbonneMqtt(new ProprietesMqtt(url, null, null, null, "serveur-essai", "fg-serveur"), null,
                (flux, appareil, message) -> {
                    String texte = new String(message, StandardCharsets.UTF_8);
                    if (texte.contains("panne")) {
                        throw new IllegalStateException("traitement en échec");
                    }
                    recus.add(new Recu(flux, appareil, texte));
                });
        abonne.demarrer();
        await().atMost(Duration.ofSeconds(20)).until(abonne::connecte);
        bracelet = new MqttClient(url, "FG-2291", new MemoryPersistence());
        bracelet.connect();
    }

    @AfterAll
    static void arreter() throws MqttException {
        bracelet.disconnect();
        bracelet.close();
        abonne.arreter();
        broker.stop();
    }

    @Test
    void lesFluxMontantsSontRemisAvecLIdentifiantDeLAppareilEtUneErreurNeCoupeRien() throws MqttException {
        publier("fg/FG-2291/telemetry", "{\"t\":1,\"seq\":1}");
        publier("fg/FG-2291/status", "{\"online\":true}");
        publier("fg/FG-2291/alert", "{\"ev\":\"panne\"}");
        publier("fg/FG-2291/alert", "{\"ev\":\"sos\"}");
        // Sujets que le serveur ne doit pas traiter comme des messages de bracelet.
        publier("fg/FG-2291/cmd", "{\"cmd\":\"x\"}");
        publier("fg/a/b/telemetry", "{}");
        publier("fg/FG-2291/telemetry", "{\"t\":2,\"seq\":2}");

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(recus).containsExactly(
                new Recu(Flux.TELEMETRY, "FG-2291", "{\"t\":1,\"seq\":1}"),
                new Recu(Flux.STATUS, "FG-2291", "{\"online\":true}"),
                new Recu(Flux.ALERT, "FG-2291", "{\"ev\":\"sos\"}"),
                new Recu(Flux.TELEMETRY, "FG-2291", "{\"t\":2,\"seq\":2}")));
        assertThat(abonne.connecte()).isTrue();
    }

    private static void publier(String sujet, String message) throws MqttException {
        bracelet.publish(sujet, message.getBytes(StandardCharsets.UTF_8), 1, false);
    }
}
