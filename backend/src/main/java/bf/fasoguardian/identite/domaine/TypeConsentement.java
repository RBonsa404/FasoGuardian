package bf.fasoguardian.identite.domaine;

/** Consentements recueillis à l'inscription (écran 10 du design). Les deux premiers sont obligatoires. */
public enum TypeConsentement {
    CONDITIONS_GENERALES(true),
    DONNEES_ENFANT(true),
    PARTAGE_FORCES_SECURITE(false),
    COMMUNICATION_SMS(false);

    private final boolean obligatoire;

    TypeConsentement(boolean obligatoire) {
        this.obligatoire = obligatoire;
    }

    public boolean obligatoire() {
        return obligatoire;
    }
}
