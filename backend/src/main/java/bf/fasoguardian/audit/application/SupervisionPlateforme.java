package bf.fasoguardian.audit.application;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;

import bf.fasoguardian.audit.JournalAudit;
import bf.fasoguardian.audit.JournalAudit.Resultat;
import bf.fasoguardian.audit.RegistrePurges;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.distribution.ValueAtPercentile;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Supervision de la plateforme (US-ADM-004). Chaque minute, le serveur note qu'il répond et joint sa base :
 * les minutes sans note sont de l'indisponibilité, d'où la disponibilité sur trente jours. Le délai entre un
 * événement du bracelet et la notification des parents est suivi au 95e centile ; au-delà de 45 secondes,
 * une alerte d'exploitation est consignée au journal et comptée pour la télésurveillance.
 */
@Service
public class SupervisionPlateforme {

    public static final double OBJECTIF_DE_DISPONIBILITE = 99.5;
    public static final Duration SEUIL_DE_DELAI = Duration.ofSeconds(45);
    private static final Duration FENETRE = Duration.ofDays(30);
    private static final Duration CONSERVATION = Duration.ofDays(90);
    /** Une alerte d'exploitation au plus par heure tant que le délai reste au-dessus du seuil. */
    private static final Duration SILENCE_ENTRE_ALERTES = Duration.ofHours(1);

    /**
     * @param disponibilite     pourcentage des minutes où le serveur a répondu, sur la période mesurée
     * @param minutesMesurees   étendue de la mesure, trente jours au plus
     * @param delaiP95S         délai événement → notification au 95e centile, en secondes ; {@code null} sans alerte récente
     * @param derniereAlerte    dernière alerte d'exploitation sur le délai, ou {@code null}
     */
    public record Tableau(double disponibilite, double objectifDeDisponibilite, long minutesMesurees, long minutesIndisponibles,
            Double delaiP95S, long seuilDeDelaiS, boolean delaiHorsSeuil, Instant derniereAlerte, int braceletsEnService,
            int braceletsMuets, long alertesOuvertes, long smsEnvoyes, long notificationsPoussees, long messagesRefuses) {
    }

    private final JdbcTemplate jdbc;
    private final MeterRegistry metriques;
    private final JournalAudit journal;
    private final RegistrePurges registre;
    private final Clock horloge;
    private final Counter alertesDExploitation;

    SupervisionPlateforme(JdbcTemplate jdbc, MeterRegistry metriques, JournalAudit journal, RegistrePurges registre, Clock horloge) {
        this.jdbc = jdbc;
        this.metriques = metriques;
        this.journal = journal;
        this.registre = registre;
        this.horloge = horloge;
        this.alertesDExploitation = Counter.builder("fasoguardian.exploitation.alertes")
                .description("Alertes d'exploitation émises par la supervision").tag("motif", "DELAI_DE_NOTIFICATION")
                .register(metriques);
    }

    /** Note la minute en cours, puis contrôle le délai de notification. */
    @Scheduled(fixedDelayString = "${fasoguardian.supervision.sonde:PT1M}")
    public void sonder() {
        noter(horloge.instant().truncatedTo(ChronoUnit.MINUTES));
        controlerLeDelai();
    }

    private void noter(Instant minute) {
        jdbc.update("INSERT INTO audit.sonde_disponibilite (minute) VALUES (?) ON CONFLICT DO NOTHING", Timestamp.from(minute));
    }

    /** @return {@code true} si une alerte d'exploitation vient d'être émise */
    public boolean controlerLeDelai() {
        Double p95 = delaiP95();
        if (p95 == null || p95 <= SEUIL_DE_DELAI.toSeconds()) {
            return false;
        }
        Instant maintenant = horloge.instant();
        Instant derniere = derniereAlerte();
        if (derniere != null && derniere.isAfter(maintenant.minus(SILENCE_ENTRE_ALERTES))) {
            return false;
        }
        journal.consigner(null, "SYSTEME", "DELAI_DE_NOTIFICATION_ELEVE", "SUPERVISION", Long.toString(Math.round(p95)), Resultat.REFUS);
        alertesDExploitation.increment();
        return true;
    }

