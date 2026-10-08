package bf.fasoguardian.dispositifs.domaine;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.Optional;

/**
 * Code d'appairage imprimé sur la carte d'activation, distinct du QR gravé (US-PAR-014). Sept caractères
 * d'un alphabet sans signe ambigu (ni 0, O, 1, I, L), présentés « K7Q4-M2X ».
 */
public final class CodeAppairage {

    public static final int LONGUEUR = 7;
    private static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";

    private CodeAppairage() {
    }

    public static String generer(SecureRandom alea) {
        StringBuilder code = new StringBuilder(LONGUEUR);
        for (int i = 0; i < LONGUEUR; i++) {
            code.append(ALPHABET.charAt(alea.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }

    /** Forme canonique d'une saisie (majuscules, sans tiret ni espace), ou vide si elle ne peut pas être un code. */
    public static Optional<String> normaliser(String saisie) {
        if (saisie == null) {
            return Optional.empty();
        }
        String code = saisie.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
        if (code.length() != LONGUEUR || !code.chars().allMatch(c -> ALPHABET.indexOf(c) >= 0)) {
            return Optional.empty();
        }
        return Optional.of(code);
    }

    /** Présentation de la carte d'activation : quatre caractères, un tiret, trois caractères. */
    public static String presenter(String code) {
        return code.substring(0, 4) + "-" + code.substring(4);
    }
}
