package bf.fasoguardian.telemetrie.infrastructure;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import bf.fasoguardian.telemetrie.EvenementBraceletRecu;
import bf.fasoguardian.telemetrie.domaine.EtatBracelet;
import bf.fasoguardian.telemetrie.domaine.Mesure;
import bf.fasoguardian.telemetrie.domaine.PositionConnue;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Accès aux tables de télémétrie. Les positions et les événements sont en ajout seul et partitionnés par
 * mois : ils sont écrits en SQL direct, l'unicité (bracelet, séquence, heure de mesure) assurant l'idempotence.
 */
@Repository
public class DepotTelemetrie {

    private final JdbcTemplate jdbc;

    DepotTelemetrie(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** @return {@code false} si cette position était déjà enregistrée */
    public boolean ajouterPosition(UUID braceletId, Mesure mesure, Instant recueLe) {
        return jdbc.update("""
                INSERT INTO telemetrie.position (id, mesuree_le, bracelet_id, point, precision_m, source, sequence, recue_le)
                VALUES (?, ?, ?, ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography, ?, ?, ?, ?)
                ON CONFLICT DO NOTHING
                """, UUID.randomUUID(), Timestamp.from(mesure.mesureeLe()), braceletId, mesure.longitude(),
                mesure.latitude(), mesure.precisionM(), mesure.source().name(), mesure.sequence(),
                Timestamp.from(recueLe)) == 1;
    }

    /** @return {@code false} si cet événement était déjà enregistré */
    public boolean ajouterEvenement(UUID braceletId, EvenementBraceletRecu.Type type, long sequence, Instant mesureLe,
            Double latitude, Double longitude, Instant recuLe) {
        return jdbc.update("""
                INSERT INTO telemetrie.evenement (id, mesure_le, bracelet_id, type, point, sequence, recu_le)
                VALUES (?, ?, ?, ?, CASE WHEN ?::float8 IS NULL THEN NULL
                        ELSE ST_SetSRID(ST_MakePoint(?::float8, ?::float8), 4326)::geography END, ?, ?)
                ON CONFLICT DO NOTHING
                """, UUID.randomUUID(), Timestamp.from(mesureLe), braceletId, type.name(), longitude, longitude,
                latitude, sequence, Timestamp.from(recuLe)) == 1;
    }

    /** Remplace les champs fournis du dernier état connu ; un champ absent du message garde sa valeur. */
    public void enregistrerEtat(UUID braceletId, EtatBracelet etat) {
        jdbc.update("""
                INSERT INTO telemetrie.etat_bracelet AS e (bracelet_id, batterie, signal_dbm, reseau, operateur,
                        en_mouvement, en_ligne, version_logiciel, dernier_contact)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (bracelet_id) DO UPDATE SET
                    batterie = COALESCE(EXCLUDED.batterie, e.batterie),
                    signal_dbm = COALESCE(EXCLUDED.signal_dbm, e.signal_dbm),
                    reseau = COALESCE(EXCLUDED.reseau, e.reseau),
                    operateur = COALESCE(EXCLUDED.operateur, e.operateur),
                    en_mouvement = COALESCE(EXCLUDED.en_mouvement, e.en_mouvement),
                    en_ligne = COALESCE(EXCLUDED.en_ligne, e.en_ligne),
                    version_logiciel = COALESCE(EXCLUDED.version_logiciel, e.version_logiciel),
                    dernier_contact = GREATEST(EXCLUDED.dernier_contact, e.dernier_contact)
                """, braceletId, etat.batterie(), etat.signalDbm(), etat.reseau(), etat.operateur(), etat.enMouvement(),
                etat.enLigne(), etat.versionLogiciel(), Timestamp.from(etat.dernierContact()));
    }

    public Optional<EtatBracelet> etat(UUID braceletId) {
        return jdbc.query("""
                SELECT batterie, signal_dbm, reseau, operateur, en_mouvement, en_ligne, version_logiciel, dernier_contact
                FROM telemetrie.etat_bracelet WHERE bracelet_id = ?
                """, (rs, i) -> new EtatBracelet((Integer) rs.getObject("batterie", Integer.class),
                rs.getObject("signal_dbm", Integer.class), rs.getString("reseau"), rs.getString("operateur"),
                rs.getObject("en_mouvement", Boolean.class), rs.getObject("en_ligne", Boolean.class),
                rs.getString("version_logiciel"), rs.getTimestamp("dernier_contact").toInstant()), braceletId)
                .stream().findFirst();
    }

    /** Dernière position mesurée depuis l'instant donné (début de l'appairage en cours). */
    public Optional<PositionConnue> dernierePosition(UUID braceletId, Instant depuis) {
        return jdbc.query("""
                SELECT ST_Y(point::geometry) AS latitude, ST_X(point::geometry) AS longitude, precision_m, source, mesuree_le
                FROM telemetrie.position WHERE bracelet_id = ? AND mesuree_le >= ?
                ORDER BY mesuree_le DESC LIMIT 1
                """, (rs, i) -> new PositionConnue(rs.getDouble("latitude"), rs.getDouble("longitude"),
                rs.getInt("precision_m"), Mesure.Source.valueOf(rs.getString("source")),
                rs.getTimestamp("mesuree_le").toInstant()), braceletId, Timestamp.from(depuis))
                .stream().findFirst();
    }

    /** Positions mesurées dans l'intervalle, dans l'ordre chronologique, bornées à {@code limite} points. */
    public List<PositionConnue> positionsEntre(UUID braceletId, Instant debut, Instant fin, int limite) {
        return jdbc.query("""
                SELECT ST_Y(point::geometry) AS latitude, ST_X(point::geometry) AS longitude, precision_m, source, mesuree_le
                FROM telemetrie.position WHERE bracelet_id = ? AND mesuree_le >= ? AND mesuree_le < ?
                ORDER BY mesuree_le LIMIT ?
                """, (rs, i) -> new PositionConnue(rs.getDouble("latitude"), rs.getDouble("longitude"),
                rs.getInt("precision_m"), Mesure.Source.valueOf(rs.getString("source")),
                rs.getTimestamp("mesuree_le").toInstant()), braceletId, Timestamp.from(debut), Timestamp.from(fin), limite);
    }

    public void creerPartitions(LocalDate jour) {
        for (String table : new String[] {"position", "evenement"}) {
            jdbc.query("SELECT telemetrie.creer_partition(?, ?)", rs -> { }, table, java.sql.Date.valueOf(jour));
        }
    }

    /** Efface les positions antérieures à la limite : partitions entières, puis lignes de la partition entamée. */
    public int purgerPositions(Instant limite) {
        LocalDate jour = LocalDate.ofInstant(limite, java.time.ZoneOffset.UTC);
        jdbc.query("SELECT telemetrie.purger_partitions('position', ?)", rs -> { }, java.sql.Date.valueOf(jour));
        return jdbc.update("DELETE FROM telemetrie.position WHERE mesuree_le < ?", Timestamp.from(limite));
    }
}
