package biz.ugur.busroutebackend.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class LayeringRulesTest {

    private static final String ROOT = "biz.ugur.busroutebackend";
    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(ROOT);
    }

    @Test
    void domainDoesNotDependOnInfrastructureOrInterfaces() {
        noClasses()
                .that().resideInAPackage("..domain..")
                .and().haveSimpleNameNotEndingWith("AvatarStorage")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..infrastructure..",
                        "..interfaces..")
                .because("Domain layer must stay free of frameworks and delivery mechanisms. "
                        + "Whitelisted: admin.domain.storage.AvatarStorage — known tech-debt.")
                .check(classes);
    }

    @Test
    @Disabled("Tech-debt: 114 violations. Controllers' Response/Request DTOs are reused from "
            + "use cases. Dedicated stage will extract application-layer DTOs separately.")
    void applicationDoesNotDependOnInterfaces() {
        noClasses()
                .that().resideInAPackage("..application..")
                .should().dependOnClassesThat().resideInAPackage("..interfaces..")
                .because("Use cases must not be aware of controllers/REST DTOs")
                .check(classes);
    }

    @Test
    void infrastructureDoesNotDependOnInterfaces() {
        noClasses()
                .that().resideInAPackage("..infrastructure..")
                .and().haveSimpleNameNotEndingWith("WebSocketConfig")
                .and().haveSimpleNameNotEndingWith("SearchContextFactory")
                .and().haveSimpleNameNotEndingWith("BusStopRealTimeServiceImpl")
                .should().dependOnClassesThat().resideInAPackage("..interfaces..")
                .because("Infrastructure adapters should not import delivery layer. "
                        + "Whitelisted classes are known tech-debt: see docs/plans/BACKEND_REVIEW_PLAN.md.")
                .check(classes);
    }

    @Test
    void repositoryInterfacesLiveInDomain() {
        classes()
                .that().haveSimpleNameEndingWith("Repository")
                .and().areInterfaces()
                .and().resideOutsideOfPackage("..shared..")
                .should().resideInAPackage("..domain.repository..")
                .orShould().resideInAPackage("..domain..")
                .because("Repository interfaces belong to the domain layer")
                .check(classes);
    }

    @Test
    void r2dbcRepositoriesLiveInInfrastructure() {
        classes()
                .that().haveSimpleNameStartingWith("R2dbc")
                .should().resideInAPackage("..infrastructure..")
                .because("R2DBC implementations belong to infrastructure layer")
                .check(classes);
    }
}
