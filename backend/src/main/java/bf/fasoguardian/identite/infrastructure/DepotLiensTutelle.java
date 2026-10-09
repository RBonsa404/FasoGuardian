package bf.fasoguardian.identite.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import bf.fasoguardian.identite.domaine.LienTutelle;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DepotLiensTutelle extends JpaRepository<LienTutelle, UUID> {

    List<LienTutelle> findByTuteurId(UUID tuteurId);

    List<LienTutelle> findByEnfantId(UUID enfantId);

    Optional<LienTutelle> findByTuteurIdAndEnfantId(UUID tuteurId, UUID enfantId);
}
