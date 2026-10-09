package bf.fasoguardian.alertes.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import bf.fasoguardian.alertes.domaine.ActionAlerte;
import bf.fasoguardian.alertes.domaine.Alerte;
import bf.fasoguardian.alertes.domaine.Alerte.Gravite;
import bf.fasoguardian.alertes.domaine.Alerte.Statut;
import bf.fasoguardian.alertes.domaine.Alerte.TransitionIllegaleException;
import bf.fasoguardian.alertes.domaine.Alerte.Type;
import bf.fasoguardian.alertes.infrastructure.DepotActions;
import bf.fasoguardian.alertes.infrastructure.DepotAlertes;
import bf.fasoguardian.audit.JournalAudit;
import bf.fasoguardian.audit.JournalAudit.Resultat;
import bf.fasoguardian.famille.AccesEnfant;
import bf.fasoguardian.identite.LiensTutelle;
import bf.fasoguardian.plateforme.erreurs.CodeErreur;
import bf.fasoguardian.plateforme.erreurs.ErreurMetier;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Alertes vues et traitées par les tuteurs (US-ENF-001, US-PAR-008, US-PAR-010) : prise en charge, levée,
 * fausse alerte, signalement. Chaque transition s'inscrit au journal d'acquittement, non modifiable, et au
 * journal d'audit. Le parent reste seul décisionnaire de la suite donnée à une alerte.
 */
@Service
public class Alertes {

    private static final String ROLE = "PARENT";
    private static final int ALERTES_MAXIMUM = 200;
    private static final List<Statut> EN_COURS = List.of(Statut.OUVERTE, Statut.ACQUITTEE, Statut.ESCALADEE);

    /** Auteur d'une action, sans jamais révéler l'identité d'un autre tuteur. */
    public enum Auteur {
        VOUS,
        AUTRE_TUTEUR,
        SYSTEME
    }

    public record ActionVue(ActionAlerte.Type type, Auteur auteur, String motif, Instant effectueeLe) {
    }

    public record AlerteVue(UUID id, UUID enfantId, Type type, Gravite gravite, Statut statut, Instant ouverteLe,
            Instant closeLe, String libelle, Double latitude, Double longitude, List<ActionVue> actions) {
    }

    private final DepotAlertes alertes;
    private final DepotActions actions;
    private final OuvertureAlertes ouverture;
    private final AccesEnfant acces;
    private final LiensTutelle liens;
    private final JournalAudit journal;
    private final Clock horloge;

    Alertes(DepotAlertes alertes, DepotActions actions, OuvertureAlertes ouverture, AccesEnfant acces,
            LiensTutelle liens, JournalAudit journal, Clock horloge) {
        this.alertes = alertes;
        this.actions = actions;
        this.ouverture = ouverture;
        this.acces = acces;
        this.liens = liens;
        this.journal = journal;
        this.horloge = horloge;
    }

    /** Alertes des enfants du tuteur, les plus récentes d'abord ; seulement celles en cours si demandé. */
    @Transactional(readOnly = true)
    public List<AlerteVue> mesAlertes(UUID tuteurId, boolean enCoursSeulement) {
        List<UUID> enfants = liens.enfantsDe(tuteurId);
        if (enfants.isEmpty()) {
            return List.of();
        }
        return vues(tuteurId, enCoursSeulement
                ? alertes.findByEnfantIdInAndStatutInOrderByOuverteLeDesc(enfants, EN_COURS)
                : alertes.findByEnfantIdInOrderByOuverteLeDesc(enfants, Limit.of(ALERTES_MAXIMUM)));
    }

    @Transactional(readOnly = true)
    public AlerteVue alerte(UUID tuteurId, UUID alerteId) {
        return vues(tuteurId, List.of(pourTuteur(tuteurId, alerteId))).get(0);
    }

    /** Journal des alertes d'un enfant, avec toutes leurs actions horodatées (US-PAR-008) ; consultation journalisée. */
    @Transactional
    public List<AlerteVue> journal(UUID tuteurId, UUID enfantId) {
        acces.exigerTuteur(tuteurId, enfantId);
        journal.consigner(tuteurId, ROLE, "JOURNAL_ALERTES_CONSULTE", "ENFANT", enfantId.toString(), Resultat.SUCCES);
        return vues(tuteurId, alertes.findByEnfantIdInOrderByOuverteLeDesc(List.of(enfantId), Limit.of(ALERTES_MAXIMUM)));
    }

    @Transactional
    public AlerteVue acquitter(UUID tuteurId, UUID alerteId) {
        Alerte alerte = pourTuteur(tuteurId, alerteId);
        transition(tuteurId, alerte, "ALERTE_ACQUITTEE", () -> alerte.acquitter(tuteurId, horloge.instant()));
        return vues(tuteurId, List.of(alerte)).get(0);
    }

