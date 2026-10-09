package bf.fasoguardian.simulateur;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import bf.fasoguardian.simulateur.VerificateurCommandes.Resultat;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

/**
 * Simulateur de bracelets FasoGuardian : chaque bracelet se connecte au broker en TLS mutuel avec son
 * propre certificat, publie sa télémétrie sur fg/{deviceId}/telemetry (QoS 1) et obéit aux commandes signées
 * reçues sur fg/{deviceId}/cmd, qu'il accuse sur fg/{deviceId}/ack.
 *
 * <p>Les commandes sont vérifiées comme le fera le logiciel embarqué : sans la clé publique de la plateforme
 * ({@code --cle-plateforme}), toutes sont refusées. Le repli SMS, les coupures et la montée à 15 000 bracelets
 * (client asynchrone) arrivent avec les tests de charge.
 *
 * <pre>
 * java -jar fasoguardian-simulateur.jar --broker=ssl://localhost:8883 --certificats=infra/certs \
 *      --bracelets=FG-DEV-0001,FG-DEV-0002 --intervalle=PT5M [--duree=PT10M] \
 *      [--cle-plateforme=infra/certs/commandes-publique.pem] [--cle-ota=infra/certs/ota-publique.pem]
 * </pre>
 */
public final class Simulateur {

    private static final int QOS = 1;
    /** Intervalle entre deux positions en mode alerte (FG-DOC-08 §7.3). */
    private static final Duration INTERVALLE_ALERTE = Duration.ofSeconds(60);

    /** Un bracelet connecté, son rythme d'émission et son vérificateur de commandes. */
    private static final class Connexion {
        final BraceletSimule bracelet;
        final MqttClient client;
        final VerificateurCommandes verificateur;
        volatile boolean modeAlerte;
        /** Émission périodique suspendue par la plateforme (commande cfg, intervalle 0). */
        volatile boolean suspendu;
        volatile Instant prochaineEmission = Instant.EPOCH;

        /** Clé de publication du logiciel embarqué ; sans elle, toute mise à jour est refusée. */
        final PublicKey clePublication;

        Connexion(BraceletSimule bracelet, MqttClient client, VerificateurCommandes verificateur, PublicKey clePublication) {
            this.bracelet = bracelet;
            this.client = client;
            this.verificateur = verificateur;
            this.clePublication = clePublication;
        }
    }

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
        PublicKey clePlateforme = options.containsKey("cle-plateforme") ? lireClePublique(Path.of(options.get("cle-plateforme"))) : null;
        PublicKey clePublication = options.containsKey("cle-ota") ? lireClePublique(Path.of(options.get("cle-ota"))) : null;

        ScheduledExecutorService planificateur = Executors.newScheduledThreadPool(2);
        List<Connexion> connexions = new ArrayList<>();
        AtomicLong publies = new AtomicLong();

        for (String identifiant : identifiants) {
            BraceletSimule bracelet = new BraceletSimule(identifiant.trim(), graine);
            MqttClient client = connecter(broker, certificats, bracelet);
            Connexion connexion = new Connexion(bracelet, client, new VerificateurCommandes(bracelet.identifiant(), clePlateforme),
                    clePublication);
            client.subscribe(topic(bracelet.identifiant(), "cmd"), QOS,
                    (sujet, message) -> recevoirCommande(connexion, new String(message.getPayload(), StandardCharsets.US_ASCII), publies));
            connexions.add(connexion);
        }
        // Une vérification par seconde : l'intervalle effectif suit le mode (normal ou alerte) de chaque bracelet.
        planificateur.scheduleAtFixedRate(() -> {
            Instant maintenant = Instant.now();
            for (Connexion connexion : connexions) {
                // Suspendu, le bracelet n'émet plus qu'en mode alerte ou à la demande (commande loc).
                if ((!connexion.suspendu || connexion.modeAlerte) && !maintenant.isBefore(connexion.prochaineEmission)) {
                    Duration rythme = connexion.modeAlerte && INTERVALLE_ALERTE.compareTo(intervalle) < 0 ? INTERVALLE_ALERTE : intervalle;
                    connexion.prochaineEmission = maintenant.plus(rythme);
                    publier(connexion, publies);
                }
            }
        }, 0, 1, TimeUnit.SECONDS);
        System.out.printf("%d bracelet(s) connecté(s) à %s, télémétrie toutes les %s, commandes %s%n", connexions.size(), broker,
                intervalle, clePlateforme == null ? "refusées (pas de clé de la plateforme)" : "vérifiées");

