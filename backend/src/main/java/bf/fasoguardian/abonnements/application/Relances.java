package bf.fasoguardian.abonnements.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

import bf.fasoguardian.abonnements.DroitsModifies;
import bf.fasoguardian.abonnements.domaine.Abonnement;
import bf.fasoguardian.abonnements.domaine.Abonnement.Relance;
import bf.fasoguardian.abonnements.domaine.Paiement;
import bf.fasoguardian.abonnements.domaine.Paiement.Statut;
import bf.fasoguardian.abonnements.infrastructure.DepotAbonnements;
import bf.fasoguardian.abonnements.infrastructure.DepotPaiements;
import bf.fasoguardian.audit.JournalAudit;
import bf.fasoguardian.audit.JournalAudit.Resultat;
import bf.fasoguardian.notifications.Notifications;
import bf.fasoguardian.notifications.Notifications.Message;
import bf.fasoguardian.notifications.Notifications.Urgence;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Suivi quotidien des échéances (US-PAR-016, US-SYS-008) : rappel sept jours avant, renouvellement automatique
 * le jour même, rappels à J+1 et J+8, restriction à J+15. Chaque abonnement est traité dans sa propre
 * transaction : l'échec de l'un n'arrête pas les autres.
 */
@Service
public class Relances {

    private static final Logger journalTechnique = LoggerFactory.getLogger(Relances.class);
    private static final DateTimeFormatter JOUR = DateTimeFormatter.ofPattern("d MMMM", Locale.FRENCH);
    private static final String LIEN = "/abonnement";

    private final DepotAbonnements abonnements;
    private final DepotPaiements paiements;
    private final Souscriptions souscriptions;
    private final Notifications notifications;
    private final ApplicationEventPublisher evenements;
    private final JournalAudit journal;
    private final TransactionTemplate transaction;
    private final Clock horloge;
    private final ZoneId fuseau;

    Relances(DepotAbonnements abonnements, DepotPaiements paiements, Souscriptions souscriptions, Notifications notifications,
            ApplicationEventPublisher evenements, JournalAudit journal, TransactionTemplate transaction, Clock horloge,
            @Value("${fasoguardian.fuseau:Africa/Ouagadougou}") ZoneId fuseau) {
        this.abonnements = abonnements;
        this.paiements = paiements;
        this.souscriptions = souscriptions;
        this.notifications = notifications;
        this.evenements = evenements;
        this.journal = journal;
        this.transaction = transaction;
        this.horloge = horloge;
        this.fuseau = fuseau;
    }

    /** Chaque matin, à une heure où un SMS de rappel est bien reçu. */
    @Scheduled(cron = "${fasoguardian.abonnements.relances:0 30 7 * * *}", zone = "${fasoguardian.fuseau:Africa/Ouagadougou}")
    @SchedulerLock(name = "abonnements-relances", lockAtMostFor = "PT30M")
    public void relancer() {
        traiter(LocalDate.now(horloge.withZone(fuseau)));
    }

    /** @return le nombre d'abonnements pour lesquels une étape a été franchie */
    public int traiter(LocalDate aujourdhui) {
        int franchies = 0;
        for (Abonnement echu : abonnements.findByProchaineEcheanceLessThanEqualAndEtapeRelanceLessThan(
                aujourdhui.plusDays(Abonnement.JOURS_AVANT_RAPPEL_D_ECHEANCE), Abonnement.RELANCES_EPUISEES)) {
            UUID id = echu.id();
            try {
                if (Boolean.TRUE.equals(transaction.execute(etat -> relancer(id, aujourdhui)))) {
                    franchies++;
                }
            } catch (RuntimeException erreur) {
                journalTechnique.error("Relance impossible pour l'abonnement {} ({})", id, erreur.getClass().getName());
            }
        }
        return franchies;
    }

    /** Une demande restée sans réponse de l'agrégateur est close : le parent peut en refaire une. */
    @Scheduled(fixedDelayString = "${fasoguardian.abonnements.expiration-paiements:PT1M}")
    @SchedulerLock(name = "abonnements-expiration-paiements", lockAtMostFor = "PT5M")
    public void expirerLesDemandes() {
        Instant maintenant = horloge.instant();
        transaction.executeWithoutResult(etat -> paiements
                .findByStatutAndInitieLeBefore(Statut.INITIE, maintenant.minus(Paiement.DELAI_D_EXPIRATION))
                .forEach(paiement -> paiement.expirer(maintenant)));
    }

    private boolean relancer(UUID abonnementId, LocalDate aujourdhui) {
        Abonnement abonnement = abonnements.findById(abonnementId).orElseThrow();
        Relance relance = abonnement.relancer(aujourdhui, horloge.instant());
        String echeance = JOUR.format(abonnement.prochaineEcheance());
        String restriction = JOUR.format(abonnement.restrictionLe());
        switch (relance) {
            case RAPPEL_D_ECHEANCE -> prevenir(abonnement, Urgence.INFORMATION, "ECHEANCE_PROCHE", "Abonnement à renouveler",
                    abonnement.renouvellementAuto()
                            ? "FasoGuardian : votre abonnement sera renouvelé le " + echeance + " depuis votre portefeuille "
                                    + abonnement.moyen().libelle() + ". Vous validerez le paiement sur votre téléphone."
                            : "FasoGuardian : votre abonnement arrive à échéance le " + echeance
                                    + ". Renouvelez-le depuis l'application.");
            case RENOUVELLEMENT -> souscriptions.renouveler(abonnement);
            case RAPPEL_1 -> prevenir(abonnement, Urgence.IMPORTANTE, "IMPAYE_RAPPEL_1", "Abonnement impayé",
                    "FasoGuardian : votre abonnement n'a pas été renouvelé. Sans paiement d'ici le " + restriction
                            + ", le suivi continu et l'historique seront suspendus. Le SOS et la page QR restent actifs.");
            case RAPPEL_2 -> prevenir(abonnement, Urgence.IMPORTANTE, "IMPAYE_RAPPEL_2", "Dernier rappel",
                    "FasoGuardian : dernier rappel. Sans paiement d'ici le " + restriction
                            + ", le suivi continu et l'historique seront suspendus. Le SOS et la page QR restent actifs.");
            case RESTRICTION -> {
                prevenir(abonnement, Urgence.IMPORTANTE, "ABONNEMENT_RESTREINT", "Suivi continu suspendu",
                        "FasoGuardian : abonnement impayé. La position n'est plus donnée qu'à la demande et l'historique est "
                                + "limité à 24 h. Le SOS et la page QR restent actifs. Payez pour tout rétablir.");
                journal.consigner(null, "SYSTEME", "ABONNEMENT_RESTREINT", "ABONNEMENT", abonnement.id().toString(), Resultat.SUCCES);
                evenements.publishEvent(new DroitsModifies(abonnement.enfantId()));
            }
            case AUCUNE -> {
                // Rien à faire aujourd'hui pour cet abonnement.
            }
        }
        return relance != Relance.AUCUNE;
    }

    private void prevenir(Abonnement abonnement, Urgence urgence, String modele, String titre, String texte) {
        notifications.notifier(abonnement.tuteurId(), urgence, new Message(modele, titre, texte, LIEN, null));
    }
}
