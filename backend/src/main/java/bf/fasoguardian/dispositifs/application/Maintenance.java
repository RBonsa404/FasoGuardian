package bf.fasoguardian.dispositifs.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import bf.fasoguardian.audit.JournalAudit;
import bf.fasoguardian.famille.AccesEnfant;
import bf.fasoguardian.audit.JournalAudit.Resultat;
import bf.fasoguardian.dispositifs.SuiviBracelets;
import bf.fasoguardian.dispositifs.domaine.Appairage;
import bf.fasoguardian.dispositifs.domaine.Bracelet;
import bf.fasoguardian.dispositifs.domaine.ConfigurationBracelet;
import bf.fasoguardian.dispositifs.domaine.StatutBracelet;
import bf.fasoguardian.dispositifs.domaine.TicketMaintenance;
import bf.fasoguardian.dispositifs.domaine.TicketMaintenance.Motif;
import bf.fasoguardian.dispositifs.domaine.TicketMaintenance.Resolution;
import bf.fasoguardian.dispositifs.domaine.TicketMaintenance.Statut;
import bf.fasoguardian.dispositifs.infrastructure.DepotAppairages;
import bf.fasoguardian.dispositifs.infrastructure.DepotBracelets;
import bf.fasoguardian.dispositifs.infrastructure.DepotConfigurations;
import bf.fasoguardian.dispositifs.infrastructure.DepotTickets;
import bf.fasoguardian.identite.LiensTutelle;
import bf.fasoguardian.notifications.Notifications;
import bf.fasoguardian.notifications.Notifications.Message;
import bf.fasoguardian.notifications.Notifications.Urgence;
import bf.fasoguardian.plateforme.erreurs.CodeErreur;
import bf.fasoguardian.plateforme.erreurs.ErreurMetier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Maintenance des bracelets en service (US-SAV-001, US-SYS-007). La supervision ouvre un ticket quand un
 * bracelet se tait et active le mode économie sous 20 % de batterie ; le parent est averti dans les deux cas.
 * Le service après-vente ne voit que l'état du bracelet, jamais l'enfant ni sa position (FG-DOC-06, tableau 17).
 */
@Service
public class Maintenance implements SuiviBracelets {

    private static final String ROLE = "SAV";
    private static final List<Statut> EN_COURS = List.of(Statut.OUVERT, Statut.EN_COURS);

    /** Délai maximal entre la proposition d'un rendez-vous en point relais et ce rendez-vous. */
    static final Duration RENDEZ_VOUS_MAX = Duration.ofDays(30);
    /** Le nom du point relais doit tenir, avec la date et le numéro, dans les 240 caractères d'une notification. */
    public static final int POINT_RELAIS_MAX = 50;

    /**
     * @param prisEnChargeParMoi le ticket est suivi par l'agent qui consulte
     * @param diagnostic état de la commande de sondage (EMISE, ACCUSEE, REFUSEE, EXPIREE), ou {@code null}
     * @param remplacement numéro de l'unité réservée pour l'échange, ou {@code null}
     * @param autresMuets autres bracelets muets en ce moment : beaucoup à la fois font penser au réseau
     */
    public record TicketVue(UUID id, String reference, String numeroSerie, Motif motif, Statut statut, Instant ouvertLe,
            Instant dernierContact, Integer batterie, String reseau, boolean prisEnCharge, boolean prisEnChargeParMoi,
            Resolution resolution, String note, Instant resoluLe, Instant diagnosticLe, String diagnostic, String pointRelais,
            Instant rendezVousLe, String remplacement, long autresMuets) {
    }

    /**
     * Ce que le parent voit du ticket ouvert pour le bracelet de son enfant : ni l'agent ni ses notes.
     *
     * @param prisEnChargeLe moment où un agent du service après-vente s'en est saisi, ou {@code null}
     * @param pointRelais point relais où le parent est attendu, ou {@code null}
     */
    public record SuiviParent(String reference, String numeroSerie, Statut statut, Instant ouvertLe, Instant dernierContact,
            Integer batterie, String reseau, Instant prisEnChargeLe, String pointRelais, Instant rendezVousLe) {
    }