    @Transactional
    public Tableau tableau() {
        Instant maintenant = horloge.instant().truncatedTo(ChronoUnit.MINUTES);
        // Le serveur répond, puisqu'il sert cet écran : la minute en cours est notée sans attendre la sonde.
        noter(maintenant);
        Instant debutDeFenetre = maintenant.minus(FENETRE);
        Timestamp premiere = jdbc.queryForObject("SELECT min(minute) FROM audit.sonde_disponibilite WHERE minute >= ?", Timestamp.class,
                Timestamp.from(debutDeFenetre));
        long attendues = premiere == null ? 0 : Duration.between(premiere.toInstant(), maintenant).toMinutes() + 1;
        Long presentes = jdbc.queryForObject("SELECT count(*) FROM audit.sonde_disponibilite WHERE minute >= ? AND minute <= ?",
                Long.class, Timestamp.from(debutDeFenetre), Timestamp.from(maintenant));
        long repondues = presentes == null ? 0 : Math.min(presentes, attendues);
        double disponibilite = attendues == 0 ? 100.0 : Math.round(repondues * 100_000.0 / attendues) / 1000.0;
        Double p95 = delaiP95();
        return new Tableau(disponibilite, OBJECTIF_DE_DISPONIBILITE, attendues, attendues - repondues,
                p95 == null ? null : Math.round(p95 * 10) / 10.0, SEUIL_DE_DELAI.toSeconds(),
                p95 != null && p95 > SEUIL_DE_DELAI.toSeconds(), derniereAlerte(), (int) jauge("fasoguardian.bracelets.en.service"),
                (int) jauge("fasoguardian.bracelets.muets"), compteur("fasoguardian.alertes.ouvertes", null, null),
                compteur("fasoguardian.notifications", "canal", "sms"), compteur("fasoguardian.notifications", "canal", "push"),
                compteur("fasoguardian.telemetrie.messages", "resultat", "APPAREIL_REFUSE")
                        + compteur("fasoguardian.telemetrie.messages", "resultat", "INVALIDE"));
    }

    /** Les sondes de plus de 90 jours ne servent plus à aucun indicateur. */
    @Scheduled(cron = "${fasoguardian.supervision.purge:0 25 3 * * *}")
    @SchedulerLock(name = "audit-purge-sondes")
    public void purger() {
        registre.consigner("SONDES_DE_DISPONIBILITE", jdbc.update("DELETE FROM audit.sonde_disponibilite WHERE minute < ?",
                Timestamp.from(horloge.instant().minus(CONSERVATION))));
    }

    /** 95e centile du délai de notification, en secondes, sur les dernières minutes ; {@code null} sans mesure. */
    private Double delaiP95() {
        Timer delai = metriques.find("fasoguardian.alertes.delai.notification").timer();
        if (delai == null) {
            return null;
        }
        for (ValueAtPercentile valeur : delai.takeSnapshot().percentileValues()) {
            if (valeur.percentile() == 0.95 && valeur.value(TimeUnit.SECONDS) > 0) {
                return valeur.value(TimeUnit.SECONDS);
            }
        }
        return null;
    }

    private Instant derniereAlerte() {
        return jdbc.query("SELECT horodatage FROM audit.entree WHERE action = 'DELAI_DE_NOTIFICATION_ELEVE' ORDER BY id DESC LIMIT 1",
                (ligne, rang) -> ligne.getTimestamp("horodatage").toInstant()).stream().findFirst().orElse(null);
    }

    private double jauge(String nom) {
        Gauge jauge = metriques.find(nom).gauge();
        return jauge == null ? 0 : jauge.value();
    }

    /** Somme des compteurs de ce nom, restreinte à une étiquette si elle est donnée ; depuis le démarrage du serveur. */
    private long compteur(String nom, String etiquette, String valeur) {
        return Math.round((etiquette == null ? metriques.find(nom) : metriques.find(nom).tag(etiquette, valeur)).counters().stream()
                .mapToDouble(Counter::count).sum());
    }
}
