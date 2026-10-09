package bf.fasoguardian.notifications.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import bf.fasoguardian.notifications.domaine.AbonnementPush;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DepotAbonnementsPush extends JpaRepository<AbonnementPush, UUID> {

    List<AbonnementPush> findByDestinataireIdOrderByCreeLe(UUID destinataireId);

    Optional<AbonnementPush> findByPointDeLivraison(String pointDeLivraison);

    boolean existsByDestinataireId(UUID destinataireId);
}
