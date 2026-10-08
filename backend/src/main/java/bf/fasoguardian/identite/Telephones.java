package bf.fasoguardian.identite;

import bf.fasoguardian.identite.domaine.NumeroTelephone;

/** Normalisation des numéros de téléphone mobile burkinabè, à l'usage des autres modules. */
public final class Telephones {

    private Telephones() {
    }

    /**
     * Normalise une saisie usuelle (« 70 12 34 56 », « +226 70123456 ») au format E.164.
     *
     * @throws IllegalArgumentException si la saisie n'est pas un numéro mobile burkinabè
     */
    public static String normaliserE164(String saisie) {
        return NumeroTelephone.depuisSaisie(saisie).e164();
    }
}
