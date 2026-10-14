package bf.fasoguardian.identite.application;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import bf.fasoguardian.plateforme.console.Decomptes;
import bf.fasoguardian.plateforme.console.IndicateursConsole;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Tableau de bord du support (écran 57) : demandes à traiter, en retard, en attente du parent, mes réponses. */
@Component
class IndicateursSupport implements IndicateursConsole {

    /** Une demande restée sans réponse plus longtemps est signalée. */
    static final Duration RETARD = Duration.ofHours(24);

    private final Decomptes decomptes;

    IndicateursSupport(Decomptes decomptes) {
        this.decomptes = decomptes;
    }

    @Override
    public String role() {
        return "SUPPORT";
    }

    @Override
    @Transactional(readOnly = true)
    public List<Indicateur> pour(UUID agentId) {
        long ouvertes = decomptes.compter("SELECT count(*) FROM identite.demande_support WHERE statut = 'OUVERTE'");
        long enRetard = decomptes.compter("SELECT count(*) FROM identite.demande_support WHERE statut = 'OUVERTE' AND modifiee_le < ?",
                decomptes.ilYA(RETARD));
        long attenteParent = decomptes.compter("SELECT count(*) FROM identite.demande_support WHERE statut = 'EN_ATTENTE_PARENT'");
        long reponses = decomptes.compter("SELECT count(*) FROM identite.message_support WHERE auteur = 'SUPPORT' AND auteur_id = ?"
                + " AND cree_le >= ?", agentId, decomptes.debutDuJour());
        return List.of(new Indicateur("support.ouvertes", ouvertes, null, false),
                new Indicateur("support.enRetard", enRetard, RETARD.toHours(), enRetard > 0),
                new Indicateur("support.attenteParent", attenteParent, null, false),
                new Indicateur("support.mesReponses", reponses, null, false));
    }
}
