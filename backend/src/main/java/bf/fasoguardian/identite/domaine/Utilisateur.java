package bf.fasoguardian.identite.domaine;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Compte authentifié. Le téléphone n'est conservé que chiffré, avec une empreinte pour la recherche exacte.
 * Après cinq échecs de connexion consécutifs, le compte est verrouillé quinze minutes (FG-DOC-06 §8.1).
 */
@Entity
@Table(schema = "identite", name = "utilisateur")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "type", length = 16)
public abstract class Utilisateur {

    public static final int ECHECS_AVANT_VERROUILLAGE = 5;
    public static final Duration DUREE_VERROUILLAGE = Duration.ofMinutes(15);

    @Id
    private UUID id;

    @Column(name = "telephone_chiffre")
    private byte[] telephoneChiffre;

    @Column(name = "telephone_hash")
    private String telephoneHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatutCompte statut;

    @Column(name = "mdp_argon2id")
    private String mdpArgon2id;

    @Column(name = "mdp_modifie_le")
    private Instant mdpModifieLe;

    @Column(name = "echecs_connexion", nullable = false)
    private int echecsConnexion;

    @Column(name = "verrouille_jusqu_a")
    private Instant verrouilleJusquA;

    @Column(name = "cree_le", nullable = false)
    private Instant creeLe;

    @Version
    private long version;

    protected Utilisateur() {
    }

    protected Utilisateur(byte[] telephoneChiffre, String telephoneHash, StatutCompte statut, Instant maintenant) {
        this.id = UUID.randomUUID();
        this.telephoneChiffre = telephoneChiffre;
        this.telephoneHash = telephoneHash;
        this.statut = statut;
        this.creeLe = maintenant;
    }

    /** Rôles portés par le jeton d'accès. */
    public abstract java.util.Set<String> roles();

    public void definirMotDePasse(String empreinteArgon2id, Instant maintenant) {
        this.mdpArgon2id = empreinteArgon2id;
        this.mdpModifieLe = maintenant;
        this.echecsConnexion = 0;
        this.verrouilleJusquA = null;
    }

    /** Un compte clos ne peut plus s'authentifier (FG-DOC-07, tableau 3). */
    public boolean peutSAuthentifier() {
        return statut != StatutCompte.CLOS && mdpArgon2id != null;
    }

    public boolean estVerrouille(Instant maintenant) {
        return verrouilleJusquA != null && maintenant.isBefore(verrouilleJusquA);
    }

    public void enregistrerEchecDeConnexion(Instant maintenant) {
        echecsConnexion++;
        if (echecsConnexion >= ECHECS_AVANT_VERROUILLAGE) {
            verrouilleJusquA = maintenant.plus(DUREE_VERROUILLAGE);
            echecsConnexion = 0;
        }
    }

    public void enregistrerConnexionReussie() {
        echecsConnexion = 0;
        verrouilleJusquA = null;
    }

    protected void changerStatut(StatutCompte nouveau) {
        this.statut = nouveau;
    }

    public UUID id() {
        return id;
    }

    public byte[] telephoneChiffre() {
        return telephoneChiffre;
    }

    public String telephoneHash() {
        return telephoneHash;
    }

    public StatutCompte statut() {
        return statut;
    }

    public String mdpArgon2id() {
        return mdpArgon2id;
    }

    public Instant creeLe() {
        return creeLe;
    }
}
