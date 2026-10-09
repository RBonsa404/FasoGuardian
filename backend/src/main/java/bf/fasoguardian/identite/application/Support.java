package bf.fasoguardian.identite.application;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.Normalizer;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import bf.fasoguardian.audit.JournalAudit;
import bf.fasoguardian.audit.JournalAudit.Resultat;
import bf.fasoguardian.audit.RegistrePurges;
import bf.fasoguardian.identite.LiensTutelle;
import bf.fasoguardian.notifications.Notifications;
import bf.fasoguardian.notifications.Notifications.Message;
import bf.fasoguardian.notifications.Notifications.Urgence;
import bf.fasoguardian.plateforme.chiffrement.CategorieDonnee;
import bf.fasoguardian.plateforme.chiffrement.ServiceChiffrement;
import bf.fasoguardian.plateforme.erreurs.CodeErreur;
import bf.fasoguardian.plateforme.erreurs.ErreurMetier;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Support (US-PAR-017, US-SUP-001). Le parent ouvre une demande et suit les réponses depuis son compte ;
 * l'opérateur support répond et publie les réponses aux questions fréquentes. L'opérateur voit les
 * coordonnées du parent, jamais ses pièces, la position ni la fiche santé de l'enfant (FG-DOC-06, tableau 17).
 */
@Service
public class Support {

    private static final String ROLE = "SUPPORT";

    public enum Statut {
        OUVERTE,
        EN_ATTENTE_PARENT,
        RESOLUE
    }

    public enum Categorie {
        BRACELET,
        ALERTES,
        SAFE_ZONES,
        PAIEMENT,
        COMPTE,
        VIE_PRIVEE
    }

    /** @param deMoi le message est celui de la personne qui consulte */
    public record MessageVue(boolean duSupport, boolean deMoi, String texte, Instant creeLe) {
    }

    /** @param parent et {@code telephone} : coordonnées du demandeur, pour l'opérateur seulement */
    public record DemandeVue(UUID id, String reference, String objet, Statut statut, Instant ouverteLe, Instant modifieeLe,
            String parent, String telephone, List<MessageVue> messages) {
    }

    public record ArticleVue(UUID id, String slug, Categorie categorie, String titre, String contenu, boolean publie,
            long lectures, Instant modifieLe) {
    }

    public record CategorieVue(Categorie categorie, long articles) {
    }

    public record SaisieArticle(Categorie categorie, String titre, String contenu) {
    }

    private final JdbcTemplate jdbc;
    private final LiensTutelle liens;
    private final ServiceChiffrement chiffrement;
    private final Notifications notifications;
    private final JournalAudit journal;
    private final RegistrePurges registre;
    private final Clock horloge;

    Support(JdbcTemplate jdbc, LiensTutelle liens, ServiceChiffrement chiffrement, Notifications notifications, JournalAudit journal,
            RegistrePurges registre, Clock horloge) {
        this.jdbc = jdbc;
        this.liens = liens;
        this.chiffrement = chiffrement;
        this.notifications = notifications;
        this.journal = journal;
        this.registre = registre;
        this.horloge = horloge;
    }

    // ------------------------------------------------------------------ parent

    @Transactional
    public DemandeVue ouvrir(UUID tuteurId, String objet, String message) {
        String titre = texte(objet, 120, "Donnez un objet à votre demande.");
        String corps = texte(message, 2000, "Décrivez votre demande.");
        UUID id = UUID.randomUUID();
        Timestamp maintenant = Timestamp.from(horloge.instant());
        Long numero = jdbc.queryForObject("SELECT nextval('identite.reference_demande_support')", Long.class);
        jdbc.update("INSERT INTO identite.demande_support (id, reference, tuteur_id, objet, statut, ouverte_le, modifiee_le)"
                + " VALUES (?, ?, ?, ?, 'OUVERTE', ?, ?)", id, "SUP-%06d".formatted(numero), tuteurId, titre, maintenant, maintenant);
        ajouter(id, "PARENT", tuteurId, corps, maintenant);
        return demande(id, tuteurId, false);
    }

    @Transactional(readOnly = true)
    public List<DemandeVue> mesDemandes(UUID tuteurId) {
        return jdbc.query("SELECT id FROM identite.demande_support WHERE tuteur_id = ? ORDER BY modifiee_le DESC",
                (ligne, rang) -> ligne.getObject("id", UUID.class), tuteurId).stream().map(id -> demande(id, tuteurId, false)).toList();
    }

