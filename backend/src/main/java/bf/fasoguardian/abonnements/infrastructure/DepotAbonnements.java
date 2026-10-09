package bf.fasoguardian.abonnements.infrastructure;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import bf.fasoguardian.abonnements.domaine.Abonnement;
import bf.fasoguardian.abonnements.domaine.Abonnement.Statut;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DepotAbonnements extends JpaRepository<Abonnement, UUID> {

    Optional<Abonnement> findByEnfantId(UUID enfantId);

    boolean existsByTuteurIdInAndOffreCodeAndStatutIn(Collection<UUID> tuteurs, String offreCode, Collection<Statut> statuts);

    List<Abonnement> findByStatutIn(Collection<Statut> statuts);

    /** Abonnements dont l'échéance approche ou est dépassée et dont les relances ne sont pas épuisées. */
    List<Abonnement> findByProchaineEcheanceLessThanEqualAndEtapeRelanceLessThan(LocalDate limite, int etape);
}
