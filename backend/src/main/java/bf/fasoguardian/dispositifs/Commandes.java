package bf.fasoguardian.dispositifs;

import java.time.Instant;
import java.util.UUID;

/**
 * Commandes de la plateforme vers le bracelet d'un enfant (FG-DOC-08 §8.1). Chacune est signée, numérotée et
 * limitée dans le temps ; le bracelet ignore toute commande qu'il ne peut pas authentifier (US-SYS-011).
 * Sans bracelet en service pour l'enfant, l'appel est sans effet.
 */
public interface Commandes {

    /** Mode alerte : une position toutes les 60 secondes tant qu'une alerte critique est en cours. */
    void modeAlerte(UUID enfantId, boolean actif);

    /**
     * Fenêtre de retrait autorisé.
     *
     * @param fin fin de la fenêtre, ou {@code null} pour la refermer tout de suite
     */
    void fenetreDeRetrait(UUID enfantId, Instant fin);

    /** Accusé d'exécution reçu du bracelet, ou signalement d'une commande qu'il a refusée. */
    void accuser(String numeroSerie, String commandeId, boolean executee);
}
