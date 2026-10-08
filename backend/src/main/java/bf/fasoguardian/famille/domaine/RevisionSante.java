package bf.fasoguardian.famille.domaine;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Entrée du journal de la fiche santé, non modifiable : décomptes seulement, aucune donnée médicale. */
@Entity
@Table(schema = "famille", name = "revision_sante")
public class RevisionSante {

    @Id
    private UUID id;

    @Column(name = "enfant_id", nullable = false)
    private UUID enfantId;

    @Column(name = "auteur_id", nullable = false)
    private UUID auteurId;

    @Column(name = "nombre_elements", nullable = false)
    private int nombreElements;

    @Column(name = "nombre_critiques", nullable = false)
    private int nombreCritiques;

    @Column(name = "modifie_le", nullable = false)
    private Instant modifieLe;

    protected RevisionSante() {
    }

    public RevisionSante(UUID enfantId, UUID auteurId, int nombreElements, int nombreCritiques, Instant maintenant) {
        this.id = UUID.randomUUID();
        this.enfantId = enfantId;
        this.auteurId = auteurId;
        this.nombreElements = nombreElements;
        this.nombreCritiques = nombreCritiques;
        this.modifieLe = maintenant;
    }

    public int nombreElements() {
        return nombreElements;
    }

    public int nombreCritiques() {
        return nombreCritiques;
    }

    public Instant modifieLe() {
        return modifieLe;
    }
}
