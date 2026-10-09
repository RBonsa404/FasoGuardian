package bf.fasoguardian.notifications.application;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import bf.fasoguardian.audit.RegistrePurges;
import bf.fasoguardian.notifications.Annuaire;
import bf.fasoguardian.notifications.Notifications;
import bf.fasoguardian.notifications.ServiceSms;
import bf.fasoguardian.notifications.application.CanalPush.Issue;
import bf.fasoguardian.notifications.domaine.AbonnementPush;
import bf.fasoguardian.notifications.domaine.Notification;
import bf.fasoguardian.notifications.infrastructure.DepotAbonnementsPush;
import bf.fasoguardian.notifications.infrastructure.DepotNotifications;
import bf.fasoguardian.plateforme.erreurs.CodeErreur;
import bf.fasoguardian.plateforme.erreurs.ErreurMetier;
import io.micrometer.core.instrument.MeterRegistry;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Achemine les notifications : push sur les navigateurs abonnés, SMS aussitôt pour une urgence critique ou
 * faute d'abonnement, SMS de repli au bout de 60 secondes pour une notification importante restée sans accusé.
 */
@Service
public class ServiceNotifications implements Notifications {

    private static final Duration CONSERVATION = Duration.ofDays(90);

    private final DepotNotifications notifications;
    private final DepotAbonnementsPush abonnements;
    private final CanalPush push;
    private final ServiceSms sms;
    private final Annuaire annuaire;
    private final ApplicationEventPublisher evenements;
    private final JsonMapper json;
    private final MeterRegistry metriques;
    private final RegistrePurges registre;
    private final Clock horloge;

    ServiceNotifications(DepotNotifications notifications, DepotAbonnementsPush abonnements, CanalPush push, ServiceSms sms,
            Annuaire annuaire, ApplicationEventPublisher evenements, JsonMapper json, MeterRegistry metriques,
            RegistrePurges registre, Clock horloge) {
        this.notifications = notifications;
        this.registre = registre;
        this.abonnements = abonnements;
        this.push = push;
        this.sms = sms;
        this.annuaire = annuaire;
        this.evenements = evenements;
        this.json = json;
        this.metriques = metriques;
        this.horloge = horloge;
    }

    @Override
    @Transactional
    public void notifier(UUID destinataireId, Urgence urgence, Message message) {
        Notification notification = notifications.save(new Notification(destinataireId, message.modele(),
                Notification.Urgence.valueOf(urgence.name()), message.titre(), message.texte(), message.lien(),
                message.reference(), horloge.instant()));
        evenements.publishEvent(new NotificationCreee(notification.id()));
    }

    @Override
    @Transactional
    public void accuser(String reference) {
        Instant maintenant = horloge.instant();
        notifications.findByReferenceAndAccuseeLeIsNull(reference).forEach(notification -> notification.accuser(maintenant));
    }

    /** Accusé de livraison envoyé par le navigateur à la réception du push. */
    @Transactional
    public void accuserLivraison(UUID notificationId) {
        notifications.findById(notificationId).ifPresent(notification -> notification.accuser(horloge.instant()));
    }

    /** Enregistre l'abonnement d'un navigateur ; au-delà de cinq, le plus ancien du compte est retiré. */
    @Transactional
    public void abonner(UUID destinataireId, String pointDeLivraison, String cleP256dh, String secretAuth) {
        if (!push.disponible()) {
            throw new ErreurMetier(CodeErreur.RESSOURCE_INTROUVABLE, "Les notifications push ne sont pas disponibles.");
        }
        if (!push.admet(pointDeLivraison)) {
            throw new ErreurMetier(CodeErreur.REQUETE_INVALIDE, "Ce service de notification n'est pas reconnu.");
        }
        Instant maintenant = horloge.instant();
        abonnements.findByPointDeLivraison(pointDeLivraison).ifPresentOrElse(
                existant -> existant.remplacer(destinataireId, cleP256dh, secretAuth, maintenant),
                () -> abonnements.save(new AbonnementPush(destinataireId, pointDeLivraison, cleP256dh, secretAuth, maintenant)));
        abonnements.flush();
        List<AbonnementPush> duCompte = abonnements.findByDestinataireIdOrderByCreeLe(destinataireId);
        duCompte.stream().limit(Math.max(0, duCompte.size() - AbonnementPush.MAXIMUM_PAR_DESTINATAIRE)).forEach(abonnements::delete);
    }

