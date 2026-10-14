package bf.fasoguardian.alertes.application;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import bf.fasoguardian.plateforme.console.Decomptes;
import bf.fasoguardian.plateforme.console.IndicateursConsole;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tableau de bord des forces de sécurité (écran 57). Il n'existe aucune liste des signalements (ADR 0023) :
 * l'agent ne voit que le décompte de ses propres accusés, jamais celui des signalements en cours.
 */
@Component
class IndicateursFds implements IndicateursConsole {

    private final Decomptes decomptes;

    IndicateursFds(Decomptes decomptes) {
        this.decomptes = decomptes;
    }

    @Override
    public String role() {
        return "FDS";
    }

    @Override
    @Transactional(readOnly = true)
    public List<Indicateur> pour(UUID agentId) {
        long aujourdhui = decomptes.compter("SELECT count(*) FROM alertes.signalement_fds WHERE accuse_par = ? AND accuse_le >= ?", agentId,
                decomptes.debutDuJour());
        long trenteJours = decomptes.compter("SELECT count(*) FROM alertes.signalement_fds WHERE accuse_par = ? AND accuse_le >= ?", agentId,
                decomptes.ilYA(Duration.ofDays(30)));
        return List.of(new Indicateur("fds.mesAccusesJour", aujourdhui, null, false),
                new Indicateur("fds.mesAccuses30j", trenteJours, null, false));
    }
}
