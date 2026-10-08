package bf.fasoguardian.identite.domaine;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

/**
 * Agent du Collectif Dedsec. Ses rôles sont cumulables mais cloisonnés ; le second facteur TOTP est
 * obligatoire avant tout accès (US-ADM-001). Jeton d'accès de 10 minutes, session close après
 * 30 minutes d'inactivité (FG-DOC-06, tableau 16).
 */
@Entity
@DiscriminatorValue("AGENT")
public class AgentInterne extends Utilisateur {

    @Column
    private String identifiant;

    @Column
    private String roles;

    @Column(name = "totp_secret_chiffre")
    private byte[] totpSecretChiffre;

    @Column(name = "totp_actif", nullable = false)
    private boolean totpActif;

    @Column(name = "totp_dernier_pas", nullable = false)
    private long totpDernierPas;

    protected AgentInterne() {
    }

    public AgentInterne(String identifiant, Set<RoleInterne> roles, Instant maintenant) {
        super(null, null, StatutCompte.ACTIF, maintenant);
        if (roles == null || roles.isEmpty()) {
            throw new IllegalArgumentException("Un agent porte au moins un rôle");
        }
        this.identifiant = identifiant;
        this.roles = roles.stream().map(Enum::name).sorted().collect(Collectors.joining(","));
    }

    @Override
    public Set<String> roles() {
        return rolesInternes().stream().map(Enum::name).collect(Collectors.toUnmodifiableSet());
    }

    public Set<RoleInterne> rolesInternes() {
        return Arrays.stream(roles.split(",")).map(RoleInterne::valueOf)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(RoleInterne.class)));
    }

    @Override
    public Duration dureeJetonAcces() {
        return Duration.ofMinutes(10);
    }

    @Override
    public Duration dureeSession() {
        return Duration.ofMinutes(30);
    }

    public void enrolerTotp(byte[] secretChiffre) {
        this.totpSecretChiffre = secretChiffre;
        this.totpActif = false;
        this.totpDernierPas = 0;
    }

    /** Enregistre un code accepté ; le premier code valide active le second facteur. */
    public void totpAccepte(long pas) {
        this.totpDernierPas = pas;
        this.totpActif = true;
    }

    public String identifiant() {
        return identifiant;
    }

    public byte[] totpSecretChiffre() {
        return totpSecretChiffre;
    }

    public boolean totpActif() {
        return totpActif;
    }

    public long totpDernierPas() {
        return totpDernierPas;
    }
}
