package com.acme.shop;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.apache.wicket.Component;
import org.junit.jupiter.api.Test;
import org.springframework.data.repository.Repository;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

class ArchitectureTests {

    ApplicationModules modules = ApplicationModules.of(ShopApplication.class);

    JavaClasses classes = new ClassFileImporter()
        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
        .importPackages("com.acme.shop");

    @Test
    void slicesStayOutOfEachOthersInternals() {
        // fails on cycles and on any use of another slice's
        // internal package, from the root too
        modules.verify();
    }

    @Test
    void uiNeverTouchesRepositories() {
        noClasses().that().areAssignableTo(Component.class)
            .should().dependOnClassesThat().areAssignableTo(Repository.class)
            .check(classes);
    }

    @Test
    void documentsTheArchitecture() {
        new Documenter(modules).writeDocumentation();
    }
}
