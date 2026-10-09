package bf.fasoguardian.geolocalisation.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import bf.fasoguardian.geolocalisation.domaine.Coordonnee;
import bf.fasoguardian.geolocalisation.domaine.PlageHoraire;
import bf.fasoguardian.geolocalisation.domaine.SafeZone;
import bf.fasoguardian.geolocalisation.domaine.SafeZone.Categorie;
import bf.fasoguardian.geolocalisation.domaine.SafeZone.Statut;
import bf.fasoguardian.geolocalisation.domaine.SuiviZone;
import bf.fasoguardian.geolocalisation.domaine.ZoneCirculaire;
import bf.fasoguardian.geolocalisation.domaine.ZonePolygonale;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Accès aux Safe Zones. Les formes sont stockées en types géographiques PostGIS (index GiST) ; l'héritage
 * SafeZone tient dans une table unique à colonne discriminante, et la version porte le verrouillage optimiste.
 */
@Repository
public class DepotZones {

    private static final String COLONNES = """
            id, enfant_id, type, nom, categorie, ST_Y(centre::geometry) AS latitude, ST_X(centre::geometry) AS longitude,
            rayon_m, ST_AsText(polygone::geometry) AS wkt, plage::text AS plage, tolerance_s, statut, version
            """;

    private final JdbcTemplate jdbc;
    private final JsonMapper json;

