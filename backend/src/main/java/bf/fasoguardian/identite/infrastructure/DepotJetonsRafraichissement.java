package bf.fasoguardian.identite.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import bf.fasoguardian.identite.domaine.JetonRafraichissement;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DepotJetonsRafraichissement extends JpaRepository<JetonRafraichissement, UUID> {

    Optional<JetonRafraichissement> findByEmpreinte(String empreinte);

    List<JetonRafraichissement> findByFamille(UUID famille);
}
