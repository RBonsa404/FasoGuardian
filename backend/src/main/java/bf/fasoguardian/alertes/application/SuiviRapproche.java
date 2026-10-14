package bf.fasoguardian.alertes.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import bf.fasoguardian.audit.JournalAudit;
import bf.fasoguardian.audit.JournalAudit.Resultat;
import bf.fasoguardian.alertes.infrastructure.DepotSuivis;
import bf.fasoguardian.dispositifs.Bracelets;
import bf.fasoguardian.dispositifs.Commandes;
import bf.fasoguardian.famille.AccesEnfant;
import bf.fasoguardian.plateforme.erreurs.CodeErreur;
import bf.fasoguardian.plateforme.erreurs.ErreurMetier;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Mode alerte demandé depuis la carte (écran 18) : un tuteur fait passer le bracelet à une position toutes les
 * 60 secondes sans qu'aucune alerte soit ouverte. Ce rythme coûte trois fois plus de batterie (FG-DOC-08 §6) :
 * le suivi rapproché s'arrête de lui-même au bout de trente minutes. Il ne coupe jamais le mode alerte d'une
 * alerte critique en cours, et une alerte qui se clôt ne coupe pas un suivi rapproché encore en cours.
 */
@Service
public class SuiviRapproche {

    static final Duration DUREE = Duration.ofMinutes(30);
    private static final String ROLE = "PARENT";

    /**
     * @param actif               le bracelet est en mode alerte, d'une façon ou d'une autre
     * @param jusquAu             fin du suivi rapproché demandé par un tuteur, ou {@code null}
     * @param alerteCritique      une alerte critique en cours tient le bracelet en mode alerte
     */
    public record EtatModeAlerte(boolean actif, Instant jusquAu, boolean alerteCritique) {
    }

    private final DepotSuivis suivis;
    private final OuvertureAlertes ouverture;
    private final Bracelets bracelets;
    private final Commandes commandes;
    private final AccesEnfant acces;
    private final JournalAudit journal;
    private final Clock horloge;

    SuiviRapproche(DepotSuivis suivis, OuvertureAlertes ouverture, Bracelets bracelets, Commandes commandes, AccesEnfant acces,
            JournalAudit journal, Clock horloge) {
        this.suivis = suivis;
        this.ouverture = ouverture;
        this.bracelets = bracelets;
        this.commandes = commandes;
        this.acces = acces;
        this.journal = journal;
        this.horloge = horloge;
    }

    @Transactional(readOnly = true)
    public EtatModeAlerte etat(UUID tuteurId, UUID enfantId) {
        acces.exigerTuteur(tuteurId, enfantId);
        return etat(enfantId);
    }

    @Transactional
    public EtatModeAlerte activer(UUID tuteurId, UUID enfantId) {
        acces.exigerTuteur(tuteurId, enfantId);
        if (bracelets.deLEnfant(enfantId).isEmpty()) {
            throw new ErreurMetier(CodeErreur.RESSOURCE_INTROUVABLE, "Aucun bracelet n'est associé à cet enfant.");
        }
        Instant maintenant = horloge.instant();
        suivis.ouvrir(enfantId, tuteurId, maintenant, maintenant.plus(DUREE));
        commandes.modeAlerte(enfantId, true);
        journal.consigner(tuteurId, ROLE, "SUIVI_RAPPROCHE_ACTIVE", "ENFANT", enfantId.toString(), Resultat.SUCCES);
        return etat(enfantId);
    }

    @Transactional
    public EtatModeAlerte desactiver(UUID tuteurId, UUID enfantId) {
        acces.exigerTuteur(tuteurId, enfantId);
        if (suivis.clore(enfantId)) {
            journal.consigner(tuteurId, ROLE, "SUIVI_RAPPROCHE_ARRETE", "ENFANT", enfantId.toString(), Resultat.SUCCES);
            ouverture.relacherModeAlerte(enfantId);
        }
        return etat(enfantId);
    }

    @Scheduled(fixedDelayString = "${fasoguardian.alertes.suivi-rapproche.cadence:PT1M}")
    @SchedulerLock(name = "alertes-fin-suivi-rapproche", lockAtMostFor = "PT2M")
    @Transactional
    public void planifierLaFin() {
        clore();
    }

    /** Clôt les suivis rapprochés échus. @return nombre de suivis clos */
    @Transactional
    public int clore() {
        int clos = 0;
        for (UUID enfantId : suivis.retirerEchus(horloge.instant())) {
            ouverture.relacherModeAlerte(enfantId);
            journal.consigner(null, "SYSTEME", "SUIVI_RAPPROCHE_ECHU", "ENFANT", enfantId.toString(), Resultat.SUCCES);
            clos++;
        }
        return clos;
    }

    private EtatModeAlerte etat(UUID enfantId) {
        Instant jusquAu = suivis.jusquAu(enfantId, horloge.instant()).orElse(null);
        boolean critique = ouverture.critiqueEnCours(enfantId);
        return new EtatModeAlerte(critique || jusquAu != null, jusquAu, critique);
    }
}
