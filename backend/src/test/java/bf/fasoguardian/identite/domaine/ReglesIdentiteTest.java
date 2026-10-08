package bf.fasoguardian.identite.domaine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import bf.fasoguardian.identite.domaine.CodeUsageUnique.Resultat;
import bf.fasoguardian.identite.domaine.NumeroTelephone.NumeroInvalideException;
import bf.fasoguardian.identite.domaine.PolitiqueMotDePasse.Refus;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class ReglesIdentiteTest {

    private static final Instant T0 = Instant.parse("2026-10-08T09:00:00Z");

    @Nested
    class Telephone {

        @ParameterizedTest
        @ValueSource(strings = {"70 12 34 56", "70123456", "+226 70 12 34 56", "00226-70-12-34-56", "(+226) 70.12.34.56"})
        void lesSaisiesUsuellesSontNormaliseesEnE164(String saisie) {
            assertThat(NumeroTelephone.depuisSaisie(saisie).e164()).isEqualTo("+22670123456");
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "7012345", "701234567", "25 30 60 70", "+33 6 12 34 56 78", "abcdefgh", "80123456"})
        void lesNumerosIncompletsFixesOuEtrangersSontRefuses(String saisie) {
            assertThatThrownBy(() -> NumeroTelephone.depuisSaisie(saisie)).isInstanceOf(NumeroInvalideException.class);
        }

        @Test
        void leNumeroNApparaitJamaisEnClairDansUnJournal() {
            NumeroTelephone numero = NumeroTelephone.depuisSaisie("70123456");

            assertThat(numero.toString()).isEqualTo("+226 •• •• •• 56").doesNotContain("7012");
        }
    }

    @Nested
    class MotDePasse {

        private final NumeroTelephone telephone = NumeroTelephone.depuisSaisie("70123456");

        @Test
        void dixCaracteresAvecUnChiffreSuffisent() {
            assertThat(PolitiqueMotDePasse.verifier("soleil2026", telephone)).isEmpty();
            assertThat(PolitiqueMotDePasse.verifier("une phrase de passe en 4 mots", telephone)).isEmpty();
        }

        @ParameterizedTest
        @CsvSource({"court1,TROP_COURT", "sanschiffreici,SANS_CHIFFRE", "awa-70123456,CONTIENT_LE_TELEPHONE"})
        void lesMotsDePasseFaiblesSontRefusesAvecLeurMotif(String motDePasse, Refus motif) {
            assertThat(PolitiqueMotDePasse.verifier(motDePasse, telephone)).contains(motif);
        }

        @Test
        void auDelaDe128CaracteresLeMotDePasseEstRefuse() {
            assertThat(PolitiqueMotDePasse.verifier("a1".repeat(65), telephone)).contains(Refus.TROP_LONG);
        }
    }

    @Nested
    class CodeSms {

        private static final String CONTEXTE = "INSCRIPTION:compte-1";

        @Test
        void leBonCodeEstAccepteUneSeuleFois() {
            var emission = CodeUsageUnique.emettre(CONTEXTE, T0);

            assertThat(emission.codeEnClair()).matches("\\d{6}");
            assertThat(emission.code().verifier(CONTEXTE, emission.codeEnClair(), T0.plusSeconds(30))).isEqualTo(Resultat.VALIDE);
            assertThat(emission.code().verifier(CONTEXTE, emission.codeEnClair(), T0.plusSeconds(31))).isEqualTo(Resultat.EPUISE);
        }

        @Test
        void troisEssaisErronesEpuisentLeCode() {
            var emission = CodeUsageUnique.emettre(CONTEXTE, T0);
            String faux = emission.codeEnClair().equals("000000") ? "111111" : "000000";

            assertThat(emission.code().verifier(CONTEXTE, faux, T0)).isEqualTo(Resultat.INCORRECT);
            assertThat(emission.code().essaisRestants()).isEqualTo(2);
            assertThat(emission.code().verifier(CONTEXTE, faux, T0)).isEqualTo(Resultat.INCORRECT);
            assertThat(emission.code().verifier(CONTEXTE, faux, T0)).isEqualTo(Resultat.EPUISE);
            assertThat(emission.code().verifier(CONTEXTE, emission.codeEnClair(), T0)).isEqualTo(Resultat.EPUISE);
        }

        @Test
        void leCodeExpireApresCinqMinutes() {
            var emission = CodeUsageUnique.emettre(CONTEXTE, T0);

            assertThat(emission.code().verifier(CONTEXTE, emission.codeEnClair(), T0.plusSeconds(301))).isEqualTo(Resultat.EXPIRE);
        }

        @Test
        void unCodeEmisPourUneActionNeValidePasUneAutreAction() {
            var emission = CodeUsageUnique.emettre(CONTEXTE, T0);

            assertThat(emission.code().verifier("RETRAIT:compte-1", emission.codeEnClair(), T0)).isEqualTo(Resultat.INCORRECT);
        }

        @Test
        void leRenvoiNEstPossibleQuApresSoixanteSecondes() {
            var code = CodeUsageUnique.emettre(CONTEXTE, T0).code();

            assertThat(code.renvoiPossible(T0.plusSeconds(59))).isFalse();
            assertThat(code.renvoiPossible(T0.plusSeconds(60))).isTrue();
        }

        @Test
        void lEtatSeReconstitueDepuisLaBaseSansLeCodeEnClair() {
            var emission = CodeUsageUnique.emettre(CONTEXTE, T0);
            var relu = CodeUsageUnique.reconstituer(emission.code().empreinte(), T0, 2, false);

            assertThat(relu.essaisRestants()).isEqualTo(1);
            assertThat(relu.verifier(CONTEXTE, emission.codeEnClair(), T0)).isEqualTo(Resultat.VALIDE);
        }
    }

    @Nested
    class CodesTotp {

        // Vecteurs de test de la RFC 6238 (annexe B), SHA-1, tronqués à 6 chiffres.
        private final byte[] secret = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

        @ParameterizedTest
        @CsvSource({"59,287082", "1111111109,081804", "1111111111,050471", "1234567890,005924", "2000000000,279037"})
        void lesCodesSontConformesALaRfc6238(long seconde, String code) {
            assertThat(Totp.code(secret, seconde / Totp.PAS_SECONDES)).isEqualTo(code);
        }

        @Test
        void unCodeDuPasPrecedentEstToleremaisPasPlusAncien() {
            Instant instant = Instant.ofEpochSecond(1111111111L);
            long pas = instant.getEpochSecond() / 30;

            assertThat(Totp.verifier(secret, Totp.code(secret, pas - 1), instant, 0)).hasValue(pas - 1);
            assertThat(Totp.verifier(secret, Totp.code(secret, pas - 2), instant, 0)).isEmpty();
            assertThat(Totp.verifier(secret, "12345", instant, 0)).isEmpty();
        }

        @Test
        void unCodeDejaAccepteNePeutPasEtreRejoue() {
            Instant instant = Instant.ofEpochSecond(1111111111L);
            long pas = instant.getEpochSecond() / 30;

            assertThat(Totp.verifier(secret, "050471", instant, pas - 1)).hasValue(pas);
            assertThat(Totp.verifier(secret, "050471", instant, pas)).isEmpty();
        }

        @Test
        void leSecretSExporteEnBase32() {
            assertThat(Totp.enBase32(secret)).isEqualTo("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ");
            assertThat(Totp.nouveauSecret()).hasSize(20);
        }
    }
}