    @Transactional(readOnly = true)
    public DemandeVue maDemande(UUID tuteurId, UUID demandeId) {
        exigerDemandeur(tuteurId, demandeId);
        return demande(demandeId, tuteurId, false);
    }

    /** Le parent complète sa demande ; une demande en attente de lui ou résolue est rouverte. */
    @Transactional
    public DemandeVue completer(UUID tuteurId, UUID demandeId, String message) {
        exigerDemandeur(tuteurId, demandeId);
        Timestamp maintenant = Timestamp.from(horloge.instant());
        ajouter(demandeId, "PARENT", tuteurId, texte(message, 2000, "Écrivez votre message."), maintenant);
        jdbc.update("UPDATE identite.demande_support SET statut = 'OUVERTE', modifiee_le = ?, version = version + 1 WHERE id = ?",
                maintenant, demandeId);
        return demande(demandeId, tuteurId, false);
    }

    // ---------------------------------------------------------------- opérateur

    /** Demandes dans l'état donné, celles qui attendent depuis le plus longtemps d'abord. */
    @Transactional(readOnly = true)
    public List<DemandeVue> demandes(UUID agentId, Statut statut) {
        return jdbc.query("SELECT id FROM identite.demande_support WHERE statut = ? ORDER BY modifiee_le LIMIT 200",
                (ligne, rang) -> ligne.getObject("id", UUID.class), statut.name()).stream().map(id -> demande(id, agentId, true)).toList();
    }

    @Transactional
    public DemandeVue demande(UUID agentId, UUID demandeId) {
        exigerExistante(demandeId);
        DemandeVue vue = demande(demandeId, agentId, true);
        journal.consigner(agentId, ROLE, "DEMANDE_SUPPORT_CONSULTEE", "DEMANDE_SUPPORT", vue.reference(), Resultat.SUCCES);
        return vue;
    }

    /** Réponse de l'opérateur ; le parent en est notifié et retrouve la réponse dans son compte. */
    @Transactional
    public DemandeVue repondre(UUID agentId, UUID demandeId, String message, Statut suite) {
        exigerExistante(demandeId);
        if (suite == null || suite == Statut.OUVERTE) {
            throw new ErreurMetier(CodeErreur.REQUETE_INVALIDE, "Après une réponse, la demande attend le parent ou est résolue.");
        }
        Timestamp maintenant = Timestamp.from(horloge.instant());
        ajouter(demandeId, "SUPPORT", agentId, texte(message, 2000, "Écrivez votre réponse."), maintenant);
        jdbc.update("UPDATE identite.demande_support SET statut = ?, modifiee_le = ?, version = version + 1 WHERE id = ?", suite.name(),
                maintenant, demandeId);
        DemandeVue vue = demande(demandeId, agentId, true);
        journal.consigner(agentId, ROLE, "DEMANDE_SUPPORT_REPONDUE", "DEMANDE_SUPPORT", vue.reference(), Resultat.SUCCES);
        UUID tuteur = jdbc.queryForObject("SELECT tuteur_id FROM identite.demande_support WHERE id = ?", UUID.class, demandeId);
        notifications.notifier(tuteur, Urgence.INFORMATION, new Message("REPONSE_DU_SUPPORT", "Réponse du support",
                "FasoGuardian : le support a répondu à votre demande " + vue.reference() + ". Ouvrez l'application pour lire la réponse.",
                "/aide/demandes/" + demandeId, null));
        return vue;
    }

    // ----------------------------------------------------- base de connaissances

    @Transactional(readOnly = true)
    public List<CategorieVue> categories() {
        return jdbc.query("SELECT categorie, count(*) AS articles FROM identite.article_aide WHERE statut = 'PUBLIE' GROUP BY categorie"
                + " ORDER BY categorie", (ligne, rang) -> new CategorieVue(Categorie.valueOf(ligne.getString("categorie")), ligne.getLong("articles")));
    }

    /** Articles publiés, les plus lus d'abord ; filtrés par catégorie ou par mots du titre et du contenu. */
    @Transactional(readOnly = true)
    public List<ArticleVue> articlesPublies(Categorie categorie, String recherche) {
        String mots = recherche == null ? "" : recherche.strip().toLowerCase(Locale.FRENCH);
        String motif = "%" + mots.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        return jdbc.query("SELECT * FROM identite.article_aide WHERE statut = 'PUBLIE' AND (?::text IS NULL OR categorie = ?)"
                + " AND (lower(titre) LIKE ? OR lower(contenu) LIKE ?) ORDER BY lectures DESC, titre LIMIT 50", Support::article,
                categorie == null ? null : categorie.name(), categorie == null ? null : categorie.name(), motif, motif);
    }

