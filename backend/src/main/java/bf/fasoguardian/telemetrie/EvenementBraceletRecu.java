package bf.fasoguardian.telemetrie;

import java.time.Instant;
import java.util.UUID;

/**
 * Le bracelet signale un événement (SOS, coupure de la boucle, perte du contact peau, chute, batterie
 * critique, mise en charge). La position jointe est la dernière connue du bracelet ; elle peut manquer.
 */
public record EvenementBraceletRecu(UUID braceletId, UUID enfantId, Type type, Double latitude, Double longitude,
        Instant mesureLe) {

    public enum Type {
        SOS,
        COUPURE_BOUCLE,
        PERTE_CONTACT_PEAU,
        CHUTE,
        BATTERIE_CRITIQUE,
        MISE_EN_CHARGE,
        /** Le contact peau est rétabli : le bracelet a été remis. */
        PORT_RETABLI
    }
}
