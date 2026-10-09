package bf.fasoguardian.notifications;

import java.util.Optional;
import java.util.UUID;

/**
 * Numéro de téléphone d'un destinataire, fourni par le module qui tient les comptes. Le numéro ne quitte pas
 * le module notifications et n'est jamais conservé avec une notification.
 */
public interface Annuaire {

    /** Vide pour un compte clos, suspendu ou sans téléphone. */
    Optional<String> telephoneE164(UUID utilisateurId);
}
