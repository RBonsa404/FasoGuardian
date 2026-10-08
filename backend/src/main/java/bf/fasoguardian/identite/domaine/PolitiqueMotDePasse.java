package bf.fasoguardian.identite.domaine;

import java.util.Optional;

/**
 * Règles de choix d'un mot de passe : 10 caractères minimum dont un chiffre (écran 10 du design),
 * 128 au maximum, sans expiration périodique (FG-DOC-07, tableau 3). Voir docs/adr/0007.
 */
public final class PolitiqueMotDePasse {

    public static final int LONGUEUR_MIN = 10;
    public static final int LONGUEUR_MAX = 128;

    private PolitiqueMotDePasse() {
    }

    public enum Refus {
        TROP_COURT,
        TROP_LONG,
        SANS_CHIFFRE,
        CONTIENT_LE_TELEPHONE
    }

    /** Renvoie le motif de refus, ou vide si le mot de passe est acceptable. */
    public static Optional<Refus> verifier(String motDePasse, NumeroTelephone telephone) {
        if (motDePasse == null || motDePasse.codePointCount(0, motDePasse.length()) < LONGUEUR_MIN) {
            return Optional.of(Refus.TROP_COURT);
        }
        if (motDePasse.length() > LONGUEUR_MAX) {
            return Optional.of(Refus.TROP_LONG);
        }
        if (motDePasse.chars().noneMatch(Character::isDigit)) {
            return Optional.of(Refus.SANS_CHIFFRE);
        }
        if (telephone != null && motDePasse.replaceAll("\\D", "").contains(telephone.e164().substring(4))) {
            return Optional.of(Refus.CONTIENT_LE_TELEPHONE);
        }
        return Optional.empty();
    }
}
