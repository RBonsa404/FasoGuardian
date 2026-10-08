package bf.fasoguardian.famille.domaine;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Personne à prévenir. Son nom et son téléphone sont chiffrés ; seul le lien avec l'enfant (« Tante »)
 * peut apparaître sur la page publique, et seulement si le parent l'a marqué visible.
 */
@Entity
@Table(schema = "famille", name = "contact_urgence")
public class ContactUrgence {

    public static final int MAXIMUM_PAR_ENFANT = 5;

    @Id
    private UUID id;

    @Column(name = "enfant_id", nullable = false)
    private UUID enfantId;

    @Column(nullable = false)
    private String lien;

    @Column(name = "nom_chiffre", nullable = false)
    private byte[] nomChiffre;

    @Column(name = "telephone_chiffre", nullable = false)
    private byte[] telephoneChiffre;

    @Column(name = "visible_sur_qr", nullable = false)
    private boolean visibleSurQr;

    @Column(nullable = false)
    private int rang;

    @Column(name = "cree_le", nullable = false)
    private Instant creeLe;

    protected ContactUrgence() {
    }

    public ContactUrgence(UUID enfantId, String lien, byte[] nomChiffre, byte[] telephoneChiffre, boolean visibleSurQr,
            int rang, Instant maintenant) {
        this.id = UUID.randomUUID();
        this.enfantId = enfantId;
        this.lien = lien;
        this.nomChiffre = nomChiffre;
        this.telephoneChiffre = telephoneChiffre;
        this.visibleSurQr = visibleSurQr;
        this.rang = rang;
        this.creeLe = maintenant;
    }

    public void modifier(String nouveauLien, byte[] nouveauNomChiffre, byte[] nouveauTelephoneChiffre, boolean visible) {
        this.lien = nouveauLien;
        this.nomChiffre = nouveauNomChiffre;
        this.telephoneChiffre = nouveauTelephoneChiffre;
        this.visibleSurQr = visible;
    }

    public UUID id() {
        return id;
    }

    public UUID enfantId() {
        return enfantId;
    }

    public String lien() {
        return lien;
    }

    public byte[] nomChiffre() {
        return nomChiffre;
    }

    public byte[] telephoneChiffre() {
        return telephoneChiffre;
    }

    public boolean visibleSurQr() {
        return visibleSurQr;
    }

    public int rang() {
        return rang;
    }
}
