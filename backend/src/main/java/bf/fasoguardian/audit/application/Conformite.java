package bf.fasoguardian.audit.application;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.UUID;

import bf.fasoguardian.audit.DemandesDroits;
import bf.fasoguardian.audit.DonneesPersonnelles;
import bf.fasoguardian.audit.DonneesPersonnelles.Personne;
import bf.fasoguardian.audit.JournalAudit;
import bf.fasoguardian.audit.JournalAudit.Resultat;
import bf.fasoguardian.audit.PerimetreFamilial;
import bf.fasoguardian.audit.RegistrePurges;
import bf.fasoguardian.audit.application.GardeAipd.Etat;
import bf.fasoguardian.audit.application.RedacteurRapport.Contenu;
import bf.fasoguardian.audit.domaine.DemandeDroit;
import bf.fasoguardian.audit.domaine.DemandeDroit.Statut;
import bf.fasoguardian.audit.domaine.DemandeDroit.Type;
import bf.fasoguardian.audit.infrastructure.DepotDemandesDroits;
import bf.fasoguardian.plateforme.erreurs.CodeErreur;
import bf.fasoguardian.plateforme.erreurs.ErreurMetier;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Pilotage de la conformité (US-ADM-003) : registre des purges, demandes d'accès et d'effacement, tableau de
 * bord et rapport mensuel. Un accès est servi aussitôt à la personne ; un effacement est exécuté par un
 * administrateur, ou par le système à l'approche de l'échéance légale, puis accusé au demandeur.
 */
@Service
public class Conformite implements RegistrePurges, DemandesDroits {

    private static final Logger journalTechnique = LoggerFactory.getLogger(Conformite.class);

    /** Durées de conservation appliquées (FG-DOC-06, tableau 18), telles que le rapport les présente. */
    private static final List<Duree> CONSERVATION = List.of(
            new Duree("Positions", "30 jours ; 24 h pour l'offre Essentiel, 90 jours pour l'offre Premium", "Purge quotidienne"),
            new Duree("Alertes et journal d'acquittement", "5 ans (finalité probatoire)", "Journal en ajout seul"),
            new Duree("Pièces KYC", "Durée de la relation + 1 an", "Purge quotidienne"),
            new Duree("Fiche santé", "Durée du compte", "Effacement à la clôture"),
            new Duree("Journal de consultation de la page QR", "12 mois, adresse IP pseudonymisée", "Purge quotidienne"),
            new Duree("Numéro des tiers (page QR)", "30 jours", "Purge quotidienne"),
            new Duree("Dossiers de signalement", "30 jours", "Purge quotidienne"),
            new Duree("Notifications", "90 jours", "Purge quotidienne"));

    public record Duree(String donnee, String duree, String mecanisme) {
    }

    /** @param executions nombre d'exécutions sur le mois ; {@code elements} : éléments supprimés sur le mois */
    public record Purge(String traitement, Instant derniereExecution, long executions, long elements) {
    }

    /** @param joursDePurge jours du mois où au moins une purge a tourné, sur {@code joursEcoules} */
    public record Tableau(Etat aipd, List<Duree> conservation, long demandesAcces, long demandesEffacement,
            long effacementsEnAttente, int joursDePurge, int joursEcoules, List<Purge> purges) {
    }

    public record DemandeVue(UUID id, String reference, Type type, Statut statut, Instant recueLe, Instant echeanceLe,
            Instant traiteeLe, boolean traiteeParLeSysteme) {
    }

    public record Export(String reference, Map<String, Object> contenu) {
    }

    public record Rapport(String nom, byte[] pdf) {
    }

    private final DepotDemandesDroits demandes;
    private final ObjectProvider<DonneesPersonnelles> modules;
    private final PerimetreFamilial perimetre;
    private final JournalAudit journal;
    private final GardeAipd aipd;
    private final RedacteurRapport redacteur;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final Clock horloge;
    private final ZoneId fuseau;
    private final Duration effacementAutomatiqueApres;

    Conformite(DepotDemandesDroits demandes, ObjectProvider<DonneesPersonnelles> modules, PerimetreFamilial perimetre,
            JournalAudit journal, GardeAipd aipd, RedacteurRapport redacteur, JdbcTemplate jdbc, TransactionTemplate transaction,
            Clock horloge, @Value("${fasoguardian.fuseau:Africa/Ouagadougou}") ZoneId fuseau,
            @Value("${fasoguardian.conformite.effacement-automatique-apres:P25D}") Duration effacementAutomatiqueApres) {
        this.demandes = demandes;
        this.modules = modules;
        this.perimetre = perimetre;
        this.journal = journal;
        this.aipd = aipd;
        this.redacteur = redacteur;
        this.jdbc = jdbc;
        this.transaction = transaction;
        this.horloge = horloge;
        this.fuseau = fuseau;
        this.effacementAutomatiqueApres = effacementAutomatiqueApres;
    }