    private final DepotTickets tickets;
    private final DepotBracelets bracelets;
    private final DepotAppairages appairages;
    private final DepotConfigurations configurations;
    private final CommandesBracelet commandes;
    private final LiensTutelle liens;
    private final AccesEnfant acces;
    private final Notifications notifications;
    private final JournalAudit journal;
    private final Clock horloge;
    private final DateTimeFormatter heure;
    private final DateTimeFormatter date;

    Maintenance(DepotTickets tickets, DepotBracelets bracelets, DepotAppairages appairages, DepotConfigurations configurations,
            CommandesBracelet commandes, LiensTutelle liens, AccesEnfant acces, Notifications notifications, JournalAudit journal, Clock horloge,
            @Value("${fasoguardian.fuseau:Africa/Ouagadougou}") ZoneId fuseau) {
        this.tickets = tickets;
        this.bracelets = bracelets;
        this.appairages = appairages;
        this.configurations = configurations;
        this.commandes = commandes;
        this.liens = liens;
        this.acces = acces;
        this.notifications = notifications;
        this.journal = journal;
        this.horloge = horloge;
        this.heure = DateTimeFormatter.ofPattern("HH:mm", Locale.FRENCH).withZone(fuseau);
        this.date = DateTimeFormatter.ofPattern("EEEE d MMMM 'à' HH:mm", Locale.FRENCH).withZone(fuseau);
    }

    /** Ticket en cours pour le bracelet que porte l'enfant, s'il y en a un (écran 40). */
    @Transactional(readOnly = true)
    public Optional<SuiviParent> suiviPour(UUID tuteurId, UUID enfantId) {
        acces.exigerTuteur(tuteurId, enfantId);
        return appairages.findByEnfantIdAndFinIsNull(enfantId)
                .flatMap(appairage -> tickets.findByBraceletIdAndMotifAndStatutIn(appairage.braceletId(), Motif.MUET, EN_COURS))
                .map(ticket -> new SuiviParent(ticket.reference(), bracelets.findById(ticket.braceletId()).orElseThrow().numeroSerie(),
                        ticket.statut(), ticket.ouvertLe(), ticket.dernierContact(), ticket.batterie(), ticket.reseau(),
                        ticket.prisEnChargeLe(), ticket.pointRelais(), ticket.rendezVousLe()));
    }

    // --------------------------------------------------------------- supervision

    @Override
    @Transactional
    public void signalerMuet(UUID braceletId, Instant dernierContact, Integer batterie, String reseau) {
        if (tickets.findByBraceletIdAndMotifAndStatutIn(braceletId, Motif.MUET, EN_COURS).isPresent()) {
            return;
        }
        Bracelet bracelet = bracelets.findById(braceletId).orElseThrow();
        TicketMaintenance ticket = tickets.save(new TicketMaintenance(tickets.prochainNumero(), braceletId, Motif.MUET,
                dernierContact, batterie, reseau, horloge.instant()));
        journal.consigner(null, "SYSTEME", "TICKET_OUVERT", "BRACELET", braceletId.toString(), Resultat.SUCCES);
        prevenir(braceletId, Urgence.IMPORTANTE, "BRACELET_MUET", "/maintenance", "Bracelet sans nouvelles",
                "le bracelet " + bracelet.numeroSerie() + " ne donne plus de nouvelles"
                        + (dernierContact == null ? "" : " depuis " + heure.format(dernierContact))
                        + ". Vérifiez qu'il est chargé et porté. Le service après-vente est prévenu (" + ticket.reference() + ").");
    }

