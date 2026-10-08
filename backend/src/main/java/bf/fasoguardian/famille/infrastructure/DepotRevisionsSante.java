package bf.fasoguardian.famille.infrastructure;

import java.util.List;
import java.util.UUID;

import bf.fasoguardian.famille.domaine.RevisionSante;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DepotRevisionsSante extends JpaRepository<RevisionSante, UUID> {
    List<RevisionSante> findByEnfantIdOrderByModifieLeDesc(UUID enfantId);
}
