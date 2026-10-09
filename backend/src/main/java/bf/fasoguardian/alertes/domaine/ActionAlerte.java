package bf.fasoguardian.alertes.domaine;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Ligne du journal d'acquittement : une transition d'une alerte, jamais modifiée (FG-DOC-07 §4.4). */
@Entity
@Table(schema = "alertes", name = "action_alerte")
public class ActionAlerte {

    public static final int LONGUEUR_MOTIF = 200;

    public enum Type {
        OUVERTURE,
        ACQUITTEMENT,
        ESCALADE,
        LEVEE,
        FAUSSE_ALERTE,
        /** Clôture par le système, la cause ayant disparu. */
        RESOLUTION,
        /** Le contact d'urgence a été sollicité faute de réponse des parents (US-SYS-005). */
        CONTACT_SOLLICITE,
        /** Le point de contact institutionnel a été sollicité à son tour. */
        INSTITUTION_SOLLICITEE
    }

    @Id
    private UUID id;

    @Column(name = "alerte_id", nullable = false, updatable = false)
    private UUID alerteId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private Type type;

    @Column(name = "acteur_id", updatable = false)
    private UUID acteurId;

    @Column(updatable = false)
    private String motif;

    @Column(name = "effectuee_le", nullable = false, updatable = false)
    private Instant effectueeLe;

    protected ActionAlerte() {
    }

    ActionAlerte(UUID alerteId, Type type, UUID acteurId, String motif, Instant effectueeLe) {
        this.id = UUID.randomUUID();
        this.alerteId = alerteId;
        this.type = type;
        this.acteurId = acteurId;
        this.motif = motif;
        this.effectueeLe = effectueeLe;
    }

    public UUID alerteId() {
        return alerteId;
    }

    public Type type() {
        return type;
    }

    public UUID acteurId() {
        return acteurId;
    }

    public String motif() {
        return motif;
    }

    public Instant effectueeLe() {
        return effectueeLe;
    }
}
