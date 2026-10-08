package bf.fasoguardian.identite.domaine;

import java.util.regex.Pattern;

/**
 * Numéro de téléphone mobile burkinabè, normalisé au format E.164 ({@code +226XXXXXXXX}).
 * Identifiant de connexion des parents : il doit pouvoir recevoir un SMS, les lignes fixes
 * (préfixe 2) sont donc refusées.
 */
public record NumeroTelephone(String e164) {

    private static final String INDICATIF = "226";
    private static final Pattern MOBILE_NATIONAL = Pattern.compile("[0567]\\d{7}");

    public NumeroTelephone {
        if (e164 == null || !e164.startsWith("+" + INDICATIF)
                || !MOBILE_NATIONAL.matcher(e164.substring(4)).matches()) {
            throw new NumeroInvalideException();
        }
    }

    /** Accepte les saisies usuelles : « 70 12 34 56 », « +226 70123456 », « 00226-70-12-34-56 ». */
    public static NumeroTelephone depuisSaisie(String saisie) {
        if (saisie == null) {
            throw new NumeroInvalideException();
        }
        String chiffres = saisie.replaceAll("[\\s.\\-()]", "");
        if (chiffres.startsWith("+")) {
            chiffres = chiffres.substring(1);
        } else if (chiffres.startsWith("00")) {
            chiffres = chiffres.substring(2);
        } else {
            chiffres = INDICATIF + chiffres;
        }
        if (!chiffres.matches("\\d{11}")) {
            throw new NumeroInvalideException();
        }
        return new NumeroTelephone("+" + chiffres);
    }

    /** Forme masquée pour l'affichage et les journaux : {@code +226 •• •• •• 56}. */
    public String masque() {
        return "+226 •• •• •• " + e164.substring(10);
    }

    @Override
    public String toString() {
        return masque();
    }

    public static class NumeroInvalideException extends IllegalArgumentException {
        public NumeroInvalideException() {
            super("Numéro de téléphone mobile burkinabè attendu (8 chiffres)");
        }
    }
}
