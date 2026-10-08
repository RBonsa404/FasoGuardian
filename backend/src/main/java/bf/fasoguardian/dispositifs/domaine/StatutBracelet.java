package bf.fasoguardian.dispositifs.domaine;

/** Cycle de vie d'un bracelet dans le parc (US-SAV-002). */
public enum StatutBracelet {
    /** Préparé à l'atelier, prêt à être remis : son code d'appairage est valable. */
    EN_STOCK,
    /** Appairé à un enfant et configuré (US-PAR-013). */
    ACTIF,
    /** Déclaré perdu : la page publique est désactivée, le suivi continue 72 h. */
    PERDU,
    /** Déclaré volé : page publique désactivée et certificat révoqué. */
    VOLE,
    /** Retourné ou en panne, entre les mains du service après-vente. */
    EN_SAV,
    /** Retiré définitivement du parc. */
    REFORME
}
