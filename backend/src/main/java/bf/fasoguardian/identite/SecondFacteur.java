package bf.fasoguardian.identite;

import java.util.UUID;

/**
 * Second facteur des actions sensibles d'un parent (FG-DOC-06 §8.1) : un code à usage unique reçu par SMS,
 * propre à l'action. Les modules qui portent ces actions (alertes, geolocalisation, famille) appellent
 * {@link #exiger} avant de les exécuter.
 */
public interface SecondFacteur {

    /** Actions soumises à un second facteur. */
    enum ActionSensible {
        AUTORISER_RETRAIT,
        MODIFIER_SAFE_ZONE,
        ESCALADER_FORCES_SECURITE,
        AJOUTER_TUTEUR,
        DECLARER_BRACELET,
        CLORE_COMPTE
    }

    /** Envoie au parent le code de confirmation de l'action. */
    void envoyerCode(UUID tuteurId, ActionSensible action);

    /**
     * Vérifie et consomme le code. Lève une erreur métier {@code SECOND_FACTEUR_REQUIS} s'il est absent,
     * {@code CODE_INCORRECT}, {@code CODE_EXPIRE} ou {@code CODE_EPUISE} sinon.
     */
    void exiger(UUID tuteurId, ActionSensible action, String code);
}
