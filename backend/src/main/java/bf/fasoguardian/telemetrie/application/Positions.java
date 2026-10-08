package bf.fasoguardian.telemetrie.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import bf.fasoguardian.audit.JournalAudit;
import bf.fasoguardian.audit.JournalAudit.Resultat;
import bf.fasoguardian.dispositifs.Bracelets;
import bf.fasoguardian.dispositifs.Bracelets.BraceletConnu;
import bf.fasoguardian.famille.AccesEnfant;
import bf.fasoguardian.telemetrie.domaine.EtatBracelet;
import bf.fasoguardian.telemetrie.domaine.PositionConnue;
import bf.fasoguardian.telemetrie.infrastructure.DepotTelemetrie;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dernière position et état du bracelet d'un enfant (US-PAR-006). L'accès exige un lien de tutelle actif et
 * chaque consultation est journalisée. Seules les données de l'appairage en cours sont restituées : un
 * bracelet reconditionné ne montre jamais les positions de l'enfant qui le portait avant.
 */
@Service
public class Positions {

    /** Situation du bracelet de l'enfant ; {@code position} et {@code etat} manquent tant qu'il n'a rien émis. */
    public record Situation(String numeroSerie, PositionConnue position, EtatBracelet etat) {
    }

    /** Un point toutes les 60 s pendant 24 h, cas le plus dense (mode alerte). */
    private static final int POINTS_PAR_JOUR = 1440;

    /** @param joursConserves durée de conservation de l'historique, en jours */
    public record Trajet(LocalDate jour, List<PositionConnue> points, int joursConserves) {
    }

    private final Bracelets bracelets;
    private final DepotTelemetrie depot;
    private final AccesEnfant acces;
    private final JournalAudit journal;
    private final Clock horloge;
    private final Duration conservation;
    private final ZoneId fuseau;

    Positions(Bracelets bracelets, DepotTelemetrie depot, AccesEnfant acces, JournalAudit journal, Clock horloge,
            @Value("${fasoguardian.telemetrie.conservation-positions:P30D}") Duration conservation,
            @Value("${fasoguardian.fuseau:Africa/Ouagadougou}") ZoneId fuseau) {
        this.fuseau = fuseau;
        this.bracelets = bracelets;
        this.depot = depot;
        this.acces = acces;
        this.journal = journal;
        this.horloge = horloge;
        this.conservation = conservation;
    }

    @Transactional
    public Optional<Situation> situation(UUID tuteurId, UUID enfantId) {
        acces.exigerTuteur(tuteurId, enfantId);
        Optional<BraceletConnu> bracelet = bracelets.deLEnfant(enfantId);
        journal.consigner(tuteurId, "PARENT", "POSITION_CONSULTEE", "ENFANT", enfantId.toString(), Resultat.SUCCES);
        return bracelet.map(connu -> new Situation(connu.numeroSerie(),
                depot.dernierePosition(connu.id(), connu.appaireDepuis()).orElse(null),
                depot.etat(connu.id()).filter(etat -> !etat.dernierContact().isBefore(connu.appaireDepuis()))
                        .orElse(null)));
    }

    /**
     * Trajet d'une journée locale (US-PAR-008) : positions de l'appairage en cours, dans la limite de la durée
     * de conservation. Consultation journalisée.
     */
    @Transactional
    public Trajet trajet(UUID tuteurId, UUID enfantId, LocalDate jour) {
        acces.exigerTuteur(tuteurId, enfantId);
        journal.consigner(tuteurId, "PARENT", "TRAJET_CONSULTE", "ENFANT", enfantId.toString(), Resultat.SUCCES);
        Instant maintenant = horloge.instant();
        Instant plancher = maintenant.minus(conservation);
        Instant debut = jour.atStartOfDay(fuseau).toInstant();
        Instant fin = jour.plusDays(1).atStartOfDay(fuseau).toInstant();
        List<PositionConnue> points = bracelets.deLEnfant(enfantId).map(connu -> {
            Instant depuis = debut.isBefore(connu.appaireDepuis()) ? connu.appaireDepuis() : debut;
            return depot.positionsEntre(connu.id(), depuis.isBefore(plancher) ? plancher : depuis, fin, POINTS_PAR_JOUR);
        }).orElse(List.of());
        return new Trajet(jour, points, (int) conservation.toDays());
    }

    /** Prépare les partitions à venir et applique la durée de conservation des positions (FG-DOC-06 tableau 18). */
    @Scheduled(cron = "${fasoguardian.telemetrie.entretien:0 20 2 * * *}")
    @SchedulerLock(name = "telemetrie-entretien")
    public void entretenir() {
        Instant maintenant = horloge.instant();
        LocalDate jour = LocalDate.ofInstant(maintenant, ZoneOffset.UTC);
        for (int mois = 0; mois <= 2; mois++) {
            depot.creerPartitions(jour.plusMonths(mois));
        }
        depot.purgerPositions(maintenant.minus(conservation));
    }
}