    /** Lecture d'un article publié par un parent : elle est comptée, pour faire remonter les plus utiles. */
    @Transactional
    public ArticleVue lire(String slug) {
        if (jdbc.update("UPDATE identite.article_aide SET lectures = lectures + 1 WHERE slug = ? AND statut = 'PUBLIE'", slug) == 0) {
            throw new ErreurMetier(CodeErreur.RESSOURCE_INTROUVABLE, "Cet article n'existe pas ou n'est plus publié.");
        }
        return jdbc.query("SELECT * FROM identite.article_aide WHERE slug = ?", Support::article, slug).get(0);
    }

    @Transactional(readOnly = true)
    public List<ArticleVue> articles() {
        return jdbc.query("SELECT * FROM identite.article_aide ORDER BY modifie_le DESC", Support::article);
    }

    /** Crée un article en brouillon : rien n'est visible des parents avant la publication. */
    @Transactional
    public ArticleVue rediger(UUID agentId, SaisieArticle saisie) {
        valider(saisie);
        UUID id = UUID.randomUUID();
        String slug = slug(saisie.titre());
        try {
            jdbc.update("INSERT INTO identite.article_aide (id, slug, categorie, titre, contenu, statut, auteur_id, modifie_le)"
                    + " VALUES (?, ?, ?, ?, ?, 'BROUILLON', ?, ?)", id, slug, saisie.categorie().name(), saisie.titre().strip(),
                    saisie.contenu().strip(), agentId, Timestamp.from(horloge.instant()));
        } catch (DuplicateKeyException doublon) {
            throw new ErreurMetier(CodeErreur.CONFLIT, "Un article porte déjà ce titre.");
        }
        journal.consigner(agentId, ROLE, "ARTICLE_REDIGE", "ARTICLE_AIDE", slug, Resultat.SUCCES);
        return article(id);
    }

    @Transactional
    public ArticleVue modifier(UUID agentId, UUID articleId, SaisieArticle saisie) {
        valider(saisie);
        if (jdbc.update("UPDATE identite.article_aide SET categorie = ?, titre = ?, contenu = ?, modifie_le = ?, version = version + 1"
                + " WHERE id = ?", saisie.categorie().name(), saisie.titre().strip(), saisie.contenu().strip(),
                Timestamp.from(horloge.instant()), articleId) == 0) {
            throw introuvable();
        }
        ArticleVue vue = article(articleId);
        journal.consigner(agentId, ROLE, "ARTICLE_MODIFIE", "ARTICLE_AIDE", vue.slug(), Resultat.SUCCES);
        return vue;
    }

    /** Publie l'article, ou le retire : dans les deux cas l'effet est immédiat pour les parents. */
    @Transactional
    public ArticleVue publier(UUID agentId, UUID articleId, boolean publie) {
        Timestamp maintenant = Timestamp.from(horloge.instant());
        if (jdbc.update("UPDATE identite.article_aide SET statut = ?, publie_le = ?, modifie_le = ?, version = version + 1 WHERE id = ?",
                publie ? "PUBLIE" : "BROUILLON", publie ? maintenant : null, maintenant, articleId) == 0) {
            throw introuvable();
        }
        ArticleVue vue = article(articleId);
        journal.consigner(agentId, ROLE, publie ? "ARTICLE_PUBLIE" : "ARTICLE_RETIRE", "ARTICLE_AIDE", vue.slug(), Resultat.SUCCES);
        return vue;
    }

    /** Les demandes résolues depuis douze mois sont effacées, avec leurs messages. */
    @Scheduled(cron = "${fasoguardian.support.purge:0 35 3 * * *}")
    @SchedulerLock(name = "identite-purge-support")
    @Transactional
    public void purger() {
        registre.consigner("DEMANDES_DE_SUPPORT", jdbc.update("DELETE FROM identite.demande_support WHERE statut = 'RESOLUE'"
                + " AND modifiee_le < now() - INTERVAL '12 months'"));
    }

    // -------------------------------------------------------------------- aides

    private void exigerDemandeur(UUID tuteurId, UUID demandeId) {
        Integer nombre = jdbc.queryForObject("SELECT count(*) FROM identite.demande_support WHERE id = ? AND tuteur_id = ?", Integer.class,
                demandeId, tuteurId);
        if (nombre == null || nombre == 0) {
            throw new ErreurMetier(CodeErreur.RESSOURCE_INTROUVABLE, "Demande introuvable.");
        }
    }

