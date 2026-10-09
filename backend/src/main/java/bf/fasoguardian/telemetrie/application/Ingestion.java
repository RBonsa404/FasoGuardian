package bf.fasoguardian.telemetrie.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import bf.fasoguardian.dispositifs.Bracelets;
import bf.fasoguardian.dispositifs.Bracelets.BraceletConnu;
import bf.fasoguardian.dispositifs.Commandes;
import bf.fasoguardian.dispositifs.SuiviBracelets;
import bf.fasoguardian.dispositifs.VersionsLogicielles;
import bf.fasoguardian.telemetrie.EvenementBraceletRecu;
import bf.fasoguardian.telemetrie.PositionRecue;
import bf.fasoguardian.telemetrie.domaine.EtatBracelet;
import bf.fasoguardian.telemetrie.domaine.Mesure;
import bf.fasoguardian.telemetrie.infrastructure.DepotTelemetrie;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Réception des messages des bracelets (FG-DOC-08 §8 ; format décrit dans docs/protocole-bracelet.md).
 * Un message n'est retenu que si l'appareil est au parc, en service et appairé, et si son certificat n'est
 * pas révoqué. Un message rejoué (QoS 1, tampon hors ligne) est ignoré sans erreur (US-SYS-002).
 */
@Service
public class Ingestion implements ReceptionMessages {

    /** Taille maximale d'un message : le format compact tient en une centaine d'octets. */
    public static final int TAILLE_MAXIMALE = 512;
    /** Seuils de batterie, en pour cent (US-SYS-007). */
    static final int BATTERIE_FAIBLE = 20;
    static final int BATTERIE_RETABLIE = 30;

    public enum Resultat {
        ACCEPTE,
        DOUBLON,
        APPAREIL_REFUSE,
        INVALIDE
    }

    private static final Logger journalTechnique = LoggerFactory.getLogger(Ingestion.class);

    private final Bracelets bracelets;
    private final Commandes commandes;
    private final SuiviBracelets suivi;
    private final VersionsLogicielles versions;
    private final DepotTelemetrie depot;
    private final ApplicationEventPublisher evenements;
    private final JsonMapper json;
    private final MeterRegistry metriques;
    private final Clock horloge;

    Ingestion(Bracelets bracelets, Commandes commandes, SuiviBracelets suivi, VersionsLogicielles versions, DepotTelemetrie depot, ApplicationEventPublisher evenements, JsonMapper json,
            MeterRegistry metriques, Clock horloge) {
        this.bracelets = bracelets;
        this.commandes = commandes;
        this.suivi = suivi;
        this.versions = versions;
        this.depot = depot;
        this.evenements = evenements;
        this.json = json;
        this.metriques = metriques;
        this.horloge = horloge;
    }

    /**
     * Point d'entrée des transports. La transaction s'ouvre ici : sans elle, les événements publiés par le
     * traitement (position reçue, événement du bracelet) ne seraient jamais remis aux autres modules.
     */
    @Override
    @Transactional
    public void recevoir(Flux flux, String identifiantAppareil, byte[] message) {
        switch (flux) {
            case TELEMETRY -> telemetrie(identifiantAppareil, message);
            case STATUS -> etat(identifiantAppareil, message);
            case ALERT -> alerte(identifiantAppareil, message);
            case ACK -> accuse(identifiantAppareil, message);
        }
    }

    /**
     * Présence relevée par une passerelle LoRaWAN (US-SYS-004) : le bracelet a été entendu dans l'enceinte
     * donnée. La position enregistrée est celle de l'enceinte, avec son rayon pour précision ; l'état radio
     * cellulaire du bracelet n'est pas touché, seul son dernier contact avance.
     */
    @Transactional
    public Resultat presence(String identifiantAppareil, Instant entenduLe, long compteur, double latitude, double longitude,
            int rayonM) {
        return compter("lorawan", () -> {
            Optional<BraceletConnu> bracelet = appareil(identifiantAppareil);
            if (bracelet.isEmpty()) {
                return Resultat.APPAREIL_REFUSE;
            }
            Instant maintenant = horloge.instant();
            Mesure mesure = new Mesure(entenduLe, compteur, latitude, longitude, rayonM, Mesure.Source.LORA, null, null, null, null,
                    null);
            BraceletConnu connu = bracelet.get();
            if (mesure.defaut(maintenant).isPresent() || mesure.mesureeLe().isBefore(connu.appaireDepuis())) {
                return Resultat.INVALIDE;
            }
            depot.enregistrerEtat(connu.id(), new EtatBracelet(null, null, null, null, null, null, null, maintenant));
            if (!depot.ajouterPosition(connu.id(), mesure, maintenant)) {
                return Resultat.DOUBLON;
            }
            evenements.publishEvent(new PositionRecue(connu.id(), connu.enfantId(), mesure.latitude(), mesure.longitude(),
                    mesure.precisionM(), mesure.mesureeLe()));
            return Resultat.ACCEPTE;
        });
    }

