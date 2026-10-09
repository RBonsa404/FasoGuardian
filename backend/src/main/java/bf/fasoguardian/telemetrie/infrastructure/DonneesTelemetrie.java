package bf.fasoguardian.telemetrie.infrastructure;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import bf.fasoguardian.audit.DonneesPersonnelles;
import bf.fasoguardian.dispositifs.Bracelets;
import bf.fasoguardian.dispositifs.Bracelets.Periode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Positions et événements des bracelets pour l'exercice des droits. Un bracelet change de porteur : seules
 * les mesures prises pendant les appairages de l'enfant sont les siennes.
 */
@Component
class DonneesTelemetrie implements DonneesPersonnelles {

    /** Au-delà, l'export ne garde que les positions les plus récentes et dit combien il en existe. */
    private static final int POSITIONS_EXPORTEES = 5000;
    private static final Timestamp SANS_FIN = Timestamp.from(Instant.parse("9999-01-01T00:00:00Z"));

    private final JdbcTemplate jdbc;
    private final Bracelets bracelets;

    DonneesTelemetrie(JdbcTemplate jdbc, Bracelets bracelets) {
        this.jdbc = jdbc;
        this.bracelets = bracelets;
    }

    @Override
    public String rubrique() {
        return "positions";
    }

    @Override
    public int ordre() {
        return 10;
    }

    @Override
    public Map<String, Object> exporter(Personne personne) {
        Map<String, Object> export = new LinkedHashMap<>();
        for (UUID enfant : personne.enfants()) {
            long total = 0;
            List<Map<String, Object>> positions = new ArrayList<>();
            for (Periode periode : bracelets.periodesDe(enfant)) {
                Object[] bornes = {periode.braceletId(), Timestamp.from(periode.debut()), fin(periode)};
                Long nombre = jdbc.queryForObject("SELECT count(*) FROM telemetrie.position WHERE bracelet_id = ?"
                        + " AND mesuree_le >= ? AND mesuree_le < ?", Long.class, bornes);
                total += nombre == null ? 0 : nombre;
                positions.addAll(jdbc.queryForList("SELECT mesuree_le::text AS \"mesureeLe\", ST_Y(point::geometry) AS latitude,"
                        + " ST_X(point::geometry) AS longitude, precision_m AS \"precisionM\", source FROM telemetrie.position"
                        + " WHERE bracelet_id = ? AND mesuree_le >= ? AND mesuree_le < ? ORDER BY mesuree_le DESC LIMIT "
                        + POSITIONS_EXPORTEES, bornes));
            }
            Map<String, Object> enfantExporte = new LinkedHashMap<>();
            enfantExporte.put("nombreDePositionsConservees", total);
            enfantExporte.put("positions", positions.size() > POSITIONS_EXPORTEES ? positions.subList(0, POSITIONS_EXPORTEES) : positions);
            export.put(enfant.toString(), enfantExporte);
        }
        return export;
    }

    @Override
    public long effacer(Personne personne) {
        long supprimes = 0;
        for (UUID enfant : personne.enfants()) {
            for (Periode periode : bracelets.periodesDe(enfant)) {
                Object[] bornes = {periode.braceletId(), Timestamp.from(periode.debut()), fin(periode)};
                supprimes += jdbc.update("DELETE FROM telemetrie.position WHERE bracelet_id = ? AND mesuree_le >= ? AND mesuree_le < ?", bornes);
                supprimes += jdbc.update("DELETE FROM telemetrie.evenement WHERE bracelet_id = ? AND mesure_le >= ? AND mesure_le < ?", bornes);
                if (periode.fin() == null) {
                    supprimes += jdbc.update("DELETE FROM telemetrie.etat_bracelet WHERE bracelet_id = ?", periode.braceletId());
                }
            }
        }
        return supprimes;
    }

    private static Timestamp fin(Periode periode) {
        return periode.fin() == null ? SANS_FIN : Timestamp.from(periode.fin());
    }
}
