package bf.fasoguardian.abonnements.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import bf.fasoguardian.abonnements.DroitsModifies;
import bf.fasoguardian.abonnements.application.AgregateurPaiement.Notification;
import bf.fasoguardian.abonnements.application.AgregateurPaiement.NotificationRejetee;
import bf.fasoguardian.abonnements.domaine.Abonnement;
import bf.fasoguardian.abonnements.domaine.Abonnement.Periode;
import bf.fasoguardian.abonnements.domaine.Facture;
import bf.fasoguardian.abonnements.domaine.Offre;
import bf.fasoguardian.abonnements.domaine.Paiement;
import bf.fasoguardian.abonnements.infrastructure.DepotAbonnements;
import bf.fasoguardian.abonnements.infrastructure.DepotFactures;
import bf.fasoguardian.abonnements.infrastructure.DepotOffres;
import bf.fasoguardian.abonnements.infrastructure.DepotPaiements;
import bf.fasoguardian.audit.JournalAudit;
import bf.fasoguardian.audit.JournalAudit.Resultat;
import bf.fasoguardian.notifications.Notifications;
import bf.fasoguardian.notifications.Notifications.Message;
import bf.fasoguardian.notifications.Notifications.Urgence;
import bf.fasoguardian.plateforme.erreurs.CodeErreur;
import bf.fasoguardian.plateforme.erreurs.ErreurMetier;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Notifications de l'agrégateur (US-PAR-015). Un abonnement n'est activé ou prolongé qu'ici, après
 * vérification de la signature et du montant : jamais sur la déclaration du client. Une notification reçue
 * deux fois n'a d'effet qu'une fois.
 */
@Service
public class ReceptionPaiements {

    private static final DateTimeFormatter JOUR = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRENCH);

    private final AgregateurPaiement agregateur;
    private final DepotPaiements paiements;
    private final DepotAbonnements abonnements;
    private final DepotOffres offres;
    private final DepotFactures factures;
    private final Notifications notifications;
    private final ApplicationEventPublisher evenements;
    private final JournalAudit journal;
    private final MeterRegistry metriques;
    private final Clock horloge;
    private final ZoneId fuseau;

    ReceptionPaiements(AgregateurPaiement agregateur, DepotPaiements paiements, DepotAbonnements abonnements,
            DepotOffres offres, DepotFactures factures, Notifications notifications, ApplicationEventPublisher evenements,
            JournalAudit journal, MeterRegistry metriques, Clock horloge,
            @Value("${fasoguardian.fuseau:Africa/Ouagadougou}") ZoneId fuseau) {
        this.agregateur = agregateur;
        this.paiements = paiements;
        this.abonnements = abonnements;
        this.offres = offres;
        this.factures = factures;
        this.notifications = notifications;
        this.evenements = evenements;
        this.journal = journal;
        this.metriques = metriques;
        this.horloge = horloge;
        this.fuseau = fuseau;
    }

    /**
     * @param signature en-tête de signature tel que reçu
     * @param corps     corps de la requête, octet pour octet
     */
    @Transactional(noRollbackFor = ErreurMetier.class)
    public void recevoir(String signature, byte[] corps) {
        Notification notification;
        try {
            notification = agregateur.lire(signature, corps);
        } catch (NotificationRejetee rejet) {
            journal.consigner(null, "SYSTEME", "NOTIFICATION_DE_PAIEMENT_REJETEE", "PAIEMENT", null, Resultat.REFUS);
            metriques.counter("fasoguardian.paiements", "issue", "NOTIFICATION_REJETEE").increment();
            throw new ErreurMetier(CodeErreur.ACCES_REFUSE, "Notification non authentifiée.");
        }
        Paiement paiement = paiements.findByReferenceOperateur(notification.reference()).orElse(null);
        if (paiement == null) {
            // Référence inconnue : rien à faire, et une erreur ferait seulement réémettre la notification.
            journal.consigner(null, "SYSTEME", "NOTIFICATION_DE_PAIEMENT_SANS_OBJET", "PAIEMENT", null, Resultat.REFUS);
            return;
        }
        Instant maintenant = horloge.instant();
        if (!notification.confirme()) {
            if (paiement.echouer(notification.motif(), maintenant)) {
                journal.consigner(null, "SYSTEME", "PAIEMENT_ECHOUE", "PAIEMENT", paiement.id().toString(), Resultat.REFUS);
                metriques.counter("fasoguardian.paiements", "issue", "ECHOUE").increment();
            }
            return;
        }
        if (notification.montantFcfa() != paiement.montantFcfa()) {
            // Montant différent de celui demandé : l'abonnement n'est pas servi, l'écart est à instruire.
            journal.consigner(null, "SYSTEME", "PAIEMENT_MONTANT_INCOHERENT", "PAIEMENT", paiement.id().toString(), Resultat.REFUS);
            metriques.counter("fasoguardian.paiements", "issue", "MONTANT_INCOHERENT").increment();
            return;
        }
        if (!paiement.confirmer(maintenant)) {
            return;
        }
        Abonnement abonnement = abonnements.findById(paiement.abonnementId()).orElseThrow();
        Offre offre = offres.findById(paiement.offreCode()).orElseThrow();
        LocalDate aujourdhui = LocalDate.ofInstant(maintenant, fuseau);
        Periode periode = abonnement.confirmerPaiement(offre.code(), aujourdhui, maintenant);
        String numero = "FG-R-%d-%02d-%04d".formatted(aujourdhui.getYear(), aujourdhui.getMonthValue(), factures.prochainNumero());
        factures.save(new Facture(numero, paiement, abonnement.tuteurId(), offre.libelle(), abonnement.numeroMasque(), periode, maintenant));
        journal.consigner(null, "SYSTEME", "PAIEMENT_CONFIRME", "ABONNEMENT", abonnement.id().toString(), Resultat.SUCCES);
        metriques.counter("fasoguardian.paiements", "issue", "CONFIRME").increment();
        notifications.notifier(abonnement.tuteurId(), Urgence.INFORMATION, new Message("PAIEMENT_RECU", "Paiement reçu",
                "FasoGuardian : paiement de " + paiement.montantFcfa() + " FCFA reçu. Abonnement " + offre.libelle()
                        + " actif jusqu'au " + JOUR.format(periode.fin()) + ". Reçu " + numero + ".",
                "/abonnement/recus", null));
        evenements.publishEvent(new DroitsModifies(abonnement.enfantId()));
    }
}
