package bf.fasoguardian.identite.domaine;

/** Cycle de vie d'un compte : un parent reste « en instruction » tant que son dossier KYC n'est pas validé. */
public enum StatutCompte {
    EN_INSTRUCTION,
    ACTIF,
    SUSPENDU,
    CLOS
}
