package com.acme.shop;

import static java.util.stream.Collectors.toCollection;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/// Keeps every slice's spec and its tests in step.
///
/// A requirement id in a slice's `package-info.java` with no test
/// naming it in its `@DisplayName` is a gap. A test naming an id the
/// spec doesn't have is drift. Either one fails the build.
class SpecTraceabilityTests {

    static final Path MAIN = Path.of("src/main/java");
    static final Path TEST = Path.of("src/test/java");

    static final Pattern REQUIREMENT = Pattern.compile(
        "^\\s*///\\s*-\\s*(R\\d+\\.\\d+)\\b", Pattern.MULTILINE);
    static final Pattern TESTED = Pattern.compile(
        "@(?:[\\w.]+\\.)?DisplayName\\(\\s*\"(R\\d+\\.\\d+)\\b");

    @TestFactory
    Stream<DynamicTest> everySliceMatchesItsSpec() throws IOException {
        try (var files = Files.walk(MAIN)) {
            return files
                .filter(file -> file.endsWith("package-info.java"))
                .filter(spec -> !ids(REQUIREMENT, spec).isEmpty())
                .map(spec -> MAIN.relativize(spec.getParent()))
                .map(slice -> {
                    var name = slice.getFileName().toString();
                    return dynamicTest(name, () -> {
                        var required = ids(REQUIREMENT, MAIN.resolve(slice).resolve("package-info.java"));
                        var tested = testedIds(TEST.resolve(slice));
                        assertAll(
                            () -> assertThat(without(required, tested))
                                .as(name + ": requirements without a test")
                                .isEmpty(),
                            () -> assertThat(without(tested, required))
                                .as(name + ": tests without a requirement")
                                .isEmpty());
                    });
                })
                .toList()
                .stream();
        }
    }

    static Set<String> testedIds(Path dir) {
        if (!Files.isDirectory(dir)) {
            return Set.of();
        }
        try (var files = Files.walk(dir)) {
            return files
                .filter(file -> file.toString().endsWith(".java"))
                .flatMap(file -> ids(TESTED, file).stream())
                .collect(toCollection(TreeSet::new));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static Set<String> ids(Pattern pattern, Path file) {
        try {
            return pattern.matcher(Files.readString(file)).results()
                .map(match -> match.group(1))
                .collect(toCollection(TreeSet::new));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static Set<String> without(Set<String> these, Set<String> those) {
        var rest = new TreeSet<>(these);
        rest.removeAll(those);
        return rest;
    }
}
