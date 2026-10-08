package bf.fasoguardian.notifications;

/**
 * Envoi d'un SMS par l'agrégateur retenu (port). Les textes ne contiennent jamais de donnée de santé ni de
 * coordonnée géographique (FG-DOC-06 §6.6). L'agrégateur n'étant pas encore choisi, l'adaptateur
 * « bac à sable » est le seul disponible ; l'adaptateur réel se branche par configuration.
 */
public interface ServiceSms {

    void envoyer(String destinataireE164, String texte);
}
