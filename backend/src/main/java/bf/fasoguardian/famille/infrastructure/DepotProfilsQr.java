package bf.fasoguardian.famille.infrastructure;

import java.util.Optional;
import java.util.UUID;

import bf.fasoguardian.famille.domaine.ProfilQr;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DepotProfilsQr extends JpaRepository<ProfilQr, UUID> {

    Optional<ProfilQr> findByJetonSha256(String jetonSha256);
}
