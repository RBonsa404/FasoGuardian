package bf.fasoguardian.dispositifs.infrastructure;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import bf.fasoguardian.dispositifs.domaine.Commande;
import bf.fasoguardian.dispositifs.domaine.Commande.Statut;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DepotCommandes extends JpaRepository<Commande, UUID> {

    Optional<Commande> findFirstByBraceletIdOrderBySequenceDesc(UUID braceletId);

    List<Commande> findByStatutAndEmiseLeBefore(Statut statut, Instant limite);
}
