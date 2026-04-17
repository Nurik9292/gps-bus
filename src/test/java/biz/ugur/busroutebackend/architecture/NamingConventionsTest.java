package biz.ugur.busroutebackend.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

class NamingConventionsTest {

    private static final String ROOT = "biz.ugur.busroutebackend";
    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(ROOT);
    }

    @Test
    void useCasesLiveInApplicationLayer() {
        classes()
                .that().haveSimpleNameEndingWith("UseCase")
                .and().areNotInterfaces()
                .and().haveSimpleNameNotStartingWith("Base")
                .should().resideInAPackage("..application..")
                .because("Use cases are application-layer concerns")
                .check(classes);
    }

    @Test
    void controllersLiveInInterfacesLayer() {
        classes()
                .that().haveSimpleNameEndingWith("Controller")
                .and().haveSimpleNameNotStartingWith("Base")
                .should().resideInAPackage("..interfaces..")
                .because("Controllers belong to the delivery layer. "
                        + "BaseController abstracts live in shared.infrastructure.web.")
                .check(classes);
    }

    @Test
    void configurationClassesLiveInInfrastructure() {
        classes()
                .that().haveSimpleNameEndingWith("Config")
                .and().haveSimpleNameNotEndingWith("SessionConfig")
                .and().haveSimpleNameNotEndingWith("GpsConfig")
                .should().resideInAPackage("..infrastructure..")
                .orShould().resideInAPackage("..shared..")
                .because("Framework configuration is an infrastructure concern. "
                        + "SessionConfig/GpsConfig are value-holders, not @Configuration.")
                .check(classes);
    }
}
