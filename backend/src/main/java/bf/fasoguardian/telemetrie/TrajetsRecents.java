package bf.fasoguardian.telemetrie;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Positions récentes d'un enfant pour un dossier de signalement (US-PAR-010). L'appelant a vérifié le lien de
 * tutelle et journalise la transmission. Seules les positions de l'appairage en cours sont rendues.
 */
public interface TrajetsRecents {

    record Point(double latitude, double longitude, int precisionM, boolean approximative, Instant mesureeLe) {
    }

    /** Positions mesurées depuis l'instant donné, dans l'ordre chronologique. */
    List<Point> depuis(UUID enfantId, Instant debut);
}
