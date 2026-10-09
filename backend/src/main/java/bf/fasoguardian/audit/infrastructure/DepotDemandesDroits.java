package bf.fasoguardian.audit.infrastructure;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import bf.fasoguardian.audit.domaine.DemandeDroit;
import bf.fasoguardian.audit.domaine.DemandeDroit.Statut;
import bf.fasoguardian.audit.domaine.DemandeDroit.Type;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface DepotDemandesDroits extends JpaRepository<DemandeDroit, UUID> {

    List<DemandeDroit> findByTypeOrderByRecueLeDesc(Type type);

    Optional<DemandeDroit> findByDemandeurIdAndTypeAndStatut(UUID demandeurId, Type type, Statut statut);

    List<DemandeDroit> findByTypeAndStatutAndRecueLeBefore(Type type, Statut statut, Instant limite);

    long countByTypeAndRecueLeBetween(Type type, Instant debut, Instant fin);

    long countByTypeAndStatut(Type type, Statut statut);

    @Query(value = "SELECT nextval('audit.reference_demande')", nativeQuery = true)
    long prochainNumero();
}
