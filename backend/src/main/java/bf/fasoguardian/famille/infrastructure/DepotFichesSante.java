package bf.fasoguardian.famille.infrastructure;

import java.util.UUID;

import bf.fasoguardian.famille.domaine.FicheSante;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DepotFichesSante extends JpaRepository<FicheSante, UUID> {}
