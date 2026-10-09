package bf.fasoguardian.dispositifs.application;

import java.time.Clock;
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

    /** @param prisEnChargeParMoi le ticket est suivi par l'agent qui consulte */
    public record TicketVue(UUID id, String reference, String numeroSerie, Motif motif, Statut statut, Instant ouvertLe,
            Instant dernierContact, Integer batterie, String reseau, boolean prisEnCharge, boolean prisEnChargeParMoi,
            Resolution resolution, String note, Instant resoluLe) {
    }

    /**
     * Ce que le parent voit du ticket ouvert pour le bracelet de son enfant : ni l'agent ni ses notes.
     *
     * @param prisEnChargeLe moment où un agent du service après-vente s'en est saisi, ou {@code null}
     */
    public record SuiviParent(String reference, String numeroSerie, Statut statut, Instant ouvertLe, Instant dernierContact,
            Integer batterie, String reseau, Instant prisEnChargeLe) {
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
    }

    /** Ticket en cours pour le bracelet que porte l'enfant, s'il y en a un (écran 40). */
    @Transactional(readOnly = true)
    public Optional<SuiviParent> suiviPour(UUID tuteurId, UUID enfantId) {
        acces.exigerTuteur(tuteurId, enfantId);
        return appairages.findByEnfantIdAndFinIsNull(enfantId)
                .flatMap(appairage -> tickets.findByBraceletIdAndMotifAndStatutIn(appairage.braceletId(), Motif.MUET, EN_COURS))
                .map(ticket -> new SuiviParent(ticket.reference(), bracelets.findById(ticket.braceletId()).orElseThrow().numeroSerie(),
                        ticket.statut(), ticket.ouvertLe(), ticket.dernierContact(), ticket.batterie(), ticket.reseau(),
                        ticket.prisEnChargeLe()));
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
                        "le bracelet " + bracelets.findById(braceletId).orElseThrow().numeroSerie() + " donne de nouveau des nouvelles.");
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
        return liste.stream().map(ticket -> vue(ticket, agentId)).toList();
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

    /** @param ecran suite du lien ouvert par la notification, sous l'écran du bracelet */
    private void prevenir(UUID braceletId, Urgence urgence, String modele, String ecran, String titre, String texte) {
        appairages.findByBraceletIdAndFinIsNull(braceletId).map(Appairage::enfantId).ifPresent(enfant -> {
            Message message = new Message(modele, titre, texte, "/enfants/" + enfant + "/bracelet" + ecran, null);
            liens.tuteursActifsDe(enfant).forEach(tuteur -> notifications.notifier(tuteur, urgence, message));
        });
    }

    private TicketVue vue(TicketMaintenance ticket, UUID agentId) {
        return new TicketVue(ticket.id(), ticket.reference(), bracelets.findById(ticket.braceletId()).orElseThrow().numeroSerie(),
                ticket.motif(), ticket.statut(), ticket.ouvertLe(), ticket.dernierContact(), ticket.batterie(), ticket.reseau(),
                ticket.agentId() != null, agentId.equals(ticket.agentId()), ticket.resolution(), ticket.note(), ticket.resoluLe());
    }
}
