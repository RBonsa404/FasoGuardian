package bf.fasoguardian.identite.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import bf.fasoguardian.identite.domaine.AgentInterne;
import bf.fasoguardian.identite.domaine.Utilisateur;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface DepotUtilisateurs extends JpaRepository<Utilisateur, UUID> {

    Optional<Utilisateur> findByTelephoneHash(String telephoneHash);

    @Query("select a from AgentInterne a where a.identifiant = :identifiant")
    Optional<AgentInterne> findAgentByIdentifiant(String identifiant);

    @Query("select a from AgentInterne a order by a.identifiant")
    List<AgentInterne> findAllAgents();
}
