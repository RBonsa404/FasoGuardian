package bf.fasoguardian.telemetrie.domaine;

import java.time.Instant;

/** Position enregistrée, telle qu'elle est restituée au parent. */
public record PositionConnue(double latitude, double longitude, int precisionM, Mesure.Source source,
        Instant mesureeLe) {
}
