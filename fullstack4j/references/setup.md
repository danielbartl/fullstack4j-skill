# Setting up a fullstack4j project

## Contents

- [Generate the project](#generate-the-project)
- [JDK 25](#jdk-25)
- [The pom](#the-pom)
- [MongoDB and Testcontainers](#mongodb-and-testcontainers)
- [The root package](#the-root-package)
- [First green build](#first-green-build)

## Generate the project

Use the fullstack4j generator, a Spring Initializr with the fullstack4j stack on board:

```bash
curl https://start.fullstack4j.dev/starter.zip -o shop.zip \
     -d javaVersion=25 -d dependencies=fullstack4j
unzip shop.zip
```

Or pick the same options at https://start.fullstack4j.dev. Pass the Java version explicitly; the
generator's default is older than 25. With `fullstack4j`, the generator adds the fullstack4j
starter and, in test scope, its test starter (see [The pom](#the-pom)). It also generates a
test application to run the app with (`TestShopApplication` in this guide) and an application
test, both already using a MongoDB replica set (see
[MongoDB and Testcontainers](#mongodb-and-testcontainers)). Don't pick Wicket, MongoDB, Spring
Modulith or Testcontainers as well: the starters bring all four, and the generator ignores them
next to `fullstack4j`.

## JDK 25

Build and run on **JDK 25**, not newer. Wicket 10.11 creates the proxies behind `@SpringBean`
with ByteBuddy's reflection-based class injection, which needs `sun.misc.Unsafe`. From Java 26
on that is switched off by default, so every page with a `@SpringBean` field fails to
construct, in tests and in the running application alike.

Check before building:

```bash
./mvnw -v              # the JDK Maven uses; must say 25
```

If it doesn't, find a JDK 25 (with SDKMAN under `~/.sdkman/candidates/java/`, on Linux often
under `/usr/lib/jvm/`, on macOS under `/Library/Java/JavaVirtualMachines/`) and set it on the
command itself. That works in any shell, including one that keeps no state between commands
(the path below is an example; use the one you found):

```bash
JAVA_HOME=~/.sdkman/candidates/java/25.0.4-tem PATH=~/.sdkman/candidates/java/25.0.4-tem/bin:$PATH ./mvnw verify
```

`sdk use java …` only works in an interactive shell where SDKMAN is initialised. For people
using SDKMAN, a `.sdkmanrc` with `java=<identifier>` in the project root lets `sdk env` switch
automatically.

Make the build refuse any other JDK, so a wrong one fails at once with a clear message instead
of deep inside the page tests:

```xml
<plugin>
    <!-- Wicket 10.11 can't create its @SpringBean proxies on Java 26+: fail fast instead -->
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-enforcer-plugin</artifactId>
    <executions>
        <execution>
            <id>require-jdk-25</id>
            <goals>
                <goal>enforce</goal>
            </goals>
            <configuration>
                <rules>
                    <requireJavaVersion>
                        <version>[25,26)</version>
                        <message>Build with JDK 25: Wicket 10.11 can't create its @SpringBean proxies on Java 26 and later.</message>
                    </requireJavaVersion>
                </rules>
            </configuration>
        </execution>
    </executions>
</plugin>
```

Spring Boot's parent pom manages the plugin's version.

## The pom

Check that the pom has the following, and add what's missing:

```xml
<properties>
    <java.version>25</java.version>
</properties>
```

```xml
<!-- Wicket with Wicket Oat, Spring Modulith and MongoDB, at versions that work together -->
<dependency>
    <groupId>dev.jbaby</groupId>
    <artifactId>fullstack4j-spring-boot-starter</artifactId>
    <version>0.4.0</version>
</dependency>

<!-- Tests: WicketTester and @WicketTest, Spring Modulith's test support, Testcontainers,
     MongoDbReplicaSetConfiguration and the fullstack4j rules -->
<dependency>
    <groupId>dev.jbaby</groupId>
    <artifactId>fullstack4j-spring-boot-starter-test</artifactId>
    <version>0.4.0</version>
    <scope>test</scope>
</dependency>
```

These two are all the stack needs: no version properties and no `dependencyManagement`. The
parent stays `spring-boot-starter-parent` 4.1.x; the starters 0.4.x are built for Spring Boot 4.1.

- `fullstack4j-spring-boot-starter` brings the Wicket Spring Boot Starter, Wicket Oat, Spring
  Data MongoDB and Spring Modulith with its event publication registry in MongoDB. It keeps all
  Wicket artifacts on the one version the Wicket starter is built for (Wicket Oat on its own
  would pull in an older `wicket-extensions`), and picks the Spring Modulith line that goes
  with Spring Boot 4.1.
- `fullstack4j-spring-boot-starter-test` brings Wicket's `wicket-tester`, Spring Boot's test
  starter, `@WicketTest` for page tests (see `testing.md`), Spring Modulith's test support with
  ArchUnit, Testcontainers, `MongoDbReplicaSetConfiguration`, and `Fullstack4jRules`, the
  architecture rules `ArchitectureTests` runs with Taikai.

Keep both starters on the same version, and don't add any of the libraries they bring on its own:
a second declaration can pull a Wicket or Spring Modulith artifact off the version the starters
chose.

A project set up before the starters existed lists all of this separately. Replace it with the
two starters: remove the dependencies on Wicket, Wicket Oat, Spring Modulith, MongoDB and
Testcontainers and their test starters, the `wicket-extensions` and `spring-modulith-bom`
entries in `dependencyManagement`, and the `spring-modulith.version` and `wicket.version`
properties. Then follow the next section for the test configuration.

## MongoDB and Testcontainers

Spring Modulith's event publication registry stores UUIDs, and the MongoDB driver refuses to
write them until the UUID representation is set. The starter sets
`spring.mongodb.representation.uuid=standard` as a default, so `application.properties` needs
nothing for it; a value set there still wins. If publishing the first event fails with "The
uuidRepresentation has not been specified", the starter is missing or something overrides the
setting. Spring Boot 4 names the property `spring.mongodb.representation.uuid`; the old
`spring.data.mongodb.uuid-representation` is no longer supported.

Tests and the development run get their database from `MongoDbReplicaSetConfiguration`, which the
test starter brings (package `dev.jbaby.fullstack4j.springboot.test`): `mongo:8.0` in Docker,
started as a **replica set**. MongoDB only supports transactions on a replica set, and Spring
Modulith runs every `@ApplicationModuleListener` in a transaction. Every test that touches
MongoDB imports it with `@Import(MongoDbReplicaSetConfiguration.class)` (see `testing.md`). Don't
write a test configuration with a MongoDB container of your own; if the project already has one,
such as a `TestcontainersConfiguration` from an earlier setup, delete it and import
`MongoDbReplicaSetConfiguration` wherever it was imported. To run another image, set
`fullstack4j.test.mongodb.image`, or the environment variable `FULLSTACK4J_TEST_MONGODB_IMAGE`
for one machine.

`TestShopApplication` (generated in the root test package) starts the app on a throwaway MongoDB
in Docker:

```java
public static void main(String[] args) {
    SpringApplication.from(ShopApplication::main)
        .with(MongoDbReplicaSetConfiguration.class)
        .run(args);
}
```

```bash
./mvnw spring-boot:test-run      # http://localhost:8080
```

To have something to click on, add a test-only configuration that places a few records through
the slices' facades, and pass it to `.with(…)` as well. It lives in test code, so it never ships
and `verify()` doesn't mind that it uses `internal` types:

```java
/// A few orders for `TestShopApplication`, so the pages have something to show.
/// One of them ships, so `billing` raises an invoice for it. Test code only.
@TestConfiguration(proxyBeanMethods = false)
class DemoData {

    @Bean
    ApplicationRunner placeDemoOrders(Orders orders) {
        return args -> {
            orders.place("ACME", List.of(new Order.Line("Widget", 3)));
            var shipped = orders.place("Initech", List.of(new Order.Line("Stapler", 1)));
            orders.ship(shipped.id());
        };
    }
}
```

```java
SpringApplication.from(ShopApplication::main)
    .with(MongoDbReplicaSetConfiguration.class, DemoData.class)
    .run(args);
```

Give the `@Bean` method a name that differs from the class name in lower camel case: a class
`SampleData` with a bean method `sampleData()` fails at startup because both register a bean
called `sampleData`.

## The root package

Create `WicketApplication` and `StartPage` in the base package, and the system doc in its
`package-info.java` (shapes in `layout.md` and `spec.md`). With no slice yet, the start page is
a heading and an empty `<nav>`; each slice adds its link later. Give it a test right away
(`StartPageTests` in `testing.md`). The generated `ShopApplication` stays as it is.

Copy `assets/ArchitectureTests.java` and `assets/SpecTraceabilityTests.java` into the root test
package. In `ArchitectureTests`, adjust the `package` line and `ShopApplication.class`;
`SpecTraceabilityTests` only needs the `package` line. The fullstack4j rules pass on a project
without slices, so `ArchitectureTests` is green from the first build.

## First green build

```bash
./mvnw verify
```

Docker must be running for the Testcontainers tests. Once it is green, the project is ready for
`/fullstack4j new <first slice>`.
