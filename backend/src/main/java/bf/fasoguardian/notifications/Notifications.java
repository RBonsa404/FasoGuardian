package bf.fasoguardian.notifications;

import java.util.UUID;

/**
 * Notification d'un utilisateur par les canaux dont il dispose (FG-DOC-07 §4.4, REQ-MUST-11). Le module choisit
 * le canal : push sur les navigateurs abonnés, SMS en repli. Les textes ne contiennent jamais de donnée de
 * santé ni de coordonnée : ils renvoient à l'application authentifiée (FG-DOC-06 §6.6).
 */
public interface Notifications {

    enum Urgence {
        /** Push et SMS, tout de suite. */
        CRITIQUE,
        /** Push ; SMS au bout de 60 secondes sans accusé. */
        IMPORTANTE,
        /** Push seulement. */
        INFORMATION
    }

    /**
     * @param modele    code stable du message, pour le suivi (par exemple {@code ALERTE_SOS})
     * @param titre     titre court de la notification push
     * @param texte     phrase complète, commune au push et au SMS
     * @param lien      chemin de l'application à ouvrir, ou {@code null}
     * @param reference objet métier concerné, ou {@code null} ; voir {@link Notifications#accuser}
     */
    record Message(String modele, String titre, String texte, String lien, String reference) {

        public static Message simple(String modele, String titre, String texte) {
            return new Message(modele, titre, texte, null, null);
        }
    }

    /** Sans abonnement push, le destinataire reçoit un SMS quelle que soit l'urgence. */
    void notifier(UUID destinataireId, Urgence urgence, Message message);

    /** L'objet a été traité (alerte prise en charge ou close) : ses notifications n'appellent plus de repli SMS. */
    void accuser(String reference);
}
