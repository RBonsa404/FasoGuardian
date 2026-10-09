package bf.fasoguardian.telemetrie.domaine;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Passerelle LoRaWAN installée autour d'un établissement partenaire (US-SYS-004). Son enceinte, un centre et
 * un rayon, est la position attribuée à tout bracelet qu'elle entend.
 */
public record Passerelle(UUID id, String eui, String etablissement, double latitude, double longitude, int rayonM,
        Instant creeeLe, Instant vueLe) {

    /** Sans trame ni signe de vie pendant ce délai, la passerelle est tenue pour hors ligne. */
    public static final Duration SILENCE_TOLERE = Duration.ofMinutes(15);

    public boolean enLigne(Instant maintenant) {
        return vueLe != null && vueLe.isAfter(maintenant.minus(SILENCE_TOLERE));
    }
}
