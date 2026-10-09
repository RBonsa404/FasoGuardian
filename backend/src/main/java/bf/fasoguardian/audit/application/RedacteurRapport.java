package bf.fasoguardian.audit.application;

import java.time.Instant;
import java.time.YearMonth;
import java.util.List;

import bf.fasoguardian.audit.application.Conformite.Duree;
import bf.fasoguardian.audit.application.Conformite.Purge;
import bf.fasoguardian.audit.application.GardeAipd.Etat;

/** Mise en page du rapport mensuel de conformité remis au délégué à la protection des données. */
public interface RedacteurRapport {

    /**
     * @param effacementsHorsDelai effacements du mois exécutés après l'échéance de trente jours
     * @param entreeAlteree        première entrée altérée du journal d'audit, ou {@code null} s'il est intègre
     */
    record Contenu(YearMonth mois, Instant etabliLe, Etat aipd, List<Duree> conservation, List<Purge> purges,
            int joursDePurge, int joursDuMois, long accesServis, long effacementsRecus, long effacementsExecutes,
            long effacementsHorsDelai, long effacementsEnAttente, Long entreeAlteree) {
    }

    byte[] rediger(Contenu contenu);
}
