package bf.fasoguardian.identite;

import java.util.UUID;

/**
 * Envoi d'un SMS à un tuteur sans exposer son numéro au module appelant. Le texte ne doit contenir aucune
 * donnée de santé ni coordonnée : il invite à ouvrir l'application authentifiée (FG-DOC-06 §6.6).
 */
public interface MessagesTuteurs {

    void envoyerSms(UUID tuteurId, String texte);
}
