package bf.fasoguardian.identite.domaine;

import java.time.Instant;
import java.util.UUID;

import bf.fasoguardian.identite.domaine.DossierKyc.NatureLien;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Lien vérifié entre un tuteur et un enfant. Un tuteur n'agit sur un enfant qu'au travers d'un lien actif ;
 * le lien peut être suspendu à titre conservatoire en cas de litige (US-KYC-001).
 */
@Entity
@Table(schema = "identite", name = "lien_tutelle")
public class LienTutelle {

    public enum Statut {
        ACTIF,
        SUSPENDU
    }

    @Id
    private UUID id;

    @Column(name = "tuteur_id", nullable = false)
    private UUID tuteurId;

    @Column(name = "enfant_id", nullable = false)
    private UUID enfantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NatureLien nature;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Statut statut;

    @Column(name = "cree_le", nullable = false)
    private Instant creeLe;

    protected LienTutelle() {
    }

    public LienTutelle(UUID tuteurId, UUID enfantId, NatureLien nature, Instant maintenant) {
        this.id = UUID.randomUUID();
        this.tuteurId = tuteurId;
        this.enfantId = enfantId;
        this.nature = nature;
        this.statut = Statut.ACTIF;
        this.creeLe = maintenant;
    }

    public UUID tuteurId() {
        return tuteurId;
    }

    public UUID enfantId() {
        return enfantId;
    }

    public boolean actif() {
        return statut == Statut.ACTIF;
    }
}
