package bf.fasoguardian.abonnements.domaine;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import bf.fasoguardian.abonnements.domaine.Abonnement.Periode;
import bf.fasoguardian.abonnements.domaine.Abonnement.Relance;
import bf.fasoguardian.abonnements.domaine.Abonnement.Statut;
import org.junit.jupiter.api.Test;

/** Échéances, relances et dégradation d'un abonnement (US-PAR-015, US-PAR-016, US-SYS-008). */
class ReglesAbonnementTest {

    private static final Instant MAINTENANT = Instant.parse("2026-10-09T10:00:00Z");
    private static final LocalDate JOUR = LocalDate.of(2026, 10, 9);
    private static final LocalDate ECHEANCE = LocalDate.of(2026, 11, 9);

    @Test
    void unAbonnementNEstActifQuApresUnPaiementConfirme() {
        Abonnement abonnement = nouveau();
        assertThat(abonnement.statut()).isEqualTo(Statut.EN_ATTENTE);
        assertThat(abonnement.prochaineEcheance()).isNull();
        assertThat(abonnement.relancer(JOUR, MAINTENANT)).isEqualTo(Relance.AUCUNE);

        Periode periode = abonnement.confirmerPaiement("INTERMEDIAIRE", JOUR, MAINTENANT);

        assertThat(abonnement.statut()).isEqualTo(Statut.ACTIF);
        assertThat(periode).isEqualTo(new Periode(JOUR, ECHEANCE));
        assertThat(abonnement.prochaineEcheance()).isEqualTo(ECHEANCE);
    }

    @Test
    void unPaiementAnticipeProlongeAPartirDeLEcheanceEtNonDuJourDuPaiement() {
        Abonnement abonnement = actif(false);

        Periode periode = abonnement.confirmerPaiement("PREMIUM", ECHEANCE.minusDays(5), MAINTENANT);

        assertThat(periode).isEqualTo(new Periode(ECHEANCE, ECHEANCE.plusMonths(1)));
        assertThat(abonnement.offreCode()).isEqualTo("PREMIUM");
    }

    @Test
    void unPaiementEnRetardRepartDuJourDuPaiementEtLeveLaRestriction() {
        Abonnement abonnement = actif(false);
        for (int jour = 0; jour <= 15; jour++) {
            abonnement.relancer(ECHEANCE.plusDays(jour), MAINTENANT);
        }
        assertThat(abonnement.statut()).isEqualTo(Statut.RESTREINT);

        LocalDate paiement = ECHEANCE.plusDays(20);
        Periode periode = abonnement.confirmerPaiement("INTERMEDIAIRE", paiement, MAINTENANT);

        assertThat(abonnement.statut()).isEqualTo(Statut.ACTIF);
        assertThat(periode).isEqualTo(new Periode(paiement, paiement.plusMonths(1)));
        // Le cycle de relances repart de zéro pour la nouvelle échéance.
        assertThat(abonnement.relancer(paiement.plusMonths(1).minusDays(7), MAINTENANT)).isEqualTo(Relance.RAPPEL_D_ECHEANCE);
    }

    @Test
    void lesRelancesSuiventLeCalendrierEtChacuneNEstFaiteQuUneFois() {
        Abonnement abonnement = actif(false);
        List<String> journal = new ArrayList<>();
        for (int jour = -10; jour <= 20; jour++) {
            Relance relance = abonnement.relancer(ECHEANCE.plusDays(jour), MAINTENANT);
            if (relance != Relance.AUCUNE) {
                journal.add("J" + (jour < 0 ? "" : "+") + jour + " " + relance + " " + abonnement.statut());
            }
        }

        assertThat(journal).containsExactly("J-7 RAPPEL_D_ECHEANCE ACTIF", "J+1 RAPPEL_1 EN_RETARD", "J+8 RAPPEL_2 EN_RETARD",
                "J+15 RESTRICTION RESTREINT");
        assertThat(abonnement.restrictionLe()).isEqualTo(ECHEANCE.plusDays(15));
    }

    @Test
    void leRenouvellementAutomatiqueEstDemandeLeJourDeLEcheance() {
        Abonnement abonnement = actif(true);

        assertThat(abonnement.relancer(ECHEANCE.minusDays(1), MAINTENANT)).isEqualTo(Relance.RAPPEL_D_ECHEANCE);
        assertThat(abonnement.relancer(ECHEANCE, MAINTENANT)).isEqualTo(Relance.RENOUVELLEMENT);
        assertThat(abonnement.relancer(ECHEANCE, MAINTENANT)).isEqualTo(Relance.AUCUNE);
        assertThat(abonnement.statut()).isEqualTo(Statut.ACTIF);
    }

    @Test
    void unTraitementRestePlusieursJoursSansTournerNeRestreintJamaisAvantLesDeuxRappels() {
        Abonnement abonnement = actif(false);
        LocalDate reprise = ECHEANCE.plusDays(30);

        assertThat(abonnement.relancer(reprise, MAINTENANT)).isEqualTo(Relance.AUCUNE);
        assertThat(abonnement.relancer(reprise, MAINTENANT)).isEqualTo(Relance.RAPPEL_1);
        assertThat(abonnement.relancer(reprise.plusDays(1), MAINTENANT)).isEqualTo(Relance.RAPPEL_2);
        assertThat(abonnement.statut()).isEqualTo(Statut.EN_RETARD);
        assertThat(abonnement.relancer(reprise.plusDays(2), MAINTENANT)).isEqualTo(Relance.RESTRICTION);
        assertThat(abonnement.relancer(reprise.plusDays(3), MAINTENANT)).isEqualTo(Relance.AUCUNE);
    }

    @Test
    void unPaiementConfirmeDeuxFoisNeCompteQuUneFoisEtUnEchecNeDefaitPasUneConfirmation() {
        Paiement paiement = new Paiement(UUID.randomUUID(), "INTERMEDIAIRE", 2250, Moyen.ORANGE_MONEY, "cle-de-test-0001", MAINTENANT);

        assertThat(paiement.confirmer(MAINTENANT)).isTrue();
        assertThat(paiement.confirmer(MAINTENANT)).isFalse();
        assertThat(paiement.echouer("Solde insuffisant", MAINTENANT)).isFalse();
        assertThat(paiement.statut()).isEqualTo(Paiement.Statut.CONFIRME);
    }

    @Test
    void uneConfirmationTardiveVautMemePourUneDemandeTenuePourExpiree() {
        Paiement paiement = new Paiement(UUID.randomUUID(), "ESSENTIEL", 1500, Moyen.MOOV_MONEY, "cle-de-test-0002", MAINTENANT);
        paiement.expirer(MAINTENANT);
        assertThat(paiement.statut()).isEqualTo(Paiement.Statut.EXPIRE);

        assertThat(paiement.confirmer(MAINTENANT)).isTrue();
        assertThat(paiement.statut()).isEqualTo(Paiement.Statut.CONFIRME);
    }

    private static Abonnement nouveau() {
        return new Abonnement(UUID.randomUUID(), UUID.randomUUID(), "INTERMEDIAIRE", MAINTENANT);
    }

    private static Abonnement actif(boolean renouvellementAuto) {
        Abonnement abonnement = nouveau();
        abonnement.retenirPaiement(UUID.randomUUID(), Moyen.ORANGE_MONEY, new byte[] {1}, "+226 72 •• •• 33", renouvellementAuto, MAINTENANT);
        abonnement.confirmerPaiement("INTERMEDIAIRE", JOUR, MAINTENANT);
        return abonnement;
    }
}
