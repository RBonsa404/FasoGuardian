package bf.fasoguardian.alertes.infrastructure;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import bf.fasoguardian.alertes.domaine.SignalementFds;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface DepotSignalements extends JpaRepository<SignalementFds, UUID> {

    List<SignalementFds> findByAlerteIdIn(Collection<UUID> alerteIds);

    List<SignalementFds> findByDossierChiffreIsNotNullAndCreeLeBefore(Instant limite);

    @Query(value = "SELECT nextval('alertes.reference_signalement')", nativeQuery = true)
    long prochainNumero();
}