    /** Message du flux {@code telemetry} : une position et l'état radio du bracelet. */
    @Transactional
    public Resultat telemetrie(String identifiantAppareil, byte[] message) {
        return compter("telemetry", () -> {
            Optional<BraceletConnu> bracelet = appareil(identifiantAppareil);
            if (bracelet.isEmpty()) {
                return Resultat.APPAREIL_REFUSE;
            }
            JsonNode corps = lire(message);
            Instant maintenant = horloge.instant();
            Mesure mesure = corps == null ? null : mesure(corps);
            BraceletConnu connu = bracelet.get();
            // Une position mesurée avant l'appairage (tampon d'un bracelet encore en stock) n'est pas celle de l'enfant.
            if (mesure == null || mesure.defaut(maintenant).isPresent()
                    || mesure.mesureeLe().isBefore(connu.appaireDepuis())) {
                return Resultat.INVALIDE;
            }
            Integer batterieAvant = depot.etat(connu.id()).map(EtatBracelet::batterie).orElse(null);
            depot.enregistrerEtat(connu.id(), new EtatBracelet(mesure.batterie(), mesure.signalDbm(),
                    mesure.reseau() == null ? null : mesure.reseau().libelle(), mesure.operateur(), mesure.enMouvement(),
                    Boolean.TRUE, null, maintenant));
            suivreLaBatterie(connu.id(), batterieAvant, mesure.batterie());
            if (!depot.ajouterPosition(connu.id(), mesure, maintenant)) {
                return Resultat.DOUBLON;
            }
            evenements.publishEvent(new PositionRecue(connu.id(), connu.enfantId(), mesure.latitude(), mesure.longitude(),
                    mesure.precisionM(), mesure.mesureeLe()));
            return Resultat.ACCEPTE;
        });
    }

    /** Message du flux {@code status} : en ligne ou hors ligne (dernière volonté), version du logiciel. */
    @Transactional
    public Resultat etat(String identifiantAppareil, byte[] message) {
        return compter("status", () -> {
            Optional<BraceletConnu> bracelet = appareil(identifiantAppareil);
            if (bracelet.isEmpty()) {
                return Resultat.APPAREIL_REFUSE;
            }
            JsonNode corps = lire(message);
            if (corps == null || !corps.path("online").isBoolean()) {
                return Resultat.INVALIDE;
            }
            String version = corps.path("fw").isString() ? borner(corps.path("fw").asString(), 16) : null;
            depot.enregistrerEtat(bracelet.get().id(), new EtatBracelet(null, null, null, null, null,
                    corps.path("online").asBoolean(), version, horloge.instant()));
            if (version != null) {
                versions.versionConstatee(bracelet.get().id(), version);
            }
            return Resultat.ACCEPTE;
        });
    }

    /** Message du flux {@code alert} : événement du bracelet, avec sa dernière position connue si elle existe. */
    @Transactional
    public Resultat alerte(String identifiantAppareil, byte[] message) {
        return compter("alert", () -> {
            Optional<BraceletConnu> bracelet = appareil(identifiantAppareil);
            if (bracelet.isEmpty()) {
                return Resultat.APPAREIL_REFUSE;
            }
            JsonNode corps = lire(message);
            Instant maintenant = horloge.instant();
            EvenementBraceletRecu.Type type = corps == null ? null : type(corps.path("ev").asString(""));
            if (type == null || !corps.path("t").isIntegralNumber() || !corps.path("seq").isIntegralNumber()) {
                return Resultat.INVALIDE;
            }
            Instant mesureLe = Instant.ofEpochSecond(corps.path("t").asLong());
            long sequence = corps.path("seq").asLong();
            BraceletConnu connu = bracelet.get();
            if (sequence < 0 || mesureLe.isAfter(maintenant.plus(Mesure.AVANCE_TOLEREE))
                    || mesureLe.isBefore(maintenant.minus(Mesure.ANCIENNETE_MAXIMALE))
                    || mesureLe.isBefore(connu.appaireDepuis())) {
                return Resultat.INVALIDE;
            }
            boolean localise = corps.path("lat").isNumber() && corps.path("lon").isNumber()
                    && Math.abs(corps.path("lat").asDouble()) <= 90 && Math.abs(corps.path("lon").asDouble()) <= 180;
            Double latitude = localise ? corps.path("lat").asDouble() : null;
            Double longitude = localise ? corps.path("lon").asDouble() : null;
            depot.enregistrerEtat(connu.id(), new EtatBracelet(null, null, null, null, null, Boolean.TRUE, null, maintenant));
            if (!depot.ajouterEvenement(connu.id(), type, sequence, mesureLe, latitude, longitude, maintenant)) {
                return Resultat.DOUBLON;
            }
            evenements.publishEvent(new EvenementBraceletRecu(connu.id(), connu.enfantId(), type, latitude, longitude,
                    mesureLe));
            return Resultat.ACCEPTE;
        });
    }

