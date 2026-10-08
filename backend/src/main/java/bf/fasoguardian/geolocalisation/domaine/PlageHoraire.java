package bf.fasoguardian.geolocalisation.domaine;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.Set;

/**
 * Jours et heures locales pendant lesquels une zone est surveillée. La plage peut chevaucher minuit : elle
 * appartient alors au jour où elle commence. Début et fin égaux désignent la journée entière.
 */
public record PlageHoraire(Set<DayOfWeek> jours, LocalTime debut, LocalTime fin) {

    public PlageHoraire {
        if (jours == null || jours.isEmpty() || debut == null || fin == null) {
            throw new IllegalArgumentException("Une plage horaire a au moins un jour, un début et une fin");
        }
        jours = Set.copyOf(EnumSet.copyOf(jours));
    }

    public boolean journeeEntiere() {
        return debut.equals(fin);
    }

    public boolean contient(LocalDateTime instantLocal) {
        DayOfWeek jour = instantLocal.getDayOfWeek();
        LocalTime heure = instantLocal.toLocalTime();
        if (journeeEntiere()) {
            return jours.contains(jour);
        }
        if (debut.isBefore(fin)) {
            return jours.contains(jour) && !heure.isBefore(debut) && heure.isBefore(fin);
        }
        // Chevauchement de minuit : la fin de plage tombe le lendemain du jour de début.
        return (jours.contains(jour) && !heure.isBefore(debut)) || (jours.contains(jour.minus(1)) && heure.isBefore(fin));
    }
}
