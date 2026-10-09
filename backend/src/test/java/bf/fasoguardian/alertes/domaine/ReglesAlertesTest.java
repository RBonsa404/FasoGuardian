package bf.fasoguardian.alertes.domaine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import bf.fasoguardian.alertes.domaine.Alerte.Gravite;
import bf.fasoguardian.alertes.domaine.Alerte.Statut;
import bf.fasoguardian.alertes.domaine.Alerte.TransitionIllegaleException;
import bf.fasoguardian.alertes.domaine.Alerte.Type;
import bf.fasoguardian.alertes.domaine.AutorisationRetrait.Motif;
import org.junit.jupiter.api.Test;

/** Machine à états des alertes (FG-DOC-07 §5) et fenêtre de retrait autorisé (US-PAR-012). */
class ReglesAlertesTest {

    private static final Instant T0 = Instant.parse("2026-10-09T08:00:00Z");
    private static final UUID TUTEUR = UUID.randomUUID();

    @Test
    void chaqueTransitionProduitUneActionDuJournal() {
        var ouverture = Alerte.ouvrir(UUID.randomUUID(), Type.SOS, null, null, null, 12.37, -1.52, T0);
        Alerte alerte = ouverture.alerte();
        assertThat(alerte.gravite()).isEqualTo(Gravite.CRITIQUE);
        assertThat(ouverture.action().type()).isEqualTo(ActionAlerte.Type.OUVERTURE);
        assertThat(ouverture.action().acteurId()).as("événement détecté par le système").isNull();

        ActionAlerte prise = alerte.acquitter(TUTEUR, T0.plusSeconds(20));
        assertThat(alerte.statut()).isEqualTo(Statut.ACQUITTEE);
        assertThat(prise.acteurId()).isEqualTo(TUTEUR);

        assertThat(alerte.escalader(TUTEUR, T0.plusSeconds(60)).type()).isEqualTo(ActionAlerte.Type.ESCALADE);
        ActionAlerte levee = alerte.lever(TUTEUR, "  Enfant retrouvé  ", T0.plusSeconds(900));
        assertThat(alerte.statut()).isEqualTo(Statut.LEVEE);
        assertThat(alerte.closeLe()).isEqualTo(T0.plusSeconds(900));
        assertThat(levee.motif()).isEqualTo("Enfant retrouvé");
    }

    @Test
    void lesTransitionsHorsMachineAEtatsSontRefusees() {
        Alerte ouverte = alerte(Type.SOS);
        assertThatThrownBy(() -> ouverte.lever(TUTEUR, "motif", T0)).as("lever sans prise en charge")
                .isInstanceOf(TransitionIllegaleException.class);
        assertThatThrownBy(() -> ouverte.escalader(TUTEUR, T0)).isInstanceOf(TransitionIllegaleException.class);

        ouverte.acquitter(TUTEUR, T0);
        assertThatThrownBy(() -> ouverte.acquitter(TUTEUR, T0)).isInstanceOf(TransitionIllegaleException.class);
        assertThatThrownBy(() -> ouverte.lever(TUTEUR, " ", T0)).as("motif obligatoire")
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(ouverte.statut()).isEqualTo(Statut.ACQUITTEE);

        ouverte.classerFausseAlerte(TUTEUR, "Appui par jeu", T0);
        assertThat(ouverte.statut()).isEqualTo(Statut.FAUSSE_ALERTE);
        assertThatThrownBy(() -> ouverte.lever(TUTEUR, "motif", T0)).isInstanceOf(TransitionIllegaleException.class);
        assertThatThrownBy(() -> ouverte.classerFausseAlerte(TUTEUR, "motif", T0)).isInstanceOf(TransitionIllegaleException.class);
    }

