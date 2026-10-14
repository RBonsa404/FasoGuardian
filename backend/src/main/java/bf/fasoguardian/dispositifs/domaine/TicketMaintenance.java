package bf.fasoguardian.dispositifs.domaine;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** Ticket de maintenance ouvert par la supervision et suivi par le service après-vente (US-SAV-001). */
@Entity
@Table(schema = "dispositifs", name = "ticket_maintenance")
public class TicketMaintenance {

    public enum Motif {
        /** Plus de trois intervalles sans nouvelles. */
        MUET
    }

    public enum Statut {
        OUVERT,
        EN_COURS,
        RESOLU
    }

    public enum Resolution {
        /** Le bracelet a redonné des nouvelles de lui-même. */
        REPRISE_SPONTANEE,
        RECHARGE,
        ECHANGE,
        RETOUR_ATELIER,
        SANS_SUITE
    }

    @Id
    private UUID id;

    @Column(nullable = false, updatable = false)
    private String reference;

    @Column(name = "bracelet_id", nullable = false, updatable = false)
    private UUID braceletId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private Motif motif;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Statut statut;

    @Column(name = "ouvert_le", nullable = false, updatable = false)
    private Instant ouvertLe;

    @Column(name = "dernier_contact", updatable = false)
    private Instant dernierContact;

    @Column(updatable = false)
    private Integer batterie;

    @Column(updatable = false)
    private String reseau;

    @Column(name = "agent_id")
    private UUID agentId;

    @Column(name = "pris_en_charge_le")
    private Instant prisEnChargeLe;

    @Enumerated(EnumType.STRING)
    private Resolution resolution;

    private String note;

    @Column(name = "resolu_le")
    private Instant resoluLe;

    /** Sondage du bracelet par une commande de localisation signée (écran 67, étape 2). */
    @Column(name = "diagnostic_le")
    private Instant diagnosticLe;

    @Column(name = "commande_diagnostic_id")
    private UUID commandeDiagnosticId;

    @Column(name = "point_relais")
    private String pointRelais;

    @Column(name = "rendez_vous_le")
    private Instant rendezVousLe;

    /** Unité de remplacement réservée : son appairage à l'enfant vaut échange et clôt le ticket. */
    @Column(name = "remplacement_id")
    private UUID remplacementId;

    @Version
    private long version;

    protected TicketMaintenance() {
    }

    public TicketMaintenance(long numero, UUID braceletId, Motif motif, Instant dernierContact, Integer batterie, String reseau,
            Instant maintenant) {
        this.id = UUID.randomUUID();
        this.reference = "SAV-%06d".formatted(numero);
        this.braceletId = braceletId;
        this.motif = motif;
        this.statut = Statut.OUVERT;
        this.ouvertLe = maintenant;
        this.dernierContact = dernierContact;
        this.batterie = batterie;
        this.reseau = reseau;
    }

    /** @return {@code false} si le ticket n'attendait pas d'agent */
    public boolean prendreEnCharge(UUID agent, Instant maintenant) {
        if (statut != Statut.OUVERT) {
            return false;
        }
        statut = Statut.EN_COURS;
        agentId = agent;
        prisEnChargeLe = maintenant;
        return true;
    }

    /** @return {@code false} si le ticket n'est pas pris en charge */
    public boolean diagnostiquer(UUID commande, Instant maintenant) {
        if (statut != Statut.EN_COURS) {
            return false;
        }
        commandeDiagnosticId = commande;
        diagnosticLe = maintenant;
        return true;
    }

    /** @return {@code false} si le ticket n'est pas pris en charge */
    public boolean fixerRendezVous(String point, Instant quand) {
        if (statut != Statut.EN_COURS) {
            return false;
        }
        pointRelais = point.strip();
        rendezVousLe = quand;
        return true;
    }

    /** @return {@code false} si le ticket n'est pas pris en charge */
    public boolean reserverRemplacement(UUID bracelet) {
        if (statut != Statut.EN_COURS) {
            return false;
        }
        remplacementId = bracelet;
        return true;
    }

    /**
     * @param agent agent qui clôt le ticket, ou {@code null} quand le système constate la reprise
     * @return {@code false} s'il était déjà résolu
     */
    public boolean resoudre(Resolution issue, String commentaire, UUID agent, Instant maintenant) {
        if (statut == Statut.RESOLU) {
            return false;
        }
        statut = Statut.RESOLU;
        resolution = issue;
        note = commentaire == null || commentaire.isBlank() ? null : commentaire.strip();
        if (agent != null) {
            agentId = agent;
        }
        resoluLe = maintenant;
        return true;
    }

    public UUID id() {
        return id;
    }

    public String reference() {
        return reference;
    }

    public UUID braceletId() {
        return braceletId;
    }

    public Motif motif() {
        return motif;
    }

    public Statut statut() {
        return statut;
    }

    public Instant ouvertLe() {
        return ouvertLe;
    }

    public Instant dernierContact() {
        return dernierContact;
    }

    public Integer batterie() {
        return batterie;
    }

    public String reseau() {
        return reseau;
    }

    public Instant prisEnChargeLe() {
        return prisEnChargeLe;
    }

    public UUID agentId() {
        return agentId;
    }

    public Resolution resolution() {
        return resolution;
    }

    public String note() {
        return note;
    }

    public Instant resoluLe() {
        return resoluLe;
    }

    public Instant diagnosticLe() {
        return diagnosticLe;
    }

    public UUID commandeDiagnosticId() {
        return commandeDiagnosticId;
    }

    public String pointRelais() {
        return pointRelais;
    }

    public Instant rendezVousLe() {
        return rendezVousLe;
    }

    public UUID remplacementId() {
        return remplacementId;
    }
}