    // ------------------------------------------------------------------- purges

    @Override
    public void consigner(String traitement, long elements) {
        jdbc.update("INSERT INTO audit.execution_purge (traitement, executee_le, elements) VALUES (?, ?, ?)", traitement,
                Timestamp.from(horloge.instant()), elements);
    }

    // ----------------------------------------------------------- droit d'accès

    /** Droit d'accès : tout ce que la plateforme détient sur le tuteur et ses enfants, servi aussitôt. */
    @Transactional
    public Export acceder(UUID tuteurId) {
        Instant maintenant = horloge.instant();
        DemandeDroit demande = new DemandeDroit(Type.ACCES, demandes.prochainNumero(), tuteurId, maintenant);
        demande.traiter(null, maintenant);
        demandes.save(demande);
        Personne personne = new Personne(demande.reference(), tuteurId, perimetre.enfantsDe(tuteurId));
        Map<String, Object> contenu = new LinkedHashMap<>();
        contenu.put("reference", demande.reference());
        contenu.put("etabliLe", maintenant.toString());
        contenu.put("responsableDeTraitement", "FasoGuardian");
        modules.orderedStream().sorted(Comparator.comparing(DonneesPersonnelles::rubrique))
                .forEach(module -> contenu.put(module.rubrique(), module.exporter(personne)));
        journal.consigner(tuteurId, "PARENT", "DONNEES_EXPORTEES", "DEMANDE_DROIT", demande.reference(), Resultat.SUCCES);
        return new Export(demande.reference(), contenu);
    }

    // ------------------------------------------------------ droit à l'effacement

    @Override
    @Transactional
    public String enregistrerEffacement(UUID tuteurId) {
        return demandes.findByDemandeurIdAndTypeAndStatut(tuteurId, Type.EFFACEMENT, Statut.RECUE).orElseGet(() -> {
            DemandeDroit demande = demandes.save(new DemandeDroit(Type.EFFACEMENT, demandes.prochainNumero(), tuteurId,
                    horloge.instant()));
            journal.consigner(tuteurId, "PARENT", "EFFACEMENT_DEMANDE", "DEMANDE_DROIT", demande.reference(), Resultat.SUCCES);
            return demande;
        }).reference();
    }

    @Transactional(readOnly = true)
    public List<DemandeVue> demandes() {
        return demandes.findByTypeOrderByRecueLeDesc(Type.EFFACEMENT).stream().map(Conformite::vue).toList();
    }

    /** Exécute une demande d'effacement à la main de l'administrateur. */
    @Transactional
    public DemandeVue traiter(UUID agentId, UUID demandeId) {
        DemandeDroit demande = demandes.findById(demandeId).filter(d -> d.type() == Type.EFFACEMENT)
                .orElseThrow(() -> new ErreurMetier(CodeErreur.RESSOURCE_INTROUVABLE, "Demande introuvable."));
        if (demande.statut() == Statut.TRAITEE) {
            throw new ErreurMetier(CodeErreur.CONFLIT, "Cette demande a déjà été exécutée.");
        }
        executer(demande, agentId);
        return vue(demande);
    }

    /** Aucune demande ne dépasse l'échéance : à son approche, le système l'exécute lui-même. */
    @Scheduled(cron = "${fasoguardian.conformite.effacements:0 10 3 * * *}")
    @SchedulerLock(name = "audit-effacements-dus", lockAtMostFor = "PT30M")
    public void executerLesEffacementsDus() {
        Instant limite = horloge.instant().minus(effacementAutomatiqueApres);
        for (DemandeDroit due : demandes.findByTypeAndStatutAndRecueLeBefore(Type.EFFACEMENT, Statut.RECUE, limite)) {
            UUID id = due.id();
            try {
                transaction.executeWithoutResult(etat -> executer(demandes.findById(id).orElseThrow(), null));
            } catch (RuntimeException erreur) {
                journalTechnique.error("Effacement impossible pour la demande {} ({})", due.reference(), erreur.getClass().getName());
            }
        }
    }

