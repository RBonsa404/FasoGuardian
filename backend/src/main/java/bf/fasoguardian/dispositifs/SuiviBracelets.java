package bf.fasoguardian.dispositifs;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Ce que la télémétrie observe d'un bracelet en service et qui appelle une réaction (US-SAV-001, US-SYS-007) :
 * silence prolongé, batterie faible, retour à la normale.
 */
public interface SuiviBracelets {

    /**
     * Le bracelet n'a pas donné de nouvelles depuis plus de trois intervalles : le parent est informé et un
     * ticket de maintenance est ouvert, s'il n'y en a pas déjà un.
     *
     * @param dernierContact dernier message reçu, ou {@code null} s'il n'a jamais rien émis depuis l'appairage
     * @param batterie       dernière batterie connue, en pour cent, ou {@code null}
     * @param reseau         dernier réseau connu, ou {@code null}
     */
    void signalerMuet(UUID braceletId, Instant dernierContact, Integer batterie, String reseau);

    /** Le bracelet redonne des nouvelles : son ticket « muet », s'il est encore ouvert, est clos. */
    void signalerReprise(UUID braceletId);

    /** Bracelets dont un ticket « muet » est en cours. */
    Set<UUID> muets();

    /** Batterie passée sous 20 % : mode économie activé d'office, parent averti. */
    void batterieFaible(UUID braceletId, int niveau);

    /** Batterie remontée : le mode économie activé d'office est levé. */
    void batterieRetablie(UUID braceletId);
}
