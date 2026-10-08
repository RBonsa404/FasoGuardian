package bf.fasoguardian.identite.domaine;

import java.time.Instant;
import java.util.Set;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

/** Parent ou tuteur légal. Son compte n'est actif qu'après validation du dossier KYC (REQ-SYS-013). */
@Entity
@DiscriminatorValue("TUTEUR")
public class Tuteur extends Utilisateur {

    public static final String ROLE = "PARENT";

    protected Tuteur() {
    }

    public Tuteur(byte[] telephoneChiffre, String telephoneHash, Instant maintenant) {
        super(telephoneChiffre, telephoneHash, StatutCompte.EN_INSTRUCTION, maintenant);
    }

    @Override
    public Set<String> roles() {
        return Set.of(ROLE);
    }

    /** Appelé à la validation du dossier KYC. */
    public void activer() {
        if (statut() == StatutCompte.EN_INSTRUCTION) {
            changerStatut(StatutCompte.ACTIF);
        }
    }
}
