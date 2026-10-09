package bf.fasoguardian.dispositifs.infrastructure;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import bf.fasoguardian.dispositifs.domaine.TicketMaintenance;
import bf.fasoguardian.dispositifs.domaine.TicketMaintenance.Motif;
import bf.fasoguardian.dispositifs.domaine.TicketMaintenance.Statut;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface DepotTickets extends JpaRepository<TicketMaintenance, UUID> {

    Optional<TicketMaintenance> findByBraceletIdAndMotifAndStatutIn(UUID braceletId, Motif motif, Collection<Statut> statuts);

    List<TicketMaintenance> findByMotifAndStatutIn(Motif motif, Collection<Statut> statuts);

    List<TicketMaintenance> findByStatutInOrderByOuvertLe(Collection<Statut> statuts);

    List<TicketMaintenance> findTop100ByStatutOrderByResoluLeDesc(Statut statut);

    @Query(value = "SELECT nextval('dispositifs.reference_ticket')", nativeQuery = true)
    long prochainNumero();
}
