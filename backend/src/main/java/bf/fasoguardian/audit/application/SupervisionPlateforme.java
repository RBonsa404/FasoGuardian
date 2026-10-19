package bf.fasoguardian.audit.application;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import bf.fasoguardian.audit.JournalAudit;
import bf.fasoguardian.audit.JournalAudit.Resultat;
import bf.fasoguardian.audit.RegistrePurges;
import bf.fasoguardian.plateforme.mesure.MesureDesRequetes;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.distribution.ValueAtPercentile;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.unit.DataSize;

/**
 * Supervision de la plateforme (US-ADM-004). Chaque minute, le serveur note qu'il répond et joint sa base :
 * les minutes sans note sont de l'indisponibilité, d'où la disponibilité sur trente jours. Le délai entre un
 * événement du bracelet et la notification des parents est suivi au 95e centile ; au-delà de 45 secondes,
 * une alerte d'exploitation est consignée au journal et comptée pour la télésurveillance. Les indicateurs de
 * FG-DOC-06 (tableau 20) sont relevés chaque heure pour que l'écran 72 montre leur évolution (ADR 0039).
 */
@Service
public class SupervisionPlateforme {

    public static final double OBJECTIF_DE_DISPONIBILITE = 99.5;
    public static final Duration SEUIL_DE_DELAI = Duration.ofSeconds(45);
    /** Objectifs de FG-DOC-06, tableau 20. */
    public static final long OBJECTIF_DE_DELAI_S = 60;
    public static final long OBJECTIF_DE_LATENCE_MS = 500;
    public static final double OBJECTIF_D_ERREURS_PCT = 0.5;
    public static final double OBJECTIF_DE_DISQUE_PCT = 70;
    /** Flux reçus du broker MQTT, et donc comptés dans le taux d'erreur ; les trames LoRaWAN n'en sont pas. */
    private static final List<String> FLUX_MQTT = List.of("telemetry", "status", "alert", "ack");
    private static final int HEURES = 24;
    private static final int JOURS = 30;
    private static final Duration FENETRE = Duration.ofDays(30);
    private static final Duration CONSERVATION = Duration.ofDays(90);
    /** Une alerte d'exploitation au plus par heure tant que le délai reste au-dessus du seuil. */
    private static final Duration SILENCE_ENTRE_ALERTES = Duration.ofHours(1);

    /**
     * @param disponibilite     pourcentage des minutes où le serveur a répondu, sur la période mesurée
     * @param minutesMesurees   étendue de la mesure, trente jours au plus
     * @param delaiP95S         délai événement → notification au 95e centile, en secondes ; {@code null} sans alerte récente
     * @param derniereAlerte    dernière alerte d'exploitation sur le délai, ou {@code null}
     * @param latenceP95Ms      temps de réponse de l'API au 95e centile, en millisecondes ; {@code null} sans requête récente
     * @param requetesParMinute requêtes servies par minute sur la dernière heure relevée ; {@code null} sans relevé
     * @param erreursBraceletsPct part des messages des bracelets écartés sur 24 heures ; {@code null} sans message
     * @param braceletsMuetsIlYAUneHeure bracelets muets relevés l'heure précédente, ou {@code null}
     * @param capaciteBaseOctets capacité du volume de la base déclarée par l'exploitation, ou {@code null}
     * @param disponibiliteParJour disponibilité de chacun des trente derniers jours, le plus ancien d'abord ;
     *                          {@code null} pour un jour sans mesure
     * @param historiques       relevés horaires des vingt-quatre dernières heures, la plus ancienne d'abord, par
     *                          indicateur ; {@code null} pour une heure sans relevé
     */
    public record Tableau(double disponibilite, double objectifDeDisponibilite, long minutesMesurees, long minutesIndisponibles,
            Double delaiP95S, long seuilDeDelaiS, boolean delaiHorsSeuil, Instant derniereAlerte, int braceletsEnService,
            int braceletsMuets, long alertesOuvertes, long smsEnvoyes, long notificationsPoussees, long messagesRefuses,
            long objectifDeDelaiS, Double latenceP95Ms, long objectifDeLatenceMs, Double requetesParMinute,
            Double erreursBraceletsPct, long messagesBracelets24h, double objectifDErreursPct, Integer braceletsMuetsIlYAUneHeure,
            long tailleBaseOctets, Long capaciteBaseOctets, double objectifDeDisquePct, List<Double> disponibiliteParJour,
            Map<String, List<Double>> historiques) {
    }

