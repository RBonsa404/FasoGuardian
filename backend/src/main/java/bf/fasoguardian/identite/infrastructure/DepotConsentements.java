package bf.fasoguardian.identite.infrastructure;

import java.util.List;
import java.util.UUID;

import bf.fasoguardian.identite.domaine.Consentement;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DepotConsentements extends JpaRepository<Consentement, UUID> {

    List<Consentement> findByUtilisateurId(UUID utilisateurId);
}
