package bf.fasoguardian.notifications.domaine;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Abonnement d'un navigateur : où livrer et avec quelles clés chiffrer (RFC 8030, RFC 8291). */
@Entity
@Table(schema = "notifications", name = "abonnement_push")
public class AbonnementPush {

    /** Au-delà, l'abonnement le plus ancien du destinataire est retiré. */
    public static final int MAXIMUM_PAR_DESTINATAIRE = 5;

    @Id
    private UUID id;

    @Column(name = "destinataire_id", nullable = false)
    private UUID destinataireId;

    @Column(name = "point_de_livraison", nullable = false)
    private String pointDeLivraison;

    @Column(name = "cle_p256dh", nullable = false)
    private String cleP256dh;

    @Column(name = "secret_auth", nullable = false)
    private String secretAuth;

    @Column(name = "cree_le", nullable = false)
    private Instant creeLe;

    @Column(name = "dernier_succes_le")
    private Instant dernierSuccesLe;

    protected AbonnementPush() {
    }

    public AbonnementPush(UUID destinataireId, String pointDeLivraison, String cleP256dh, String secretAuth, Instant maintenant) {
        this.id = UUID.randomUUID();
        this.destinataireId = destinataireId;
        this.pointDeLivraison = pointDeLivraison;
        this.cleP256dh = cleP256dh;
        this.secretAuth = secretAuth;
        this.creeLe = maintenant;
    }

    /** Le même navigateur se réabonne, éventuellement sous un autre compte. */
    public void remplacer(UUID nouveauDestinataire, String nouvelleCle, String nouveauSecret, Instant maintenant) {
        this.destinataireId = nouveauDestinataire;
        this.cleP256dh = nouvelleCle;
        this.secretAuth = nouveauSecret;
        this.creeLe = maintenant;
    }

    public void noterSucces(Instant maintenant) {
        dernierSuccesLe = maintenant;
    }

    public UUID destinataireId() {
        return destinataireId;
    }

    public String pointDeLivraison() {
        return pointDeLivraison;
    }

    public String cleP256dh() {
        return cleP256dh;
    }

    public String secretAuth() {
        return secretAuth;
    }
}
