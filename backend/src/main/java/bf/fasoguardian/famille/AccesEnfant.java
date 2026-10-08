package bf.fasoguardian.famille;

import java.util.UUID;

/**
 * Contrôle d'appartenance parent-enfant (FG-DOC-06 §8.2) : tout module qui expose une donnée d'un enfant
 * l'appelle avant de la lire ou de la modifier, en plus du contrôle de rôle.
 */
public interface AccesEnfant {

    /**
     * Vérifie que le tuteur est rattaché à l'enfant par un lien de tutelle actif. Sinon, le refus est
     * journalisé et une erreur « ressource introuvable » est levée : l'existence de l'enfant n'est pas révélée.
     */
    void exigerTuteur(UUID tuteurId, UUID enfantId);
}
