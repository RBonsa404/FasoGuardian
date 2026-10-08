package bf.fasoguardian.geolocalisation.domaine;

/** Point WGS 84 en degrés décimaux. */
public record Coordonnee(double latitude, double longitude) {

    private static final double RAYON_TERRE_M = 6_371_008.8;

    public Coordonnee {
        if (!(latitude >= -90 && latitude <= 90) || !(longitude >= -180 && longitude <= 180)) {
            throw new IllegalArgumentException("Coordonnée hors limites");
        }
    }

    /** Distance orthodromique en mètres (formule de haversine). */
    public double distanceM(Coordonnee autre) {
        double phi1 = Math.toRadians(latitude);
        double phi2 = Math.toRadians(autre.latitude);
        double dPhi = phi2 - phi1;
        double dLambda = Math.toRadians(autre.longitude - longitude);
        double a = Math.sin(dPhi / 2) * Math.sin(dPhi / 2)
                + Math.cos(phi1) * Math.cos(phi2) * Math.sin(dLambda / 2) * Math.sin(dLambda / 2);
        return 2 * RAYON_TERRE_M * Math.asin(Math.min(1, Math.sqrt(a)));
    }
}
