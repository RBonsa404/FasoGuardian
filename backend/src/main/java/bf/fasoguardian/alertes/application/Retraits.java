package bf.fasoguardian.alertes.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import bf.fasoguardian.alertes.domaine.Alerte.Type;
import bf.fasoguardian.alertes.domaine.AutorisationRetrait;
import bf.fasoguardian.alertes.domaine.AutorisationRetrait.Motif;
import bf.fasoguardian.alertes.domaine.AutorisationRetrait.Statut;
import bf.fasoguardian.alertes.infrastructure.DepotAutorisations;
import bf.fasoguardian.audit.JournalAudit;
import bf.fasoguardian.audit.JournalAudit.Resultat;
import bf.fasoguardian.dispositifs.Bracelets;
import bf.fasoguardian.dispositifs.Bracelets.BraceletConnu;
import bf.fasoguardian.dispositifs.Commandes;
import bf.fasoguardian.famille.AccesEnfant;
import bf.fasoguardian.identite.LiensTutelle;
import bf.fasoguardian.notifications.Notifications;
import bf.fasoguardian.notifications.Notifications.Message;
import bf.fasoguardian.notifications.Notifications.Urgence;
import bf.fasoguardian.identite.SecondFacteur;
import bf.fasoguardian.identite.SecondFacteur.ActionSensible;
import bf.fasoguardian.plateforme.erreurs.CodeErreur;
import bf.fasoguardian.plateforme.erreurs.ErreurMetier;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Autorisation de retrait du bracelet (US-PAR-012) : accordée par un tuteur avec second facteur, pour 15 minutes
 * à 12 heures. Pendant la fenêtre, un retrait ne déclenche rien ; cinq minutes avant la fin, un rappel part si
 * le bracelet n'a pas été remis ; à l'échéance, un bracelet toujours retiré ouvre une alerte critique.
 */
@Service
public class Retraits {

    private static final String ROLE = "PARENT";

    public record AutorisationVue(UUID id, Motif motif, Instant debut, Instant fin, boolean retire) {
    }

    private final DepotAutorisations autorisations;
    private final OuvertureAlertes ouverture;
    private final Bracelets bracelets;
    private final Commandes commandes;
    private final AccesEnfant acces;
    private final SecondFacteur secondFacteur;
    private final LiensTutelle liens;
    private final Notifications notifications;
    private final JournalAudit journal;
    private final Clock horloge;

    Retraits(DepotAutorisations autorisations, OuvertureAlertes ouverture, Bracelets bracelets, Commandes commandes,
            AccesEnfant acces,
            SecondFacteur secondFacteur, LiensTutelle liens, Notifications notifications, JournalAudit journal,
            Clock horloge) {
        this.autorisations = autorisations;
        this.ouverture = ouverture;
        this.bracelets = bracelets;
        this.commandes = commandes;
        this.acces = acces;
        this.secondFacteur = secondFacteur;
        this.liens = liens;
        this.notifications = notifications;
        this.journal = journal;
        this.horloge = horloge;
    }

    @Transactional(readOnly = true)
    public Optional<AutorisationVue> enCours(UUID tuteurId, UUID enfantId) {
        acces.exigerTuteur(tuteurId, enfantId);
        return autorisations.findByEnfantIdAndStatut(enfantId, Statut.ACTIVE).map(Retraits::vue);
    }

    @Transactional
    public AutorisationVue accorder(UUID tuteurId, UUID enfantId, Motif motif, int dureeMinutes, String codeSecondFacteur) {
        acces.exigerTuteur(tuteurId, enfantId);
        BraceletConnu bracelet = bracelets.deLEnfant(enfantId).filter(BraceletConnu::accepteLesMessages).orElseThrow(
                () -> new ErreurMetier(CodeErreur.RESSOURCE_INTROUVABLE, "Aucun bracelet en service n'est associé à cet enfant."));
        if (autorisations.findByBraceletIdAndStatut(bracelet.id(), Statut.ACTIVE).isPresent()) {
            throw new ErreurMetier(CodeErreur.CONFLIT, "Un retrait est déjà autorisé. Prolongez-le ou terminez-le.");
        }
        AutorisationRetrait autorisation;
        try {
            autorisation = new AutorisationRetrait(enfantId, bracelet.id(), tuteurId, motif,
                    Duration.ofMinutes(dureeMinutes), horloge.instant());
        } catch (IllegalArgumentException erreur) {
            throw new ErreurMetier(CodeErreur.REQUETE_INVALIDE, erreur.getMessage() + ".");
        }
        secondFacteur.exiger(tuteurId, ActionSensible.AUTORISER_RETRAIT, codeSecondFacteur);
        autorisations.save(autorisation);
        commandes.fenetreDeRetrait(enfantId, autorisation.fin());
        journal.consigner(tuteurId, ROLE, "RETRAIT_AUTORISE", "BRACELET", bracelet.id().toString(), Resultat.SUCCES);
        prevenir(enfantId, Urgence.INFORMATION, "RETRAIT_AUTORISE", "Retrait autorisé", "le retrait du bracelet "
                + bracelet.numeroSerie() + " est autorisé pendant " + duree(dureeMinutes) + ".");
        return vue(autorisation);
    }

