package bf.fasoguardian.famille.application;

import bf.fasoguardian.audit.RegistrePurges;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Durées de conservation du module (FG-DOC-06, tableau 18) : le journal des consultations de la page QR est
 * gardé 12 mois, les numéros laissés par les tiers 30 jours. Le même traitement prépare les partitions
 * mensuelles à venir du journal des consultations.
 */
@Service
public class PurgesFamille {

    private static final int MOIS_CONSERVES = 12;

    private final JdbcTemplate jdbc;
    private final RegistrePurges registre;

    PurgesFamille(JdbcTemplate jdbc, RegistrePurges registre) {
        this.jdbc = jdbc;
        this.registre = registre;
    }

    @Scheduled(cron = "${fasoguardian.famille.purges:0 5 3 * * *}")
    @SchedulerLock(name = "famille-purges")
    @Transactional
    public void purger() {
        for (int mois = 0; mois <= 2; mois++) {
            jdbc.query("SELECT famille.creer_partition_consultation_qr((CURRENT_DATE + make_interval(months => ?))::date)",
                    ligne -> { }, mois);
        }
        Long echues = jdbc.queryForObject("SELECT count(*) FROM famille.consultation_qr WHERE consulte_le < now() - INTERVAL '"
                + MOIS_CONSERVES + " months'", Long.class);
        // Partitions entièrement échues, puis lignes échues de la partition entamée.
        jdbc.query("SELECT famille.purger_consultations_qr(?)", ligne -> { }, MOIS_CONSERVES);
        jdbc.update("DELETE FROM famille.consultation_qr WHERE consulte_le < now() - INTERVAL '" + MOIS_CONSERVES + " months'");
        registre.consigner("CONSULTATIONS_PAGE_QR", echues == null ? 0 : echues);
        registre.consigner("NUMEROS_DES_TIERS",
                jdbc.update("DELETE FROM famille.signalement_tiers WHERE recu_le < now() - INTERVAL '30 days'"));
    }
}
