package bf.fasoguardian.dispositifs.infrastructure;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/** Campagnes de mise à jour du logiciel embarqué et bracelets qu'elles visent. */
@Repository
public class DepotOta {

    public record Campagne(UUID id, String version, String urlImage, long tailleOctets, String sha256, String signature, String note,
            String statut, int vagueCourante, Instant creeeLe) {
    }

    /** Bracelet en service, porté par un enfant. */
    public record Porte(UUID braceletId, String version, UUID enfantId) {
    }

    /** @param installes bracelets de la vague qui exécutent déjà la version de la campagne */
    public record Avancement(int vague, int cibles, int installes) {
    }

    private static final RowMapper<Campagne> CAMPAGNE = (rs, i) -> new Campagne(rs.getObject("id", UUID.class), rs.getString("version"),
            rs.getString("url_image"), rs.getLong("taille_octets"), rs.getString("sha256"), rs.getString("signature"), rs.getString("note"),
            rs.getString("statut"), rs.getInt("vague_courante"), rs.getTimestamp("creee_le").toInstant());

    private final JdbcTemplate jdbc;

    DepotOta(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Campagne> campagnes() {
        return jdbc.query("SELECT * FROM dispositifs.campagne_ota ORDER BY creee_le DESC", CAMPAGNE);
    }

    public Optional<Campagne> campagne(UUID id) {
        return jdbc.query("SELECT * FROM dispositifs.campagne_ota WHERE id = ? FOR UPDATE", CAMPAGNE, id).stream().findFirst();
    }

    public boolean versionDejaPubliee(String version) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM dispositifs.campagne_ota WHERE version = ?)",
                Boolean.class, version));
    }

    /** @return version d'une autre campagne en cours, s'il y en a une */
    public Optional<String> autreEnCours(UUID campagneId) {
        return jdbc.queryForList("SELECT version FROM dispositifs.campagne_ota WHERE statut = 'EN_COURS' AND id <> ? LIMIT 1",
                String.class, campagneId).stream().findFirst();
    }

    public void creer(Campagne campagne, UUID agentId) {
        jdbc.update("""
                INSERT INTO dispositifs.campagne_ota (id, version, url_image, taille_octets, sha256, signature, note, statut,
                        vague_courante, creee_par, creee_le)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, campagne.id(), campagne.version(), campagne.urlImage(), campagne.tailleOctets(), campagne.sha256(),
                campagne.signature(), campagne.note(), campagne.statut(), campagne.vagueCourante(), agentId,
                Timestamp.from(campagne.creeeLe()));
    }

    public void changerStatut(UUID id, String statut) {
        jdbc.update("UPDATE dispositifs.campagne_ota SET statut = ? WHERE id = ?", statut, id);
    }

    public void noterVague(UUID id, int vague) {
        jdbc.update("UPDATE dispositifs.campagne_ota SET vague_courante = ?, statut = 'EN_COURS' WHERE id = ?", vague, id);
    }

    /** Bracelets actifs, non révoqués et portés par un enfant, dans l'ordre de leur numéro. */
    public List<Porte> parcPorte() {
        return jdbc.query("""
                SELECT b.id, b.version_logiciel, a.enfant_id FROM dispositifs.bracelet b
                JOIN dispositifs.appairage a ON a.bracelet_id = b.id AND a.fin IS NULL
                WHERE b.statut = 'ACTIF' AND b.certificat_revoque_le IS NULL ORDER BY b.numero_serie
                """, (rs, i) -> new Porte(rs.getObject("id", UUID.class), rs.getString("version_logiciel"),
                rs.getObject("enfant_id", UUID.class)));
    }

    public List<UUID> cibles(UUID campagneId) {
        return jdbc.queryForList("SELECT bracelet_id FROM dispositifs.cible_ota WHERE campagne_id = ?", UUID.class, campagneId);
    }

    public void viser(UUID campagneId, UUID braceletId, int vague, Instant maintenant) {
        jdbc.update("INSERT INTO dispositifs.cible_ota (campagne_id, bracelet_id, vague, emise_le) VALUES (?, ?, ?, ?)", campagneId,
                braceletId, vague, Timestamp.from(maintenant));
    }

    public List<Avancement> avancement(UUID campagneId) {
        return jdbc.query("""
                SELECT vague, count(*) AS cibles, count(installee_le) AS installes FROM dispositifs.cible_ota
                WHERE campagne_id = ? GROUP BY vague ORDER BY vague
                """, (rs, i) -> new Avancement(rs.getInt("vague"), rs.getInt("cibles"), rs.getInt("installes")), campagneId);
    }

    /** Marque installée la mise à jour que ce bracelet attendait pour cette version. @return campagnes concernées */
    public List<UUID> noterInstallation(UUID braceletId, String version, Instant maintenant) {
        return jdbc.queryForList("""
                UPDATE dispositifs.cible_ota c SET installee_le = ? FROM dispositifs.campagne_ota o
                WHERE o.id = c.campagne_id AND o.version = ? AND c.bracelet_id = ? AND c.installee_le IS NULL
                RETURNING c.campagne_id
                """, UUID.class, Timestamp.from(maintenant), version, braceletId);
    }

    public int enAttente(UUID campagneId) {
        Integer nombre = jdbc.queryForObject("SELECT count(*) FROM dispositifs.cible_ota WHERE campagne_id = ? AND installee_le IS NULL",
                Integer.class, campagneId);
        return nombre == null ? 0 : nombre;
    }

    /** Bracelets visés par une campagne en cours, sans installation, dont la dernière demande date d'avant la limite. */
    public List<UUID[]> aRelancer(Instant limite) {
        return jdbc.query("""
                SELECT c.campagne_id, c.bracelet_id FROM dispositifs.cible_ota c
                JOIN dispositifs.campagne_ota o ON o.id = c.campagne_id AND o.statut = 'EN_COURS'
                WHERE c.installee_le IS NULL AND c.emise_le < ?
                """, (rs, i) -> new UUID[] {rs.getObject(1, UUID.class), rs.getObject(2, UUID.class)}, Timestamp.from(limite));
    }

    public void noterRelance(UUID campagneId, UUID braceletId, Instant maintenant) {
        jdbc.update("UPDATE dispositifs.cible_ota SET emise_le = ? WHERE campagne_id = ? AND bracelet_id = ?", Timestamp.from(maintenant),
                campagneId, braceletId);
    }
}
