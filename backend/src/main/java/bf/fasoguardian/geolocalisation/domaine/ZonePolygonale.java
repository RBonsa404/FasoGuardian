package bf.fasoguardian.geolocalisation.domaine;

import java.util.List;
import java.util.UUID;

/**
 * Zone de forme libre, de 3 à 20 sommets, non croisée. À l'échelle d'un quartier, le test d'appartenance se
 * fait dans le plan des coordonnées ; l'écart avec le calcul géodésique y est inférieur au mètre.
 */
public final class ZonePolygonale extends SafeZone {

    public static final int SOMMETS_MINIMUM = 3;
    public static final int SOMMETS_MAXIMUM = 20;
    /** Plus grande distance admise entre deux sommets : une zone reste à l'échelle d'un quartier. */
    public static final int ENVERGURE_MAXIMALE_M = 10_000;

    private final List<Coordonnee> sommets;

    public ZonePolygonale(UUID id, UUID enfantId, String nom, Categorie categorie, PlageHoraire plage, int toleranceS,
            Statut statut, long version, List<Coordonnee> sommets) {
        super(id, enfantId, nom, categorie, plage, toleranceS, statut, version);
        if (sommets == null || sommets.size() < SOMMETS_MINIMUM || sommets.size() > SOMMETS_MAXIMUM) {
            throw new IllegalArgumentException("Un polygone compte de 3 à 20 sommets");
        }
        this.sommets = List.copyOf(sommets);
        if (croise() || envergureM() > ENVERGURE_MAXIMALE_M || aireApprocheeM2() < 500) {
            throw new IllegalArgumentException("Le polygone est croisé, trop étendu ou trop petit");
        }
    }

    @Override
    public boolean contient(Coordonnee point) {
        boolean dedans = false;
        for (int i = 0, j = sommets.size() - 1; i < sommets.size(); j = i++) {
            Coordonnee a = sommets.get(i);
            Coordonnee b = sommets.get(j);
            if (surSegment(a, b, point)) {
                return true;
            }
            boolean chevauche = (a.latitude() > point.latitude()) != (b.latitude() > point.latitude());
            if (chevauche && point.longitude() < (b.longitude() - a.longitude()) * (point.latitude() - a.latitude())
                    / (b.latitude() - a.latitude()) + a.longitude()) {
                dedans = !dedans;
            }
        }
        return dedans;
    }

    public List<Coordonnee> sommets() {
        return sommets;
    }

    private double envergureM() {
        double maximum = 0;
        for (Coordonnee a : sommets) {
            for (Coordonnee b : sommets) {
                maximum = Math.max(maximum, a.distanceM(b));
            }
        }
        return maximum;
    }

    /** Aire par la formule du lacet, dans le plan local (suffisant pour écarter un polygone dégénéré). */
    private double aireApprocheeM2() {
        double metresParDegreLat = 111_320;
        double metresParDegreLon = 111_320 * Math.cos(Math.toRadians(sommets.get(0).latitude()));
        double somme = 0;
        for (int i = 0, j = sommets.size() - 1; i < sommets.size(); j = i++) {
            somme += (sommets.get(j).longitude() * metresParDegreLon) * (sommets.get(i).latitude() * metresParDegreLat)
                    - (sommets.get(i).longitude() * metresParDegreLon) * (sommets.get(j).latitude() * metresParDegreLat);
        }
        return Math.abs(somme) / 2;
    }

    /** Vrai si deux côtés non adjacents se coupent. */
    private boolean croise() {
        int n = sommets.size();
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                boolean adjacents = j == i + 1 || (i == 0 && j == n - 1);
                if (!adjacents && seCoupent(sommets.get(i), sommets.get((i + 1) % n), sommets.get(j),
                        sommets.get((j + 1) % n))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean seCoupent(Coordonnee a, Coordonnee b, Coordonnee c, Coordonnee d) {
        double d1 = orientation(c, d, a);
        double d2 = orientation(c, d, b);
        double d3 = orientation(a, b, c);
        double d4 = orientation(a, b, d);
        return ((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) && ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0));
    }

    private static double orientation(Coordonnee a, Coordonnee b, Coordonnee c) {
        return (b.longitude() - a.longitude()) * (c.latitude() - a.latitude())
                - (b.latitude() - a.latitude()) * (c.longitude() - a.longitude());
    }

    private static boolean surSegment(Coordonnee a, Coordonnee b, Coordonnee p) {
        double tolerance = 1e-9;
        return Math.abs(orientation(a, b, p)) < tolerance * tolerance
                && p.latitude() >= Math.min(a.latitude(), b.latitude()) - tolerance
                && p.latitude() <= Math.max(a.latitude(), b.latitude()) + tolerance
                && p.longitude() >= Math.min(a.longitude(), b.longitude()) - tolerance
                && p.longitude() <= Math.max(a.longitude(), b.longitude()) + tolerance;
    }
}
