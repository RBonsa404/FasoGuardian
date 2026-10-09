package bf.fasoguardian.telemetrie.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import bf.fasoguardian.abonnements.Droits;
import bf.fasoguardian.audit.JournalAudit;
import bf.fasoguardian.audit.RegistrePurges;
import bf.fasoguardian.audit.JournalAudit.Resultat;
import bf.fasoguardian.dispositifs.Bracelets;
import bf.fasoguardian.dispositifs.Bracelets.BraceletConnu;
import bf.fasoguardian.famille.AccesEnfant;
import bf.fasoguardian.telemetrie.TrajetsRecents;
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
public class Positions implements TrajetsRecents {

    /** Situation du bracelet de l'enfant ; {@code position} et {@code etat} manquent tant qu'il n'a rien émis. */
    public record Situation(String numeroSerie, PositionConnue position, EtatBracelet etat) {
    }

    /** Un point toutes les 60 s pendant 24 h, cas le plus dense (mode alerte). */
    private static final int POINTS_PAR_JOUR = 1440;

    /** @param joursConserves profondeur de l'historique ouverte par l'abonnement, en jours */
    public record Trajet(LocalDate jour, List<PositionConnue> points, int joursConserves) {
    }

    private final Bracelets bracelets;
    private final DepotTelemetrie depot;
    private final AccesEnfant acces;
    private final JournalAudit journal;
    private final Clock horloge;
    private final Duration conservation;
    private final Duration conservationMaximale;
    private final Droits droits;
    private final RegistrePurges registre;
    private final ZoneId fuseau;

    Positions(Bracelets bracelets, DepotTelemetrie depot, AccesEnfant acces, JournalAudit journal, Clock horloge, Droits droits,
            RegistrePurges registre,
            @Value("${fasoguardian.telemetrie.conservation-positions:P30D}") Duration conservation,
            @Value("${fasoguardian.telemetrie.conservation-maximale:P90D}") Duration conservationMaximale,
            @Value("${fasoguardian.fuseau:Africa/Ouagadougou}") ZoneId fuseau) {
        this.fuseau = fuseau;
        this.bracelets = bracelets;
        this.depot = depot;
        this.acces = acces;
        this.journal = journal;
        this.horloge = horloge;
        this.conservation = conservation;
        this.conservationMaximale = conservationMaximale;
        this.droits = droits;
        this.registre = registre;
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
     * Trajet d'une journée locale (US-PAR-008) : positions de l'appairage en cours, dans la limite de
     * l'historique ouvert par l'abonnement (24 heures quand il est restreint pour impayé, US-SYS-008).
     * Consultation journalisée.
     */
    @Transactional
    public Trajet trajet(UUID tuteurId, UUID enfantId, LocalDate jour) {
        acces.exigerTuteur(tuteurId, enfantId);
        journal.consigner(tuteurId, "PARENT", "TRAJET_CONSULTE", "ENFANT", enfantId.toString(), Resultat.SUCCES);
        Instant maintenant = horloge.instant();
        Duration historique = historique(enfantId);
        Instant plancher = maintenant.minus(historique);
        Instant debut = jour.atStartOfDay(fuseau).toInstant();
        Instant fin = jour.plusDays(1).atStartOfDay(fuseau).toInstant();
        List<PositionConnue> points = bracelets.deLEnfant(enfantId).map(connu -> {
            Instant depuis = debut.isBefore(connu.appaireDepuis()) ? connu.appaireDepuis() : debut;
            return depot.positionsEntre(connu.id(), depuis.isBefore(plancher) ? plancher : depuis, fin, POINTS_PAR_JOUR);
        }).orElse(List.of());
        return new Trajet(jour, points, (int) historique.toDays());
    }

    /** Historique ouvert par l'offre, sans jamais dépasser la durée maximale de conservation. */
    private Duration historique(UUID enfantId) {
        Duration offre = Duration.ofDays(droits.de(enfantId).historiqueJours());
        return offre.compareTo(conservationMaximale) < 0 ? offre : conservationMaximale;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Point> depuis(UUID enfantId, Instant debut) {
        return bracelets.deLEnfant(enfantId)
                .map(connu -> depot.positionsEntre(connu.id(), debut.isBefore(connu.appaireDepuis()) ? connu.appaireDepuis() : debut,
                        horloge.instant().plusSeconds(1), POINTS_PAR_JOUR))
                .orElse(List.of()).stream().map(p -> new Point(p.latitude(), p.longitude(), p.precisionM(),
                        p.source() != bf.fasoguardian.telemetrie.domaine.Mesure.Source.GNSS, p.mesureeLe()))
                .toList();
    }

    /**
     * Prépare les partitions à venir et applique la durée de conservation des positions (FG-DOC-04, FG-DOC-06
     * tableau 18) : 30 jours par défaut, 24 heures pour l'offre sans historique, 90 jours au plus pour l'offre à historique étendu.
     */
    @Scheduled(cron = "${fasoguardian.telemetrie.entretien:0 20 2 * * *}")
    @SchedulerLock(name = "telemetrie-entretien")
    public void entretenir() {
        Instant maintenant = horloge.instant();
        LocalDate jour = LocalDate.ofInstant(maintenant, ZoneOffset.UTC);
        for (int mois = 0; mois <= 2; mois++) {
            depot.creerPartitions(jour.plusMonths(mois));
        }
        long supprimees = depot.purgerPositions(maintenant.minus(conservationMaximale));
        // Les enfants dont l'offre fixe une autre durée que la durée par défaut sont purgés à part.
        int parDefaut = (int) conservation.toDays();
        Map<UUID, Integer> particuliers = new HashMap<>();
        droits.joursDeConservation().forEach((enfant, jours) -> {
            if (jours != parDefaut) {
                bracelets.deLEnfant(enfant).ifPresent(connu -> particuliers.put(connu.id(), jours));
            }
        });
        supprimees += depot.purgerPositionsSauf(maintenant.minus(conservation), List.copyOf(particuliers.keySet()));
        for (Map.Entry<UUID, Integer> particulier : particuliers.entrySet()) {
            supprimees += depot.purgerPositionsDe(particulier.getKey(), maintenant.minus(Duration.ofDays(particulier.getValue())));
        }
        registre.consigner("POSITIONS", supprimees);
    }
}
