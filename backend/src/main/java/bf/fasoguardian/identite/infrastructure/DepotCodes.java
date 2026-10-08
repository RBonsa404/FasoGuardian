package bf.fasoguardian.identite.infrastructure;

import java.util.Optional;
import java.util.UUID;

import bf.fasoguardian.identite.domaine.CodeEmis;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DepotCodes extends JpaRepository<CodeEmis, UUID> {

    Optional<CodeEmis> findFirstByFinaliteAndCibleHashOrderByEmisLeDesc(String finalite, String cibleHash);
}
