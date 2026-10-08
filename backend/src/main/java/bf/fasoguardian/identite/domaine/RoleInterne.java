package bf.fasoguardian.identite.domaine;

/** Rôles des agents internes, aux périmètres cloisonnés (FG-DOC-06, tableau 17). */
public enum RoleInterne {
    /** Instruction des dossiers KYC : identité et pièces, jamais les positions ni la santé. */
    KYC,
    /** Support : coordonnées des parents, jamais les pièces ni les positions. */
    SUPPORT,
    /** SAV et points relais : état des bracelets seulement. */
    SAV,
    /** Administration : habilitations, journal d'audit, conformité, supervision. */
    ADMIN,
    /** Forces de sécurité : dossiers de signalement qui leur sont transmis. */
    FDS
}
