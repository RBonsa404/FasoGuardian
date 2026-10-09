package bf.fasoguardian.audit.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/** La mise en production avec données réelles est bloquée tant que l'AIPD n'est pas documentée (US-ADM-003). */
class GardeAipdTest {

    private static final Clock HORLOGE = Clock.fixed(Instant.parse("2026-10-09T10:00:00Z"), ZoneOffset.UTC);

    @Test
    void sousLeProfilProdLeServeurRefuseDeDemarrerSansAipd() {
        assertThatThrownBy(() -> garde("", "", "", "prod")).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Mise en production bloquée").hasMessageContaining("FG_AIPD_REFERENCE");
    }

    @Test
    void uneAipdIncompleteOuDateeDansLeFuturNeLeveLeBlocagePas() {
        assertThatThrownBy(() -> garde("AIPD-2026-01", "2026-09-30", "", "prod")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> garde("AIPD-2026-01", "", "dpo@exemple.bf", "prod")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> garde("AIPD-2026-01", "2026-12-01", "dpo@exemple.bf", "prod")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void uneAipdDocumenteeLaisseDemarrerLaProduction() {
        GardeAipd garde = garde("AIPD-2026-01", "2026-09-30", "dpo@exemple.bf", "prod");

        assertThat(garde.etat().documentee()).isTrue();
        assertThat(garde.etat().reference()).isEqualTo("AIPD-2026-01");
        assertThat(garde.etat().valideeLe()).isEqualTo(LocalDate.of(2026, 9, 30));
        assertThat(garde.etat().delegue()).isEqualTo("dpo@exemple.bf");
    }

    @Test
    void horsProductionLeServeurDemarreEtDitQueLAipdManque() {
        GardeAipd garde = garde("", "", "", "dev");

        assertThat(garde.etat().documentee()).isFalse();
        assertThat(garde.etat().reference()).isNull();
    }

    @Test
    void uneDateIllisibleEstRefuseeDansTousLesProfils() {
        assertThatThrownBy(() -> garde("AIPD-2026-01", "30/09/2026", "dpo@exemple.bf", "dev"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("AAAA-MM-JJ");
    }

    private static GardeAipd garde(String reference, String valideeLe, String delegue, String profil) {
        MockEnvironment environnement = new MockEnvironment();
        environnement.setActiveProfiles(profil);
        return new GardeAipd(reference, valideeLe, delegue, environnement, HORLOGE);
    }
}
