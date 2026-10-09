package bf.fasoguardian.identite;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Liens de tutelle vérifiés par le KYC, exposés aux autres modules. Un parent n'accède qu'aux enfants
 * auxquels le rattache un lien actif (FG-DOC-06 §8.2) ; un lien suspendu pour litige ne donne aucun accès.
 */
public interface LiensTutelle {

    /** Identité de l'enfant telle que déclarée dans le dossier KYC approuvé. */
    record EnfantVerifie(UUID enfantId, String prenom, String nom, LocalDate dateNaissance) {
    }

    boolean estTuteurActif(UUID tuteurId, UUID enfantId);

    List<UUID> enfantsDe(UUID tuteurId);

    List<UUID> tuteursActifsDe(UUID enfantId);

    /** Premier prénom du tuteur, tel que vérifié par son dossier KYC ; vide s'il n'a pas de dossier approuvé. */
    Optional<String> prenomDuTuteur(UUID tuteurId);

    /** Identité déclarée de l'enfant d'un dossier approuvé ; réservé à la création de sa fiche par famille. */
    EnfantVerifie enfantDuDossier(UUID dossierId);
}
