package bf.fasoguardian.abonnements.domaine;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Reçu d'un paiement confirmé, à numérotation continue (FG-DOC-07 §4.5). Il ne change plus une fois émis. */
@Entity
@Table(schema = "abonnements", name = "facture")
public class Facture {

    @Id
    private String numero;

    @Column(name = "paiement_id", nullable = false, updatable = false)
    private UUID paiementId;

    @Column(name = "abonnement_id", nullable = false, updatable = false)
    private UUID abonnementId;

    @Column(name = "tuteur_id", nullable = false, updatable = false)
    private UUID tuteurId;

    @Column(name = "offre_libelle", nullable = false, updatable = false)
    private String offreLibelle;

    @Column(name = "montant_fcfa", nullable = false, updatable = false)
    private int montantFcfa;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private Moyen moyen;

    @Column(name = "numero_masque", nullable = false, updatable = false)
    private String numeroMasque;

    @Column(name = "periode_debut", nullable = false, updatable = false)
    private LocalDate periodeDebut;

    @Column(name = "periode_fin", nullable = false, updatable = false)
    private LocalDate periodeFin;

    @Column(name = "emise_le", nullable = false, updatable = false)
    private Instant emiseLe;

    protected Facture() {
    }

    public Facture(String numero, Paiement paiement, UUID tuteurId, String offreLibelle, String numeroMasque,
            Abonnement.Periode periode, Instant maintenant) {
        this.numero = numero;
        this.paiementId = paiement.id();
        this.abonnementId = paiement.abonnementId();
        this.tuteurId = tuteurId;
        this.offreLibelle = offreLibelle;
        this.montantFcfa = paiement.montantFcfa();
        this.moyen = paiement.moyen();
        this.numeroMasque = numeroMasque;
        this.periodeDebut = periode.debut();
        this.periodeFin = periode.fin();
        this.emiseLe = maintenant;
    }

    public String numero() {
        return numero;
    }

    public UUID paiementId() {
        return paiementId;
    }

    public UUID tuteurId() {
        return tuteurId;
    }

    public String offreLibelle() {
        return offreLibelle;
    }

    public int montantFcfa() {
        return montantFcfa;
    }

    public Moyen moyen() {
        return moyen;
    }

    public String numeroMasque() {
        return numeroMasque;
    }

    public LocalDate periodeDebut() {
        return periodeDebut;
    }

    public LocalDate periodeFin() {
        return periodeFin;
    }

    public Instant emiseLe() {
        return emiseLe;
    }
}
