package bf.fasoguardian.identite.application;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import bf.fasoguardian.plateforme.console.Decomptes;
import bf.fasoguardian.plateforme.console.IndicateursConsole;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Tableau de bord KYC (écran 57) : la file, ses dossiers prioritaires, le délai de décision et les miennes. */
@Component
class IndicateursKyc implements IndicateursConsole {

    /** Au-delà, un dossier est à traiter en priorité pour tenir l'objectif de 48 h. */
    static final Duration PRIORITE = Duration.ofHours(36);
    static final long OBJECTIF_H = 48;

    private final Decomptes decomptes;

    IndicateursKyc(Decomptes decomptes) {
        this.decomptes = decomptes;
    }

    @Override
    public String role() {
        return "KYC";
    }

    @Override
    @Transactional(readOnly = true)
    public List<Indicateur> pour(UUID agentId) {
        long enFile = decomptes.compter("SELECT count(*) FROM identite.dossier_kyc WHERE statut IN ('DEPOSE', 'EN_INSTRUCTION')");
        long prioritaires = decomptes.compter("SELECT count(*) FROM identite.dossier_kyc WHERE statut IN ('DEPOSE', 'EN_INSTRUCTION')"
                + " AND depose_le < ?", decomptes.ilYA(PRIORITE));
        // Délai médian entre le dépôt et la décision finale, sur les trente derniers jours.
        Double secondes = decomptes.mesurer("SELECT percentile_cont(0.5) WITHIN GROUP (ORDER BY extract(epoch FROM decide_le - depose_le))"
                + " FROM identite.dossier_kyc WHERE statut IN ('APPROUVE', 'REJETE') AND decide_le > ?", decomptes.ilYA(Duration.ofDays(30)));
        Long heures = secondes == null ? null : Math.round(secondes / 3600);
        long miennes = decomptes.compter("SELECT count(*) FROM identite.dossier_kyc WHERE agent_id = ? AND decide_le >= ?", agentId,
                decomptes.debutDuJour());
        return List.of(new Indicateur("kyc.enFile", enFile, null, false),
                new Indicateur("kyc.prioritaires", prioritaires, PRIORITE.toHours(), prioritaires > 0),
                new Indicateur("kyc.delaiMedian", heures, OBJECTIF_H, heures != null && heures > OBJECTIF_H),
                new Indicateur("kyc.mesDecisions", miennes, null, false));
    }
}
