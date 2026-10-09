package bf.fasoguardian.identite.application;

import java.util.List;
import java.util.UUID;

import bf.fasoguardian.audit.PerimetreFamilial;
import bf.fasoguardian.identite.domaine.LienTutelle;
import bf.fasoguardian.identite.infrastructure.DepotLiensTutelle;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Enfants d'un tuteur pour l'exercice des droits ; le compte du tuteur peut déjà être clos. */
@Service
class PerimetreDesTuteurs implements PerimetreFamilial {

    private final DepotLiensTutelle liens;

    PerimetreDesTuteurs(DepotLiensTutelle liens) {
        this.liens = liens;
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> enfantsDe(UUID tuteurId) {
        return liens.findByTuteurId(tuteurId).stream().map(LienTutelle::enfantId).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> enfantsSansAutreTuteur(UUID tuteurId) {
        return enfantsDe(tuteurId).stream().filter(enfant -> liens.findByEnfantId(enfant).stream()
                .noneMatch(lien -> lien.actif() && !lien.tuteurId().equals(tuteurId))).toList();
    }
}
