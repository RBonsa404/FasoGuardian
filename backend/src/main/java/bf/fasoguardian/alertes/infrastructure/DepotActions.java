package bf.fasoguardian.alertes.infrastructure;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import bf.fasoguardian.alertes.domaine.ActionAlerte;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DepotActions extends JpaRepository<ActionAlerte, UUID> {

    List<ActionAlerte> findByAlerteIdInOrderByEffectueeLe(Collection<UUID> alerteIds);
}