    /** Indicateurs relevés chaque heure. */
    public enum Indicateur {
        /** Pire 95e centile du délai de notification de l'heure, en secondes. */
        DELAI_P95_S,
        /** Pire 95e centile du temps de réponse de l'API de l'heure, en millisecondes. */
        LATENCE_P95_MS,
        /** Part des messages des bracelets écartés pendant l'heure, en pour cent ; déduite des deux décomptes suivants. */
        ERREURS_BRACELETS_PCT,
        MESSAGES_BRACELETS,
        MESSAGES_EN_ERREUR,
        REQUETES,
        BRACELETS_MUETS,
        BASE_OCTETS
    }

    private final JdbcTemplate jdbc;
    private final MeterRegistry metriques;
    private final JournalAudit journal;
    private final RegistrePurges registre;
    private final Clock horloge;
    private final Counter alertesDExploitation;
    private final Long capaciteBase;
    /** Derniers totaux vus par cette instance : chacune verse au relevé ce qu'elle a compté depuis la minute précédente. */
    private final Map<Indicateur, Double> totauxPrecedents = new ConcurrentHashMap<>();

    SupervisionPlateforme(JdbcTemplate jdbc, MeterRegistry metriques, JournalAudit journal, RegistrePurges registre, Clock horloge,
            @Value("${fasoguardian.supervision.capacite-base:}") String capaciteBase) {
        this.capaciteBase = capaciteBase == null || capaciteBase.isBlank() ? null : DataSize.parse(capaciteBase.trim()).toBytes();
        this.jdbc = jdbc;
        this.metriques = metriques;
        this.journal = journal;
        this.registre = registre;
        this.horloge = horloge;
        this.alertesDExploitation = Counter.builder("fasoguardian.exploitation.alertes")
                .description("Alertes d'exploitation émises par la supervision").tag("motif", "DELAI_DE_NOTIFICATION")
                .register(metriques);
    }

    /** Note la minute en cours, verse les mesures de l'instance au relevé de l'heure, puis contrôle le délai de notification. */
    @Scheduled(fixedDelayString = "${fasoguardian.supervision.sonde:PT1M}")
    public void sonder() {
        Instant minute = horloge.instant().truncatedTo(ChronoUnit.MINUTES);
        noter(minute);
        relever(minute.truncatedTo(ChronoUnit.HOURS));
        controlerLeDelai();
    }

    private void relever(Instant heure) {
        Double delai = delaiP95();
        if (delai != null) {
            garderLePire(heure, Indicateur.DELAI_P95_S, delai);
        }
        Double latence = latenceP95Ms();
        if (latence != null) {
            garderLePire(heure, Indicateur.LATENCE_P95_MS, latence);
        }
        garderLePire(heure, Indicateur.BRACELETS_MUETS, jauge("fasoguardian.bracelets.muets"));
        garderLePire(heure, Indicateur.BASE_OCTETS, tailleBase());
        ajouter(heure, Indicateur.MESSAGES_BRACELETS, messagesMqtt(null));
        ajouter(heure, Indicateur.MESSAGES_EN_ERREUR, messagesMqtt("APPAREIL_REFUSE") + messagesMqtt("INVALIDE"));
        Timer requetes = metriques.find(MesureDesRequetes.METRIQUE).timer();
        ajouter(heure, Indicateur.REQUETES, requetes == null ? 0 : requetes.count());
    }

    private void garderLePire(Instant heure, Indicateur indicateur, double valeur) {
        jdbc.update("""
                INSERT INTO audit.releve_supervision (heure, indicateur, valeur) VALUES (?, ?, ?)
                ON CONFLICT (heure, indicateur) DO UPDATE SET valeur = GREATEST(audit.releve_supervision.valeur, EXCLUDED.valeur)
                """, Timestamp.from(heure), indicateur.name(), valeur);
    }

