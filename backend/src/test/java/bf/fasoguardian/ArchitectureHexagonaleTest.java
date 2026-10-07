package bf.fasoguardian;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Règles de couches internes de chaque module (FG-DOC-06 §6.2, FG-DOC-07 §8.1). */
class ArchitectureHexagonaleTest {

    private static JavaClasses classes;

    @BeforeAll
    static void importerLesClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("bf.fasoguardian");
    }

    @Test
    void leDomaineNeDependDAucunCadreTechnique() {
        noClasses().that().resideInAPackage("bf.fasoguardian.*.domaine..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..", "jakarta.servlet..", "org.eclipse.paho..", "tools.jackson..")
                .because("le domaine ne porte que des règles métier et le mapping JPA")
                .allowEmptyShould(true)
                .check(classes);
    }

    @Test
    void leDomaineNeDependPasDesAutresCouches() {
        noClasses().that().resideInAPackage("bf.fasoguardian.*.domaine..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "bf.fasoguardian.*.web..", "bf.fasoguardian.*.application..",
                        "bf.fasoguardian.*.infrastructure..")
                .allowEmptyShould(true)
                .check(classes);
    }

    @Test
    void lesCasDUsageNeDependentPasDeLaCoucheWeb() {
        noClasses().that().resideInAPackage("bf.fasoguardian.*.application..")
                .should().dependOnClassesThat().resideInAPackage("bf.fasoguardian.*.web..")
                .allowEmptyShould(true)
                .check(classes);
    }

    @Test
    void laCoucheWebPasseParLesCasDUsage() {
        noClasses().that().resideInAPackage("bf.fasoguardian.*.web..")
                .should().dependOnClassesThat().resideInAPackage("bf.fasoguardian.*.infrastructure..")
                .because("toute écriture passe par un cas d'usage transactionnel")
                .allowEmptyShould(true)
                .check(classes);
    }
}