    private void executer(DemandeDroit demande, UUID agentId) {
        Personne personne = new Personne(demande.reference(), demande.demandeurId(),
                perimetre.enfantsSansAutreTuteur(demande.demandeurId()));
        modules.orderedStream().sorted(Comparator.comparingInt(DonneesPersonnelles::ordre)).forEach(module -> module.effacer(personne));
        if (demande.traiter(agentId, horloge.instant())) {
            journal.consigner(agentId, agentId == null ? "SYSTEME" : "ADMIN", "EFFACEMENT_EXECUTE", "DEMANDE_DROIT",
                    demande.reference(), Resultat.SUCCES);
        }
    }

    // --------------------------------------------------- tableau de bord, rapport

    @Transactional(readOnly = true)
    public Tableau tableau() {
        YearMonth mois = YearMonth.now(horloge.withZone(fuseau));
        Instant debut = mois.atDay(1).atStartOfDay(fuseau).toInstant();
        Instant fin = mois.plusMonths(1).atDay(1).atStartOfDay(fuseau).toInstant();
        return new Tableau(aipd.etat(), CONSERVATION, demandes.countByTypeAndRecueLeBetween(Type.ACCES, debut, fin),
                demandes.countByTypeAndRecueLeBetween(Type.EFFACEMENT, debut, fin),
                demandes.countByTypeAndStatut(Type.EFFACEMENT, Statut.RECUE), joursDePurge(debut, fin),
                LocalDate.now(horloge.withZone(fuseau)).getDayOfMonth(), purges(debut, fin));
    }

    /** Rapport du mois demandé, en PDF ; sa production est journalisée. */
    @Transactional
    public Rapport rapport(UUID agentId, YearMonth mois) {
        Instant debut = mois.atDay(1).atStartOfDay(fuseau).toInstant();
        Instant fin = mois.plusMonths(1).atDay(1).atStartOfDay(fuseau).toInstant();
        Long horsDelai = jdbc.queryForObject("SELECT count(*) FROM audit.demande_droit WHERE type = 'EFFACEMENT'"
                + " AND traitee_le >= ? AND traitee_le < ? AND traitee_le > echeance_le", Long.class, Timestamp.from(debut), Timestamp.from(fin));
        Long executes = jdbc.queryForObject("SELECT count(*) FROM audit.demande_droit WHERE type = 'EFFACEMENT'"
                + " AND traitee_le >= ? AND traitee_le < ?", Long.class, Timestamp.from(debut), Timestamp.from(fin));
        OptionalLong alteree = journal.premiereEntreeAlteree();
        byte[] pdf = redacteur.rediger(new Contenu(mois, horloge.instant(), aipd.etat(), CONSERVATION, purges(debut, fin),
                joursDePurge(debut, fin), mois.lengthOfMonth(), demandes.countByTypeAndRecueLeBetween(Type.ACCES, debut, fin),
                demandes.countByTypeAndRecueLeBetween(Type.EFFACEMENT, debut, fin), executes == null ? 0 : executes,
                horsDelai == null ? 0 : horsDelai, demandes.countByTypeAndStatut(Type.EFFACEMENT, Statut.RECUE),
                alteree.isPresent() ? alteree.getAsLong() : null));
        journal.consigner(agentId, "ADMIN", "RAPPORT_DE_CONFORMITE_PRODUIT", "RAPPORT", mois.toString(), Resultat.SUCCES);
        return new Rapport("rapport-conformite-" + mois, pdf);
    }

    private List<Purge> purges(Instant debut, Instant fin) {
        return jdbc.query("SELECT traitement, max(executee_le) AS derniere, count(*) AS executions, sum(elements) AS elements"
                + " FROM audit.execution_purge WHERE executee_le >= ? AND executee_le < ? GROUP BY traitement ORDER BY traitement",
                (ligne, rang) -> new Purge(ligne.getString("traitement"), ligne.getTimestamp("derniere").toInstant(),
                        ligne.getLong("executions"), ligne.getLong("elements")),
                Timestamp.from(debut), Timestamp.from(fin));
    }

    private int joursDePurge(Instant debut, Instant fin) {
        Integer jours = jdbc.queryForObject("SELECT count(DISTINCT (executee_le AT TIME ZONE ?)::date) FROM audit.execution_purge"
                + " WHERE executee_le >= ? AND executee_le < ?", Integer.class, fuseau.getId(), Timestamp.from(debut), Timestamp.from(fin));
        return jours == null ? 0 : jours;
    }

    private static DemandeVue vue(DemandeDroit demande) {
        return new DemandeVue(demande.id(), demande.reference(), demande.type(), demande.statut(), demande.recueLe(),
                demande.echeanceLe(), demande.traiteeLe(), demande.statut() == Statut.TRAITEE && demande.traiteePar() == null);
    }
}
