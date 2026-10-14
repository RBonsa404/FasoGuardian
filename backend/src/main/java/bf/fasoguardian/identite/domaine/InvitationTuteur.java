package bf.fasoguardian.identite.domaine;

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
 * Invitation d'un second tuteur (US-PAR-003). Elle ne donne aucun accès : l'invité ouvre avec elle son propre
 * dossier KYC, dont l'approbation crée le lien de tutelle. Le numéro de l'invité n'est conservé que tant
 * que l'invitation est ouverte.
 */
@Entity
@Table(schema = "identite", name = "invitation_tuteur")
public class InvitationTuteur {

    public static final Duration VALIDITE = Duration.ofDays(7);

    public enum Statut {
        /** Envoyée, en attente du dossier de l'invité. */
        EN_ATTENTE,
        /** L'invité a ouvert son dossier KYC : l'invitation n'expire plus, la décision de l'agent la clôt. */
        EN_VERIFICATION,
        ACCEPTEE,
        ANNULEE,
        EXPIREE;

        public boolean ouverte() {
            return this == EN_ATTENTE || this == EN_VERIFICATION;
        }
    }

    @Id
    private UUID id;

    @Column(name = "enfant_id", nullable = false)
    private UUID enfantId;

    @Column(name = "invitant_id", nullable = false)
    private UUID invitantId;

    @Column(name = "telephone_chiffre")
    private byte[] telephoneChiffre;

    @Column(name = "telephone_hash")
    private String telephoneHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Statut statut;

    @Column(name = "cree_le", nullable = false)
    private Instant creeLe;

    @Column(name = "expire_le", nullable = false)
    private Instant expireLe;

    @Column(name = "close_le")
    private Instant closeLe;

    @Version
    private long version;

    protected InvitationTuteur() {
    }

    public InvitationTuteur(UUID enfantId, UUID invitantId, byte[] telephoneChiffre, String telephoneHash, Instant maintenant) {
        this.id = UUID.randomUUID();
        this.enfantId = enfantId;
        this.invitantId = invitantId;
        this.telephoneChiffre = telephoneChiffre;
        this.telephoneHash = telephoneHash;
        this.statut = Statut.EN_ATTENTE;
        this.creeLe = maintenant;
        this.expireLe = maintenant.plus(VALIDITE);
    }

    /** Une invitation en attente au-delà de sa validité est expirée ; un dossier ouvert la fait durer jusqu'à la décision. */
    public boolean expiree(Instant maintenant) {
        return statut == Statut.EN_ATTENTE && !maintenant.isBefore(expireLe);
    }

    public boolean ouverte(Instant maintenant) {
        return statut.ouverte() && !expiree(maintenant);
    }

    public void ouvrirDossier() {
        exiger(statut == Statut.EN_ATTENTE);
        statut = Statut.EN_VERIFICATION;
    }

    public void accepter(Instant maintenant) {
        exiger(statut == Statut.EN_VERIFICATION);
        clore(Statut.ACCEPTEE, maintenant);
    }

    public void annuler(Instant maintenant) {
        exiger(statut.ouverte());
        clore(Statut.ANNULEE, maintenant);
    }

    public void expirer(Instant maintenant) {
        exiger(expiree(maintenant));
        clore(Statut.EXPIREE, maintenant);
    }

    private void clore(Statut fin, Instant maintenant) {
        statut = fin;
        closeLe = maintenant;
        telephoneChiffre = null;
        telephoneHash = null;
    }

    private void exiger(boolean condition) {
        if (!condition) {
            throw new IllegalStateException("Transition interdite depuis " + statut);
        }
    }

    public UUID id() {
        return id;
    }

    public UUID enfantId() {
        return enfantId;
    }

    public UUID invitantId() {
        return invitantId;
    }

    public byte[] telephoneChiffre() {
        return telephoneChiffre;
    }

    public String telephoneHash() {
        return telephoneHash;
    }

    public Statut statut() {
        return statut;
    }

    public Instant expireLe() {
        return expireLe;
    }
}
