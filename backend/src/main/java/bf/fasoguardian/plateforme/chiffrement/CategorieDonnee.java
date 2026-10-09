package bf.fasoguardian.plateforme.chiffrement;

/** Catégories de données chiffrées au niveau applicatif, chacune avec sa propre clé (FG-DOC-06 §8.3). */
public enum CategorieDonnee {
    TELEPHONE,
    PIECE_KYC,
    SANTE,
    SECRET_MFA,
    IMEI,
    PROFIL_ENFANT,
    /** Dossiers de signalement générés pour les forces de sécurité. */
    SIGNALEMENT
}
