package bf.fasoguardian.identite.domaine;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Litige de filiation signalé sur un compte actif (US-KYC-001). Tant qu'il est ouvert, le tuteur contesté ne
 * peut plus modifier ce qui concerne l'enfant, et sa vue de la position peut être suspendue à titre
 * conservatoire. Il se clôt par une décision fondée sur une décision de justice ou un accord écrit.
 */
@Entity
@Table(schema = "identite", name = "litige")
public class Litige {

    /** Délai d'instruction affiché à l'agent. */
    public static final Duration DELAI_DE_DECISION = Duration.ofDays(7);

    public enum Statut {
        OUVERT,
        CLOS
    }

    public enum Decision {
        LIEN_MAINTENU,
        LIEN_RETIRE
    }

    public enum Fondement {
        DECISION_DE_JUSTICE,
        ACCORD_ECRIT
    }

    @Id
    private UUID id;

    @Column(nullable = false, updatable = false)
    private String reference;

    @Column(name = "tuteur_id", nullable = false, updatable = false)
    private UUID tuteurId;

    @Column(name = "enfant_id", nullable = false, updatable = false)
    private UUID enfantId;

    @Column(nullable = false, updatable = false)
    private String motif;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Statut statut;

    @Column(name = "geolocalisation_suspendue", nullable = false)
    private boolean geolocalisationSuspendue;

    @Column(name = "ouvert_par", nullable = false, updatable = false)
    private UUID ouvertPar;

    @Column(name = "ouvert_le", nullable = false, updatable = false)
    private Instant ouvertLe;

    @Column(name = "echeance_le", nullable = false, updatable = false)
    private Instant echeanceLe;

    @Enumerated(EnumType.STRING)
    private Decision decision;

    @Enumerated(EnumType.STRING)
    private Fondement fondement;

    @Column(name = "reference_du_fondement")
    private String referenceDuFondement;

    @Column(name = "decide_par")
    private UUID decidePar;

    @Column(name = "decide_le")
    private Instant decideLe;

    @Version
    private long version;

    protected Litige() {
    }

    public Litige(long numero, UUID tuteurId, UUID enfantId, String motif, boolean geolocalisationSuspendue, UUID agentId,
            Instant maintenant) {
        this.id = UUID.randomUUID();
        this.reference = "LIT-%06d".formatted(numero);
        this.tuteurId = tuteurId;
        this.enfantId = enfantId;
        this.motif = motif;
        this.statut = Statut.OUVERT;
        this.geolocalisationSuspendue = geolocalisationSuspendue;
        this.ouvertPar = agentId;
        this.ouvertLe = maintenant;
        this.echeanceLe = maintenant.plus(DELAI_DE_DECISION);
    }

    /** Pose ou lève la suspension conservatoire de la géolocalisation. @return {@code false} si rien ne change */
    public boolean suspendreLaGeolocalisation(boolean suspendue) {
        if (statut == Statut.CLOS || geolocalisationSuspendue == suspendue) {
            return false;
        }
        geolocalisationSuspendue = suspendue;
        return true;
    }

    /** @return {@code false} si le litige était déjà clos */
    public boolean decider(Decision issue, Fondement surQuoi, String referenceDeLaPiece, UUID agentId, Instant maintenant) {
        if (statut == Statut.CLOS) {
            return false;
        }
        statut = Statut.CLOS;
        decision = issue;
        fondement = surQuoi;
        referenceDuFondement = referenceDeLaPiece == null || referenceDeLaPiece.isBlank() ? null : referenceDeLaPiece.strip();
        decidePar = agentId;
        decideLe = maintenant;
        geolocalisationSuspendue = false;
        return true;
    }

    public UUID id() {
        return id;
    }

    public String reference() {
        return reference;
    }

    public UUID tuteurId() {
        return tuteurId;
    }

    public UUID enfantId() {
        return enfantId;
    }

    public String motif() {
        return motif;
    }

    public Statut statut() {
        return statut;
    }

    public boolean geolocalisationSuspendue() {
        return geolocalisationSuspendue;
    }

    public Instant ouvertLe() {
        return ouvertLe;
    }

    public Instant echeanceLe() {
        return echeanceLe;
    }

    public Decision decision() {
        return decision;
    }

    public Fondement fondement() {
        return fondement;
    }

    public String referenceDuFondement() {
        return referenceDuFondement;
    }

    public Instant decideLe() {
        return decideLe;
    }
}
