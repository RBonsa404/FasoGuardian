package bf.fasoguardian.telemetrie.infrastructure;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import bf.fasoguardian.telemetrie.domaine.Passerelle;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/** Registre des passerelles LoRaWAN en service. */
@Repository
public class DepotPasserelles {

    private static final String COLONNES = """
            SELECT id, eui, etablissement, ST_Y(centre::geometry) AS latitude, ST_X(centre::geometry) AS longitude, rayon_m,
                   creee_le, vue_le
            FROM telemetrie.passerelle WHERE retiree_le IS NULL""";

    private static final RowMapper<Passerelle> LIGNE = (rs, i) -> new Passerelle(rs.getObject("id", UUID.class), rs.getString("eui"),
            rs.getString("etablissement"), rs.getDouble("latitude"), rs.getDouble("longitude"), rs.getInt("rayon_m"),
            rs.getTimestamp("creee_le").toInstant(), rs.getTimestamp("vue_le") == null ? null : rs.getTimestamp("vue_le").toInstant());

    private final JdbcTemplate jdbc;

    DepotPasserelles(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Passerelle> enService() {
        return jdbc.query(COLONNES + " ORDER BY etablissement, eui", LIGNE);
    }

    public Optional<Passerelle> parEui(String eui) {
        return jdbc.query(COLONNES + " AND eui = ?", LIGNE, eui).stream().findFirst();
    }

    public Optional<Passerelle> parId(UUID id) {
        return jdbc.query(COLONNES + " AND id = ?", LIGNE, id).stream().findFirst();
    }

    /** @return {@code true} si une passerelle porte déjà cet identifiant, en service ou retirée */
    public boolean existe(String eui) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM telemetrie.passerelle WHERE eui = ?)", Boolean.class, eui));
    }

    public void ajouter(Passerelle passerelle) {
        jdbc.update("""
                INSERT INTO telemetrie.passerelle (id, eui, etablissement, centre, rayon_m, creee_le)
                VALUES (?, ?, ?, ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography, ?, ?)
                """, passerelle.id(), passerelle.eui(), passerelle.etablissement(), passerelle.longitude(), passerelle.latitude(),
                passerelle.rayonM(), Timestamp.from(passerelle.creeeLe()));
    }

    public void noterVue(UUID id, Instant maintenant) {
        jdbc.update("UPDATE telemetrie.passerelle SET vue_le = ? WHERE id = ?", Timestamp.from(maintenant), id);
    }

    public void retirer(UUID id, Instant maintenant) {
        jdbc.update("UPDATE telemetrie.passerelle SET retiree_le = ? WHERE id = ? AND retiree_le IS NULL", Timestamp.from(maintenant), id);
    }
}