    /** Allonge la fenêtre en cours ; comme l'autorisation, la prolongation exige le second facteur. */
    @Transactional
    public AutorisationVue prolonger(UUID tuteurId, UUID enfantId, int minutes, String codeSecondFacteur) {
        acces.exigerTuteur(tuteurId, enfantId);
        AutorisationRetrait autorisation = active(enfantId);
        secondFacteur.exiger(tuteurId, ActionSensible.AUTORISER_RETRAIT, codeSecondFacteur);
        try {
            autorisation.prolonger(Duration.ofMinutes(minutes));
        } catch (IllegalArgumentException erreur) {
            throw new ErreurMetier(CodeErreur.REQUETE_INVALIDE, erreur.getMessage() + ".");
        }
        commandes.fenetreDeRetrait(enfantId, autorisation.fin());
        journal.consigner(tuteurId, ROLE, "RETRAIT_PROLONGE", "BRACELET", autorisation.braceletId().toString(),
                Resultat.SUCCES);
        return vue(autorisation);
    }

    /** « Bracelet remis » : le parent clôt la fenêtre, la surveillance du retrait reprend. */
    @Transactional
    public void terminer(UUID tuteurId, UUID enfantId) {
        acces.exigerTuteur(tuteurId, enfantId);
        AutorisationRetrait autorisation = active(enfantId);
        autorisation.terminer(horloge.instant());
        commandes.fenetreDeRetrait(enfantId, null);
        journal.consigner(tuteurId, ROLE, "RETRAIT_TERMINE", "BRACELET", autorisation.braceletId().toString(),
                Resultat.SUCCES);
    }

    /**
     * Le bracelet signale un retrait.
     *
     * @return {@code true} si une autorisation couvrait l'instant du retrait : il est alors seulement noté
     */
    @Transactional
    public boolean noterRetraitAutorise(UUID braceletId, Instant mesureLe) {
        Optional<AutorisationRetrait> autorisation = autorisations.findByBraceletIdAndStatut(braceletId, Statut.ACTIVE)
                .filter(active -> active.couvre(mesureLe));
        autorisation.ifPresent(active -> {
            active.noterRetrait();
            journal.consigner(null, "SYSTEME", "RETRAIT_CONSTATE", "BRACELET", braceletId.toString(), Resultat.SUCCES);
        });
        return autorisation.isPresent();
    }

    /** Le bracelet signale que le contact peau est rétabli : une fenêtre de retrait en cours se referme. */
    @Transactional
    public void noterBraceletRemis(UUID braceletId) {
        autorisations.findByBraceletIdAndStatut(braceletId, Statut.ACTIVE).filter(AutorisationRetrait::retire)
                .ifPresent(autorisation -> {
                    autorisation.terminer(horloge.instant());
                    commandes.fenetreDeRetrait(autorisation.enfantId(), null);
                    journal.consigner(null, "SYSTEME", "BRACELET_REMIS", "BRACELET", braceletId.toString(), Resultat.SUCCES);
                });
    }

    /** Rappels avant l'échéance, puis alerte pour les fenêtres échues dont le bracelet n'a pas été remis. */
    @Scheduled(fixedDelayString = "${fasoguardian.alertes.echeances-retrait:PT30S}")
    @SchedulerLock(name = "alertes-echeances-retrait", lockAtMostFor = "PT2M")
    @Transactional
    public void surveillerEcheances() {
        Instant maintenant = horloge.instant();
        autorisations.findByStatutAndRetireTrueAndRappelEnvoyeFalseAndFinBefore(Statut.ACTIVE,
                maintenant.plus(AutorisationRetrait.AVANCE_DU_RAPPEL)).stream()
                .filter(autorisation -> autorisation.rappelDu(maintenant)).forEach(autorisation -> {
                    autorisation.noterRappel();
                    prevenir(autorisation.enfantId(), Urgence.IMPORTANTE, "RAPPEL_RETRAIT", "Fin du retrait autorisé",
                            "le retrait autorisé du bracelet se termine dans quelques minutes. Remettez-le à votre enfant "
                                    + "pour éviter une alerte.");
                });
        for (AutorisationRetrait autorisation : autorisations.findByStatutAndFinBefore(Statut.ACTIVE, maintenant)) {
            if (autorisation.echoir(maintenant)) {
                ouverture.ouvrir(autorisation.enfantId(), Type.RETRAIT, null, null, null, null, null);
            }
        }
    }

    // -------------------------------------------------------------------- aides

    private AutorisationRetrait active(UUID enfantId) {
        return autorisations.findByEnfantIdAndStatut(enfantId, Statut.ACTIVE).orElseThrow(
                () -> new ErreurMetier(CodeErreur.RESSOURCE_INTROUVABLE, "Aucun retrait n'est autorisé en ce moment."));
    }

    private void prevenir(UUID enfantId, Urgence urgence, String modele, String titre, String texte) {
        Message message = new Message(modele, titre, texte, "/enfants/" + enfantId + "/bracelet/retrait", null);
        liens.tuteursActifsDe(enfantId).forEach(tuteur -> notifications.notifier(tuteur, urgence, message));
    }

    private static String duree(int minutes) {
        if (minutes < 60) {
            return minutes + " min";
        }
        return minutes % 60 == 0 ? (minutes / 60) + " h" : (minutes / 60) + " h " + (minutes % 60) + " min";
    }

    private static AutorisationVue vue(AutorisationRetrait a) {
        return new AutorisationVue(a.id(), a.motif(), a.debut(), a.fin(), a.retire());
    }
}
