package bf.fasoguardian.identite.domaine;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;

/**
 * Code à six chiffres envoyé par SMS (inscription, réinitialisation, second facteur des actions sensibles).
 * Seule son empreinte est conservée ; il expire après 5 minutes, tolère 3 essais et ne sert qu'une fois.
 * Un nouvel envoi n'est possible qu'après 60 secondes (composant fg-otp du design).
 */
public final class CodeUsageUnique {

    public static final Duration VALIDITE = Duration.ofMinutes(5);
    public static final Duration DELAI_RENVOI = Duration.ofSeconds(60);
    public static final int ESSAIS_MAX = 3;

    private static final SecureRandom ALEA = new SecureRandom();

    public enum Resultat {
        VALIDE,
        INCORRECT,
        EXPIRE,
        EPUISE
    }

    /** Code en clair à transmettre par SMS, accompagné de l'objet à conserver. */
    public record Emission(String codeEnClair, CodeUsageUnique code) {
    }

    private final byte[] empreinte;
    private final Instant emisLe;
    private int essais;
    private boolean consomme;

    private CodeUsageUnique(byte[] empreinte, Instant emisLe, int essais, boolean consomme) {
        this.empreinte = empreinte;
        this.emisLe = emisLe;
        this.essais = essais;
        this.consomme = consomme;
    }

    public static Emission emettre(String contexte, Instant maintenant) {
        String code = String.format("%06d", ALEA.nextInt(1_000_000));
        return new Emission(code, new CodeUsageUnique(empreinteDe(contexte, code), maintenant, 0, false));
    }

    public static CodeUsageUnique reconstituer(byte[] empreinte, Instant emisLe, int essais, boolean consomme) {
        return new CodeUsageUnique(empreinte.clone(), emisLe, essais, consomme);
    }

    /**
     * Vérifie un code saisi. Le contexte (finalité et identifiant du compte) est lié à l'empreinte :
     * un code émis pour une action ne valide pas une autre action.
     */
    public Resultat verifier(String contexte, String saisie, Instant maintenant) {
        if (consomme || essais >= ESSAIS_MAX) {
            return Resultat.EPUISE;
        }
        if (maintenant.isAfter(emisLe.plus(VALIDITE))) {
            return Resultat.EXPIRE;
        }
        essais++;
        if (saisie != null && MessageDigest.isEqual(empreinte, empreinteDe(contexte, saisie.trim()))) {
            consomme = true;
            return Resultat.VALIDE;
        }
        return essais >= ESSAIS_MAX ? Resultat.EPUISE : Resultat.INCORRECT;
    }

    public boolean renvoiPossible(Instant maintenant) {
        return !maintenant.isBefore(emisLe.plus(DELAI_RENVOI));
    }

    public int essaisRestants() {
        return consomme ? 0 : ESSAIS_MAX - essais;
    }

    public byte[] empreinte() {
        return empreinte.clone();
    }

    public Instant emisLe() {
        return emisLe;
    }

    public int essais() {
        return essais;
    }

    public boolean consomme() {
        return consomme;
    }

    private static byte[] empreinteDe(String contexte, String code) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            sha.update(contexte.getBytes(StandardCharsets.UTF_8));
            sha.update((byte) 0);
            return sha.digest(code.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException erreur) {
            throw new IllegalStateException(erreur);
        }
    }
}
