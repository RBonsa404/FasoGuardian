package bf.fasoguardian.identite.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import bf.fasoguardian.identite.domaine.Litige;
import bf.fasoguardian.identite.domaine.Litige.Statut;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface DepotLitiges extends JpaRepository<Litige, UUID> {

    List<Litige> findTop200ByOrderByOuvertLeDesc();

    List<Litige> findByTuteurIdAndStatut(UUID tuteurId, Statut statut);

    Optional<Litige> findByTuteurIdAndEnfantIdAndStatut(UUID tuteurId, UUID enfantId, Statut statut);

    @Query(value = "SELECT nextval('identite.reference_litige')", nativeQuery = true)
    long prochainNumero();
}
