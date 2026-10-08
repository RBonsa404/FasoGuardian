package bf.fasoguardian.famille.infrastructure;

import java.util.List;
import java.util.UUID;

import bf.fasoguardian.famille.domaine.RevisionEnfant;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DepotRevisionsEnfant extends JpaRepository<RevisionEnfant, UUID> {
    List<RevisionEnfant> findByEnfantIdOrderByModifieLeDesc(UUID enfantId);
}
