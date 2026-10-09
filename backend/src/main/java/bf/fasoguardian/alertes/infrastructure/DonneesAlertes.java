package bf.fasoguardian.alertes.infrastructure;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import bf.fasoguardian.audit.DonneesPersonnelles;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Alertes et autorisations de retrait pour l'exercice des droits. Les alertes et leur journal d'acquittement
 * sont conservés cinq ans à titre probatoire (FG-DOC-06, tableau 18) : l'effacement détruit les dossiers de
 * signalement, qui portent l'identité de l'enfant, et clôt les fenêtres de retrait en cours ; les alertes
 * restent, rattachées à un identifiant qui ne désigne plus personne.
 */
@Component
class DonneesAlertes implements DonneesPersonnelles {

    private final JdbcTemplate jdbc;
    private final Clock horloge;

    DonneesAlertes(JdbcTemplate jdbc, Clock horloge) {
        this.jdbc = jdbc;
        this.horloge = horloge;
    }

    @Override
    public String rubrique() {
        return "alertes";
    }

    @Override
    public Map<String, Object> exporter(Personne personne) {
        Map<String, Object> export = new LinkedHashMap<>();
        for (UUID enfant : personne.enfants()) {
            Map<String, Object> enfantExporte = new LinkedHashMap<>();
            enfantExporte.put("alertes", jdbc.queryForList("SELECT type, gravite, statut, libelle, ouverte_le::text AS \"ouverteLe\","
                    + " close_le::text AS \"closeLe\" FROM alertes.alerte WHERE enfant_id = ? ORDER BY ouverte_le", enfant));
            enfantExporte.put("autorisationsDeRetrait", jdbc.queryForList("SELECT motif, statut, debut::text AS debut, fin::text AS fin"
                    + " FROM alertes.autorisation_retrait WHERE enfant_id = ? ORDER BY debut", enfant));
            enfantExporte.put("signalements", jdbc.queryForList("SELECT s.reference, s.canal, s.cree_le::text AS \"creeLe\","
                    + " (s.dossier_chiffre IS NOT NULL) AS \"dossierDisponible\" FROM alertes.signalement_fds s"
                    + " JOIN alertes.alerte a ON a.id = s.alerte_id WHERE a.enfant_id = ? ORDER BY s.cree_le", enfant));
            export.put(enfant.toString(), enfantExporte);
        }
        return export;
    }

    @Override
    public long effacer(Personne personne) {
        Timestamp maintenant = Timestamp.from(horloge.instant());
        long supprimes = 0;
        for (UUID enfant : personne.enfants()) {
            supprimes += jdbc.update("UPDATE alertes.signalement_fds SET dossier_chiffre = NULL, efface_le = ? WHERE dossier_chiffre"
                    + " IS NOT NULL AND alerte_id IN (SELECT id FROM alertes.alerte WHERE enfant_id = ?)", maintenant, enfant);
            jdbc.update("UPDATE alertes.autorisation_retrait SET statut = 'TERMINEE', cloturee_le = ? WHERE enfant_id = ?"
                    + " AND statut = 'ACTIVE'", maintenant, enfant);
        }
        return supprimes;
    }
}
