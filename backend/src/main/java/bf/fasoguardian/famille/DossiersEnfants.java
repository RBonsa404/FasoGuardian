package bf.fasoguardian.famille;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Éléments d'identification d'un enfant destinés à un dossier de signalement (US-PAR-010). L'appelant a
 * vérifié le lien de tutelle, obtenu le second facteur et journalise la transmission.
 */
public interface DossiersEnfants {

    /** @param informationsMedicales seulement celles que le parent a marquées critiques */
    record Identification(String prenom, String nom, LocalDate dateNaissance, Integer tailleCm, String signesDistinctifs,
            String ecole, String quartier, List<String> informationsMedicales) {
    }

    Identification pourSignalement(UUID enfantId);
}