    /** Ajoute au relevé de l'heure ce que l'instance a compté depuis son passage précédent. */
    private void ajouter(Instant heure, Indicateur indicateur, double total) {
        Double precedent = totauxPrecedents.put(indicateur, total);
        double ecart = precedent == null || precedent > total ? total : total - precedent;
        jdbc.update("""
                INSERT INTO audit.releve_supervision (heure, indicateur, valeur) VALUES (?, ?, ?)
                ON CONFLICT (heure, indicateur) DO UPDATE SET valeur = audit.releve_supervision.valeur + EXCLUDED.valeur
                """, Timestamp.from(heure), indicateur.name(), ecart);
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
        Double latence = latenceP95Ms();
        Instant heure = maintenant.truncatedTo(ChronoUnit.HOURS);
        Map<Indicateur, Map<Instant, Double>> releves = releves(heure.minus(Duration.ofHours(HEURES - 1)));
        Map<String, List<Double>> historiques = new LinkedHashMap<>();
        for (Indicateur indicateur : List.of(Indicateur.DELAI_P95_S, Indicateur.LATENCE_P95_MS, Indicateur.ERREURS_BRACELETS_PCT,
                Indicateur.BRACELETS_MUETS, Indicateur.BASE_OCTETS)) {
            List<Double> valeurs = new ArrayList<>();
            for (int rang = HEURES - 1; rang >= 0; rang--) {
                valeurs.add(valeurDe(releves, indicateur, heure.minus(Duration.ofHours(rang))));
            }
            historiques.put(indicateur.name(), valeurs);
        }
        double messages = somme(releves, Indicateur.MESSAGES_BRACELETS);
        double erreurs = somme(releves, Indicateur.MESSAGES_EN_ERREUR);
        Double requetes = valeurDe(releves, Indicateur.REQUETES, heure.minus(Duration.ofHours(1)));
        Double muetsAvant = valeurDe(releves, Indicateur.BRACELETS_MUETS, heure.minus(Duration.ofHours(1)));
        return new Tableau(disponibilite, OBJECTIF_DE_DISPONIBILITE, attendues, attendues - repondues,
                p95 == null ? null : Math.round(p95 * 10) / 10.0, SEUIL_DE_DELAI.toSeconds(),
                p95 != null && p95 > SEUIL_DE_DELAI.toSeconds(), derniereAlerte(), (int) jauge("fasoguardian.bracelets.en.service"),
                (int) jauge("fasoguardian.bracelets.muets"), compteur("fasoguardian.alertes.ouvertes", null, null),
                compteur("fasoguardian.notifications", "canal", "sms"), compteur("fasoguardian.notifications", "canal", "push"),
                compteur("fasoguardian.telemetrie.messages", "resultat", "APPAREIL_REFUSE")
                        + compteur("fasoguardian.telemetrie.messages", "resultat", "INVALIDE"),
                OBJECTIF_DE_DELAI_S, latence == null ? null : (double) Math.round(latence), OBJECTIF_DE_LATENCE_MS,
                requetes == null ? null : Math.round(requetes / 6.0) / 10.0,
                messages == 0 ? null : Math.round(erreurs * 1000 / messages) / 10.0, Math.round(messages), OBJECTIF_D_ERREURS_PCT,
                muetsAvant == null ? null : (int) Math.round(muetsAvant), Math.round(tailleBase()), capaciteBase,
                OBJECTIF_DE_DISQUE_PCT, disponibiliteParJour(maintenant, premiere == null ? null : premiere.toInstant()), historiques);
    }

    /** Disponibilité de chacun des trente derniers jours, comptée sur les minutes attendues depuis la première mesure. */
    private List<Double> disponibiliteParJour(Instant maintenant, Instant premiere) {
        Instant aujourdhui = maintenant.truncatedTo(ChronoUnit.DAYS);
        Instant debut = aujourdhui.minus(Duration.ofDays(JOURS - 1));
        Map<Instant, Long> notees = new HashMap<>();
        jdbc.query("SELECT date_trunc('day', minute AT TIME ZONE 'UTC') AS jour, count(*) AS n FROM audit.sonde_disponibilite"
                + " WHERE minute >= ? AND minute <= ? GROUP BY 1", (RowCallbackHandler) ligne -> notees.put(
                        ligne.getTimestamp("jour").toLocalDateTime().toInstant(java.time.ZoneOffset.UTC), ligne.getLong("n")),
                Timestamp.from(debut), Timestamp.from(maintenant));
        List<Double> jours = new ArrayList<>();
        for (int rang = JOURS - 1; rang >= 0; rang--) {
            Instant jour = aujourdhui.minus(Duration.ofDays(rang));
            Instant de = premiere == null || premiere.isBefore(jour) ? jour : premiere;
            Instant a = rang == 0 ? maintenant : jour.plus(Duration.ofDays(1)).minus(Duration.ofMinutes(1));
            long attendues = premiere == null || a.isBefore(de) ? 0 : Duration.between(de, a).toMinutes() + 1;
            jours.add(attendues == 0 ? null
                    : Math.round(Math.min(notees.getOrDefault(jour, 0L), attendues) * 100_000.0 / attendues) / 1000.0);
        }
        return jours;
    }

