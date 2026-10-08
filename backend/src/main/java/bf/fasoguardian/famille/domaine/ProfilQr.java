package bf.fasoguardian.famille.domaine;

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
 * Jeton du QR code gravé sur le bracelet, rattaché à un enfant lors de l'appairage. Seule son empreinte
 * est conservée ; il est suspendu dès que le bracelet est déclaré perdu ou volé.
 */
@Entity
@Table(schema = "famille", name = "profil_qr")
public class ProfilQr {

    public enum Statut {
        ACTIF,
        SUSPENDU
    }

    @Id
    @Column(name = "enfant_id")
    private UUID enfantId;

    @Column(name = "jeton_sha256", nullable = false)
    private String jetonSha256;

    @Column(name = "numero_bracelet", nullable = false)
    private String numeroBracelet;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Statut statut;

    @Column(name = "associe_le", nullable = false)
    private Instant associeLe;

    @Version
    private long version;

    protected ProfilQr() {
    }

    public ProfilQr(UUID enfantId, String jetonSha256, String numeroBracelet, Instant maintenant) {
        this.enfantId = enfantId;
        remplacer(jetonSha256, numeroBracelet, maintenant);
    }

    /** Nouveau bracelet pour le même enfant (remplacement) : l'ancien jeton cesse aussitôt de fonctionner. */
    public void remplacer(String nouveauJetonSha256, String nouveauNumero, Instant maintenant) {
        this.jetonSha256 = nouveauJetonSha256;
        this.numeroBracelet = nouveauNumero;
        this.statut = Statut.ACTIF;
        this.associeLe = maintenant;
    }

    public void suspendre() {
        this.statut = Statut.SUSPENDU;
    }

    public void reactiver() {
        this.statut = Statut.ACTIF;
    }

    public UUID enfantId() {
        return enfantId;
    }

    public String numeroBracelet() {
        return numeroBracelet;
    }

    public boolean actif() {
        return statut == Statut.ACTIF;
    }
}
