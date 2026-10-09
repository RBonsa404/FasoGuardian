package bf.fasoguardian.audit;

import java.util.List;
import java.util.UUID;

/** Enfants rattachés à un tuteur, vus par l'exercice des droits. Fourni par le module identite. */
public interface PerimetreFamilial {

    /** Tous les enfants dont il est tuteur. */
    List<UUID> enfantsDe(UUID tuteurId);

    /** Ceux dont il est le seul tuteur : leurs données disparaissent avec son compte. */
    List<UUID> enfantsSansAutreTuteur(UUID tuteurId);
}
