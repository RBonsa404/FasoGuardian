package bf.fasoguardian.abonnements.domaine;

import java.time.Duration;
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
 * Transaction mobile money (FG-DOC-07 §4.5). Elle n'est tenue pour payée qu'à la confirmation signée de
 * l'agrégateur, jamais sur la déclaration du client.
 */
@Entity
@Table(schema = "abonnements", name = "paiement")
public class Paiement {

    /** Durée pendant laquelle le parent peut valider la demande sur son téléphone. */
    public static final Duration VALIDITE = Duration.ofMinutes(2);
    /** Au-delà, une demande restée sans réponse de l'agrégateur est tenue pour expirée. */
    public static final Duration DELAI_D_EXPIRATION = Duration.ofMinutes(10);

    public enum Statut {
        INITIE,
        CONFIRME,
        ECHOUE,
        EXPIRE
    }

    @Id
    private UUID id;

    @Column(name = "abonnement_id", nullable = false)
    private UUID abonnementId;

    @Column(name = "offre_code", nullable = false)
    private String offreCode;

    @Column(name = "montant_fcfa", nullable = false)
    private int montantFcfa;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Moyen moyen;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Statut statut;

    @Column(name = "reference_operateur")
    private String referenceOperateur;

    @Column(name = "cle_idempotence", nullable = false)
    private String cleIdempotence;

    @Column(name = "motif_echec")
    private String motifEchec;

    @Column(name = "initie_le", nullable = false)
    private Instant initieLe;

    @Column(name = "conclu_le")
    private Instant concluLe;

    @Version
    private long version;

    protected Paiement() {
    }

    public Paiement(UUID abonnementId, String offreCode, int montantFcfa, Moyen moyen, String cleIdempotence, Instant maintenant) {
        this.id = UUID.randomUUID();
        this.abonnementId = abonnementId;
        this.offreCode = offreCode;
        this.montantFcfa = montantFcfa;
        this.moyen = moyen;
        this.cleIdempotence = cleIdempotence;
        this.statut = Statut.INITIE;
        this.initieLe = maintenant;
    }

    public void noterReference(String reference) {
        this.referenceOperateur = reference;
    }

    /**
     * Confirmation de l'agrégateur. Elle vaut même pour une demande que la plateforme avait tenue pour expirée :
     * si l'argent a été débité, l'abonnement doit être servi.
     *
     * @return {@code false} si le paiement était déjà confirmé (confirmation reçue deux fois)
     */
    public boolean confirmer(Instant maintenant) {
        if (statut == Statut.CONFIRME) {
            return false;
        }
        statut = Statut.CONFIRME;
        motifEchec = null;
        concluLe = maintenant;
        return true;
    }

    /** @return {@code false} si le paiement n'attendait plus de réponse */
    public boolean echouer(String motif, Instant maintenant) {
        if (statut != Statut.INITIE) {
            return false;
        }
        statut = Statut.ECHOUE;
        motifEchec = motif == null || motif.isBlank() ? null : motif.strip().substring(0, Math.min(120, motif.strip().length()));
        concluLe = maintenant;
        return true;
    }

    public void expirer(Instant maintenant) {
        if (statut == Statut.INITIE) {
            statut = Statut.EXPIRE;
            concluLe = maintenant;
        }
    }

    public UUID id() {
        return id;
    }

    public UUID abonnementId() {
        return abonnementId;
    }

    public String offreCode() {
        return offreCode;
    }

    public int montantFcfa() {
        return montantFcfa;
    }

    public Moyen moyen() {
        return moyen;
    }

    public Statut statut() {
        return statut;
    }

    public String motifEchec() {
        return motifEchec;
    }

    public String referenceOperateur() {
        return referenceOperateur;
    }

    public Instant initieLe() {
        return initieLe;
    }
}
