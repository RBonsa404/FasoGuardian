package bf.fasoguardian.famille.application;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import bf.fasoguardian.audit.DonneesPersonnelles;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Fiches des enfants, fiches santé et contacts d'urgence, pour l'exercice des droits. */
@Component
class DonneesFamille implements DonneesPersonnelles {

    private final Familles familles;
    private final DossierMedical dossiers;
    private final JdbcTemplate jdbc;

    DonneesFamille(Familles familles, DossierMedical dossiers, JdbcTemplate jdbc) {
        this.familles = familles;
        this.dossiers = dossiers;
        this.jdbc = jdbc;
    }

    @Override
    public String rubrique() {
        return "enfants";
    }

    @Override
    public Map<String, Object> exporter(Personne personne) {
        Map<String, Object> export = new LinkedHashMap<>();
        export.put("fiches", familles.enfantsDe(personne.tuteurId()).stream().map(fiche -> {
            Map<String, Object> enfant = new LinkedHashMap<>();
            enfant.put("fiche", fiche);
            enfant.put("sante", dossiers.fiche(personne.tuteurId(), fiche.id()));
            enfant.put("contactsDUrgence", dossiers.contacts(personne.tuteurId(), fiche.id()));
            enfant.put("consultationsDeLaPageQr", jdbc.queryForObject(
                    "SELECT count(*) FROM famille.consultation_qr WHERE enfant_id = ?", Long.class, fiche.id()));
            return enfant;
        }).toList());
        return export;
    }

    @Override
    public long effacer(Personne personne) {
        long supprimes = 0;
        for (UUID enfant : personne.enfants()) {
            // Le journal des révisions de santé, en ajout seul, reste : il ne porte que des décomptes.
            for (String table : List.of("contact_urgence", "fiche_sante", "enfant_revision", "profil_qr", "consultation_qr",
                    "signalement_tiers")) {
                supprimes += jdbc.update("DELETE FROM famille." + table + " WHERE enfant_id = ?", enfant);
            }
            supprimes += jdbc.update("DELETE FROM famille.enfant WHERE id = ?", enfant);
        }
        return supprimes;
    }
}
