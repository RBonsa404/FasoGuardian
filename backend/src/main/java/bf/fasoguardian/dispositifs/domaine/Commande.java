package bf.fasoguardian.dispositifs.domaine;

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

/** Commande signée envoyée à un bracelet, suivie jusqu'à son accusé ou son expiration. */
@Entity
@Table(schema = "dispositifs", name = "commande")
public class Commande {

    /** Au-delà, une commande restée sans accusé n'est plus réémise et le bracelet la refuserait. */
    public static final Duration VALIDITE = Duration.ofMinutes(15);

    public enum Type {
        MODE_ALERTE("alert"),
        CONFIGURATION("cfg"),
        RETRAIT("rm"),
        LOCALISER("loc");

        private final String code;

        Type(String code) {
            this.code = code;
        }

        /** Code court du protocole (docs/protocole-bracelet.md). */
        public String code() {
            return code;
        }
    }

    public enum Statut {
        EMISE,
        ACCUSEE,
        /** Le bracelet a reçu la commande et l'a rejetée. */
        REFUSEE,
        EXPIREE
    }

    @Id
    private UUID id;

    @Column(name = "bracelet_id", nullable = false)
    private UUID braceletId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Type type;

    @Column(nullable = false)
    private long sequence;

    @Column(nullable = false)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Statut statut;

    @Column(name = "emise_le", nullable = false)
    private Instant emiseLe;

    @Column(name = "expire_le", nullable = false)
    private Instant expireLe;

    @Column(name = "accusee_le")
    private Instant accuseeLe;

    @Version
    private long version;

    protected Commande() {
    }

    public Commande(UUID id, UUID braceletId, Type type, long sequence, String message, Instant maintenant) {
        this.id = id;
        this.braceletId = braceletId;
        this.type = type;
        this.sequence = sequence;
        this.message = message;
        this.statut = Statut.EMISE;
        this.emiseLe = maintenant;
        this.expireLe = maintenant.plus(VALIDITE);
    }

    /** @return {@code false} si la commande n'attendait plus d'accusé (déjà accusée, expirée) */
    public boolean accuser(boolean executee, Instant maintenant) {
        if (statut != Statut.EMISE) {
            return false;
        }
        statut = executee ? Statut.ACCUSEE : Statut.REFUSEE;
        accuseeLe = maintenant;
        return true;
    }

    public void expirer() {
        if (statut == Statut.EMISE) {
            statut = Statut.EXPIREE;
        }
    }

    public UUID id() {
        return id;
    }

    public UUID braceletId() {
        return braceletId;
    }

    public Type type() {
        return type;
    }

    public long sequence() {
        return sequence;
    }

    public String message() {
        return message;
    }

    public Statut statut() {
        return statut;
    }

    public Instant expireLe() {
        return expireLe;
    }
}
