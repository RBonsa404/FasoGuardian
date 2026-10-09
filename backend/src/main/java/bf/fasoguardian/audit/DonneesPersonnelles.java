package bf.fasoguardian.audit;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Ce qu'un module détient sur une famille, pour l'exercice des droits d'accès et d'effacement (US-ADM-003).
 * Chaque module qui conserve des données personnelles en fournit une implémentation ; le module audit les
 * appelle toutes, sans connaître leurs tables.
 */
public interface DonneesPersonnelles {

    /**
     * @param reference référence de la demande (par exemple {@code EFF-000012})
     * @param tuteurId  auteur de la demande
     * @param enfants   enfants concernés : tous les siens pour un accès, ceux dont il est le seul tuteur pour un
     *                  effacement (un enfant qui garde un autre tuteur n'est pas effacé)
     */
    record Personne(String reference, UUID tuteurId, List<UUID> enfants) {
    }

    /** Nom de la rubrique dans l'export, et dans le journal de l'effacement. */
    String rubrique();

    /** Rang d'exécution de l'effacement : les modules qui en renseignent d'autres passent en dernier. */
    default int ordre() {
        return 0;
    }

    /** Données détenues, en clair, sous une forme lisible par la personne. */
    Map<String, Object> exporter(Personne personne);

    /** Supprime ce que le module n'a pas l'obligation de conserver. @return le nombre d'éléments supprimés */
    long effacer(Personne personne);
}
