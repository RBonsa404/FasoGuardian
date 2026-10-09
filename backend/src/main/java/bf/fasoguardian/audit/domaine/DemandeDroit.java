package bf.fasoguardian.audit.domaine;

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

/** Demande d'accès ou d'effacement d'une personne (loi n° 001-2021/AN ; FG-DOC-04 : traitée en moins de 30 jours). */
@Entity
@Table(schema = "audit", name = "demande_droit")
public class DemandeDroit {

    /** Délai légal de traitement. */
    public static final Duration DELAI = Duration.ofDays(30);

    public enum Type {
        ACCES("ACC"),
        EFFACEMENT("EFF");

        private final String prefixe;

        Type(String prefixe) {
            this.prefixe = prefixe;
        }
    }

    public enum Statut {
        RECUE,
        TRAITEE
    }

    @Id
    private UUID id;

    @Column(nullable = false, updatable = false)
    private String reference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private Type type;

    @Column(name = "demandeur_id", nullable = false, updatable = false)
    private UUID demandeurId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Statut statut;

    @Column(name = "recue_le", nullable = false, updatable = false)
    private Instant recueLe;

    @Column(name = "echeance_le", nullable = false, updatable = false)
    private Instant echeanceLe;

    @Column(name = "traitee_le")
    private Instant traiteeLe;

    @Column(name = "traitee_par")
    private UUID traiteePar;

    @Version
    private long version;

    protected DemandeDroit() {
    }

    public DemandeDroit(Type type, long numero, UUID demandeurId, Instant maintenant) {
        this.id = UUID.randomUUID();
        this.reference = "%s-%06d".formatted(type.prefixe, numero);
        this.type = type;
        this.demandeurId = demandeurId;
        this.statut = Statut.RECUE;
        this.recueLe = maintenant;
        this.echeanceLe = maintenant.plus(DELAI);
    }

    /**
     * @param agentId agent qui l'exécute, ou {@code null} pour le système
     * @return {@code false} si elle était déjà traitée
     */
    public boolean traiter(UUID agentId, Instant maintenant) {
        if (statut == Statut.TRAITEE) {
            return false;
        }
        statut = Statut.TRAITEE;
        traiteeLe = maintenant;
        traiteePar = agentId;
        return true;
    }

    public UUID id() {
        return id;
    }

    public String reference() {
        return reference;
    }

    public Type type() {
        return type;
    }

    public UUID demandeurId() {
        return demandeurId;
    }

    public Statut statut() {
        return statut;
    }

    public Instant recueLe() {
        return recueLe;
    }

    public Instant echeanceLe() {
        return echeanceLe;
    }

    public Instant traiteeLe() {
        return traiteeLe;
    }

    public UUID traiteePar() {
        return traiteePar;
    }
}
