package bf.fasoguardian.identite.application;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Ports du module identite vers les mécanismes techniques (hachage, jetons signés). */
public final class Ports {

    private Ports() {
    }

    public interface HacheurMotDePasse {

        String hacher(String motDePasse);

        boolean correspond(String motDePasse, String empreinte);
    }

    public interface EmetteurJetons {

        /** Jeton d'accès de courte durée (15 minutes pour un parent). */
        JetonAcces acces(UUID utilisateurId, Set<String> roles, Instant maintenant);

        /** Preuve, valable 15 minutes, que le numéro a été vérifié par SMS pendant l'inscription. */
        String preuveTelephone(String telephoneE164, Instant maintenant);

        Optional<String> lirePreuveTelephone(String jeton);
    }

    public record JetonAcces(String valeur, long expireDansSecondes) {
    }
}