    /**
     * Message du flux {@code ack} : {@code {"id":"…","ok":true}}. Avec {@code "ok":false}, le bracelet signale
     * une commande qu'il a refusée, faute de signature valide par exemple (US-SYS-011).
     */
    @Transactional
    public Resultat accuse(String identifiantAppareil, byte[] message) {
        return compter("ack", () -> {
            JsonNode corps = lire(message);
            if (corps == null || !corps.path("id").isString() || !corps.path("ok").isBoolean()) {
                return Resultat.INVALIDE;
            }
            commandes.accuser(identifiantAppareil, corps.path("id").asString(), corps.path("ok").asBoolean());
            return Resultat.ACCEPTE;
        });
    }

    // -------------------------------------------------------------------- aides

    /** Appareil autorisé à émettre : au parc, en service, appairé, certificat valide. */
    private Optional<BraceletConnu> appareil(String identifiant) {
        return bracelets.parNumeroSerie(identifiant)
                .filter(bracelet -> bracelet.accepteLesMessages() && bracelet.enfantId() != null);
    }

    private JsonNode lire(byte[] message) {
        if (message == null || message.length == 0 || message.length > TAILLE_MAXIMALE) {
            return null;
        }
        try {
            JsonNode corps = json.readTree(message);
            return corps != null && corps.isObject() ? corps : null;
        } catch (JacksonException erreur) {
            return null;
        }
    }

    private static Mesure mesure(JsonNode corps) {
        if (!corps.path("t").isIntegralNumber() || !corps.path("seq").isIntegralNumber()
                || !corps.path("lat").isNumber() || !corps.path("lon").isNumber()) {
            return null;
        }
        Mesure.Source source = switch (corps.path("src").asString("gnss").toLowerCase(Locale.ROOT)) {
            case "gnss" -> Mesure.Source.GNSS;
            case "cell" -> Mesure.Source.CELLULE;
            case "wifi" -> Mesure.Source.WIFI;
            default -> null;
        };
        if (source == null) {
            return null;
        }
        Mesure.Reseau reseau = switch (corps.path("net").asString("").toLowerCase(Locale.ROOT)) {
            case "2g" -> Mesure.Reseau.G2;
            case "3g" -> Mesure.Reseau.G3;
            case "4g" -> Mesure.Reseau.G4;
            default -> null;
        };
        return new Mesure(Instant.ofEpochSecond(corps.path("t").asLong()), corps.path("seq").asLong(),
                corps.path("lat").asDouble(), corps.path("lon").asDouble(), corps.path("acc").asInt(0), source,
                entier(corps, "bat"), entier(corps, "rssi"), reseau,
                corps.path("op").isString() ? borner(corps.path("op").asString(), 24) : null,
                corps.path("mv").isIntegralNumber() ? corps.path("mv").asInt() != 0 : null);
    }

    private static EvenementBraceletRecu.Type type(String code) {
        return switch (code.toLowerCase(Locale.ROOT)) {
            case "sos" -> EvenementBraceletRecu.Type.SOS;
            case "strap" -> EvenementBraceletRecu.Type.COUPURE_BOUCLE;
            case "skin" -> EvenementBraceletRecu.Type.PERTE_CONTACT_PEAU;
            case "fall" -> EvenementBraceletRecu.Type.CHUTE;
            case "batcrit" -> EvenementBraceletRecu.Type.BATTERIE_CRITIQUE;
            case "charge" -> EvenementBraceletRecu.Type.MISE_EN_CHARGE;
            case "worn" -> EvenementBraceletRecu.Type.PORT_RETABLI;
            default -> null;
        };
    }

    private static Integer entier(JsonNode corps, String champ) {
        return corps.path(champ).isIntegralNumber() ? corps.path(champ).asInt() : null;
    }

    private static String borner(String valeur, int longueur) {
        String propre = valeur.strip();
        return propre.isEmpty() ? null : propre.substring(0, Math.min(longueur, propre.length()));
    }

    /** Compte les messages par flux et par issue ; le journal technique ne porte aucun identifiant d'appareil. */
    private Resultat compter(String flux, Supplier<Resultat> traitement) {
        Resultat resultat = traitement.get();
        metriques.counter("fasoguardian.telemetrie.messages", "flux", flux, "resultat", resultat.name()).increment();
        if (resultat == Resultat.INVALIDE || resultat == Resultat.APPAREIL_REFUSE) {
            journalTechnique.debug("Message {} écarté : {}", flux, resultat);
        }
        return resultat;
    }

    /**
     * Seuils de batterie (US-SYS-007) : le franchissement de 20 % vers le bas avertit le parent et fait passer
     * le bracelet en mode économie ; la remontée à 30 % lève ce mode. L'écart évite d'osciller autour du seuil.
     */
    private void suivreLaBatterie(UUID braceletId, Integer avant, Integer maintenant) {
        if (maintenant == null) {
            return;
        }
        if (maintenant < BATTERIE_FAIBLE && (avant == null || avant >= BATTERIE_FAIBLE)) {
            suivi.batterieFaible(braceletId, maintenant);
        } else if (maintenant >= BATTERIE_RETABLIE && avant != null && avant < BATTERIE_RETABLIE) {
            suivi.batterieRetablie(braceletId);
        }
    }
}
