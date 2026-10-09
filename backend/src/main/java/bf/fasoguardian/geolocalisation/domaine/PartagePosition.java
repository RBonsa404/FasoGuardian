package bf.fasoguardian.geolocalisation.domaine;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Partage temporaire de la position d'un enfant avec un contact d'urgence (US-SEC-001). Il vaut de son début
 * à sa fin, sauf révocation ; le contact n'a ni compte ni autre accès.
 *
 * @param revoqueLe  révocation par le parent, ou {@code null}
 * @param ouvertures nombre de fois où le lien a été ouvert
 */
public record PartagePosition(UUID id, UUID enfantId, UUID creePar, String destinataireLien, String destinataireMasque,
        Instant debut, Instant fin, Instant revoqueLe, int ouvertures, Instant derniereOuverture) {

    public static final Duration DUREE_MINIMALE = Duration.ofMinutes(15);
    public static final Duration DUREE_MAXIMALE = Duration.ofHours(12);

    public boolean actif(Instant maintenant) {
        return revoqueLe == null && !maintenant.isBefore(debut) && maintenant.isBefore(fin);
    }

    /** @throws IllegalArgumentException si la durée sort des bornes admises */
    public static Duration duree(int minutes) {
        Duration duree = Duration.ofMinutes(minutes);
        if (duree.compareTo(DUREE_MINIMALE) < 0 || duree.compareTo(DUREE_MAXIMALE) > 0) {
            throw new IllegalArgumentException("Un partage dure de 15 minutes à 12 heures.");
        }
        return duree;
    }
}
