package bf.fasoguardian.identite.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import bf.fasoguardian.identite.domaine.DossierKyc.TypePiece;
import bf.fasoguardian.identite.domaine.PieceJustificative;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DepotPiecesKyc extends JpaRepository<PieceJustificative, UUID> {

    List<PieceJustificative> findByDossierId(UUID dossierId);

    Optional<PieceJustificative> findByDossierIdAndType(UUID dossierId, TypePiece type);
}
