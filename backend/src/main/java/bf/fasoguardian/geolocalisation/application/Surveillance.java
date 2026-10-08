package bf.fasoguardian.geolocalisation.application;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;

import bf.fasoguardian.geolocalisation.RetourEnZone;
import bf.fasoguardian.geolocalisation.SortieDeZone;
import bf.fasoguardian.geolocalisation.domaine.Coordonnee;
import bf.fasoguardian.geolocalisation.domaine.SafeZone;
import bf.fasoguardian.geolocalisation.domaine.SuiviZone;
import bf.fasoguardian.geolocalisation.domaine.SuiviZone.Evaluation;
import bf.fasoguardian.geolocalisation.infrastructure.DepotZones;
import bf.fasoguardian.telemetrie.PositionRecue;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Évalue chaque position reçue contre les Safe Zones de l'enfant (US-ENF-001, FG-DOC-06 §9.1). Les zones
 * viennent du cache et l'appartenance se calcule en mémoire ; une sortie n'est publiée qu'après le délai de
 * tolérance. Une position trop imprécise ne dit rien de fiable et ne change pas le suivi (ADR 0010).
 */
@Component
class Surveillance {

    private final SafeZones zones;
    private final DepotZones depot;
    private final ApplicationEventPublisher evenements;
    private final Clock horloge;
    private final int precisionMaximaleM;

    Surveillance(SafeZones zones, DepotZones depot, ApplicationEventPublisher evenements, Clock horloge,
            @Value("${fasoguardian.geolocalisation.precision-maximale-m:300}") int precisionMaximaleM) {
        this.zones = zones;
        this.depot = depot;
        this.evenements = evenements;
        this.horloge = horloge;
        this.precisionMaximaleM = precisionMaximaleM;
    }

    @ApplicationModuleListener
    void surPositionRecue(PositionRecue position) {
        if (position.enfantId() == null) {
            return;
        }
        LocalDateTime heureLocale = zones.heureLocale(position.mesureeLe());
        Coordonnee point = new Coordonnee(position.latitude(), position.longitude());
        for (SafeZone zone : zones.pourEvaluation(position.enfantId())) {
            if (!zone.active() || !zone.plage().contient(heureLocale)) {
                depot.oublierSuivi(zone.id());
            } else if (position.precisionM() <= precisionMaximaleM) {
                evaluer(zone, point, position);
            }
        }
    }

    private void evaluer(SafeZone zone, Coordonnee point, PositionRecue position) {
        Evaluation evaluation = SuiviZone.evaluer(depot.suivi(zone.id()).orElse(null), zone.contient(point),
                position.mesureeLe(), Duration.ofSeconds(zone.toleranceS()));
        depot.enregistrerSuivi(zone.id(), evaluation.suivi());
        switch (evaluation.constat()) {
            case SORTIE -> {
                depot.ajouterFranchissement(zone.id(), "SORTIE", point, position.mesureeLe(), horloge.instant());
                evenements.publishEvent(new SortieDeZone(zone.id(), zone.enfantId(), zone.nom(), position.latitude(),
                        position.longitude(), evaluation.suivi().dehorsDepuis(), horloge.instant()));
            }
            case RETOUR -> {
                depot.ajouterFranchissement(zone.id(), "RETOUR", point, position.mesureeLe(), horloge.instant());
                evenements.publishEvent(new RetourEnZone(zone.id(), zone.enfantId(), zone.nom(), position.mesureeLe()));
            }
            case RIEN -> { }
        }
    }
}
