package bf.fasoguardian.audit;

/**
 * Registre des purges planifiées (US-ADM-003). Chaque module y consigne l'exécution de ses purges : le rapport
 * de conformité montre ainsi que les durées de conservation sont appliquées sans intervention manuelle.
 */
public interface RegistrePurges {

    /**
     * @param traitement code stable de la purge (par exemple {@code POSITIONS})
     * @param elements   nombre d'éléments supprimés par cette exécution
     */
    void consigner(String traitement, long elements);
}
