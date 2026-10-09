package bf.fasoguardian.alertes.infrastructure;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import bf.fasoguardian.alertes.domaine.AutorisationRetrait;
import bf.fasoguardian.alertes.domaine.AutorisationRetrait.Statut;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DepotAutorisations extends JpaRepository<AutorisationRetrait, UUID> {

    Optional<AutorisationRetrait> findByEnfantIdAndStatut(UUID enfantId, Statut statut);

    Optional<AutorisationRetrait> findByBraceletIdAndStatut(UUID braceletId, Statut statut);

    List<AutorisationRetrait> findByStatutAndFinBefore(Statut statut, Instant limite);

    List<AutorisationRetrait> findByStatutAndRetireTrueAndRappelEnvoyeFalseAndFinBefore(Statut statut, Instant limite);
}
