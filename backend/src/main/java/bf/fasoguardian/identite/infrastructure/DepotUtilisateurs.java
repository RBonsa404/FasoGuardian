package bf.fasoguardian.identite.infrastructure;

import java.util.Optional;
import java.util.UUID;

import bf.fasoguardian.identite.domaine.Utilisateur;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DepotUtilisateurs extends JpaRepository<Utilisateur, UUID> {

    Optional<Utilisateur> findByTelephoneHash(String telephoneHash);
}
