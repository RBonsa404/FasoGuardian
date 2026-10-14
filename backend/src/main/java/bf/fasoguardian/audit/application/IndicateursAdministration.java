package bf.fasoguardian.audit.application;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import bf.fasoguardian.plateforme.console.Decomptes;
import bf.fasoguardian.plateforme.console.IndicateursConsole;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tableau de bord de l'administration (écran 57) : refus d'accès des dernières 24 h, demandes de droits des
 * personnes à traiter et celles dont l'échéance approche.
 */
@Component
class IndicateursAdministration implements IndicateursConsole {

    /** Une demande de droits dont l'échéance tombe dans ce délai est signalée. */
    static final Duration ECHEANCE_PROCHE = Duration.ofDays(7);

    private final Decomptes decomptes;

    IndicateursAdministration(Decomptes decomptes) {
        this.decomptes = decomptes;
    }

    @Override
    public String role() {
        return "ADMIN";
    }

    @Override
    @Transactional(readOnly = true)
    public List<Indicateur> pour(UUID agentId) {
        long refus = decomptes.compter("SELECT count(*) FROM audit.entree WHERE resultat = 'REFUS' AND horodatage >= ?",
                decomptes.ilYA(Duration.ofHours(24)));
        long demandes = decomptes.compter("SELECT count(*) FROM audit.demande_droit WHERE statut = 'RECUE'");
        long echeances = decomptes.compter("SELECT count(*) FROM audit.demande_droit WHERE statut = 'RECUE' AND echeance_le < ?",
                decomptes.ilYA(ECHEANCE_PROCHE.negated()));
        return List.of(new Indicateur("admin.refus24h", refus, null, refus > 0),
                new Indicateur("admin.demandesDroits", demandes, null, false),
                new Indicateur("admin.echeancesProches", echeances, ECHEANCE_PROCHE.toDays(), echeances > 0));
    }
}
