package bf.fasoguardian.geolocalisation;

import java.time.Instant;
import java.util.UUID;

/** L'enfant est revenu dans une zone dont la sortie avait été signalée. */
public record RetourEnZone(UUID zoneId, UUID enfantId, String nomZone, Instant mesureLe) {
}
