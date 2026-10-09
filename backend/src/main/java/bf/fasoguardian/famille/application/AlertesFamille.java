package bf.fasoguardian.famille.application;

import java.time.Duration;
import java.util.UUID;

import bf.fasoguardian.famille.QrConsulte;
import bf.fasoguardian.famille.TiersAPrevenu;
import bf.fasoguardian.identite.LiensTutelle;
import bf.fasoguardian.notifications.Notifications;
import bf.fasoguardian.notifications.Notifications.Message;
import bf.fasoguardian.notifications.Notifications.Urgence;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Informe les tuteurs d'un enfant lorsque son bracelet est scanné ou qu'un tiers leur laisse un message.
 * Les SMS ne portent ni lieu, ni numéro du tiers, ni information médicale : ils renvoient à l'application.
 */
@Component
class AlertesFamille {

    private final LiensTutelle liens;
    private final Notifications notifications;
    // Un seul SMS de scan par enfant et par tranche de dix minutes.
    private final Cache<UUID, Boolean> scansRecents =
            Caffeine.newBuilder().expireAfterWrite(Duration.ofMinutes(10)).maximumSize(50_000).build();

    AlertesFamille(LiensTutelle liens, Notifications notifications) {
        this.liens = liens;
        this.notifications = notifications;
    }

    @ApplicationModuleListener
    void surQrConsulte(QrConsulte evenement) {
        if (scansRecents.asMap().putIfAbsent(evenement.enfantId(), Boolean.TRUE) != null) {
            return;
        }
        prevenir(evenement.enfantId(), "QR_SCANNE", "Bracelet scanné", "le bracelet " + evenement.numeroBracelet()
                + " vient d'être scanné. Ouvrez l'application.");
    }

    @ApplicationModuleListener
    void surTiersAPrevenu(TiersAPrevenu evenement) {
        prevenir(evenement.enfantId(), "MESSAGE_D_UN_TIERS", "Message d'un tiers", "la personne qui a scanné le bracelet "
                + evenement.numeroBracelet() + " vous a laissé un message. Ouvrez l'application pour la rappeler.");
    }

    /** Quelqu'un est auprès de l'enfant : la famille doit le savoir vite, par push puis par SMS. */
    private void prevenir(UUID enfantId, String modele, String titre, String texte) {
        Message message = new Message(modele, titre, texte, "/enfants/" + enfantId, null);
        liens.tuteursActifsDe(enfantId).forEach(tuteurId -> notifications.notifier(tuteurId, Urgence.IMPORTANTE, message));
    }
}
