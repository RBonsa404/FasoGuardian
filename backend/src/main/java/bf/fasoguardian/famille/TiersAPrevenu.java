package bf.fasoguardian.famille;

import java.time.Instant;
import java.util.UUID;

/** Une personne ayant trouvé l'enfant a laissé un message pour la famille depuis la page publique. */
public record TiersAPrevenu(UUID enfantId, String numeroBracelet, UUID signalementId, Instant quand) {
}
