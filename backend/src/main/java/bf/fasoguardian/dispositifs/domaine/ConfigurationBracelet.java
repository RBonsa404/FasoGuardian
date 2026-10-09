package bf.fasoguardian.dispositifs.domaine;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Intervalles d'émission et mode économie ; les valeurs par défaut viennent de la révision matérielle. */
@Entity
@Table(schema = "dispositifs", name = "configuration")
public class ConfigurationBracelet {

    @Id
    @Column(name = "bracelet_id")
    private UUID braceletId;

    @Column(name = "intervalle_normal_s", nullable = false)
    private int intervalleNormalS;

    @Column(name = "intervalle_alerte_s", nullable = false)
    private int intervalleAlerteS;

    @Column(name = "intervalle_economie_s", nullable = false)
    private int intervalleEconomieS;

    @Column(name = "mode_economie", nullable = false)
    private boolean modeEconomie;

    protected ConfigurationBracelet() {
    }

    ConfigurationBracelet(UUID braceletId, int intervalleNormalS, int intervalleAlerteS, int intervalleEconomieS) {
        this.braceletId = braceletId;
        this.intervalleNormalS = intervalleNormalS;
        this.intervalleAlerteS = intervalleAlerteS;
        this.intervalleEconomieS = intervalleEconomieS;
        this.modeEconomie = false;
    }

    /** @return {@code true} si la valeur a changé */
    public boolean reglerModeEconomie(boolean actif) {
        boolean change = modeEconomie != actif;
        modeEconomie = actif;
        return change;
    }

    public boolean modeEconomie() {
        return modeEconomie;
    }

    /** Intervalle entre deux positions dans le mode courant, hors alerte. */
    public int intervalleCourantS() {
        return modeEconomie ? intervalleEconomieS : intervalleNormalS;
    }

    /**
     * Intervalle à appliquer compte tenu de l'abonnement : jamais plus court que celui de l'offre, et 0, soit
     * aucune émission périodique, quand le suivi continu est suspendu. Le mode alerte n'en dépend pas.
     */
    public int intervalleS(int intervalleDeLOffreS, boolean suiviContinu) {
        return suiviContinu ? Math.max(intervalleCourantS(), intervalleDeLOffreS) : 0;
    }

    public int intervalleNormalS() {
        return intervalleNormalS;
    }

    public int intervalleAlerteS() {
        return intervalleAlerteS;
    }
}
