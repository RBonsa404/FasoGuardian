package bf.fasoguardian.geolocalisation.domaine;

import java.time.Duration;
import java.time.Instant;

/**
 * État du suivi d'une zone pendant sa plage horaire. Une sortie n'est signalée que si l'enfant a d'abord été vu
 * dans la zone, puis mesuré dehors pendant tout le délai de tolérance : un simple passage hors de la limite,
 * ou un enfant qui n'est pas encore arrivé, ne déclenche rien (ADR 0010).
 *
 * @param vuDedans       l'enfant a été vu dans la zone depuis le début de la plage en cours
 * @param dehorsDepuis   heure de la première mesure hors zone depuis sa dernière présence, ou {@code null}
 * @param sortieSignalee la sortie en cours a déjà été signalée
 * @param derniereMesure heure de la dernière mesure prise en compte
 */
public record SuiviZone(boolean vuDedans, Instant dehorsDepuis, boolean sortieSignalee, Instant derniereMesure) {

    public enum Constat {
        RIEN,
        SORTIE,
        RETOUR
    }

    /** Nouvel état et ce qu'il y a à signaler. */
    public record Evaluation(SuiviZone suivi, Constat constat) {
    }

    /**
     * @param precedent état connu, ou {@code null} à la première mesure de la plage
     * @param dedans    la mesure est-elle dans la zone
     */
    public static Evaluation evaluer(SuiviZone precedent, boolean dedans, Instant mesureLe, Duration tolerance) {
        if (precedent != null && !mesureLe.isAfter(precedent.derniereMesure())) {
            // Position plus ancienne que la dernière traitée (vidange du tampon hors ligne) : elle ne rejoue pas le suivi.
            return new Evaluation(precedent, Constat.RIEN);
        }
        if (dedans) {
            boolean retour = precedent != null && precedent.sortieSignalee();
            return new Evaluation(new SuiviZone(true, null, false, mesureLe), retour ? Constat.RETOUR : Constat.RIEN);
        }
        if (precedent == null || !precedent.vuDedans()) {
            return new Evaluation(new SuiviZone(false, null, false, mesureLe), Constat.RIEN);
        }
        Instant dehorsDepuis = precedent.dehorsDepuis() == null ? mesureLe : precedent.dehorsDepuis();
        boolean echu = !Duration.between(dehorsDepuis, mesureLe).minus(tolerance).isNegative();
        boolean signaler = echu && !precedent.sortieSignalee();
        return new Evaluation(new SuiviZone(true, dehorsDepuis, precedent.sortieSignalee() || signaler, mesureLe),
                signaler ? Constat.SORTIE : Constat.RIEN);
    }
}
