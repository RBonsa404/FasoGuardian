package bf.fasoguardian.telemetrie;

import java.time.Instant;
import java.util.UUID;

/**
 * Une position nouvelle vient d'être enregistrée pour un bracelet appairé. Les modules geolocalisation
 * (Safe Zones) et alertes y réagissent. {@code mesureeLe} est l'heure de la mesure, pas celle de la réception :
 * une position sortie du tampon hors ligne peut être ancienne.
 */
public record PositionRecue(UUID braceletId, UUID enfantId, double latitude, double longitude, int precisionM,
        Instant mesureeLe) {
}
