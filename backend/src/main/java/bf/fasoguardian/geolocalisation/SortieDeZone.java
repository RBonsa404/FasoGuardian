package bf.fasoguardian.geolocalisation;

import java.time.Instant;
import java.util.UUID;

/**
 * L'enfant est sorti d'une Safe Zone active pendant sa plage horaire et n'y est pas revenu avant la fin du
 * délai de tolérance. Le module alertes en fait une alerte.
 *
 * @param depuis heure de la première position mesurée hors de la zone
 */
public record SortieDeZone(UUID zoneId, UUID enfantId, String nomZone, double latitude, double longitude,
        Instant depuis, Instant detecteLe) {
}
