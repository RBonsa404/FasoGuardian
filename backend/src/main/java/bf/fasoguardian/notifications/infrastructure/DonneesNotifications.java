package bf.fasoguardian.notifications.infrastructure;

import java.util.LinkedHashMap;
import java.util.Map;

import bf.fasoguardian.audit.DonneesPersonnelles;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Notifications reçues et navigateurs abonnés, pour l'exercice des droits. */
@Component
class DonneesNotifications implements DonneesPersonnelles {

    private final JdbcTemplate jdbc;

    DonneesNotifications(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public String rubrique() {
        return "notifications";
    }

    @Override
    public Map<String, Object> exporter(Personne personne) {
        Map<String, Object> export = new LinkedHashMap<>();
        export.put("navigateursAbonnes", jdbc.queryForObject(
                "SELECT count(*) FROM notifications.abonnement_push WHERE destinataire_id = ?", Long.class, personne.tuteurId()));
        export.put("recues", jdbc.queryForList("SELECT modele, titre, texte, creee_le::text AS \"creeeLe\""
                + " FROM notifications.notification WHERE destinataire_id = ? ORDER BY creee_le", personne.tuteurId()));
        return export;
    }

    @Override
    public long effacer(Personne personne) {
        return jdbc.update("DELETE FROM notifications.abonnement_push WHERE destinataire_id = ?", personne.tuteurId())
                + jdbc.update("DELETE FROM notifications.notification WHERE destinataire_id = ?", personne.tuteurId());
    }
}
