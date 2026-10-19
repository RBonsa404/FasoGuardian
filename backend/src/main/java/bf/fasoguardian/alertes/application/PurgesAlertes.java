package bf.fasoguardian.alertes.application;

import bf.fasoguardian.audit.RegistrePurges;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Durée de conservation des alertes (FG-DOC-06, tableau 18) : une alerte close depuis plus de 5 ans est effacée
 * avec son journal d'acquittement et la trace de son signalement. Le journal reste en ajout seul : la base
 * n'autorise ces suppressions que dans cette transaction et pour ces seules alertes (V35).
 */
@Service
public class PurgesAlertes {

    static final String ECHUES = "SELECT id FROM alertes.alerte WHERE close_le < now() - INTERVAL '5 years'";

    private final JdbcTemplate jdbc;
    private final RegistrePurges registre;

    PurgesAlertes(JdbcTemplate jdbc, RegistrePurges registre) {
        this.jdbc = jdbc;
        this.registre = registre;
    }

    @Scheduled(cron = "${fasoguardian.alertes.purges:0 25 3 * * *}")
    @SchedulerLock(name = "alertes-purges")
    @Transactional
    public void purger() {
        // Marqueur limité à cette transaction : hors purge, le déclencheur refuse toujours la suppression.
        jdbc.execute("SET LOCAL fasoguardian.purge_alertes = 'oui'");
        jdbc.update("DELETE FROM alertes.signalement_fds WHERE alerte_id IN (" + ECHUES + ")");
        jdbc.update("DELETE FROM alertes.action_alerte WHERE alerte_id IN (" + ECHUES + ")");
        registre.consigner("ALERTES", jdbc.update("DELETE FROM alertes.alerte WHERE close_le < now() - INTERVAL '5 years'"));
    }
}
