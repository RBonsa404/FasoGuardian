package bf.fasoguardian.alertes.application;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

import bf.fasoguardian.alertes.domaine.Alerte;
import bf.fasoguardian.alertes.domaine.Alerte.Statut;
import bf.fasoguardian.alertes.domaine.Alerte.Type;
import bf.fasoguardian.alertes.infrastructure.DepotActions;
import bf.fasoguardian.alertes.infrastructure.DepotAlertes;
import bf.fasoguardian.geolocalisation.RetourEnZone;
import bf.fasoguardian.geolocalisation.SortieDeZone;
import bf.fasoguardian.telemetrie.EvenementBraceletRecu;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Transforme en alertes les événements du bracelet et des Safe Zones (US-ENF-001, US-ENF-002). Un retrait
 * pendant une fenêtre autorisée est seulement noté ; une cause qui disparaît d'elle-même clôt son alerte.
 */
@Component
class Declenchement {

    private static final List<Statut> A_RESOUDRE = List.of(Statut.OUVERTE, Statut.ACQUITTEE);

    private final OuvertureAlertes ouverture;
    private final Retraits retraits;
    private final DepotAlertes alertes;
    private final DepotActions actions;
    private final Clock horloge;
    private final Timer delaiDeNotification;

    Declenchement(OuvertureAlertes ouverture, Retraits retraits, DepotAlertes alertes, DepotActions actions,
            Clock horloge, MeterRegistry metriques) {
        this.delaiDeNotification = Timer.builder("fasoguardian.alertes.delai.notification")
                .description("Délai entre un événement du bracelet et la notification des parents")
                .publishPercentiles(0.95).publishPercentileHistogram().register(metriques);
        this.ouverture = ouverture;
        this.retraits = retraits;
        this.alertes = alertes;
        this.actions = actions;
        this.horloge = horloge;
    }

    @ApplicationModuleListener
    void surEvenementDuBracelet(EvenementBraceletRecu evenement) {
        if (evenement.enfantId() == null) {
            return;
        }
        switch (evenement.type()) {
            case SOS -> ouvrir(evenement, Type.SOS);
            case CHUTE -> ouvrir(evenement, Type.CHUTE);
            case BATTERIE_CRITIQUE -> ouvrir(evenement, Type.BATTERIE_CRITIQUE);
            case COUPURE_BOUCLE, PERTE_CONTACT_PEAU -> {
                if (!retraits.noterRetraitAutorise(evenement.braceletId(), evenement.mesureLe())) {
                    ouvrir(evenement, Type.RETRAIT);
                }
            }
            case PORT_RETABLI -> retraits.noterBraceletRemis(evenement.braceletId());
            case MISE_EN_CHARGE -> resoudre(alertes.findByEnfantIdAndTypeAndStatutIn(evenement.enfantId(),
                    Type.BATTERIE_CRITIQUE, A_RESOUDRE), "Bracelet mis en charge");
        }
    }

    @ApplicationModuleListener
    void surSortieDeZone(SortieDeZone sortie) {
        ouverture.ouvrir(sortie.enfantId(), Type.SORTIE_ZONE, null, sortie.zoneId(), sortie.nomZone(), sortie.latitude(),
                sortie.longitude());
    }

    @ApplicationModuleListener
    void surRetourEnZone(RetourEnZone retour) {
        resoudre(alertes.findByEnfantIdAndTypeAndStatutIn(retour.enfantId(), Type.SORTIE_ZONE, A_RESOUDRE).stream()
                .filter(alerte -> retour.zoneId().equals(alerte.zoneId())).toList(), "Retour dans la zone");
    }

    private void ouvrir(EvenementBraceletRecu evenement, Type type) {
        ouverture.ouvrir(evenement.enfantId(), type, null, null, null, evenement.latitude(), evenement.longitude())
                // Délai entre l'événement au poignet et la notification des parents : l'indicateur suivi par
                // la supervision (objectif : moins de 45 s au 95e centile, US-ADM-004).
                .ifPresent(alerte -> delaiDeNotification.record(Duration.between(evenement.mesureLe(), horloge.instant())));
    }

    private void resoudre(List<Alerte> concernees, String motif) {
        concernees.forEach(alerte -> {
            actions.save(alerte.resoudre(motif, horloge.instant()));
            ouverture.accuser(alerte);
        });
    }
}