    DepotZones(JdbcTemplate jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public List<SafeZone> deLEnfant(UUID enfantId) {
        return jdbc.query("SELECT " + COLONNES + " FROM geolocalisation.safe_zone WHERE enfant_id = ? ORDER BY cree_le",
                this::lire, enfantId);
    }

    public Optional<SafeZone> parId(UUID zoneId) {
        return jdbc.query("SELECT " + COLONNES + " FROM geolocalisation.safe_zone WHERE id = ?", this::lire, zoneId)
                .stream().findFirst();
    }

    public void creer(SafeZone zone, Instant maintenant) {
        jdbc.update("""
                INSERT INTO geolocalisation.safe_zone (id, enfant_id, type, nom, categorie, centre, rayon_m, polygone, plage,
                        tolerance_s, statut, cree_le, modifie_le)
                VALUES (?, ?, ?, ?, ?, ST_GeogFromText(?), ?, ST_GeogFromText(?), ?::jsonb, ?, ?, ?, ?)
                """, zone.id(), zone.enfantId(), type(zone), zone.nom(), zone.categorie().name(), centre(zone),
                rayon(zone), polygone(zone), plage(zone.plage()), zone.toleranceS(), zone.statut().name(),
                Timestamp.from(maintenant), Timestamp.from(maintenant));
    }

    /** @return {@code false} si la zone a été modifiée entre-temps (version dépassée) */
    public boolean remplacer(SafeZone zone, Instant maintenant) {
        return jdbc.update("""
                UPDATE geolocalisation.safe_zone SET type = ?, nom = ?, categorie = ?, centre = ST_GeogFromText(?),
                        rayon_m = ?, polygone = ST_GeogFromText(?), plage = ?::jsonb, tolerance_s = ?, statut = ?,
                        modifie_le = ?, version = version + 1
                WHERE id = ? AND version = ?
                """, type(zone), zone.nom(), zone.categorie().name(), centre(zone), rayon(zone), polygone(zone),
                plage(zone.plage()), zone.toleranceS(), zone.statut().name(), Timestamp.from(maintenant), zone.id(),
                zone.version()) == 1;
    }

    public boolean changerStatut(UUID zoneId, Statut statut, long version, Instant maintenant) {
        return jdbc.update("""
                UPDATE geolocalisation.safe_zone SET statut = ?, modifie_le = ?, version = version + 1
                WHERE id = ? AND version = ?
                """, statut.name(), Timestamp.from(maintenant), zoneId, version) == 1;
    }

    public void supprimer(UUID zoneId) {
        jdbc.update("DELETE FROM geolocalisation.safe_zone WHERE id = ?", zoneId);
    }

    /**
     * Sérialise, jusqu'à la fin de la transaction, l'évaluation des positions d'un même enfant : deux messages
     * traités en parallèle ne se marchent pas dessus.
     */
    public void verrouillerSuivi(UUID enfantId) {
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", rs -> { }, "suivi-zone:" + enfantId);
    }

    public Optional<SuiviZone> suivi(UUID zoneId) {
        return jdbc.query("""
                SELECT vu_dedans, dehors_depuis, sortie_signalee, derniere_mesure FROM geolocalisation.suivi_zone
                WHERE zone_id = ?
                """, (rs, i) -> new SuiviZone(rs.getBoolean("vu_dedans"),
                rs.getTimestamp("dehors_depuis") == null ? null : rs.getTimestamp("dehors_depuis").toInstant(),
                rs.getBoolean("sortie_signalee"), rs.getTimestamp("derniere_mesure").toInstant()), zoneId)
                .stream().findFirst();
    }

    public void enregistrerSuivi(UUID zoneId, SuiviZone suivi) {
        jdbc.update("""
                INSERT INTO geolocalisation.suivi_zone (zone_id, vu_dedans, dehors_depuis, sortie_signalee, derniere_mesure)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (zone_id) DO UPDATE SET vu_dedans = EXCLUDED.vu_dedans, dehors_depuis = EXCLUDED.dehors_depuis,
                        sortie_signalee = EXCLUDED.sortie_signalee, derniere_mesure = EXCLUDED.derniere_mesure
                """, zoneId, suivi.vuDedans(), suivi.dehorsDepuis() == null ? null : Timestamp.from(suivi.dehorsDepuis()),
                suivi.sortieSignalee(), Timestamp.from(suivi.derniereMesure()));
    }

    /** Hors plage horaire ou zone suspendue : le suivi repart de zéro à la prochaine plage. */
    public void oublierSuivi(UUID zoneId) {
        jdbc.update("DELETE FROM geolocalisation.suivi_zone WHERE zone_id = ?", zoneId);
    }

    public void ajouterFranchissement(UUID zoneId, String type, Coordonnee point, Instant mesureLe, Instant detecteLe) {
        jdbc.update("""
                INSERT INTO geolocalisation.franchissement (id, zone_id, type, point, mesure_le, detecte_le)
                VALUES (?, ?, ?, ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography, ?, ?)
                """, UUID.randomUUID(), zoneId, type, point.longitude(), point.latitude(), Timestamp.from(mesureLe),
                Timestamp.from(detecteLe));
    }

    // -------------------------------------------------------------------- aides

    private SafeZone lire(ResultSet rs, int ligne) throws SQLException {
        UUID id = rs.getObject("id", UUID.class);
        UUID enfantId = rs.getObject("enfant_id", UUID.class);
        Categorie categorie = Categorie.valueOf(rs.getString("categorie"));
        Statut statut = Statut.valueOf(rs.getString("statut"));
        PlageHoraire plage = plage(rs.getString("plage"));
        if ("CERCLE".equals(rs.getString("type"))) {
            return new ZoneCirculaire(id, enfantId, rs.getString("nom"), categorie, plage, rs.getInt("tolerance_s"), statut,
                    rs.getLong("version"), new Coordonnee(rs.getDouble("latitude"), rs.getDouble("longitude")),
                    rs.getInt("rayon_m"));
        }
        return new ZonePolygonale(id, enfantId, rs.getString("nom"), categorie, plage, rs.getInt("tolerance_s"), statut,
                rs.getLong("version"), sommets(rs.getString("wkt")));
    }

    private static String type(SafeZone zone) {
        return zone instanceof ZoneCirculaire ? "CERCLE" : "POLYGONE";
    }

    private static String centre(SafeZone zone) {
        return zone instanceof ZoneCirculaire cercle
                ? String.format(Locale.ROOT, "POINT(%.7f %.7f)", cercle.centre().longitude(), cercle.centre().latitude())
                : null;
    }

    private static Integer rayon(SafeZone zone) {
        return zone instanceof ZoneCirculaire cercle ? cercle.rayonM() : null;
    }

    /** Anneau fermé au format WKT, longitude avant latitude. */
    private static String polygone(SafeZone zone) {
        if (!(zone instanceof ZonePolygonale polygone)) {
            return null;
        }
        List<Coordonnee> anneau = new ArrayList<>(polygone.sommets());
        anneau.add(anneau.get(0));
        return anneau.stream().map(c -> String.format(Locale.ROOT, "%.7f %.7f", c.longitude(), c.latitude()))
                .collect(Collectors.joining(",", "POLYGON((", "))"));
    }

    private static List<Coordonnee> sommets(String wkt) {
        String[] points = wkt.substring(wkt.indexOf("((") + 2, wkt.indexOf("))")).split(",");
        List<Coordonnee> sommets = new ArrayList<>();
        // Le dernier point de l'anneau répète le premier.
        for (int i = 0; i < points.length - 1; i++) {
            String[] xy = points[i].trim().split(" ");
            sommets.add(new Coordonnee(Double.parseDouble(xy[1]), Double.parseDouble(xy[0])));
        }
        return sommets;
    }

    private String plage(PlageHoraire plage) {
        return json.writeValueAsString(java.util.Map.of(
                "jours", plage.jours().stream().map(DayOfWeek::getValue).sorted().toList(),
                "debut", plage.debut().toString(), "fin", plage.fin().toString()));
    }

    private PlageHoraire plage(String texte) {
        JsonNode noeud = json.readTree(texte);
        Set<DayOfWeek> jours = new java.util.HashSet<>();
        noeud.path("jours").forEach(jour -> jours.add(DayOfWeek.of(jour.asInt())));
        return new PlageHoraire(jours, LocalTime.parse(noeud.path("debut").asString()),
                LocalTime.parse(noeud.path("fin").asString()));
    }
}
