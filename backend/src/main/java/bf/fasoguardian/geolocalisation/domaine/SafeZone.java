package bf.fasoguardian.geolocalisation.domaine;

import java.util.UUID;

/**
 * Zone dans laquelle l'enfant est attendu pendant une plage horaire (FG-DOC-07 §4.4). Chaque forme définit
 * {@link #contient} ; la suspension conserve toute la configuration.
 */
public abstract sealed class SafeZone permits ZoneCirculaire, ZonePolygonale {

    public static final int TOLERANCE_MAXIMALE_S = 3600;
    public static final int LONGUEUR_NOM = 40;

    public enum Categorie {
        ECOLE,
        MAISON,
        FAMILLE,
        CULTE,
        AUTRE
    }

    public enum Statut {
        ACTIVE,
        SUSPENDUE
    }

    private final UUID id;
    private final UUID enfantId;
    private final String nom;
    private final Categorie categorie;
    private final PlageHoraire plage;
    private final int toleranceS;
    private final Statut statut;
    private final long version;

    protected SafeZone(UUID id, UUID enfantId, String nom, Categorie categorie, PlageHoraire plage, int toleranceS,
            Statut statut, long version) {
        if (nom == null || nom.isBlank() || nom.length() > LONGUEUR_NOM) {
            throw new IllegalArgumentException("Le nom de la zone compte de 1 à 40 caractères");
        }
        if (toleranceS < 0 || toleranceS > TOLERANCE_MAXIMALE_S) {
            throw new IllegalArgumentException("Le délai de tolérance va de 0 à 60 minutes");
        }
        this.id = id;
        this.enfantId = enfantId;
        this.nom = nom.strip();
        this.categorie = categorie;
        this.plage = plage;
        this.toleranceS = toleranceS;
        this.statut = statut;
        this.version = version;
    }

    /** Vrai si le point est dans la zone, bord compris. */
    public abstract boolean contient(Coordonnee point);

    public UUID id() {
        return id;
    }

    public UUID enfantId() {
        return enfantId;
    }

    public String nom() {
        return nom;
    }

    public Categorie categorie() {
        return categorie;
    }

    public PlageHoraire plage() {
        return plage;
    }

    public int toleranceS() {
        return toleranceS;
    }

    public Statut statut() {
        return statut;
    }

    public boolean active() {
        return statut == Statut.ACTIVE;
    }

    public long version() {
        return version;
    }
}
