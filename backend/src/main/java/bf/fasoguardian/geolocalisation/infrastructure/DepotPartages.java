package bf.fasoguardian.geolocalisation.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import bf.fasoguardian.geolocalisation.domaine.PartagePosition;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DepotPartages {

    private static final String COLONNES = "id, enfant_id, cree_par, destinataire_lien, destinataire_masque, debut, fin, revoque_le,"
            + " ouvertures, derniere_ouverture";

    private final JdbcTemplate jdbc;

    DepotPartages(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void creer(PartagePosition partage, byte[] destinataireChiffre, String jetonSha256) {
        jdbc.update("INSERT INTO geolocalisation.partage_position (id, enfant_id, cree_par, destinataire_lien, destinataire_chiffre,"
                + " destinataire_masque, jeton_sha256, debut, fin) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)", partage.id(), partage.enfantId(),
                partage.creePar(), partage.destinataireLien(), destinataireChiffre, partage.destinataireMasque(), jetonSha256,
                Timestamp.from(partage.debut()), Timestamp.from(partage.fin()));
    }

    /** Partage de l'enfant qui n'est ni échu ni révoqué. */
    public Optional<PartagePosition> enCours(UUID enfantId, Instant maintenant) {
        return jdbc.query("SELECT " + COLONNES + " FROM geolocalisation.partage_position WHERE enfant_id = ? AND revoque_le IS NULL"
                + " AND fin > ? ORDER BY fin DESC LIMIT 1", DepotPartages::lire, enfantId, Timestamp.from(maintenant)).stream().findFirst();
    }

    public Optional<PartagePosition> parJeton(String jetonSha256) {
        return jdbc.query("SELECT " + COLONNES + " FROM geolocalisation.partage_position WHERE jeton_sha256 = ?", DepotPartages::lire,
                jetonSha256).stream().findFirst();
    }

    /** @return le nombre de partages en cours révoqués */
    public int revoquer(UUID enfantId, Instant maintenant) {
        return jdbc.update("UPDATE geolocalisation.partage_position SET revoque_le = ?, version = version + 1 WHERE enfant_id = ?"
                + " AND revoque_le IS NULL AND fin > ?", Timestamp.from(maintenant), enfantId, Timestamp.from(maintenant));
    }

    public void noterOuverture(UUID partageId, Instant maintenant) {
        jdbc.update("UPDATE geolocalisation.partage_position SET ouvertures = ouvertures + 1, derniere_ouverture = ? WHERE id = ?",
                Timestamp.from(maintenant), partageId);
    }

    /** Efface les partages terminés avant la limite : ils portent le numéro, chiffré, d'un tiers. */
    public int purger(Instant limite) {
        return jdbc.update("DELETE FROM geolocalisation.partage_position WHERE fin < ?", Timestamp.from(limite));
    }

    private static PartagePosition lire(ResultSet ligne, int rang) throws SQLException {
        Timestamp revoque = ligne.getTimestamp("revoque_le");
        Timestamp ouverture = ligne.getTimestamp("derniere_ouverture");
        return new PartagePosition(ligne.getObject("id", UUID.class), ligne.getObject("enfant_id", UUID.class),
                ligne.getObject("cree_par", UUID.class), ligne.getString("destinataire_lien"), ligne.getString("destinataire_masque"),
                ligne.getTimestamp("debut").toInstant(), ligne.getTimestamp("fin").toInstant(),
                revoque == null ? null : revoque.toInstant(), ligne.getInt("ouvertures"), ouverture == null ? null : ouverture.toInstant());
    }
}