    @Override
    @Transactional
    public void signalerReprise(UUID braceletId) {
        tickets.findByBraceletIdAndMotifAndStatutIn(braceletId, Motif.MUET, EN_COURS).ifPresent(ticket -> {
            if (ticket.resoudre(Resolution.REPRISE_SPONTANEE, null, null, horloge.instant())) {
                journal.consigner(null, "SYSTEME", "TICKET_RESOLU", "BRACELET", braceletId.toString(), Resultat.SUCCES);
                prevenir(braceletId, Urgence.INFORMATION, "BRACELET_DE_RETOUR", "", "Bracelet de retour",
                        "le bracelet " + bracelets.findById(braceletId).orElseThrow().numeroSerie() + " donne de nouveau des nouvelles."
                                + (ticket.rendezVousLe() == null ? "" : " Le rendez-vous en point relais n'est plus nécessaire."));
            }
        });
    }

    @Override
    @Transactional(readOnly = true)
    public Set<UUID> muets() {
        return tickets.findByMotifAndStatutIn(Motif.MUET, EN_COURS).stream().map(TicketMaintenance::braceletId)
                .collect(Collectors.toSet());
    }

    @Override
    @Transactional
    public void batterieFaible(UUID braceletId, int niveau) {
        appairages.findByBraceletIdAndFinIsNull(braceletId).ifPresent(appairage -> {
            Bracelet bracelet = bracelets.findById(braceletId).orElseThrow();
            ConfigurationBracelet configuration = configurations.findById(braceletId).orElseThrow();
            boolean active = configuration.economiserDOffice();
            if (active) {
                commandes.configurer(bracelet, configuration, appairage.enfantId(), null);
                journal.consigner(null, "SYSTEME", "MODE_ECONOMIE_ACTIVE", "BRACELET", braceletId.toString(), Resultat.SUCCES);
            }
            prevenir(braceletId, Urgence.IMPORTANTE, "BATTERIE_FAIBLE", "", "Batterie faible",
                    "la batterie du bracelet " + bracelet.numeroSerie() + " est à " + niveau + " %. "
                            + (active ? "Le mode économie est activé pour la préserver. " : "") + "Pensez à le recharger.");
        });
    }

    @Override
    @Transactional
    public void batterieRetablie(UUID braceletId) {
        appairages.findByBraceletIdAndFinIsNull(braceletId).ifPresent(appairage -> {
            ConfigurationBracelet configuration = configurations.findById(braceletId).orElseThrow();
            if (configuration.leverLEconomieDOffice()) {
                commandes.configurer(bracelets.findById(braceletId).orElseThrow(), configuration, appairage.enfantId(), null);
                journal.consigner(null, "SYSTEME", "MODE_ECONOMIE_DESACTIVE", "BRACELET", braceletId.toString(), Resultat.SUCCES);
            }
        });
    }

    // ------------------------------------------------------ service après-vente

    /** Tickets à traiter, les plus anciens d'abord ; ou les cent derniers résolus. */
    @Transactional(readOnly = true)
    public List<TicketVue> tickets(UUID agentId, boolean resolus) {
        List<TicketMaintenance> liste = resolus ? tickets.findTop100ByStatutOrderByResoluLeDesc(Statut.RESOLU)
                : tickets.findByStatutInOrderByOuvertLe(EN_COURS);
        long muets = tickets.countByMotifAndStatutIn(Motif.MUET, EN_COURS);
        return liste.stream().map(ticket -> vue(ticket, agentId, muets)).toList();
    }

    @Transactional
    public TicketVue prendreEnCharge(UUID agentId, UUID ticketId) {
        TicketMaintenance ticket = ticket(ticketId);
        if (!ticket.prendreEnCharge(agentId, horloge.instant())) {
            throw new ErreurMetier(CodeErreur.CONFLIT, "Ce ticket est déjà pris en charge ou résolu.");
        }
        journal.consigner(agentId, ROLE, "TICKET_PRIS_EN_CHARGE", "TICKET", ticket.reference(), Resultat.SUCCES);
        return vue(ticket, agentId);
    }

