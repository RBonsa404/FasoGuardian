package bf.fasoguardian.geolocalisation.domaine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import bf.fasoguardian.geolocalisation.domaine.SafeZone.Categorie;
import bf.fasoguardian.geolocalisation.domaine.SafeZone.Statut;
import bf.fasoguardian.geolocalisation.domaine.SuiviZone.Constat;
import bf.fasoguardian.geolocalisation.domaine.SuiviZone.Evaluation;
import org.junit.jupiter.api.Test;

/** Règles des Safe Zones : plage horaire, appartenance, délai de tolérance (US-PAR-007, US-ENF-001). */
class ReglesZonesTest {

    private static final Coordonnee ECOLE = new Coordonnee(12.3714, -1.5197);
    private static final PlageHoraire SEMAINE = new PlageHoraire(
            EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), LocalTime.of(7, 0), LocalTime.of(17, 30));
    private static final Instant T0 = Instant.parse("2026-10-08T10:00:00Z");
    private static final Duration CINQ_MINUTES = Duration.ofMinutes(5);

    @Test
    void laPlageCouvreSesJoursEtSesHeuresDebutComprisFinExclue() {
        // Le 8 octobre 2026 est un jeudi.
        assertThat(SEMAINE.contient(LocalDateTime.parse("2026-10-08T07:00:00"))).isTrue();
        assertThat(SEMAINE.contient(LocalDateTime.parse("2026-10-08T17:29:59"))).isTrue();
        assertThat(SEMAINE.contient(LocalDateTime.parse("2026-10-08T17:30:00"))).isFalse();
        assertThat(SEMAINE.contient(LocalDateTime.parse("2026-10-08T06:59:59"))).isFalse();
        assertThat(SEMAINE.contient(LocalDateTime.parse("2026-10-10T10:00:00"))).as("samedi").isFalse();
    }

    @Test
    void unePlageQuiChevaucheMinuitAppartientAuJourOuElleCommence() {
        PlageHoraire nuitDuVendredi = new PlageHoraire(Set.of(DayOfWeek.FRIDAY), LocalTime.of(20, 0), LocalTime.of(6, 30));
        assertThat(nuitDuVendredi.contient(LocalDateTime.parse("2026-10-09T23:00:00"))).as("vendredi soir").isTrue();
        assertThat(nuitDuVendredi.contient(LocalDateTime.parse("2026-10-10T05:00:00"))).as("samedi matin").isTrue();
        assertThat(nuitDuVendredi.contient(LocalDateTime.parse("2026-10-10T06:30:00"))).isFalse();
        assertThat(nuitDuVendredi.contient(LocalDateTime.parse("2026-10-09T05:00:00"))).as("vendredi matin").isFalse();
        assertThat(nuitDuVendredi.contient(LocalDateTime.parse("2026-10-10T23:00:00"))).as("samedi soir").isFalse();

        PlageHoraire touteLaJournee = new PlageHoraire(Set.of(DayOfWeek.SUNDAY), LocalTime.MIDNIGHT, LocalTime.MIDNIGHT);
        assertThat(touteLaJournee.journeeEntiere()).isTrue();
        assertThat(touteLaJournee.contient(LocalDateTime.parse("2026-10-11T00:00:00"))).isTrue();
        assertThat(touteLaJournee.contient(LocalDateTime.parse("2026-10-11T23:59:59"))).isTrue();
        assertThat(touteLaJournee.contient(LocalDateTime.parse("2026-10-12T00:00:00"))).isFalse();
        assertThatThrownBy(() -> new PlageHoraire(Set.of(), LocalTime.NOON, LocalTime.MIDNIGHT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void uneZoneCirculaireContientLesPointsJusquASonRayon() {
        ZoneCirculaire zone = cercle(220);
        // Un degré de latitude vaut environ 111,2 km : 0,0018° ≈ 200 m, 0,0022° ≈ 245 m.
        assertThat(ECOLE.distanceM(new Coordonnee(12.3732, -1.5197))).isCloseTo(200, within(1.0));
        assertThat(zone.contient(ECOLE)).isTrue();
        assertThat(zone.contient(new Coordonnee(12.3732, -1.5197))).isTrue();
        assertThat(zone.contient(new Coordonnee(12.3736, -1.5197))).isFalse();
        assertThatThrownBy(() -> cercle(49)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> cercle(5001)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void uneZonePolygonaleContientSonInterieurEtSonBordEtRefuseUnTraceCroise() {
        ZonePolygonale maison = polygone(List.of(new Coordonnee(12.300, -1.500), new Coordonnee(12.300, -1.498),
                new Coordonnee(12.302, -1.498), new Coordonnee(12.302, -1.500)));
        assertThat(maison.contient(new Coordonnee(12.301, -1.499))).isTrue();
        assertThat(maison.contient(new Coordonnee(12.300, -1.499))).as("sur le bord").isTrue();
        assertThat(maison.contient(new Coordonnee(12.303, -1.499))).isFalse();
        assertThat(maison.contient(new Coordonnee(12.301, -1.497))).isFalse();

        // Forme en L : le creux est dehors.
        ZonePolygonale enL = polygone(List.of(new Coordonnee(12.300, -1.500), new Coordonnee(12.300, -1.496),
                new Coordonnee(12.301, -1.496), new Coordonnee(12.301, -1.499), new Coordonnee(12.303, -1.499),
                new Coordonnee(12.303, -1.500)));
        assertThat(enL.contient(new Coordonnee(12.3005, -1.497))).isTrue();
        assertThat(enL.contient(new Coordonnee(12.302, -1.497))).as("dans le creux du L").isFalse();

        assertThatThrownBy(() -> polygone(List.of(new Coordonnee(12.300, -1.500), new Coordonnee(12.302, -1.498),
                new Coordonnee(12.302, -1.500), new Coordonnee(12.300, -1.498)))).as("tracé en sablier")
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> polygone(List.of(new Coordonnee(12.300, -1.500), new Coordonnee(12.301, -1.500))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> polygone(List.of(new Coordonnee(12.0, -1.5), new Coordonnee(12.0, -1.3),
                new Coordonnee(12.2, -1.3)))).as("trop étendu").isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void uneSortieNEstSignaleeQuApresLeDelaiDeToleranceEtUneSeuleFois() {
        Evaluation dedans = SuiviZone.evaluer(null, true, T0, CINQ_MINUTES);
        assertThat(dedans.constat()).isEqualTo(Constat.RIEN);

        Evaluation dehors = SuiviZone.evaluer(dedans.suivi(), false, T0.plusSeconds(60), CINQ_MINUTES);
        assertThat(dehors.constat()).as("sortie de courte durée").isEqualTo(Constat.RIEN);
        assertThat(dehors.suivi().dehorsDepuis()).isEqualTo(T0.plusSeconds(60));

        Evaluation encoreDehors = SuiviZone.evaluer(dehors.suivi(), false, T0.plusSeconds(300), CINQ_MINUTES);
        assertThat(encoreDehors.constat()).as("4 minutes dehors").isEqualTo(Constat.RIEN);

        Evaluation echu = SuiviZone.evaluer(encoreDehors.suivi(), false, T0.plusSeconds(360), CINQ_MINUTES);
        assertThat(echu.constat()).isEqualTo(Constat.SORTIE);

        Evaluation toujoursDehors = SuiviZone.evaluer(echu.suivi(), false, T0.plusSeconds(660), CINQ_MINUTES);
        assertThat(toujoursDehors.constat()).as("déjà signalée").isEqualTo(Constat.RIEN);

        Evaluation retour = SuiviZone.evaluer(toujoursDehors.suivi(), true, T0.plusSeconds(900), CINQ_MINUTES);
        assertThat(retour.constat()).isEqualTo(Constat.RETOUR);
        assertThat(retour.suivi().sortieSignalee()).isFalse();
    }

    @Test
    void unRetourAvantLaFinDuDelaiAnnuleLaSortie() {
        SuiviZone dehors = SuiviZone.evaluer(new SuiviZone(true, null, false, T0), false, T0.plusSeconds(60), CINQ_MINUTES).suivi();
        Evaluation retour = SuiviZone.evaluer(dehors, true, T0.plusSeconds(200), CINQ_MINUTES);
        assertThat(retour.constat()).isEqualTo(Constat.RIEN);
        Evaluation resort = SuiviZone.evaluer(retour.suivi(), false, T0.plusSeconds(420), CINQ_MINUTES);
        assertThat(resort.constat()).as("le délai repart de la nouvelle sortie").isEqualTo(Constat.RIEN);
    }

    @Test
    void unEnfantJamaisVuDansLaZoneOuUnePositionAncienneNeDeclenchentRien() {
        Evaluation pasEncoreArrive = SuiviZone.evaluer(null, false, T0, Duration.ZERO);
        assertThat(pasEncoreArrive.constat()).isEqualTo(Constat.RIEN);
        assertThat(SuiviZone.evaluer(pasEncoreArrive.suivi(), false, T0.plusSeconds(3600), Duration.ZERO).constat())
                .isEqualTo(Constat.RIEN);

        SuiviZone dedans = new SuiviZone(true, null, false, T0);
        Evaluation tamponnee = SuiviZone.evaluer(dedans, false, T0.minusSeconds(600), Duration.ZERO);
        assertThat(tamponnee.constat()).isEqualTo(Constat.RIEN);
        assertThat(tamponnee.suivi()).isEqualTo(dedans);
        assertThat(SuiviZone.evaluer(dedans, true, T0.minusSeconds(600), Duration.ZERO).suivi()).isEqualTo(dedans);
        assertThat(SuiviZone.evaluer(dedans, false, T0.plusSeconds(1), Duration.ZERO).constat())
                .as("sans tolérance, la première mesure dehors suffit").isEqualTo(Constat.SORTIE);
    }

    @Test
    void deuxPositionsTraiteesDansLeDesordreNeFontPasManquerUneSortie() {
        // La position « dehors » de 10:01 est évaluée avant la position « dedans » de 10:00.
        SuiviZone dehorsDAbord = SuiviZone.evaluer(null, false, T0.plusSeconds(60), Duration.ZERO).suivi();
        Evaluation enRetard = SuiviZone.evaluer(dehorsDAbord, true, T0, Duration.ZERO);
        assertThat(enRetard.constat()).as("sans tolérance, la sortie est acquise").isEqualTo(Constat.SORTIE);
        assertThat(enRetard.suivi().dehorsDepuis()).isEqualTo(T0.plusSeconds(60));

        // Avec un délai de tolérance, la sortie commence à la mesure la plus récente et se confirme ensuite.
        Evaluation avecDelai = SuiviZone.evaluer(dehorsDAbord, true, T0, CINQ_MINUTES);
        assertThat(avecDelai.constat()).isEqualTo(Constat.RIEN);
        assertThat(SuiviZone.evaluer(avecDelai.suivi(), false, T0.plusSeconds(420), CINQ_MINUTES).constat())
                .isEqualTo(Constat.SORTIE);
    }

    private static ZoneCirculaire cercle(int rayonM) {
        return new ZoneCirculaire(UUID.randomUUID(), UUID.randomUUID(), "École", Categorie.ECOLE, SEMAINE, 300, Statut.ACTIVE,
                0, ECOLE, rayonM);
    }

    private static ZonePolygonale polygone(List<Coordonnee> sommets) {
        return new ZonePolygonale(UUID.randomUUID(), UUID.randomUUID(), "Maison", Categorie.MAISON, SEMAINE, 300,
                Statut.ACTIVE, 0, sommets);
    }
}