        Runnable arreter = () -> {
            planificateur.shutdownNow();
            for (Connexion connexion : connexions) {
                try {
                    connexion.client.disconnectForcibly(1000, 1000);
                    connexion.client.close();
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

    /** Vérifie la commande, l'accuse (ou signale son rejet), puis l'applique. */
    private static void recevoirCommande(Connexion connexion, String message, AtomicLong publies) {
        Resultat resultat = connexion.verificateur.verifier(message, Instant.now());
        String id = connexion.bracelet.identifiant();
        boolean redemarre = false;
        boolean imageRefusee = false;
        if (resultat.dejaExecutee()) {
            System.out.printf("%s : commande déjà exécutée, accusée de nouveau%n", id);
        } else if (!resultat.acceptee()) {
            System.out.printf("%s : commande refusée (%s)%n", id, resultat.rejet());
        } else if ("alert".equals(resultat.commande().code())) {
            connexion.modeAlerte = resultat.commande().parametre("on", 0) == 1;
            connexion.prochaineEmission = Instant.now();
            System.out.printf("%s : mode alerte %s%n", id, connexion.modeAlerte ? "activé" : "désactivé");
        } else if ("ota".equals(resultat.commande().code())) {
            VerificateurCommandes.Commande demande = resultat.commande();
            String version = demande.texte("v");
            if (ManifesteOta.valide(connexion.clePublication, version, demande.parametre("size", 0), demande.texte("sha"), demande.texte("sig"))) {
                connexion.bracelet.installer(version);
                redemarre = true;
                System.out.printf("%s : image %s vérifiée, redémarrage sur la nouvelle version%n", id, version);
            } else {
                imageRefusee = true;
                System.out.printf("%s : mise à jour refusée (manifeste non signé par la clé de publication)%n", id);
            }
        } else if ("cfg".equals(resultat.commande().code())) {
            connexion.suspendu = resultat.commande().parametre("int", 1) == 0;
            System.out.printf("%s : configuration reçue, émission périodique %s%n", id, connexion.suspendu ? "suspendue" : "active");
        } else {
            System.out.printf("%s : commande %s exécutée%n", id, resultat.commande().code());
        }
        String accuse = "{\"id\":\"" + (resultat.idLu() == null ? "inconnue" : resultat.idLu()) + "\",\"ok\":" + (resultat.accusePositif() && !imageRefusee) + "}";
        boolean nouvelleVersion = redemarre;
        // L'accusé part d'un autre fil : le client synchrone ne publie pas depuis sa propre fonction de rappel.
        new Thread(() -> {
            try {
                connexion.client.publish(topic(id, "ack"), accuse.getBytes(StandardCharsets.UTF_8), QOS, false);
                if (resultat.acceptee() && "loc".equals(resultat.commande().code())) {
                    publier(connexion, publies);
                }
                if (nouvelleVersion) {
                    // Après redémarrage, le bracelet annonce la version qu'il exécute.
                    connexion.client.publish(topic(id, "status"), connexion.bracelet.etat(true).getBytes(StandardCharsets.UTF_8), QOS, true);
                }
            } catch (MqttException erreur) {
                System.err.printf("%s : accusé impossible (%s)%n", id, erreur.getMessage());
            }
        }, "accuse-" + id).start();
    }

    private static void publier(Connexion connexion, AtomicLong publies) {
        try {
            String message = connexion.bracelet.prochaineTelemetrie(Instant.now());
            connexion.client.publish(topic(connexion.bracelet.identifiant(), "telemetry"), message.getBytes(StandardCharsets.UTF_8), QOS, false);
            publies.incrementAndGet();
        } catch (MqttException erreur) {
            System.err.printf("%s : publication impossible (%s)%n", connexion.bracelet.identifiant(), erreur.getMessage());
        }
    }

    /** Clé publique ECDSA de la plateforme au format PEM (« BEGIN PUBLIC KEY »). */
    static PublicKey lireClePublique(Path chemin) throws IOException, GeneralSecurityException {
        String pem = Files.readString(chemin, StandardCharsets.US_ASCII);
        String base64 = pem.replaceAll("-----(BEGIN|END) PUBLIC KEY-----", "").replaceAll("\\s", "");
        return KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(base64)));
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
