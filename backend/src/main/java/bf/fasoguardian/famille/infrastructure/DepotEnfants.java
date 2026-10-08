package bf.fasoguardian.famille.infrastructure;

import java.util.UUID;

import bf.fasoguardian.famille.domaine.Enfant;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DepotEnfants extends JpaRepository<Enfant, UUID> {}
