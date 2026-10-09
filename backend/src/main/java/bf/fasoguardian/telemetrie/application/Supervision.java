package bf.fasoguardian.telemetrie.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import bf.fasoguardian.dispositifs.Bracelets;
import bf.fasoguardian.dispositifs.Bracelets.EnService;
import bf.fasoguardian.dispositifs.SuiviBracelets;
import bf.fasoguardian.telemetrie.domaine.EtatBracelet;
import bf.fasoguardian.telemetrie.infrastructure.DepotTelemetrie;
import io.micrometer.core.instrument.MeterRegistry;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Supervision des bracelets en service (US-SAV-001) : un bracelet qui n'a rien émis depuis plus de trois
 * intervalles est signalé au suivi, qui informe le parent et ouvre un ticket ; quand il redonne des nouvelles,
 * le ticket est clos. Un bracelet dont l'émission périodique est suspendue n'est pas attendu.
 */
@Service
public class Supervision {

    /** Nombre d'intervalles sans nouvelles au-delà duquel le bracelet est tenu pour muet. */
    static final int INTERVALLES_TOLERES = 3;

    private final Bracelets bracelets;
    private final SuiviBracelets suivi;
    private final DepotTelemetrie depot;
    private final Clock horloge;

    private final AtomicInteger enService = new AtomicInteger();
    private final AtomicInteger silencieux = new AtomicInteger();

    Supervision(Bracelets bracelets, SuiviBracelets suivi, DepotTelemetrie depot, Clock horloge, MeterRegistry metriques) {
        metriques.gauge("fasoguardian.bracelets.en.service", enService);
        metriques.gauge("fasoguardian.bracelets.muets", silencieux);
        this.bracelets = bracelets;
        this.suivi = suivi;
        this.depot = depot;
        this.horloge = horloge;
    }

    @Scheduled(fixedDelayString = "${fasoguardian.telemetrie.supervision:PT1M}")
    @SchedulerLock(name = "telemetrie-supervision", lockAtMostFor = "PT5M")
    public void superviser() {
        Instant maintenant = horloge.instant();
        Set<UUID> muets = suivi.muets();
        List<EnService> suivis = bracelets.enService();
        int sansNouvelles = 0;
        for (EnService bracelet : suivis) {
            if (bracelet.intervalleS() <= 0) {
                continue;
            }
            Optional<EtatBracelet> etat = depot.etat(bracelet.braceletId())
                    .filter(e -> !e.dernierContact().isBefore(bracelet.appaireDepuis()));
            Instant contact = etat.map(EtatBracelet::dernierContact).orElse(bracelet.appaireDepuis());
            boolean muet = Duration.between(contact, maintenant).getSeconds() > (long) INTERVALLES_TOLERES * bracelet.intervalleS();
            if (muet) {
                sansNouvelles++;
            }
            if (muet && !muets.contains(bracelet.braceletId())) {
                suivi.signalerMuet(bracelet.braceletId(), etat.map(EtatBracelet::dernierContact).orElse(null),
                        etat.map(EtatBracelet::batterie).orElse(null), etat.map(EtatBracelet::reseau).orElse(null));
            } else if (!muet && muets.contains(bracelet.braceletId())) {
                suivi.signalerReprise(bracelet.braceletId());
            }
        }
        enService.set(suivis.size());
        silencieux.set(sansNouvelles);
    }
}