    private void exigerExistante(UUID demandeId) {
        Integer nombre = jdbc.queryForObject("SELECT count(*) FROM identite.demande_support WHERE id = ?", Integer.class, demandeId);
        if (nombre == null || nombre == 0) {
            throw new ErreurMetier(CodeErreur.RESSOURCE_INTROUVABLE, "Demande introuvable.");
        }
    }

    private void ajouter(UUID demandeId, String auteur, UUID auteurId, String texte, Timestamp maintenant) {
        jdbc.update("INSERT INTO identite.message_support (id, demande_id, auteur, auteur_id, texte, cree_le) VALUES (?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), demandeId, auteur, auteurId, texte, maintenant);
    }

    /** @param pourLeSupport joint les coordonnées du demandeur, que seul l'opérateur voit */
    private DemandeVue demande(UUID demandeId, UUID lecteurId, boolean pourLeSupport) {
        List<MessageVue> messages = jdbc.query("SELECT auteur, auteur_id, texte, cree_le FROM identite.message_support WHERE demande_id = ?"
                + " ORDER BY cree_le, id", (ligne, rang) -> new MessageVue("SUPPORT".equals(ligne.getString("auteur")),
                        lecteurId.equals(ligne.getObject("auteur_id", UUID.class)), ligne.getString("texte"),
                        ligne.getTimestamp("cree_le").toInstant()), demandeId);
        return jdbc.query("SELECT d.*, u.telephone_chiffre FROM identite.demande_support d JOIN identite.utilisateur u ON u.id = d.tuteur_id"
                + " WHERE d.id = ?", (ligne, rang) -> {
                    byte[] telephone = ligne.getBytes("telephone_chiffre");
                    UUID tuteur = ligne.getObject("tuteur_id", UUID.class);
                    return new DemandeVue(demandeId, ligne.getString("reference"), ligne.getString("objet"),
                            Statut.valueOf(ligne.getString("statut")), ligne.getTimestamp("ouverte_le").toInstant(),
                            ligne.getTimestamp("modifiee_le").toInstant(),
                            pourLeSupport ? liens.prenomDuTuteur(tuteur).orElse(null) : null,
                            pourLeSupport && telephone != null ? chiffrement.dechiffrerTexte(CategorieDonnee.TELEPHONE, telephone) : null,
                            messages);
                }, demandeId).get(0);
    }

    private ArticleVue article(UUID articleId) {
        return jdbc.query("SELECT * FROM identite.article_aide WHERE id = ?", Support::article, articleId).stream().findFirst()
                .orElseThrow(Support::introuvable);
    }

    private static ArticleVue article(ResultSet ligne, int rang) throws SQLException {
        return new ArticleVue(ligne.getObject("id", UUID.class), ligne.getString("slug"), Categorie.valueOf(ligne.getString("categorie")),
                ligne.getString("titre"), ligne.getString("contenu"), "PUBLIE".equals(ligne.getString("statut")), ligne.getLong("lectures"),
                ligne.getTimestamp("modifie_le").toInstant());
    }

    private static void valider(SaisieArticle saisie) {
        if (saisie.categorie() == null) {
            throw new ErreurMetier(CodeErreur.REQUETE_INVALIDE, "Choisissez la catégorie de l'article.");
        }
        texte(saisie.titre(), 120, "Donnez un titre à l'article.");
        texte(saisie.contenu(), 6000, "Rédigez le contenu de l'article.");
    }

    private static String texte(String saisie, int maximum, String siVide) {
        if (saisie == null || saisie.isBlank()) {
            throw new ErreurMetier(CodeErreur.REQUETE_INVALIDE, siVide);
        }
        String propre = saisie.strip();
        if (propre.length() > maximum) {
            throw new ErreurMetier(CodeErreur.REQUETE_INVALIDE, "Ce texte dépasse " + maximum + " caractères.");
        }
        return propre;
    }

    /** « Que faire si la sangle s'ouvre seule ? » devient « que-faire-si-la-sangle-s-ouvre-seule ». */
    static String slug(String titre) {
        String sansAccents = Normalizer.normalize(titre.strip().toLowerCase(Locale.FRENCH), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String slug = sansAccents.replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        return slug.length() > 80 ? slug.substring(0, 80).replaceAll("-$", "") : slug;
    }

    private static ErreurMetier introuvable() {
        return new ErreurMetier(CodeErreur.RESSOURCE_INTROUVABLE, "Article introuvable.");
    }
}
