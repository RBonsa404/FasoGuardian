package bf.fasoguardian.famille;

import java.time.Instant;
import java.util.UUID;

/**
 * Le QR code d'un bracelet rattaché à un enfant vient d'être scanné. La famille en est informée
 * (ADR 0005, point 3). {@code actif} est faux lorsque la page était désactivée (bracelet déclaré perdu ou volé).
 */
public record QrConsulte(UUID enfantId, String numeroBracelet, boolean actif, Instant quand) {
}
