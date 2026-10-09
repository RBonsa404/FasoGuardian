package bf.fasoguardian.telemetrie.infrastructure;

import java.nio.charset.StandardCharsets;

import bf.fasoguardian.dispositifs.CommandeAEnvoyer;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Publie sur le broker les commandes signées émises par le module dispositifs. Sans broker (poste de
 * développement, tests), ou s'il est injoignable, la commande reste en attente : elle sera réémise.
 */
@Component
class EmissionCommandes {

    private static final Logger journalTechnique = LoggerFactory.getLogger(EmissionCommandes.class);

    private final ObjectProvider<AbonneMqtt> broker;

    EmissionCommandes(ObjectProvider<AbonneMqtt> broker) {
        this.broker = broker;
    }

    @ApplicationModuleListener
    void surCommandeAEnvoyer(CommandeAEnvoyer commande) {
        AbonneMqtt abonne = broker.getIfAvailable();
        if (abonne == null || !abonne.connecte()) {
            return;
        }
        try {
            abonne.publierCommande(commande.numeroSerie(), commande.message().getBytes(StandardCharsets.US_ASCII));
        } catch (MqttException erreur) {
            journalTechnique.warn("Commande non publiée ({}), elle sera réémise", erreur.getMessage());
        }
    }
}
