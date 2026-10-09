package bf.fasoguardian.alertes.infrastructure;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import bf.fasoguardian.alertes.domaine.Alerte;
import bf.fasoguardian.alertes.domaine.Alerte.Gravite;
import bf.fasoguardian.alertes.domaine.Alerte.Statut;
import bf.fasoguardian.alertes.domaine.Alerte.Type;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DepotAlertes extends JpaRepository<Alerte, UUID> {

    List<Alerte> findByEnfantIdInOrderByOuverteLeDesc(Collection<UUID> enfantIds, Limit limite);

    List<Alerte> findByEnfantIdInAndStatutInOrderByOuverteLeDesc(Collection<UUID> enfantIds, Collection<Statut> statuts);

    List<Alerte> findByEnfantIdAndTypeAndStatutIn(UUID enfantId, Type type, Collection<Statut> statuts);

    List<Alerte> findByEnfantIdAndStatut(UUID enfantId, Statut statut);

    List<Alerte> findByStatutAndGraviteAndOuverteLeBefore(Statut statut, Gravite gravite, Instant limite);
}
