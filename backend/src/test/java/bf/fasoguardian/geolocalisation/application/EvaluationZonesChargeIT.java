package bf.fasoguardian.geolocalisation.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.SplittableRandom;
import java.util.UUID;

import bf.fasoguardian.TestIntegration;
import bf.fasoguardian.telemetrie.PositionRecue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Exigence de performance (FG-DOC-04, FG-DOC-05) : évaluation d'appartenance à une Safe Zone en moins de 50 ms au
 * 95e centile pour 10 000 bracelets actifs. Chaque position est traitée comme en production : verrou de l'enfant,
 * zones du cache, appartenance, lecture et écriture du suivi, dans sa propre transaction.
 */
class EvaluationZonesChargeIT extends TestIntegration {

    private static final int ENFANTS = 10_000;
    private static final int ECHAUFFEMENT = 500;
    private static final int MESURES = 2_000;
    private static final double LAT = 12.3714;
    private static final double LON = -1.5197;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    Surveillance surveillance;

    @Autowired
    TransactionTemplate transactions;

    private final String lot = "charge-" + UUID.randomUUID().toString().substring(0, 8);

    @AfterEach
    void nettoyer() {
        jdbc.update("DELETE FROM geolocalisation.safe_zone WHERE nom = ?", lot);
    }

    @Test
    void auQuatreVingtQuinziemeCentileUnePositionEstEvalueeEnMoinsDeCinquanteMillisecondes() {
        // Deux zones actives toute la semaine par enfant : un cercle de 200 m et un polygone, répartis sur la ville.
        jdbc.update("""
                INSERT INTO geolocalisation.safe_zone (id, enfant_id, type, nom, categorie, centre, rayon_m, polygone, plage,
                        tolerance_s, statut, cree_le, modifie_le)
                SELECT gen_random_uuid(), e.id, 'CERCLE', ?, 'MAISON',
                       ST_SetSRID(ST_MakePoint(? + (n % 100) * 0.002, ? + (n / 100) * 0.002), 4326)::geography, 200, NULL,
                       '{"jours":[1,2,3,4,5,6,7],"debut":"00:00","fin":"00:00"}'::jsonb, 300, 'ACTIVE', now(), now()
                FROM (SELECT gen_random_uuid() AS id, n FROM generate_series(0, ? - 1) AS n) e
                """, lot, LON, LAT, ENFANTS);
        jdbc.update("""
                INSERT INTO geolocalisation.safe_zone (id, enfant_id, type, nom, categorie, centre, rayon_m, polygone, plage,
                        tolerance_s, statut, cree_le, modifie_le)
                SELECT gen_random_uuid(), z.enfant_id, 'POLYGONE', ?, 'ECOLE', NULL, NULL,
                       ST_Buffer(z.centre::geometry, 0.003, 'quad_segs=2')::geography,
                       '{"jours":[1,2,3,4,5],"debut":"07:00","fin":"17:30"}'::jsonb, 300, 'ACTIVE', now(), now()
                FROM geolocalisation.safe_zone z WHERE z.nom = ? AND z.type = 'CERCLE'
                """, lot, lot);
        List<Object[]> enfants = jdbc.query("SELECT enfant_id, ST_Y(centre::geometry), ST_X(centre::geometry) FROM geolocalisation.safe_zone"
                + " WHERE nom = ? AND type = 'CERCLE'", (ligne, i) -> new Object[] {ligne.getObject(1, UUID.class), ligne.getDouble(2),
                        ligne.getDouble(3)}, lot);
        assertThat(enfants).hasSize(ENFANTS);

        SplittableRandom hasard = new SplittableRandom(42);
        for (int i = 0; i < ECHAUFFEMENT; i++) {
            traiter(enfants.get(hasard.nextInt(ENFANTS)), hasard);
        }
        long[] durees = new long[MESURES];
        for (int i = 0; i < MESURES; i++) {
            Object[] enfant = enfants.get(hasard.nextInt(ENFANTS));
            long debut = System.nanoTime();
            traiter(enfant, hasard);
            durees[i] = System.nanoTime() - debut;
        }
        Arrays.sort(durees);
        double p95 = durees[(int) Math.ceil(0.95 * MESURES) - 1] / 1_000_000.0;
        double mediane = durees[MESURES / 2] / 1_000_000.0;
        System.out.printf("Évaluation des Safe Zones, %d enfants : médiane %.2f ms, 95e centile %.2f ms%n", ENFANTS, mediane, p95);
        assertThat(p95).as("95e centile en millisecondes").isLessThan(50.0);
    }

    /** Une position tantôt dans la zone, tantôt à 2 km : le suivi est relu et réécrit à chaque fois. */
    private void traiter(Object[] enfant, SplittableRandom hasard) {
        boolean dedans = hasard.nextBoolean();
        double lat = (double) enfant[1] + (dedans ? 0.0003 : 0.02);
        double lon = (double) enfant[2];
        PositionRecue position = new PositionRecue(UUID.randomUUID(), (UUID) enfant[0], lat, lon, 15, Instant.now());
        transactions.executeWithoutResult(etat -> surveillance.surPositionRecue(position));
    }
}
