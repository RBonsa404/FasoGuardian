package bf.fasoguardian.audit.application;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import java.util.UUID;

import bf.fasoguardian.audit.JournalAudit;
import bf.fasoguardian.audit.JournalAudit.Resultat;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lecture du journal d'audit par l'administrateur (écran 70, US-ADM-002). La consultation du journal est
 * elle-même journalisée ; l'état de la chaîne est celui du dernier contrôle, quotidien ou demandé.
 */
@Service
public class ConsultationJournal {

    private static final int TAILLE_MAXIMALE = 200;

    /** @param empreinte abrégée : début et fin, assez pour rapprocher une entrée d'un export */
    public record Entree(long id, Instant horodatage, UUID acteurId, String role, String action, String typeCible,
            String cibleId, Resultat resultat, String empreinte) {
    }

    /**
     * @param verifieeLe    date du dernier contrôle, ou {@code null} s'il n'y en a pas encore eu
     * @param entreeAlteree première entrée altérée trouvée par ce contrôle, ou {@code null}
     */
    public record Chaine(boolean integre, Instant verifieeLe, Long entreeAlteree) {
    }

    public record Page(List<Entree> entrees, long total, int page, int taille, Chaine chaine) {
    }

    /** Critères facultatifs : une valeur {@code null} ne filtre pas. */
    public record Filtre(String action, String role, Resultat resultat, UUID acteurId, Instant depuis, Instant jusqua) {
    }

    private final JdbcTemplate jdbc;
    private final JournalAudit journal;

    ConsultationJournal(JdbcTemplate jdbc, JournalAudit journal) {
        this.jdbc = jdbc;
        this.journal = journal;
    }

    @Transactional
    public Page entrees(UUID agentId, Filtre filtre, int page, int taille) {
        int limite = Math.max(1, Math.min(taille, TAILLE_MAXIMALE));
        int rang = Math.max(0, page);
        StringBuilder ou = new StringBuilder(" WHERE TRUE");
        List<Object> valeurs = new ArrayList<>();
        ajouter(ou, valeurs, " AND action = ?", filtre.action());
        ajouter(ou, valeurs, " AND role = ?", filtre.role());
        ajouter(ou, valeurs, " AND resultat = ?", filtre.resultat() == null ? null : filtre.resultat().name());
        ajouter(ou, valeurs, " AND acteur_id = ?", filtre.acteurId());
        ajouter(ou, valeurs, " AND horodatage >= ?", filtre.depuis() == null ? null : Timestamp.from(filtre.depuis()));
        ajouter(ou, valeurs, " AND horodatage < ?", filtre.jusqua() == null ? null : Timestamp.from(filtre.jusqua()));
        Long total = jdbc.queryForObject("SELECT count(*) FROM audit.entree" + ou, Long.class, valeurs.toArray());
        List<Object> pagination = new ArrayList<>(valeurs);
        pagination.add(limite);
        pagination.add((long) rang * limite);
        List<Entree> entrees = jdbc.query("SELECT id, horodatage, acteur_id, role, action, type_cible, cible_id, resultat, empreinte"
                + " FROM audit.entree" + ou + " ORDER BY id DESC LIMIT ? OFFSET ?",
                (ligne, numero) -> new Entree(ligne.getLong("id"), ligne.getTimestamp("horodatage").toInstant(),
                        ligne.getObject("acteur_id", UUID.class), ligne.getString("role"), ligne.getString("action"),
                        ligne.getString("type_cible"), ligne.getString("cible_id"), Resultat.valueOf(ligne.getString("resultat")),
                        abreger(ligne.getString("empreinte"))),
                pagination.toArray());
        Chaine chaine = chaine();
        journal.consigner(agentId, "ADMIN", "JOURNAL_CONSULTE", "JOURNAL", null, Resultat.SUCCES);
        return new Page(entrees, total == null ? 0 : total, rang, limite, chaine);
    }

    /** Contrôle de la chaîne à la demande ; son résultat est consigné comme celui du contrôle quotidien. */
    public Chaine verifier(UUID agentId) {
        OptionalLong alteree = journal.premiereEntreeAlteree();
        if (alteree.isPresent()) {
            journal.consigner(agentId, "ADMIN", ServiceJournalAudit.CHAINE_ROMPUE, "JOURNAL", Long.toString(alteree.getAsLong()),
                    Resultat.REFUS);
        } else {
            journal.consigner(agentId, "ADMIN", ServiceJournalAudit.CHAINE_VERIFIEE, "JOURNAL", null, Resultat.SUCCES);
        }
        return chaine();
    }

    /** État de la chaîne d'après le dernier contrôle consigné. */
    private Chaine chaine() {
        return jdbc.query("SELECT horodatage, action, cible_id FROM audit.entree WHERE action IN (?, ?) ORDER BY id DESC LIMIT 1",
                (ligne, numero) -> {
                    boolean rompue = ServiceJournalAudit.CHAINE_ROMPUE.equals(ligne.getString("action"));
                    return new Chaine(!rompue, ligne.getTimestamp("horodatage").toInstant(),
                            rompue ? Long.valueOf(ligne.getString("cible_id")) : null);
                }, ServiceJournalAudit.CHAINE_VERIFIEE, ServiceJournalAudit.CHAINE_ROMPUE)
                .stream().findFirst().orElse(new Chaine(true, null, null));
    }

    private static void ajouter(StringBuilder ou, List<Object> valeurs, String condition, Object valeur) {
        if (valeur != null && !(valeur instanceof String texte && texte.isBlank())) {
            ou.append(condition);
            valeurs.add(valeur);
        }
    }

    private static String abreger(String empreinte) {
        return empreinte.substring(0, 4) + "…" + empreinte.substring(empreinte.length() - 4);
    }
}
