package bf.fasoguardian.abonnements.infrastructure;

import java.util.List;

import bf.fasoguardian.abonnements.domaine.Offre;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DepotOffres extends JpaRepository<Offre, String> {

    List<Offre> findAllByOrderByRang();
}