    /** Une seule prise en charge pour toutes les alertes ouvertes d'un enfant ; chacune reste tracée (US-PAR-018). */
    @Transactional
    public List<AlerteVue> prendreEnCharge(UUID tuteurId, UUID enfantId) {
        acces.exigerTuteur(tuteurId, enfantId);
        List<Alerte> ouvertes = alertes.findByEnfantIdAndStatut(enfantId, Statut.OUVERTE);
        ouvertes.forEach(alerte -> transition(tuteurId, alerte, "ALERTE_ACQUITTEE",
                () -> alerte.acquitter(tuteurId, horloge.instant())));
        return vues(tuteurId, ouvertes);
    }

    @Transactional
    public AlerteVue lever(UUID tuteurId, UUID alerteId, String motif) {
        Alerte alerte = pourTuteur(tuteurId, alerteId);
        transition(tuteurId, alerte, "ALERTE_LEVEE", () -> alerte.lever(tuteurId, motif, horloge.instant()));
        ouverture.relacherModeAlerte(alerte.enfantId());
        return vues(tuteurId, List.of(alerte)).get(0);
    }

    @Transactional
    public AlerteVue classerFausseAlerte(UUID tuteurId, UUID alerteId, String motif) {
        Alerte alerte = pourTuteur(tuteurId, alerteId);
        transition(tuteurId, alerte, "ALERTE_CLASSEE_FAUSSE",
                () -> alerte.classerFausseAlerte(tuteurId, motif, horloge.instant()));
        ouverture.relacherModeAlerte(alerte.enfantId());
        return vues(tuteurId, List.of(alerte)).get(0);
    }

    /**
     * Le parent signale lui-même une situation inquiétante (US-PAR-010) : l'alerte est horodatée et aussitôt
     * prise en charge par lui ; les autres tuteurs sont prévenus. L'escalade reste sa seule décision.
     */
    @Transactional
    public AlerteVue signaler(UUID tuteurId, UUID enfantId) {
        acces.exigerTuteur(tuteurId, enfantId);
        Alerte alerte = ouverture.ouvrir(enfantId, Type.SIGNALEMENT, tuteurId, null, null, null, null).orElseThrow(
                () -> new ErreurMetier(CodeErreur.CONFLIT, "Un signalement est déjà en cours pour cet enfant."));
        journal.consigner(tuteurId, ROLE, "SIGNALEMENT_OUVERT", "ALERTE", alerte.id().toString(), Resultat.SUCCES);
        transition(tuteurId, alerte, "ALERTE_ACQUITTEE", () -> alerte.acquitter(tuteurId, horloge.instant()));
        return vues(tuteurId, List.of(alerte)).get(0);
    }

    // -------------------------------------------------------------------- aides

    private void transition(UUID tuteurId, Alerte alerte, String action, java.util.function.Supplier<ActionAlerte> effet) {
        try {
            actions.saveAndFlush(effet.get());
        } catch (TransitionIllegaleException erreur) {
            throw new ErreurMetier(CodeErreur.CONFLIT, "Cette action n'est plus possible : l'alerte a changé d'état.");
        } catch (IllegalArgumentException erreur) {
            throw new ErreurMetier(CodeErreur.REQUETE_INVALIDE, "Indiquez le motif.");
        }
        journal.consigner(tuteurId, ROLE, action, "ALERTE", alerte.id().toString(), Resultat.SUCCES);
        ouverture.accuser(alerte);
    }

    /** L'alerte, si le tuteur est rattaché à l'enfant ; sinon elle est introuvable et le refus est journalisé. */
    private Alerte pourTuteur(UUID tuteurId, UUID alerteId) {
        Alerte alerte = alertes.findById(alerteId).orElseThrow(Alertes::introuvable);
        acces.exigerTuteur(tuteurId, alerte.enfantId());
        return alerte;
    }

    private List<AlerteVue> vues(UUID tuteurId, Collection<Alerte> liste) {
        actions.flush();
        Map<UUID, List<ActionAlerte>> parAlerte = actions
                .findByAlerteIdInOrderByEffectueeLe(liste.stream().map(Alerte::id).toList()).stream()
                .collect(Collectors.groupingBy(ActionAlerte::alerteId));
        Function<ActionAlerte, ActionVue> vue = action -> new ActionVue(action.type(),
                action.acteurId() == null ? Auteur.SYSTEME : action.acteurId().equals(tuteurId) ? Auteur.VOUS : Auteur.AUTRE_TUTEUR,
                action.motif(), action.effectueeLe());
        return liste.stream().map(a -> new AlerteVue(a.id(), a.enfantId(), a.type(), a.gravite(), a.statut(), a.ouverteLe(),
                a.closeLe(), a.libelle(), a.latitude(), a.longitude(),
                parAlerte.getOrDefault(a.id(), List.of()).stream().map(vue).toList())).toList();
    }

    private static ErreurMetier introuvable() {
        return new ErreurMetier(CodeErreur.RESSOURCE_INTROUVABLE, "Alerte introuvable.");
    }
}
