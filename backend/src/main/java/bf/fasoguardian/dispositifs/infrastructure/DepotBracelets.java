package bf.fasoguardian.dispositifs.infrastructure;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import bf.fasoguardian.dispositifs.domaine.Bracelet;
import bf.fasoguardian.dispositifs.domaine.StatutBracelet;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DepotBracelets extends JpaRepository<Bracelet, UUID> {

    Optional<Bracelet> findByNumeroSerie(String numeroSerie);

    Optional<Bracelet> findByCodeAppairageEmpreinte(String empreinte);

    boolean existsByNumeroSerie(String numeroSerie);

    boolean existsByImeiEmpreinte(String empreinte);

    boolean existsByEmpreinteCertificat(String empreinte);

    List<Bracelet> findByStatutOrderByNumeroSerie(StatutBracelet statut);

    List<Bracelet> findAllByOrderByNumeroSerie();

    List<Bracelet> findByStatutAndSuiviJusquAuBefore(StatutBracelet statut, Instant limite);

    List<Bracelet> findByCertificatRevoqueLeIsNotNullOrderByCertificatRevoqueLe();
}