    /** Retire l'abonnement de ce navigateur, s'il appartient bien au compte. */
    @Transactional
    public void desabonner(UUID destinataireId, String pointDeLivraison) {
        abonnements.findByPointDeLivraison(pointDeLivraison).filter(a -> a.destinataireId().equals(destinataireId))
                .ifPresent(abonnements::delete);
    }

    public String clePubliquePush() {
        if (!push.disponible()) {
            throw new ErreurMetier(CodeErreur.RESSOURCE_INTROUVABLE, "Les notifications push ne sont pas disponibles.");
        }
        return push.clePublique();
    }

    @ApplicationModuleListener
    void acheminer(NotificationCreee evenement) {
        Notification notification = notifications.findById(evenement.notificationId()).orElse(null);
        if (notification == null) {
            return;
        }
        Instant maintenant = horloge.instant();
        boolean poussee = pousser(notification, maintenant);
        // Critique : le SMS double le push. Sans push possible, le SMS est le seul canal, quelle que soit l'urgence.
        if (notification.urgence() == Notification.Urgence.CRITIQUE || !poussee) {
            envoyerSms(notification, maintenant);
        }
    }

    /** Repli SMS des notifications importantes restées sans accusé (US-ENF-001). */
    @Scheduled(fixedDelayString = "${fasoguardian.notifications.repli:PT15S}")
    @SchedulerLock(name = "notifications-repli-sms", lockAtMostFor = "PT1M")
    @Transactional
    public void replierParSms() {
        Instant maintenant = horloge.instant();
        notifications.findByUrgenceAndAccuseeLeIsNullAndSmsEnvoyeLeIsNullAndCreeeLeBefore(Notification.Urgence.IMPORTANTE,
                maintenant.minus(Notification.DELAI_AVANT_SMS)).stream().filter(n -> n.repliDu(maintenant))
                .forEach(notification -> envoyerSms(notification, maintenant));
    }

    /** Les notifications ne sont gardées que 90 jours. */
    @Scheduled(cron = "${fasoguardian.notifications.purge:0 50 2 * * *}")
    @SchedulerLock(name = "notifications-purge")
    @Transactional
    public void purger() {
        registre.consigner("NOTIFICATIONS", notifications.deleteByCreeeLeBefore(horloge.instant().minus(CONSERVATION)));
    }

    // -------------------------------------------------------------------- aides

    /** @return {@code true} si au moins un navigateur a reçu la notification */
    private boolean pousser(Notification notification, Instant maintenant) {
        if (!push.disponible()) {
            return false;
        }
        Map<String, Object> contenu = new LinkedHashMap<>();
        contenu.put("id", notification.id().toString());
        contenu.put("titre", notification.titre());
        contenu.put("texte", majuscule(notification.texte()));
        contenu.put("lien", notification.lien() == null ? "/" : notification.lien());
        contenu.put("critique", notification.urgence() == Notification.Urgence.CRITIQUE);
        byte[] octets = json.writeValueAsString(contenu).getBytes(StandardCharsets.UTF_8);
        boolean livree = false;
        for (AbonnementPush abonnement : abonnements.findByDestinataireIdOrderByCreeLe(notification.destinataireId())) {
            Issue issue = push.pousser(abonnement.pointDeLivraison(), abonnement.cleP256dh(), abonnement.secretAuth(), octets,
                    notification.urgence() != Notification.Urgence.INFORMATION);
            metriques.counter("fasoguardian.notifications", "canal", "push", "issue", issue.name()).increment();
            if (issue == Issue.LIVREE) {
                abonnement.noterSucces(maintenant);
                livree = true;
            } else if (issue == Issue.ABONNEMENT_PERIME) {
                abonnements.delete(abonnement);
            }
        }
        if (livree) {
            notification.noterPoussee(maintenant);
        }
        return livree;
    }

    private void envoyerSms(Notification notification, Instant maintenant) {
        annuaire.telephoneE164(notification.destinataireId()).ifPresent(numero -> {
            sms.envoyer(numero, "FasoGuardian : " + notification.texte());
            metriques.counter("fasoguardian.notifications", "canal", "sms", "issue", "ENVOYE").increment();
        });
        notification.noterSms(maintenant);
    }

    private static String majuscule(String texte) {
        return texte.substring(0, 1).toUpperCase(java.util.Locale.FRENCH) + texte.substring(1);
    }
}
