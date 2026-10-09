package bf.fasoguardian.audit.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.OptionalLong;
import java.util.UUID;

import bf.fasoguardian.audit.JournalAudit;
import bf.fasoguardian.plateforme.securite.AccesRefuse;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Écriture sérialisée et vérification du journal d'audit. Chaque entrée porte l'empreinte
 * SHA-256(empreinte précédente + contenu) : toute altération rompt la chaîne.
 */
@Service
class ServiceJournalAudit implements JournalAudit {

    static final String GENESE = "0".repeat(64);
    /** Actions consignées par chaque contrôle de la chaîne : l'écran du journal en tire l'état affiché. */
    static final String CHAINE_VERIFIEE = "CHAINE_VERIFIEE";
    static final String CHAINE_ROMPUE = "CHAINE_ROMPUE";
    private static final long VERROU = 0x4647_4155_4449_54L;
    private static final Logger journal = LoggerFactory.getLogger(ServiceJournalAudit.class);

    private final JdbcTemplate jdbc;
    private final Clock horloge;
    private final Counter ruptures;
    private final TransactionTemplate transaction;
    private final TransactionTemplate transactionPropre;

    ServiceJournalAudit(JdbcTemplate jdbc, Clock horloge, MeterRegistry metriques,
            PlatformTransactionManager gestionnaire) {
        this.transaction = new TransactionTemplate(gestionnaire);
        this.transactionPropre = new TransactionTemplate(gestionnaire);
        this.transactionPropre.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.jdbc = jdbc;
        this.horloge = horloge;
        this.ruptures = Counter.builder("fasoguardian.audit.chaine.rompue")
                .description("Contrôles d'intégrité du journal d'audit ayant détecté une altération")
                .register(metriques);
    }

    @Override
    public void consigner(UUID acteurId, String role, String action, String typeCible, String cibleId,
            Resultat resultat) {
        // Une action réussie est tracée dans sa propre transaction métier : l'une ne va pas sans l'autre.
        // Un refus est tracé à part : il doit survivre à l'annulation de l'opération refusée. Il survient
        // toujours avant toute écriture de l'opération, qui ne détient donc pas encore le verrou du journal.
        TransactionTemplate modele = resultat == Resultat.REFUS ? transactionPropre : transaction;
        modele.executeWithoutResult(etat -> ecrire(acteurId, role, action, typeCible, cibleId, resultat));
    }

    private void ecrire(UUID acteurId, String role, String action, String typeCible, String cibleId,
            Resultat resultat) {
        // Un seul écrivain à la fois, y compris entre instances : la chaîne ne peut pas bifurquer.
        jdbc.query("SELECT pg_advisory_xact_lock(?)", resultats -> { }, VERROU);
        Long dernierId = jdbc.queryForObject("SELECT max(id) FROM audit.entree", Long.class);
        String precedente = dernierId == null ? GENESE
                : jdbc.queryForObject("SELECT empreinte FROM audit.entree WHERE id = ?", String.class, dernierId);
        long id = dernierId == null ? 1 : dernierId + 1;
        // Tronqué à la microseconde, précision de PostgreSQL : l'empreinte se recalcule à l'identique.
        Instant horodatage = horloge.instant().truncatedTo(ChronoUnit.MICROS);
        String empreinte = empreinte(precedente, id, horodatage, acteurId, role, action, typeCible, cibleId,
                resultat.name());
        jdbc.update("""
                INSERT INTO audit.entree (id, horodatage, acteur_id, role, action, type_cible, cible_id, resultat,
                                          empreinte_precedente, empreinte)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, Timestamp.from(horodatage), acteurId, role, action, typeCible, cibleId, resultat.name(),
                precedente, empreinte);
    }

    @Override
    @Transactional(readOnly = true)
    public OptionalLong premiereEntreeAlteree() {
        String[] precedente = {GENESE};
        long[] attendu = {1};
        long[] alteree = {0};
        jdbc.query("""
                SELECT id, horodatage, acteur_id, role, action, type_cible, cible_id, resultat,
                       empreinte_precedente, empreinte
                FROM audit.entree ORDER BY id
                """, ligne -> {
            if (alteree[0] != 0) {
                return;
            }
            long id = ligne.getLong("id");
            String calculee = empreinte(precedente[0], id, ligne.getTimestamp("horodatage").toInstant(),
                    ligne.getObject("acteur_id", UUID.class), ligne.getString("role"), ligne.getString("action"),
                    ligne.getString("type_cible"), ligne.getString("cible_id"), ligne.getString("resultat"));
            if (id != attendu[0] || !precedente[0].equals(ligne.getString("empreinte_precedente"))
                    || !calculee.equals(ligne.getString("empreinte"))) {
                alteree[0] = id;
                return;
            }
            precedente[0] = calculee;
            attendu[0] = id + 1;
        });
        return alteree[0] == 0 ? OptionalLong.empty() : OptionalLong.of(alteree[0]);
    }

    /** Contrôle quotidien d'intégrité (FG-DOC-06, tableau 13). */
    @Scheduled(cron = "0 30 2 * * *", zone = "UTC")
    @SchedulerLock(name = "audit-controle-integrite", lockAtMostFor = "PT30M")
    public void controleQuotidien() {
        premiereEntreeAlteree().ifPresentOrElse(id -> {
            ruptures.increment();
            journal.error("Journal d'audit altéré : chaîne rompue à l'entrée {}", id);
            consigner(null, "SYSTEME", CHAINE_ROMPUE, "JOURNAL", Long.toString(id), Resultat.REFUS);
        }, () -> consigner(null, "SYSTEME", CHAINE_VERIFIEE, "JOURNAL", null, Resultat.SUCCES));
    }

    /** Chaque accès refusé par le contrôle d'accès est journalisé (REQ-SYS-016). */
    @EventListener
    public void surAccesRefuse(AccesRefuse refus) {
        consigner(refus.acteurId(), refus.role(), "ACCES_REFUSE", "ROUTE", refus.methode() + " " + refus.chemin(),
                Resultat.REFUS);
    }

    private static String empreinte(String precedente, long id, Instant horodatage, UUID acteurId, String role,
            String action, String typeCible, String cibleId, String resultat) {
        String contenu = String.join("\u001f", precedente, Long.toString(id), horodatage.toString(),
                String.valueOf(acteurId), role, action, typeCible, String.valueOf(cibleId), resultat);
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(contenu.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException erreur) {
            throw new IllegalStateException(erreur);
        }
    }
}
