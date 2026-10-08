package bf.fasoguardian.simulateur;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class BraceletSimuleTest {

    private static final Instant MAINTENANT = Instant.parse("2026-10-07T12:00:00Z");

    @Test
    void laTelemetrieRespecteLeFormatCompactEtIncrementeLaSequence() {
        BraceletSimule bracelet = new BraceletSimule("FG-DEV-0001", 1);

        String premier = bracelet.prochaineTelemetrie(MAINTENANT);
        String second = bracelet.prochaineTelemetrie(MAINTENANT.plusSeconds(300));

        assertThat(premier).matches(
                "\\{\"t\":1791374400,\"seq\":1,\"lat\":-?\\d+\\.\\d{5},\"lon\":-?\\d+\\.\\d{5},\"acc\":\\d+,"
                        + "\"src\":\"gnss\",\"bat\":\\d+,\"rssi\":-\\d+,\"net\":\"4g\",\"op\":\"Orange BF\",\"mv\":1}");
        assertThat(second).contains("\"t\":1791374700", "\"seq\":2");
        assertThat(bracelet.sequence()).isEqualTo(2);
    }

    @Test
    void lePointDecimalNeDependPasDeLaLangueDuPoste() {
        java.util.Locale origine = java.util.Locale.getDefault();
        java.util.Locale.setDefault(java.util.Locale.FRANCE);
        try {
            assertThat(new BraceletSimule("FG-DEV-0001", 1).prochaineTelemetrie(MAINTENANT)).doesNotContain(",5", "12,3");
        } finally {
            java.util.Locale.setDefault(origine);
        }
    }

    @Test
    void laPositionResteAuVoisinageDuPointDeDepartEtLaBatterieDecroit() {
        BraceletSimule bracelet = new BraceletSimule("FG-DEV-0001", 7);
        int batterieInitiale = bracelet.batterie();

        for (int i = 0; i < 2000; i++) {
            bracelet.prochaineTelemetrie(MAINTENANT.plusSeconds(300L * i));
        }

        assertThat(bracelet.latitude()).isBetween(BraceletSimule.LATITUDE_DEPART - 0.0201, BraceletSimule.LATITUDE_DEPART + 0.0201);
        assertThat(bracelet.longitude()).isBetween(BraceletSimule.LONGITUDE_DEPART - 0.0201, BraceletSimule.LONGITUDE_DEPART + 0.0201);
        assertThat(bracelet.batterie()).isLessThan(batterieInitiale).isGreaterThanOrEqualTo(1);
    }

    @Test
    void deuxSimulationsDeMemeGraineSontIdentiques() {
        BraceletSimule a = new BraceletSimule("FG-DEV-0002", 42);
        BraceletSimule b = new BraceletSimule("FG-DEV-0002", 42);

        assertThat(a.prochaineTelemetrie(MAINTENANT)).isEqualTo(b.prochaineTelemetrie(MAINTENANT));
    }

    @Test
    void lEtatAnnonceLaPresenceEnLigne() {
        BraceletSimule bracelet = new BraceletSimule("FG-DEV-0001", 1);

        assertThat(bracelet.etat(true)).isEqualTo("{\"online\":true,\"fw\":\"sim-0.1.0\"}");
        assertThat(bracelet.etat(false)).isEqualTo("{\"online\":false,\"fw\":\"sim-0.1.0\"}");
    }

    @Test
    void lesOptionsSontLuesSousLaFormeCleValeur() {
        assertThat(Simulateur.lireOptions(new String[] {"--broker=ssl://localhost:8883", "--bracelets=A,B"}))
                .containsEntry("broker", "ssl://localhost:8883")
                .containsEntry("bracelets", "A,B");
        assertThatThrownBy(() -> Simulateur.lireOptions(new String[] {"broker"}))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
