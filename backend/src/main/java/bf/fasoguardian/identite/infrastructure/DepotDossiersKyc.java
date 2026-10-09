package bf.fasoguardian.identite.infrastructure;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

import bf.fasoguardian.identite.domaine.DossierKyc;
import bf.fasoguardian.identite.domaine.DossierKyc.Statut;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface DepotDossiersKyc extends JpaRepository<DossierKyc, UUID> {

    Optional<DossierKyc> findFirstByDemandeurIdOrderByCreeLeDesc(UUID demandeurId);

    boolean existsByDemandeurIdAndStatutIn(UUID demandeurId, Collection<Statut> statuts);

    Optional<DossierKyc> findFirstByDemandeurIdAndStatutOrderByDecideLeDesc(UUID demandeurId, Statut statut);

    Optional<DossierKyc> findByReference(String reference);

    Page<DossierKyc> findByStatutInOrderByDeposeLeAsc(Collection<Statut> statuts, Pageable page);

    @Query(value = "SELECT nextval('identite.dossier_kyc_reference_seq')", nativeQuery = true)
    long prochaineReference();
}
