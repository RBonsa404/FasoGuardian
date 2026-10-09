package bf.fasoguardian.abonnements.domaine;

/** Portefeuilles mobile money couverts par l'agrégateur (FG-DOC-06 §6.7). */
public enum Moyen {
    ORANGE_MONEY("Orange Money"),
    MOOV_MONEY("Moov Money");

    private final String libelle;

    Moyen(String libelle) {
        this.libelle = libelle;
    }

    public String libelle() {
        return libelle;
    }
}
