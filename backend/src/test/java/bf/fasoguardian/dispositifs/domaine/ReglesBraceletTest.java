package bf.fasoguardian.dispositifs.domaine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;

import bf.fasoguardian.dispositifs.domaine.Bracelet.TransitionIllegaleException;
import org.junit.jupiter.api.Test;

/** Règles du domaine dispositifs : code d'appairage et cycle de vie du bracelet (US-PAR-013, 014, US-SAV-002). */
class ReglesBraceletTest {

    private static final Instant T0 = Instant.parse("2026-10-08T10:00:00Z");
    private static final String EMPREINTE = "a".repeat(64);

    @Test
    void leCodeDAppairageEviteLesSignesAmbigusEtSeNormalise() {
        SecureRandom alea = new SecureRandom();
        for (int i = 0; i < 200; i++) {
            String code = CodeAppairage.generer(alea);
            assertThat(code).hasSize(CodeAppairage.LONGUEUR).matches("[A-HJ-KM-NP-Z2-9]+");
            assertThat(CodeAppairage.normaliser(CodeAppairage.presenter(code).toLowerCase())).contains(code);
        }
        assertThat(CodeAppairage.presenter("K7Q4M2X")).isEqualTo("K7Q4-M2X");
        assertThat(CodeAppairage.normaliser(" k7q4 – m2x ")).contains("K7Q4M2X");
        assertThat(CodeAppairage.normaliser("K7Q4M2")).isEmpty();
        assertThat(CodeAppairage.normaliser("K7Q4M2O")).isEmpty();
        assertThat(CodeAppairage.normaliser(null)).isEmpty();
    }

    @Test
    void laConfigurationParDefautVientDeLaRevisionMaterielle() {
        var prepare = preparer();
        assertThat(prepare.bracelet().statut()).isEqualTo(StatutBracelet.EN_STOCK);
        assertThat(prepare.configuration().intervalleCourantS()).isEqualTo(300);
        assertThat(prepare.configuration().intervalleAlerteS()).isEqualTo(60);
        assertThat(prepare.configuration().reglerModeEconomie(true)).isTrue();
        assertThat(prepare.configuration().reglerModeEconomie(true)).isFalse();
        assertThat(prepare.configuration().intervalleCourantS()).isEqualTo(900);
        assertThatThrownBy(() -> FabriqueBracelet.preparer("FG-0001", new byte[0], "i", "V9", "1.0.0", EMPREINTE,
                EMPREINTE, "c", T0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void lAppairageConsommeLeCodeEtDemarreLaGarantie() {
        Bracelet bracelet = preparer().bracelet();
        bracelet.activer(T0);
        assertThat(bracelet.statut()).isEqualTo(StatutBracelet.ACTIF);
        assertThat(bracelet.garantieJusquAu()).isEqualTo(LocalDate.parse("2027-10-08"));
        assertThatThrownBy(() -> bracelet.activer(T0)).isInstanceOf(TransitionIllegaleException.class);
    }

    @Test
    void laPerteOuvreUnSuiviDe72HeuresEtLeVolRevoqueAussitotLeCertificat() {
        Bracelet perdu = actif();
        perdu.declarerPerdu(T0);
        assertThat(perdu.suiviJusquAu()).isEqualTo(T0.plus(Bracelet.SUIVI_APRES_PERTE));
        assertThat(perdu.certificatRevoque()).isFalse();
        perdu.retrouver(T0);
        assertThat(perdu.statut()).isEqualTo(StatutBracelet.ACTIF);
        assertThat(perdu.suiviJusquAu()).isNull();

        perdu.declarerPerdu(T0);
        perdu.cloreSuivi(T0);
        assertThat(perdu.certificatRevoque()).isTrue();
        assertThatThrownBy(() -> perdu.retrouver(T0)).isInstanceOf(TransitionIllegaleException.class);

        Bracelet vole = actif();
        vole.declarerVole(T0);
        assertThat(vole.statut()).isEqualTo(StatutBracelet.VOLE);
        assertThat(vole.certificatRevoqueLe()).isEqualTo(T0);
    }

    @Test
    void uneUniteAuCertificatRevoqueNeRepartEnStockQuAvecUnNouveauCertificat() {
        Bracelet bracelet = actif();
        bracelet.declarerVole(T0);
        bracelet.retournerAuSav(T0);
        assertThatThrownBy(() -> bracelet.remettreEnStock("code", null, T0)).isInstanceOf(TransitionIllegaleException.class);
        bracelet.remettreEnStock("code", "b".repeat(64), T0);
        assertThat(bracelet.statut()).isEqualTo(StatutBracelet.EN_STOCK);
        assertThat(bracelet.certificatRevoque()).isFalse();
        bracelet.activer(T0);
        assertThatThrownBy(() -> bracelet.reformer(T0)).isInstanceOf(TransitionIllegaleException.class);
    }

    private static Bracelet actif() {
        Bracelet bracelet = preparer().bracelet();
        bracelet.activer(T0);
        return bracelet;
    }

    private static FabriqueBracelet.BraceletPrepare preparer() {
        return FabriqueBracelet.preparer("FG-2291", new byte[] {1}, "empreinte-imei", "V1", "2.4.1", EMPREINTE, EMPREINTE,
                "empreinte-code", T0);
    }
}
