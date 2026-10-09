package bf.fasoguardian.abonnements.infrastructure;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import bf.fasoguardian.abonnements.domaine.Paiement;
import bf.fasoguardian.abonnements.domaine.Paiement.Statut;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DepotPaiements extends JpaRepository<Paiement, UUID> {

    Optional<Paiement> findByCleIdempotence(String cle);

    Optional<Paiement> findByReferenceOperateur(String reference);

    Optional<Paiement> findFirstByAbonnementIdAndStatutOrderByInitieLeDesc(UUID abonnementId, Statut statut);

    List<Paiement> findByStatutAndInitieLeBefore(Statut statut, Instant limite);
}
