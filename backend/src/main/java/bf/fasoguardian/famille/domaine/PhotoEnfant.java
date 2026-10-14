package bf.fasoguardian.famille.domaine;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Photo de l'enfant, chiffrée ; une seule par enfant, remplacée à chaque envoi. */
@Entity
@Table(schema = "famille", name = "photo_enfant")
public class PhotoEnfant {

    @Id
    @Column(name = "enfant_id")
    private UUID enfantId;

    @Column(name = "contenu_chiffre", nullable = false)
    private byte[] contenuChiffre;

    @Column(name = "type_mime", nullable = false)
    private String typeMime;

    @Column(name = "taille_octets", nullable = false)
    private int tailleOctets;

    @Column(name = "modifiee_le", nullable = false)
    private Instant modifieeLe;

    protected PhotoEnfant() {
    }

    public PhotoEnfant(UUID enfantId, byte[] contenuChiffre, String typeMime, int tailleOctets, Instant maintenant) {
        this.enfantId = enfantId;
        remplacer(contenuChiffre, typeMime, tailleOctets, maintenant);
    }

    public void remplacer(byte[] nouveauContenu, String nouveauType, int nouvelleTaille, Instant maintenant) {
        this.contenuChiffre = nouveauContenu;
        this.typeMime = nouveauType;
        this.tailleOctets = nouvelleTaille;
        this.modifieeLe = maintenant;
    }

    public UUID enfantId() {
        return enfantId;
    }

    public byte[] contenuChiffre() {
        return contenuChiffre;
    }

    public String typeMime() {
        return typeMime;
    }

    public Instant modifieeLe() {
        return modifieeLe;
    }
}