    /** Sonde le bracelet par une localisation signée : son accusé dira s'il reçoit encore ses commandes. */
    @Transactional
    public TicketVue diagnostiquer(UUID agentId, UUID ticketId) {
        TicketMaintenance ticket = ticketEnCours(ticketId);
        UUID commande = commandes.sonder(bracelets.findById(ticket.braceletId()).orElseThrow(), agentId)
                .orElseThrow(() -> new ErreurMetier(CodeErreur.CONFLIT, "Ce bracelet n'est plus en service : il ne peut pas être sondé."));
        ticket.diagnostiquer(commande, horloge.instant());
        journal.consigner(agentId, ROLE, "TICKET_DIAGNOSTIC", "TICKET", ticket.reference(), Resultat.SUCCES);
        return vue(ticket, agentId);
    }

    /**
     * Propose au parent un rendez-vous en point relais. Le service après-vente ne connaît pas le parent : c'est
     * la plateforme qui le lui transmet, par notification et SMS.
     */
    @Transactional
    public TicketVue fixerRendezVous(UUID agentId, UUID ticketId, String pointRelais, Instant quand) {
        TicketMaintenance ticket = ticketEnCours(ticketId);
        Instant maintenant = horloge.instant();
        if (pointRelais == null || pointRelais.isBlank() || pointRelais.strip().length() > POINT_RELAIS_MAX || quand == null || !quand.isAfter(maintenant)
                || quand.isAfter(maintenant.plus(RENDEZ_VOUS_MAX))) {
            throw new ErreurMetier(CodeErreur.REQUETE_INVALIDE, "Indiquez un point relais et un rendez-vous dans les trente prochains jours.");
        }
        ticket.fixerRendezVous(pointRelais, quand);
        journal.consigner(agentId, ROLE, "TICKET_RENDEZ_VOUS", "TICKET", ticket.reference(), Resultat.SUCCES);
        Bracelet bracelet = bracelets.findById(ticket.braceletId()).orElseThrow();
        prevenir(ticket.braceletId(), Urgence.IMPORTANTE, "RENDEZ_VOUS_POINT_RELAIS", "/maintenance", "Rendez-vous en point relais",
                "rendez-vous SAV au point relais « " + ticket.pointRelais() + " » le " + date.format(quand) + ", avec le bracelet "
                        + bracelet.numeroSerie() + ". En cas d'échange, vous saisirez le code du nouveau bracelet dans l'application.");
        return vue(ticket, agentId);
    }

    /** Réserve au ticket l'unité en stock qui sera remise au parent ; c'est lui qui l'associe, avec son code. */
    @Transactional
    public TicketVue reserverRemplacement(UUID agentId, UUID ticketId, String numeroSerie) {
        TicketMaintenance ticket = ticketEnCours(ticketId);
        Bracelet unite = bracelets.findByNumeroSerie(numeroSerie == null ? "" : numeroSerie.strip().toUpperCase(Locale.ROOT))
                .orElseThrow(() -> new ErreurMetier(CodeErreur.RESSOURCE_INTROUVABLE, "Aucune unité de ce numéro au parc."));
        if (unite.statut() != StatutBracelet.EN_STOCK || unite.certificatRevoque()) {
            throw new ErreurMetier(CodeErreur.CONFLIT, "Cette unité n'est pas en stock : elle ne peut pas être remise.");
        }
        tickets.findByRemplacementIdAndStatutIn(unite.id(), EN_COURS).filter(autre -> !autre.id().equals(ticket.id()))
                .ifPresent(autre -> {
                    throw new ErreurMetier(CodeErreur.CONFLIT, "Cette unité est déjà réservée pour le ticket " + autre.reference() + ".");
                });
        ticket.reserverRemplacement(unite.id());
        journal.consigner(agentId, ROLE, "TICKET_REMPLACEMENT_RESERVE", "TICKET", ticket.reference(), Resultat.SUCCES);
        return vue(ticket, agentId);
    }

