package bf.fasoguardian.alertes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import bf.fasoguardian.TestIntegration;
import bf.fasoguardian.alertes.application.PurgesAlertes;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** Conservation des alertes : 5 ans après la clôture, puis effacement ; le journal reste en ajout seul. */
class PurgesAlertesIT extends TestIntegration {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PurgesAlertes purges;

    @Test
    void uneAlerteCloseDepuisPlusDeCinqAnsEstEffaceeAvecSonJournalEtLesAutresRestent() {
        UUID ancienne = alerte("LEVEE", "now() - INTERVAL '5 years 2 days'");
        UUID recente = alerte("LEVEE", "now() - INTERVAL '4 years'");
        UUID ouverte = alerte("OUVERTE", null);

        purges.purger();

        assertThat(compter("alertes.alerte", ancienne)).isZero();
        assertThat(compter("alertes.action_alerte", ancienne)).isZero();
        assertThat(compter("alertes.alerte", recente)).isEqualTo(1);
        assertThat(compter("alertes.action_alerte", recente)).isEqualTo(2);
        assertThat(compter("alertes.alerte", ouverte)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit.execution_purge WHERE traitement = 'ALERTES'", Long.class)).isPositive();
    }

    @Test
    void horsDeLaPurgeLeJournalNeSeModifieNiNeSEfface() {
        UUID ancienne = alerte("LEVEE", "now() - INTERVAL '6 years'");

        assertThatThrownBy(() -> jdbc.update("DELETE FROM alertes.action_alerte WHERE alerte_id = ?", ancienne))
                .hasMessageContaining("ajout seul");
        assertThatThrownBy(() -> jdbc.update("UPDATE alertes.action_alerte SET motif = 'x' WHERE alerte_id = ?", ancienne))
                .hasMessageContaining("ajout seul");
    }

    private UUID alerte(String statut, String closeLe) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO alertes.alerte (id, enfant_id, type, gravite, statut, ouverte_le, close_le) VALUES (?, ?, 'SOS', 'CRITIQUE', ?, "
                + "now() - INTERVAL '6 years', " + (closeLe == null ? "NULL" : closeLe) + ")", id, UUID.randomUUID(), statut);
        jdbc.update("INSERT INTO alertes.action_alerte (id, alerte_id, type, effectuee_le) VALUES (?, ?, 'OUVERTURE', now() - INTERVAL '6 years')",
                UUID.randomUUID(), id);
        if (closeLe != null) {
            jdbc.update("INSERT INTO alertes.action_alerte (id, alerte_id, type, effectuee_le) VALUES (?, ?, 'LEVEE', " + closeLe + ")",
                    UUID.randomUUID(), id);
        }
        return id;
    }

    private long compter(String table, UUID alerte) {
        String colonne = table.endsWith("action_alerte") ? "alerte_id" : "id";
        return jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE " + colonne + " = ?", Long.class, alerte);
    }
}
