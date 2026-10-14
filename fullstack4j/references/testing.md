# Testing

Four kinds of test, all run by `./mvnw verify`:

| Kind | Proves | Where |
|---|---|---|
| Requirement tests | each `Rn.m` of a slice's spec | `src/test/java/<base>/<slice>/<Facade>Tests.java` |
| Page tests | markup, `wicket:id`s and injection fit together | `src/test/java/<base>/<slice>/<Page>Tests.java` |
| `ArchitectureTests` | slices stay out of each other's `internal`, no cycles, UI never touches repositories | root test package |
| `SpecTraceabilityTests` | every `Rn.m` has a test, every test id has an `Rn.m` | root test package |

Every test that touches MongoDB runs against a real MongoDB in Docker via Testcontainers
(`TestcontainersConfiguration` in `assets/`). There are no mocks of repositories and no
in-memory database, so what passes here also works in production. The one thing a requirement
test does replace is another slice's interface: see
[A slice that calls another](#a-slice-that-calls-another).

Test code may use any slice's `internal` types: `verify()` and the ArchUnit rules only check
main code. A slice's tests use its own facade and repository; a page test or seed data may use
another slice's facade to set things up.

Run one test class while you work (`./mvnw test -Dtest=OrdersTests`, with JDK 25 set as in
`setup.md`), and the whole `./mvnw verify` before you call it done.

## Contents

- [Requirement tests](#requirement-tests)
- [States the operations can't reach](#states-the-operations-cant-reach)
- [Publishing an event](#publishing-an-event)
- [Listening to an event](#listening-to-an-event)
- [Repeated delivery](#repeated-delivery)
- [A slice that calls another](#a-slice-that-calls-another)
- [Page tests](#page-tests)
- [Start page](#start-page)
- [Imports](#imports)
- [Architecture and traceability](#architecture-and-traceability)
- [When verify fails](#when-verify-fails)

## Requirement tests

One test class per slice facade, in the slice's **base** test package, bootstrapping only that
slice with `@ApplicationModuleTest`. One `@Test` per requirement id, with the id first in
`@DisplayName`:

```java
package com.acme.shop.ordering;

/// Each test starts with the id of the requirement it proves.
/// The requirements live in this slice's `package-info.java`.
@ApplicationModuleTest
@Import(TestcontainersConfiguration.class)
class OrdersTests {

    static final List<Order.Line> ONE_WIDGET = List.of(new Order.Line("Widget", 1));

    @Autowired Orders orders;
    @Autowired OrderRepository repository;

    @BeforeEach
    void startEmpty() {
        repository.deleteAll();
    }

    @Test
    @DisplayName("R1.1 a placed order is listed as open")
    void placedOrderIsOpen() {
        var order = orders.place("ACME", ONE_WIDGET);

        assertThat(orders.open()).extracting(Order::id).containsExactly(order.id());
    }

    @Test
    @DisplayName("R1.2 an order without lines is rejected")
    void orderWithoutLinesIsRejected() {
        assertThatThrownBy(() -> orders.place("ACME", List.of()))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("R2.3 an order cannot ship twice")
    void orderCannotShipTwice() {
        var order = orders.place("ACME", ONE_WIDGET);
        orders.ship(order.id());

        assertThatThrownBy(() -> orders.ship(order.id()))
            .isInstanceOf(IllegalStateException.class);
    }
}
```

- The `@DisplayName` starts with the id and a space (`"R2.3 an order cannot ship twice"`);
  `SpecTraceabilityTests` reads exactly that.
- One test per id, not one parameterized test per group: the statements of a group usually check
  different things (a list, an exception, an event), which don't share one test body.
- Test through the facade, the way the page and other slices use the slice. The repository is
  only there to reset state.
- Start every test from an empty collection (`deleteAll()` in `@BeforeEach`). Tests share one
  MongoDB container per Spring context.
- An id may have more than one test when one statement needs several cases. Each of them starts
  with the same id.

## States the operations can't reach

Some requirements are about states the operations can't create: a booking from yesterday can
only age into the past, it can't be booked there. Save such a document directly through the
repository in the test's setup; that is setup, not a shortcut around the rules under test.

For rules about "today" or "now", inject a `java.time.Clock` into the facade and call
`LocalDate.now(clock)`. Declare the production clock once on the application class:

```java
@Bean
Clock clock() {
    return Clock.systemDefaultZone();
}
```

and fix it in the test, so the requirement no longer depends on the day the build runs:

```java
@ApplicationModuleTest
@Import(TestcontainersConfiguration.class)
class BookingsTests {

    @TestConfiguration
    static class FixedClock {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-03-01T09:00:00Z"), ZoneOffset.UTC);
        }
    }
    // ...
}
```

## Publishing an event

Spring Modulith's `Scenario` is injected as a test method parameter. `stimulate` runs the
operation and waits for the event:

```java
@Test
@DisplayName("R2.2 shipping an order publishes OrderShipped")
void shippingPublishesOrderShipped(Scenario scenario) {
    var order = orders.place("ACME", ONE_WIDGET);

    scenario.stimulate(() -> orders.ship(order.id()))
        .andWaitForEventOfType(OrderShipped.class)
        .toArriveAndVerify(event -> assertThat(event.orderId()).isEqualTo(order.id()));
}
```

## Listening to an event

`publish` sends the event the way the other slice would, then waits until the listener's effect
is visible. The listener runs asynchronously, so assert on the state you waited for, not right
after `publish`:

```java
@Test
@DisplayName("R1.1 a shipped order gets an invoice")
void shippedOrderIsBilled(Scenario scenario) {
    scenario.publish(new OrderShipped("order-1"))
        .andWaitForStateChange(invoices::all, all -> !all.isEmpty())
        .andVerify(all -> assertThat(all).extracting(Invoice::orderId).containsExactly("order-1"));
}
```

## Repeated delivery

To prove a listener is idempotent, deliver the same event twice, one after the other. Through
the Spring bean, `@ApplicationModuleListener` makes each call asynchronous: the calls race and
the assertion runs before either finished. Call the bean itself instead:

```java
@Test
@DisplayName("R1.2 an order shipped twice is billed once")
void orderIsBilledOnce() {
    // through the Spring proxy the listener runs asynchronously, so
    // deliver the event twice to the bean itself, one after the other
    Invoices listener = AopTestUtils.getTargetObject(invoices);
    listener.on(new OrderShipped("order-1"));
    listener.on(new OrderShipped("order-1"));

    assertThat(invoices.all()).hasSize(1);
}
```

Delivery itself is already covered by the `Scenario` test above; this one is about the rule.

## A slice that calls another

`@ApplicationModuleTest` starts only the slice under test, so the interface it calls on another
slice has no bean. Replace it with `@MockitoBean` and stub what the requirements need. The
interface is the agreed contract between the two slices, so standing in for it keeps the
slice's tests independent of the other slice's rules, which that slice's own tests prove:

```java
/// Only this slice is bootstrapped, so `rooms` is stood in for by a mock of
/// `BookableRooms`, the one thing this slice asks it.
@ApplicationModuleTest
@Import(TestcontainersConfiguration.class)
class BookingsTests {

    static final BookableRoom ATLAS = new BookableRoom("atlas", "Atlas", 8);

    @Autowired Bookings bookings;
    @Autowired BookingRepository repository;
    @MockitoBean BookableRooms rooms;

    @BeforeEach
    void startEmpty() {
        repository.deleteAll();
        when(rooms.find(anyString())).thenReturn(Optional.empty());
        when(rooms.find(ATLAS.id())).thenReturn(Optional.of(ATLAS));
    }

    // ... one @Test per requirement id, as above
}
```

Page tests (`@WicketTest`) and `@SpringBootTest` start the whole application, so there the real
implementation answers.

## Page tests

Page tests render a page in the whole application with Wicket's `WicketTester`. They prove the
page works (markup and `wicket:id`s match, `@SpringBean` injection works, Ajax updates the right
component), not the business rules, which the requirement tests already cover. So they carry no
requirement id unless a requirement is genuinely about the UI.

`@WicketTest`, from the starter's test starter (see `setup.md`), is `@SpringBootTest` plus a
`WicketTester` for each test: declare it as a parameter of the `@Test` method (or of a
`@BeforeEach`, which then gets the same one). It runs a new instance of `WicketApplication`, with
`@SpringBean` injection, and is destroyed after the test. No servlet container starts.

```java
package com.acme.shop.ordering;

/// Renders the page in the whole application: proves markup, wicket:ids
/// and injection fit together. The rules themselves are proven in `OrdersTests`.
@WicketTest
@Import(TestcontainersConfiguration.class)
class OrdersPageTests {

    @Autowired Orders orders;
    @Autowired OrderRepository repository;

    @BeforeEach
    void startEmpty() {
        repository.deleteAll();
    }

    @Test
    void listsOpenOrders(WicketTester tester) {
        orders.place("ACME", List.of(new Order.Line("Widget", 1)));

        tester.startPage(OrdersPage.class);

        tester.assertRenderedPage(OrdersPage.class);
        tester.assertLabel("list:orders:0:customer", "ACME");
    }

    @Test
    void shipsAnOrderFromTheList(WicketTester tester) {
        orders.place("ACME", List.of(new Order.Line("Widget", 1)));
        tester.startPage(OrdersPage.class);

        tester.clickLink("list:orders:0:ship");

        tester.assertComponentOnAjaxResponse("list");
        assertThat(orders.open()).isEmpty();
    }
}
```

The tester runs the application's own `WebApplication` bean, so `WicketApplication` has to be a
`@Component` (see `layout.md`). Without one, the starter's placeholder application answers instead,
and `WicketOats.install` never runs. Component paths are the `wicket:id`s joined with `:`, with list
items numbered from 0.

A form (see "Forms" in `layout.md`) is filled through a `FormTester`. Oat fields are panels, so
the value goes to `<field id>:input`, and the Ajax submit button is clicked with an event:

```java
@Test
void addsARoomFromTheForm(WicketTester tester) {
    tester.startPage(RoomsPage.class);

    var form = tester.newFormTester("add");
    form.setValue("name:input", "Atlas");
    form.setValue("capacity:input", "8");
    tester.executeAjaxEvent("add:submit", "click");

    tester.assertComponentOnAjaxResponse("list");
    assertThat(rooms.all()).extracting(Room::name, Room::capacity)
        .containsExactly(tuple("Atlas", 8));
}
```

## Start page

The root's `StartPage` has no slice of its own, so give it a small test of its own; otherwise a
broken link or missing markup there never shows up in `verify`:

```java
@WicketTest
@Import(TestcontainersConfiguration.class)
class StartPageTests {

    @Test
    void linksToEverySlice(WicketTester tester) {
        tester.startPage(StartPage.class);

        tester.assertRenderedPage(StartPage.class);
        tester.assertBookmarkablePageLink("orders", OrdersPage.class, new PageParameters());
        tester.assertBookmarkablePageLink("invoices", InvoicesPage.class, new PageParameters());
    }
}
```

## Imports

| Type | Package |
|---|---|
| `@ApplicationModuleTest`, `Scenario` | `org.springframework.modulith.test` |
| `@MockitoBean` | `org.springframework.test.context.bean.override.mockito` |
| `AopTestUtils` | `org.springframework.test.util` |
| `@WicketTest` | `dev.jbaby.wicket.springboot.test` |
| `WicketTester`, `FormTester` | `org.apache.wicket.util.tester` |
| `PageParameters` | `org.apache.wicket.request.mapper.parameter` |

## Architecture and traceability

Copy `ArchitectureTests.java` and `SpecTraceabilityTests.java` from `assets/` into the root test
package once per project, and adjust the `package` line and the application class name.

- `slicesStayOutOfEachOthersInternals`: Spring Modulith's `verify()`. Fails on cycles between
  slices, on any use of another slice's `internal` package (from the root too), and on
  dependencies missing from `allowedDependencies`.
- `uiNeverTouchesRepositories`: no Wicket component depends on a Spring Data repository.
- `documentsTheArchitecture`: writes PlantUML and AsciiDoc module docs to
  `target/spring-modulith-docs`.
- `SpecTraceabilityTests`: one dynamic test per slice with requirements, failing on gaps and drift.

## When verify fails

| Failure | Meaning and fix |
|---|---|
| `Module 'x' depends on non-exposed type … within module 'y'` | `x` (or the root) uses something from `y.internal`. If `x` should react, publish an event. If it must ask, add an interface and records to `y`'s base package. Don't move documents or facades to the base package to make it pass. |
| `Module 'x' depends on module 'y' via … Allowed targets: …` | `y` is missing from `x`'s `allowedDependencies`. Add it to `@ApplicationModule(allowedDependencies = …)` and record it in the root's `## Components`, but only if the dependency is intended. |
| `Cycle detected` | Two slices depend on each other. Turn one direction into an event. |
| `[ordering: requirements without a test]` … `R2.3` | Write the test for that id in that slice. While you apply several slices one by one, the ones you haven't reached yet show up here too; that's expected. |
| `[ordering: tests without a requirement]` … `R4.1` | A test names an id the spec doesn't have. Ask the user: declare it in the spec, or remove the test. |
| `uuidRepresentation has not been specified` | Add `spring.mongodb.representation.uuid=standard` to `application.properties` (see `setup.md`). |
| `Cannot define class using reflection … As of Java 26` / `error while injecting object … Page` | The build runs on Java 26 or later. Switch to JDK 25 (see `setup.md`). |
| A listener test sees no effect, or sees it only sometimes | The listener is asynchronous. Use `Scenario.publish(…).andWaitForStateChange(…)`, or call the target bean as shown under [Repeated delivery](#repeated-delivery). |
| `Could not find a valid Docker environment` | Testcontainers needs a running Docker (or compatible) daemon. |
| `No ParameterResolver registered for parameter [… WicketTester …]` | The test class has `@SpringBootTest` instead of `@WicketTest`. |
| `MarkupNotFoundException` | The page's HTML is missing, misnamed, or not under `src/main/resources/<package path>/`. A stale copy in `target/classes` can hide this until the next `clean`. |