    /**
     * Le parent associe à son enfant l'unité réservée pour le ticket de l'ancien bracelet : c'est l'échange en
     * point relais. Le ticket est clos ; l'appelant renvoie l'ancienne unité au service après-vente.
     *
     * @return {@code false} si aucun ticket en cours n'attendait cet échange
     */
    @Transactional
    public boolean echanger(UUID ancienId, UUID nouveauId, Instant maintenant) {
        return tickets.findByBraceletIdAndMotifAndStatutIn(ancienId, Motif.MUET, EN_COURS)
                .filter(ticket -> nouveauId.equals(ticket.remplacementId()))
                .map(ticket -> {
                    ticket.resoudre(Resolution.ECHANGE, "Échange en point relais contre "
                            + bracelets.findById(nouveauId).orElseThrow().numeroSerie() + ".", null, maintenant);
                    journal.consigner(null, "SYSTEME", "TICKET_RESOLU", "TICKET", ticket.reference(), Resultat.SUCCES);
                    return true;
                }).orElse(false);
    }

    @Transactional
    public TicketVue resoudre(UUID agentId, UUID ticketId, Resolution resolution, String note) {
        TicketMaintenance ticket = ticket(ticketId);
        if (resolution == null || resolution == Resolution.REPRISE_SPONTANEE) {
            throw new ErreurMetier(CodeErreur.REQUETE_INVALIDE, "Indiquez comment le ticket a été résolu.");
        }
        if (!ticket.resoudre(resolution, note, agentId, horloge.instant())) {
            throw new ErreurMetier(CodeErreur.CONFLIT, "Ce ticket est déjà résolu.");
        }
        journal.consigner(agentId, ROLE, "TICKET_RESOLU", "TICKET", ticket.reference(), Resultat.SUCCES);
        return vue(ticket, agentId);
    }

    // -------------------------------------------------------------------- aides

    private TicketMaintenance ticket(UUID ticketId) {
        return tickets.findById(ticketId)
                .orElseThrow(() -> new ErreurMetier(CodeErreur.RESSOURCE_INTROUVABLE, "Ticket introuvable."));
    }

    private TicketMaintenance ticketEnCours(UUID ticketId) {
        TicketMaintenance ticket = ticket(ticketId);
        if (ticket.statut() != Statut.EN_COURS) {
            throw new ErreurMetier(CodeErreur.CONFLIT, ticket.statut() == Statut.RESOLU ? "Ce ticket est déjà résolu."
                    : "Prenez d'abord le ticket en charge.");
        }
        return ticket;
    }

    /** @param ecran suite du lien ouvert par la notification, sous l'écran du bracelet */
    private void prevenir(UUID braceletId, Urgence urgence, String modele, String ecran, String titre, String texte) {
        appairages.findByBraceletIdAndFinIsNull(braceletId).map(Appairage::enfantId).ifPresent(enfant -> {
            Message message = new Message(modele, titre, texte, "/enfants/" + enfant + "/bracelet" + ecran, null);
            liens.tuteursActifsDe(enfant).forEach(tuteur -> notifications.notifier(tuteur, urgence, message));
        });
    }

    private TicketVue vue(TicketMaintenance ticket, UUID agentId) {
        return vue(ticket, agentId, tickets.countByMotifAndStatutIn(Motif.MUET, EN_COURS));
    }

    /** @param muets bracelets muets en ce moment, celui du ticket compris s'il est encore en cours */
    private TicketVue vue(TicketMaintenance ticket, UUID agentId, long muets) {
        String diagnostic = ticket.commandeDiagnosticId() == null ? null
                : commandes.statut(ticket.commandeDiagnosticId()).map(Enum::name).orElse(null);
        String remplacement = ticket.remplacementId() == null ? null
                : bracelets.findById(ticket.remplacementId()).orElseThrow().numeroSerie();
        long autres = Math.max(0, muets - (ticket.statut() == Statut.RESOLU ? 0 : 1));
        return new TicketVue(ticket.id(), ticket.reference(), bracelets.findById(ticket.braceletId()).orElseThrow().numeroSerie(),
                ticket.motif(), ticket.statut(), ticket.ouvertLe(), ticket.dernierContact(), ticket.batterie(), ticket.reseau(),
                ticket.agentId() != null, agentId.equals(ticket.agentId()), ticket.resolution(), ticket.note(), ticket.resoluLe(),
                ticket.diagnosticLe(), diagnostic, ticket.pointRelais(), ticket.rendezVousLe(), remplacement, autres);
    }
}
