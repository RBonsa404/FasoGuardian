package bf.fasoguardian.famille.infrastructure;

import java.util.List;
import java.util.UUID;

import bf.fasoguardian.famille.domaine.ContactUrgence;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DepotContacts extends JpaRepository<ContactUrgence, UUID> {
    List<ContactUrgence> findByEnfantIdOrderByRang(UUID enfantId);

    long countByEnfantId(UUID enfantId);
}
