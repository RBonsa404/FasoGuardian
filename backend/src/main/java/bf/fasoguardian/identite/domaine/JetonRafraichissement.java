package bf.fasoguardian.identite.domaine;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Jeton de rafraîchissement rotatif (30 jours pour un parent, 30 minutes d'inactivité pour un agent). Seule son empreinte est conservée. Chaque échange produit un
 * nouveau jeton de la même famille ; présenter un jeton déjà échangé signale un vol et révoque la famille.
 */
@Entity
@Table(schema = "identite", name = "jeton_rafraichissement")
public class JetonRafraichissement {

    @Id
    private UUID id;

    @Column(name = "utilisateur_id", nullable = false)
    private UUID utilisateurId;

    @Column(nullable = false)
    private UUID famille;

    @Column(nullable = false)
    private String empreinte;

    @Column(name = "emis_le", nullable = false)
    private Instant emisLe;

    @Column(name = "expire_le", nullable = false)
    private Instant expireLe;

    @Column(name = "utilise_le")
    private Instant utiliseLe;

    @Column(name = "revoque_le")
    private Instant revoqueLe;

    protected JetonRafraichissement() {
    }

    private JetonRafraichissement(UUID utilisateurId, UUID famille, String empreinte, Instant maintenant,
            Duration duree) {
        this.id = UUID.randomUUID();
        this.utilisateurId = utilisateurId;
        this.famille = famille;
        this.empreinte = empreinte;
        this.emisLe = maintenant;
        this.expireLe = maintenant.plus(duree);
    }

    public static JetonRafraichissement nouvelleFamille(UUID utilisateurId, String empreinte, Instant maintenant,
            Duration duree) {
        return new JetonRafraichissement(utilisateurId, UUID.randomUUID(), empreinte, maintenant, duree);
    }

    public JetonRafraichissement successeur(String empreinteSuivante, Instant maintenant, Duration duree) {
        this.utiliseLe = maintenant;
        return new JetonRafraichissement(utilisateurId, famille, empreinteSuivante, maintenant, duree);
    }

    public boolean dejaUtiliseOuRevoque() {
        return utiliseLe != null || revoqueLe != null;
    }

    public boolean expire(Instant maintenant) {
        return !maintenant.isBefore(expireLe);
    }

    public void revoquer(Instant maintenant) {
        if (revoqueLe == null) {
            revoqueLe = maintenant;
        }
    }

    public UUID utilisateurId() {
        return utilisateurId;
    }

    public UUID famille() {
        return famille;
    }

    public Instant expireLe() {
        return expireLe;
    }
}
