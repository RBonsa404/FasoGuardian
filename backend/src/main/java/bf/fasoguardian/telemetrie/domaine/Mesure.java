package bf.fasoguardian.telemetrie.domaine;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Position mesurée par le bracelet et état radio qui l'accompagne (FG-DOC-08 §8.2). L'heure de mesure est
 * celle du bracelet : une position gardée en mémoire pendant une coupure arrive avec son horodatage d'origine.
 */
public record Mesure(Instant mesureeLe, long sequence, double latitude, double longitude, int precisionM,
        Source source, Integer batterie, Integer signalDbm, Reseau reseau, String operateur, Boolean enMouvement) {

    public enum Source {
        GNSS,
        CELLULE,
        WIFI,
        /** Présence relevée par une passerelle LoRaWAN : la position est celle de l'enceinte couverte. */
        LORA
    }

    public enum Reseau {
        G2("2G"),
        G3("3G"),
        G4("4G");

        private final String libelle;

        Reseau(String libelle) {
            this.libelle = libelle;
        }

        public String libelle() {
            return libelle;
        }
    }

    /** Tolérance sur l'horloge du bracelet, recalée par le réseau. */
    public static final Duration AVANCE_TOLEREE = Duration.ofMinutes(5);
    /** Au-delà de la durée de conservation par défaut, une position tamponnée n'a plus à être enregistrée. */
    public static final Duration ANCIENNETE_MAXIMALE = Duration.ofDays(30);

    /** Motif du refus d'une mesure, ou vide si elle est recevable à l'instant donné. */
    public Optional<String> defaut(Instant maintenant) {
        if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180
                || (latitude == 0 && longitude == 0)) {
            return Optional.of("coordonnées hors limites");
        }
        if (sequence < 0 || precisionM < 0 || precisionM > 100_000) {
            return Optional.of("séquence ou précision hors limites");
        }
        if (mesureeLe.isAfter(maintenant.plus(AVANCE_TOLEREE))) {
            return Optional.of("horodatage dans le futur");
        }
        if (mesureeLe.isBefore(maintenant.minus(ANCIENNETE_MAXIMALE))) {
            return Optional.of("mesure trop ancienne");
        }
        if (batterie != null && (batterie < 0 || batterie > 100)) {
            return Optional.of("batterie hors limites");
        }
        return Optional.empty();
    }
}
