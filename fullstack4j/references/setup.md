# Setting up a fullstack4j project

## Contents

- [Generate the project](#generate-the-project)
- [JDK 25](#jdk-25)
- [The pom](#the-pom)
- [MongoDB and Testcontainers](#mongodb-and-testcontainers)
- [The root package](#the-root-package)
- [First green build](#first-green-build)

## Generate the project

Use the fullstack4j generator, a Spring Initializr with Wicket on board:

```bash
curl https://start.fullstack4j.dev/starter.zip -o shop.zip \
     -d javaVersion=25 -d dependencies=wicket,data-mongodb,testcontainers,modulith
unzip shop.zip
```

Or pick the same options at https://start.fullstack4j.dev. Pass the Java version explicitly; the
generator's default is older than 25. With `wicket`, the generator adds the Wicket Spring Boot
Starter and, in test scope, its test starter.

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
    <spring-modulith.version>2.1.1</spring-modulith.version>
    <wicket.version>10.11.0</wicket.version>
</properties>
```

```xml
<!-- Wicket on Spring Boot, plus Oat components for the UI -->
<dependency>
    <groupId>dev.jbaby</groupId>
    <artifactId>wicket-spring-boot-starter</artifactId>
    <version>0.2.0</version>
</dependency>
<dependency>
    <groupId>dev.jbaby</groupId>
    <artifactId>wicket-oat-core</artifactId>
    <version>0.3.0</version>
</dependency>

<!-- Spring Modulith, with its event publication registry in MongoDB -->
<dependency>
    <groupId>org.springframework.modulith</groupId>
    <artifactId>spring-modulith-starter-core</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.modulith</groupId>
    <artifactId>spring-modulith-starter-mongodb</artifactId>
</dependency>

<!-- Tests: WicketTester and @WicketTest, at the starter's version -->
<dependency>
    <groupId>dev.jbaby</groupId>
    <artifactId>wicket-spring-boot-starter-test</artifactId>
    <version>0.2.0</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.springframework.modulith</groupId>
    <artifactId>spring-modulith-starter-test</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>testcontainers-mongodb</artifactId>
    <scope>test</scope>
</dependency>
```

```xml
<dependencyManagement>
    <dependencies>
        <!-- Wicket Oat pulls in an older wicket-extensions; keep all Wicket artifacts on one version -->
        <dependency>
            <groupId>org.apache.wicket</groupId>
            <artifactId>wicket-extensions</artifactId>
            <version>${wicket.version}</version>
        </dependency>
        <dependency>
            <groupId>org.springframework.modulith</groupId>
            <artifactId>spring-modulith-bom</artifactId>
            <version>${spring-modulith.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

The test starter brings Wicket's `wicket-tester` at the Wicket version of the starter, Spring
Boot's test starter, and `@WicketTest` for page tests (see `testing.md`). Keep both starters on the
same version, and don't add `wicket-tester` on its own. ArchUnit comes in through Spring Modulith;
it needs no dependency of its own.

## MongoDB and Testcontainers

`src/main/resources/application.properties`:

```properties
spring.application.name=shop

# Spring Modulith's event publication registry stores UUIDs
spring.mongodb.representation.uuid=standard
```

Without the UUID setting, publishing the first event fails with "The uuidRepresentation has not
been specified". Spring Boot 4 names the property `spring.mongodb.representation.uuid`; the old
`spring.data.mongodb.uuid-representation` is no longer supported.

Replace the generated test configuration with `assets/TestcontainersConfiguration.java`:
`mongo:8` started as a **replica set**. MongoDB only supports transactions on a replica set, and
Spring Modulith runs every `@ApplicationModuleListener` in a transaction.

`TestShopApplication` (generated in the root test package) starts the app on a throwaway MongoDB
in Docker:

```java
public static void main(String[] args) {
    SpringApplication.from(ShopApplication::main)
        .with(TestcontainersConfiguration.class)
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
    .with(TestcontainersConfiguration.class, DemoData.class)
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
package. In `ArchitectureTests`, adjust the `package` line, `ShopApplication.class` and the
package passed to `importPackages`; `SpecTraceabilityTests` only needs the `package` line.

## First green build

```bash
./mvnw verify
```

Docker must be running for the Testcontainers tests. Once it is green, the project is ready for
`/fullstack4j new <first slice>`.
