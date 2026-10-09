package bf.fasoguardian.abonnements;

import java.util.Map;
import java.util.UUID;

/**
 * Ce que l'abonnement d'un enfant ouvre aux autres modules (FG-DOC-11 §3.3, US-SYS-008). Le SOS, la détection
 * de retrait et la page publique QR ne dépendent jamais de l'abonnement et ne passent donc pas par ici.
 */
public interface Droits {

    /**
     * @param offre           code de l'offre appliquée
     * @param zonesMaximum    nombre de Safe Zones permis
     * @param historiqueJours profondeur de l'historique des trajets
     * @param intervalleS     intervalle entre deux positions en usage normal
     * @param suiviContinu    faux quand un impayé a suspendu la géolocalisation continue
     */
    record DroitsEnfant(String offre, int zonesMaximum, int historiqueJours, int intervalleS, boolean suiviContinu) {
    }

    DroitsEnfant de(UUID enfantId);

    /**
     * Durée de conservation des positions fixée par l'offre, en jours, pour chaque enfant couvert par un
     * abonnement. Un impayé ne la raccourcit pas : les positions reviennent au paiement.
     */
    Map<UUID, Integer> joursDeConservation();
}
