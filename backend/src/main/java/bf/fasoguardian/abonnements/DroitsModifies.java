package bf.fasoguardian.abonnements;

import java.util.UUID;

/** Les droits d'un enfant ont changé (paiement confirmé, changement d'offre, restriction pour impayé). */
public record DroitsModifies(UUID enfantId) {
}
