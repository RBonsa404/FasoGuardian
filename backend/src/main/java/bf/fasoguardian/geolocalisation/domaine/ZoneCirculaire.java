package bf.fasoguardian.geolocalisation.domaine;

import java.util.UUID;

/** Zone définie par un centre et un rayon de 50 m à 5 km. */
public final class ZoneCirculaire extends SafeZone {

    public static final int RAYON_MINIMAL_M = 50;
    public static final int RAYON_MAXIMAL_M = 5000;

    private final Coordonnee centre;
    private final int rayonM;

    public ZoneCirculaire(UUID id, UUID enfantId, String nom, Categorie categorie, PlageHoraire plage, int toleranceS,
            Statut statut, long version, Coordonnee centre, int rayonM) {
        super(id, enfantId, nom, categorie, plage, toleranceS, statut, version);
        if (centre == null || rayonM < RAYON_MINIMAL_M || rayonM > RAYON_MAXIMAL_M) {
            throw new IllegalArgumentException("Le rayon d'une zone circulaire va de 50 m à 5 km");
        }
        this.centre = centre;
        this.rayonM = rayonM;
    }

    @Override
    public boolean contient(Coordonnee point) {
        return centre.distanceM(point) <= rayonM;
    }

    public Coordonnee centre() {
        return centre;
    }

    public int rayonM() {
        return rayonM;
    }
}
