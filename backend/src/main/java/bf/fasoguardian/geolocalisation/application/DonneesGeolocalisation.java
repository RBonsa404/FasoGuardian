package bf.fasoguardian.geolocalisation.application;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import bf.fasoguardian.audit.DonneesPersonnelles;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Safe Zones des enfants et franchissements enregistrés, pour l'exercice des droits. */
@Component
class DonneesGeolocalisation implements DonneesPersonnelles {

    private static final String ZONES_DE_L_ENFANT = "SELECT id FROM geolocalisation.safe_zone WHERE enfant_id = ?";

    private final JdbcTemplate jdbc;
    private final SafeZones zones;

    DonneesGeolocalisation(JdbcTemplate jdbc, SafeZones zones) {
        this.jdbc = jdbc;
        this.zones = zones;
    }

    @Override
    public String rubrique() {
        return "safeZones";
    }

    @Override
    public Map<String, Object> exporter(Personne personne) {
        Map<String, Object> export = new LinkedHashMap<>();
        for (UUID enfant : personne.enfants()) {
            export.put(enfant.toString(), jdbc.queryForList("SELECT z.nom, z.categorie, z.type AS forme, z.rayon_m AS \"rayonM\","
                    + " z.statut, z.cree_le::text AS \"creeLe\", (SELECT count(*) FROM geolocalisation.franchissement f"
                    + " WHERE f.zone_id = z.id) AS franchissements FROM geolocalisation.safe_zone z WHERE z.enfant_id = ?"
                    + " ORDER BY z.cree_le", enfant));
        }
        export.put("partagesDePosition", jdbc.queryForList("SELECT enfant_id::text AS enfant, destinataire_lien AS contact,"
                + " destinataire_masque AS numero, debut::text AS debut, fin::text AS fin, revoque_le::text AS \"revoqueLe\","
                + " ouvertures FROM geolocalisation.partage_position WHERE cree_par = ? ORDER BY debut", personne.tuteurId()));
        return export;
    }

    @Override
    public long effacer(Personne personne) {
        long supprimes = 0;
        for (UUID enfant : personne.enfants()) {
            supprimes += jdbc.update("DELETE FROM geolocalisation.franchissement WHERE zone_id IN (" + ZONES_DE_L_ENFANT + ")", enfant);
            supprimes += jdbc.update("DELETE FROM geolocalisation.suivi_zone WHERE zone_id IN (" + ZONES_DE_L_ENFANT + ")", enfant);
            supprimes += jdbc.update("DELETE FROM geolocalisation.safe_zone WHERE enfant_id = ?", enfant);
            supprimes += jdbc.update("DELETE FROM geolocalisation.partage_position WHERE enfant_id = ?", enfant);
            zones.oublier(enfant);
        }
        return supprimes;
    }
}
