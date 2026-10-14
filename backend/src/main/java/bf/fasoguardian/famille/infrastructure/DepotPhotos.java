package bf.fasoguardian.famille.infrastructure;

import java.util.UUID;

import bf.fasoguardian.famille.domaine.PhotoEnfant;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DepotPhotos extends JpaRepository<PhotoEnfant, UUID> {}
