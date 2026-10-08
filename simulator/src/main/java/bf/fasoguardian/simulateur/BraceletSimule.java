package bf.fasoguardian.simulateur;

import java.time.Instant;
import java.util.Locale;
import java.util.Random;

/**
 * État d'un bracelet simulé : position en marche aléatoire, batterie, signal et numéro de séquence.
 * Déterministe pour une graine donnée, afin de rejouer un scénario à l'identique.
 */
final class BraceletSimule {

    // Place de la Nation, Ouagadougou.
    static final double LATITUDE_DEPART = 12.3714;
    static final double LONGITUDE_DEPART = -1.5197;
    private static final double PAS_MAX_DEGRES = 0.0004;
    private static final double RAYON_MAX_DEGRES = 0.02;

    private final String identifiant;
    private final Random hasard;
    private double latitude;
    private double longitude;
    private double batterie;
    private long sequence;

    BraceletSimule(String identifiant, long graine) {
        this.identifiant = identifiant;
        this.hasard = new Random(graine ^ identifiant.hashCode());
        this.latitude = LATITUDE_DEPART + ecart(RAYON_MAX_DEGRES / 2);
        this.longitude = LONGITUDE_DEPART + ecart(RAYON_MAX_DEGRES / 2);
        this.batterie = 60 + hasard.nextInt(41);
    }

    String identifiant() {
        return identifiant;
    }

    long sequence() {
        return sequence;
    }

    double latitude() {
        return latitude;
    }

    double longitude() {
        return longitude;
    }

    int batterie() {
        return (int) Math.round(batterie);
    }

    /** Fait avancer le bracelet d'un intervalle et produit le message de télémétrie (FG-DOC-08 §8.2). */
    String prochaineTelemetrie(Instant mesure) {
        latitude = borner(latitude + ecart(PAS_MAX_DEGRES), LATITUDE_DEPART);
        longitude = borner(longitude + ecart(PAS_MAX_DEGRES), LONGITUDE_DEPART);
        batterie = Math.max(1, batterie - 0.05 - hasard.nextDouble() * 0.1);
        sequence++;
        int precisionMetres = 5 + hasard.nextInt(16);
        int signalDbm = -95 + hasard.nextInt(31);
        return String.format(Locale.ROOT,
                "{\"t\":%d,\"seq\":%d,\"lat\":%.5f,\"lon\":%.5f,\"acc\":%d,\"src\":\"gnss\",\"bat\":%d,\"rssi\":%d,\"mv\":1}",
                mesure.getEpochSecond(), sequence, latitude, longitude, precisionMetres, batterie(), signalDbm);
    }

    String etat(boolean enLigne) {
        return String.format(Locale.ROOT, "{\"online\":%b,\"fw\":\"sim-0.1.0\"}", enLigne);
    }

    private double ecart(double amplitude) {
        return (hasard.nextDouble() * 2 - 1) * amplitude;
    }

    private static double borner(double valeur, double centre) {
        return Math.max(centre - RAYON_MAX_DEGRES, Math.min(centre + RAYON_MAX_DEGRES, valeur));
    }
}
