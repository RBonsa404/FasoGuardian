package bf.fasoguardian.abonnements.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import bf.fasoguardian.abonnements.domaine.Facture;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface DepotFactures extends JpaRepository<Facture, String> {

    List<Facture> findByTuteurIdOrderByEmiseLeDesc(UUID tuteurId);

    Optional<Facture> findByPaiementId(UUID paiementId);

    Optional<Facture> findByNumeroAndTuteurId(String numero, UUID tuteurId);

    /** Avance le compteur des reçus dans la transaction en cours : un reçu annulé ne consomme pas de numéro. */
    @Query(value = "UPDATE abonnements.compteur_recus SET dernier = dernier + 1 RETURNING dernier", nativeQuery = true)
    long prochainNumero();
}
