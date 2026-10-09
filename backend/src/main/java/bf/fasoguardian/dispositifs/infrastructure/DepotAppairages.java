package bf.fasoguardian.dispositifs.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import bf.fasoguardian.dispositifs.domaine.Appairage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DepotAppairages extends JpaRepository<Appairage, UUID> {

    Optional<Appairage> findByEnfantIdAndFinIsNull(UUID enfantId);

    List<Appairage> findByEnfantIdOrderByDebut(UUID enfantId);

    Optional<Appairage> findByBraceletIdAndFinIsNull(UUID braceletId);

    List<Appairage> findByBraceletIdOrderByDebutDesc(UUID braceletId);
}
