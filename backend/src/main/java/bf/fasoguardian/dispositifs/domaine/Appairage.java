package bf.fasoguardian.dispositifs.domaine;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Association d'un bracelet à un enfant ; les appairages clos forment l'historique. */
@Entity
@Table(schema = "dispositifs", name = "appairage")
public class Appairage {

    @Id
    private UUID id;

    @Column(name = "bracelet_id", nullable = false)
    private UUID braceletId;

    @Column(name = "enfant_id", nullable = false)
    private UUID enfantId;

    @Column(nullable = false)
    private Instant debut;

    private Instant fin;

    @Enumerated(EnumType.STRING)
    @Column(name = "motif_fin")
    private MotifFin motifFin;

    protected Appairage() {
    }

    public Appairage(UUID braceletId, UUID enfantId, Instant debut) {
        this.id = UUID.randomUUID();
        this.braceletId = braceletId;
        this.enfantId = enfantId;
        this.debut = debut;
    }

    public void clore(MotifFin motif, Instant maintenant) {
        if (fin == null) {
            this.fin = maintenant;
            this.motifFin = motif;
        }
    }

    public UUID braceletId() {
        return braceletId;
    }

    public UUID enfantId() {
        return enfantId;
    }

    public Instant debut() {
        return debut;
    }

    public Instant fin() {
        return fin;
    }

    public MotifFin motifFin() {
        return motifFin;
    }
}
