package bf.fasoguardian.famille.domaine;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** Fiche santé de l'enfant. Son contenu est chiffré au niveau applicatif ; toute modification crée une révision. */
@Entity
@Table(schema = "famille", name = "fiche_sante")
public class FicheSante {

    @Id
    @Column(name = "enfant_id")
    private UUID enfantId;

    @Column(name = "contenu_chiffre", nullable = false)
    private byte[] contenuChiffre;

    @Column(name = "modifie_le", nullable = false)
    private Instant modifieLe;

    @Version
    private long version;

    protected FicheSante() {
    }

    public FicheSante(UUID enfantId, byte[] contenuChiffre, Instant maintenant) {
        this.enfantId = enfantId;
        this.contenuChiffre = contenuChiffre;
        this.modifieLe = maintenant;
    }

    public void remplacer(byte[] nouveauContenuChiffre, Instant maintenant) {
        this.contenuChiffre = nouveauContenuChiffre;
        this.modifieLe = maintenant;
    }

    public byte[] contenuChiffre() {
        return contenuChiffre;
    }

    public Instant modifieLe() {
        return modifieLe;
    }
}
