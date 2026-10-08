package bf.fasoguardian.famille.application;

import java.time.Duration;
import java.util.UUID;

import bf.fasoguardian.famille.QrConsulte;
import bf.fasoguardian.famille.TiersAPrevenu;
import bf.fasoguardian.identite.LiensTutelle;
import bf.fasoguardian.identite.MessagesTuteurs;
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
    private final MessagesTuteurs messages;
    // Un seul SMS de scan par enfant et par tranche de dix minutes.
    private final Cache<UUID, Boolean> scansRecents =
            Caffeine.newBuilder().expireAfterWrite(Duration.ofMinutes(10)).maximumSize(50_000).build();

    AlertesFamille(LiensTutelle liens, MessagesTuteurs messages) {
        this.liens = liens;
        this.messages = messages;
    }

    @ApplicationModuleListener
    void surQrConsulte(QrConsulte evenement) {
        if (scansRecents.asMap().putIfAbsent(evenement.enfantId(), Boolean.TRUE) != null) {
            return;
        }
        prevenir(evenement.enfantId(), "FasoGuardian : le bracelet " + evenement.numeroBracelet()
                + " vient d'être scanné. Ouvrez l'application.");
    }

    @ApplicationModuleListener
    void surTiersAPrevenu(TiersAPrevenu evenement) {
        prevenir(evenement.enfantId(), "FasoGuardian : la personne qui a scanné le bracelet " + evenement.numeroBracelet()
                + " vous a laissé un message. Ouvrez l'application pour la rappeler.");
    }

    private void prevenir(UUID enfantId, String texte) {
        liens.tuteursActifsDe(enfantId).forEach(tuteurId -> messages.envoyerSms(tuteurId, texte));
    }
}
