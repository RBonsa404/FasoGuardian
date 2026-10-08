package bf.fasoguardian.famille.domaine;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Trace horodatée d'une modification de la fiche enfant : auteur et champ, sans les valeurs. */
@Entity
@Table(schema = "famille", name = "enfant_revision")
public class RevisionEnfant {

    @Id
    private UUID id;

    @Column(name = "enfant_id", nullable = false)
    private UUID enfantId;

    @Column(name = "auteur_id", nullable = false)
    private UUID auteurId;

    @Column(nullable = false)
    private String champ;

    @Column(name = "modifie_le", nullable = false)
    private Instant modifieLe;

    protected RevisionEnfant() {
    }

    public RevisionEnfant(UUID enfantId, UUID auteurId, String champ, Instant maintenant) {
        this.id = UUID.randomUUID();
        this.enfantId = enfantId;
        this.auteurId = auteurId;
        this.champ = champ;
        this.modifieLe = maintenant;
    }

    public String champ() {
        return champ;
    }

    public Instant modifieLe() {
        return modifieLe;
    }
}
