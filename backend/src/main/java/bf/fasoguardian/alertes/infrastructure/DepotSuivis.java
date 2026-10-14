package bf.fasoguardian.alertes.infrastructure;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Suivis rapprochés en cours : un par enfant au plus. */
@Repository
public class DepotSuivis {

    private final JdbcTemplate jdbc;

    DepotSuivis(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Fin du suivi rapproché de l'enfant, s'il est encore en cours à l'instant donné. */
    public Optional<Instant> jusquAu(UUID enfantId, Instant maintenant) {
        return jdbc.queryForList("SELECT jusqu_au FROM alertes.suivi_rapproche WHERE enfant_id = ? AND jusqu_au > ?", Timestamp.class,
                enfantId, Timestamp.from(maintenant)).stream().findFirst().map(Timestamp::toInstant);
    }

    public void ouvrir(UUID enfantId, UUID tuteurId, Instant debut, Instant fin) {
        jdbc.update("""
                INSERT INTO alertes.suivi_rapproche (enfant_id, demande_par, debut, jusqu_au) VALUES (?, ?, ?, ?)
                ON CONFLICT (enfant_id) DO UPDATE SET demande_par = EXCLUDED.demande_par, debut = EXCLUDED.debut,
                    jusqu_au = EXCLUDED.jusqu_au
                """, enfantId, tuteurId, Timestamp.from(debut), Timestamp.from(fin));
    }

    /** @return {@code true} s'il y avait un suivi à clore */
    public boolean clore(UUID enfantId) {
        return jdbc.update("DELETE FROM alertes.suivi_rapproche WHERE enfant_id = ?", enfantId) == 1;
    }

    /** Retire les suivis échus et rend les enfants concernés. */
    public List<UUID> retirerEchus(Instant maintenant) {
        return jdbc.queryForList("DELETE FROM alertes.suivi_rapproche WHERE jusqu_au <= ? RETURNING enfant_id", UUID.class,
                Timestamp.from(maintenant));
    }
}
