package bf.fasoguardian.identite.infrastructure;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import bf.fasoguardian.identite.domaine.InvitationTuteur;
import bf.fasoguardian.identite.domaine.InvitationTuteur.Statut;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DepotInvitationsTuteur extends JpaRepository<InvitationTuteur, UUID> {

    Optional<InvitationTuteur> findFirstByEnfantIdAndStatutIn(UUID enfantId, Collection<Statut> statuts);

    List<InvitationTuteur> findByTelephoneHashAndStatut(String telephoneHash, Statut statut);
}
