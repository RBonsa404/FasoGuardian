package bf.fasoguardian.identite.application;

import bf.fasoguardian.audit.RegistrePurges;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Pièces et dossiers KYC : conservés la durée de la relation plus un an, puis détruits (FG-DOC-06, tableau 18). */
@Service
public class PurgeKyc {

    private static final String DOSSIERS_ECHUS = "SELECT d.id FROM identite.dossier_kyc d JOIN identite.utilisateur u"
            + " ON u.id = d.demandeur_id WHERE u.clos_le < now() - INTERVAL '1 year'";

    private final JdbcTemplate jdbc;
    private final RegistrePurges registre;

    PurgeKyc(JdbcTemplate jdbc, RegistrePurges registre) {
        this.jdbc = jdbc;
        this.registre = registre;
    }

    @Scheduled(cron = "${fasoguardian.identite.purge-kyc:0 0 3 * * *}")
    @SchedulerLock(name = "identite-purge-kyc")
    @Transactional
    public void purger() {
        long supprimes = jdbc.update("DELETE FROM identite.piece_justificative WHERE dossier_id IN (" + DOSSIERS_ECHUS + ")");
        supprimes += jdbc.update("DELETE FROM identite.dossier_kyc WHERE id IN (" + DOSSIERS_ECHUS + ")");
        registre.consigner("PIECES_KYC", supprimes);
    }
}
