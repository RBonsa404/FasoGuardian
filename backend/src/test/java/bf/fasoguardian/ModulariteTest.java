package bf.fasoguardian;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModule;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/** Vérifie à chaque construction les frontières des modules (REQ-SYS-010). */
class ModulariteTest {

    private final ApplicationModules modules = ApplicationModules.of(FasoGuardianApplication.class);

    @Test
    void aucunModuleNAccedeAuxClassesInternesDUnAutre() {
        modules.verify();
    }

    @Test
    void leServeurEstDecoupeEnNeufModulesMetierEtUnSocle() {
        assertThat(modules.stream().map(ApplicationModule::getName))
                .containsExactlyInAnyOrder("identite", "famille", "dispositifs", "telemetrie", "geolocalisation",
                        "alertes", "notifications", "abonnements", "audit", "plateforme");
    }

    @Test
    void laDocumentationDesModulesEstGeneree() {
        new Documenter(modules).writeModulesAsPlantUml().writeIndividualModulesAsPlantUml();
    }
}
