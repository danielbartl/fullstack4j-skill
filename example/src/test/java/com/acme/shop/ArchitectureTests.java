package com.acme.shop;

import dev.jbaby.fullstack4j.springboot.test.Fullstack4jRules;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

class ArchitectureTests {

    ApplicationModules modules = ApplicationModules.of(ShopApplication.class);

    @Test
    void slicesStayOutOfEachOthersInternals() {
        // fails on cycles and on any use of another slice's
        // internal package, from the root too
        modules.verify();
    }

    @Test
    void followsTheFullstack4jRules() {
        // what verify() leaves alone: slice names, what a base package
        // holds, documents, events, pages, and no @Disabled tests
        Fullstack4jRules.builder(ShopApplication.class)
            .build()
            .checkAll();
    }

    @Test
    void documentsTheArchitecture() {
        new Documenter(modules).writeDocumentation();
    }
}