    @Test
    void leSystemeResoutUneAlerteDontLaCauseADisparuSaufSiElleEstEscaladee() {
        Alerte sortie = alerte(Type.SORTIE_ZONE);
        assertThat(sortie.gravite()).isEqualTo(Gravite.IMPORTANTE);
        ActionAlerte resolution = sortie.resoudre("Retour dans la zone", T0.plusSeconds(600));
        assertThat(sortie.statut()).isEqualTo(Statut.LEVEE);
        assertThat(resolution.type()).isEqualTo(ActionAlerte.Type.RESOLUTION);
        assertThat(resolution.acteurId()).isNull();

        Alerte escaladee = alerte(Type.SORTIE_ZONE);
        escaladee.acquitter(TUTEUR, T0);
        escaladee.escalader(TUTEUR, T0);
        assertThatThrownBy(() -> escaladee.resoudre("Retour dans la zone", T0)).isInstanceOf(TransitionIllegaleException.class);
    }

    @Test
    void unRetraitSAutoriseDe15MinutesA12HeuresParPasDe15Minutes() {
        assertThat(retrait(15).fin()).isEqualTo(T0.plusSeconds(900));
        assertThat(retrait(720).fin()).isEqualTo(T0.plus(Duration.ofHours(12)));
        for (int minutes : new int[] {0, 10, 20, 735, -15}) {
            assertThatThrownBy(() -> retrait(minutes)).as("%d minutes", minutes).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void laFenetreCouvreSaDureePuisEchoitEnSignalantUnBraceletNonRemis() {
        AutorisationRetrait autorisation = retrait(30);
        assertThat(autorisation.couvre(T0)).isTrue();
        assertThat(autorisation.couvre(T0.plusSeconds(1799))).isTrue();
        assertThat(autorisation.couvre(T0.plusSeconds(1800))).as("fin exclue").isFalse();
        assertThat(autorisation.couvre(T0.minusSeconds(1))).isFalse();

        assertThat(autorisation.rappelDu(T0.plusSeconds(1500))).as("bracelet encore porté").isFalse();
        autorisation.noterRetrait();
        assertThat(autorisation.rappelDu(T0.plusSeconds(1499))).isFalse();
        assertThat(autorisation.rappelDu(T0.plusSeconds(1500))).as("cinq minutes avant la fin").isTrue();
        autorisation.noterRappel();
        assertThat(autorisation.rappelDu(T0.plusSeconds(1600))).as("un seul rappel").isFalse();

        assertThat(autorisation.echoir(T0.plusSeconds(1800))).as("retiré et non remis").isTrue();
        assertThat(autorisation.statut()).isEqualTo(AutorisationRetrait.Statut.ECHUE);
        assertThat(autorisation.couvre(T0.plusSeconds(100))).isFalse();
        assertThatThrownBy(() -> autorisation.terminer(T0)).isInstanceOf(IllegalStateException.class);

        assertThat(retrait(30).echoir(T0.plusSeconds(1800))).as("jamais retiré").isFalse();
    }

    @Test
    void laProlongationRelanceLeRappelEtResteBorneeA12Heures() {
        AutorisationRetrait autorisation = retrait(600);
        autorisation.noterRetrait();
        autorisation.noterRappel();
        autorisation.prolonger(Duration.ofMinutes(30));
        assertThat(autorisation.fin()).isEqualTo(T0.plus(Duration.ofMinutes(630)));
        assertThat(autorisation.rappelDu(T0.plus(Duration.ofMinutes(626)))).isTrue();
        assertThatThrownBy(() -> autorisation.prolonger(Duration.ofMinutes(120))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> autorisation.prolonger(Duration.ofMinutes(10))).isInstanceOf(IllegalArgumentException.class);
    }

    private static Alerte alerte(Type type) {
        return Alerte.ouvrir(UUID.randomUUID(), type, null, null, null, null, null, T0).alerte();
    }

    private static AutorisationRetrait retrait(int minutes) {
        return new AutorisationRetrait(UUID.randomUUID(), UUID.randomUUID(), TUTEUR, Motif.TOILETTE, Duration.ofMinutes(minutes), T0);
    }
}