    private Map<Indicateur, Map<Instant, Double>> releves(Instant depuis) {
        Map<Indicateur, Map<Instant, Double>> releves = new HashMap<>();
        jdbc.query("SELECT heure, indicateur, valeur FROM audit.releve_supervision WHERE heure >= ?", (RowCallbackHandler) ligne -> {
            Indicateur indicateur = Indicateur.valueOf(ligne.getString("indicateur"));
            releves.computeIfAbsent(indicateur, i -> new HashMap<>()).put(ligne.getTimestamp("heure").toInstant(), ligne.getDouble("valeur"));
        }, Timestamp.from(depuis));
        return releves;
    }

    private static Double valeurDe(Map<Indicateur, Map<Instant, Double>> releves, Indicateur indicateur, Instant heure) {
        if (indicateur == Indicateur.ERREURS_BRACELETS_PCT) {
            Double messages = valeurDe(releves, Indicateur.MESSAGES_BRACELETS, heure);
            Double erreurs = valeurDe(releves, Indicateur.MESSAGES_EN_ERREUR, heure);
            return messages == null || messages == 0 ? null : Math.round((erreurs == null ? 0 : erreurs) * 1000 / messages) / 10.0;
        }
        return releves.getOrDefault(indicateur, Map.of()).get(heure);
    }

    private static double somme(Map<Indicateur, Map<Instant, Double>> releves, Indicateur indicateur) {
        return releves.getOrDefault(indicateur, Map.of()).values().stream().mapToDouble(Double::doubleValue).sum();
    }

    /** Les sondes de plus de 90 jours ne servent plus à aucun indicateur. */
    @Scheduled(cron = "${fasoguardian.supervision.purge:0 25 3 * * *}")
    @SchedulerLock(name = "audit-purge-sondes")
    public void purger() {
        Timestamp limite = Timestamp.from(horloge.instant().minus(CONSERVATION));
        registre.consigner("SONDES_DE_DISPONIBILITE", jdbc.update("DELETE FROM audit.sonde_disponibilite WHERE minute < ?", limite));
        registre.consigner("RELEVES_DE_SUPERVISION", jdbc.update("DELETE FROM audit.releve_supervision WHERE heure < ?", limite));
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

    /** 95e centile du temps de réponse de l'API, en millisecondes, sur les dernières minutes ; {@code null} sans requête. */
    private Double latenceP95Ms() {
        Timer duree = metriques.find(MesureDesRequetes.METRIQUE).timer();
        if (duree == null) {
            return null;
        }
        for (ValueAtPercentile valeur : duree.takeSnapshot().percentileValues()) {
            if (valeur.percentile() == 0.95 && valeur.value(TimeUnit.MILLISECONDS) > 0) {
                return valeur.value(TimeUnit.MILLISECONDS);
            }
        }
        return null;
    }

    /** Messages des flux MQTT des bracelets depuis le démarrage, tous ou d'une seule issue. */
    private double messagesMqtt(String resultat) {
        return metriques.find("fasoguardian.telemetrie.messages").counters().stream()
                .filter(c -> FLUX_MQTT.contains(c.getId().getTag("flux")))
                .filter(c -> resultat == null || resultat.equals(c.getId().getTag("resultat"))).mapToDouble(Counter::count).sum();
    }

    private double tailleBase() {
        Long taille = jdbc.queryForObject("SELECT pg_database_size(current_database())", Long.class);
        return taille == null ? 0 : taille;
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
