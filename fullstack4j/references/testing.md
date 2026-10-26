# Testing

Four kinds of test, all run by `./mvnw verify`:

| Kind | Proves | Where |
|---|---|---|
| Requirement tests | each `Rn.m` of a slice's spec | `src/test/java/<base>/<slice>/<Facade>Tests.java` |
| Page tests | markup, `wicket:id`s and injection fit together | `src/test/java/<base>/<slice>/<Page>Tests.java` |
| `ArchitectureTests` | slices stay out of each other's `internal`, no cycles, and the fullstack4j rules: what a slice holds, how events travel, what pages keep, no `@Disabled` | root test package |
| `SpecTraceabilityTests` | every `Rn.m` has a test, every test id has an `Rn.m` | root test package |

Every test that touches MongoDB runs against a real MongoDB in Docker via Testcontainers
(`MongoDbReplicaSetConfiguration` from the test starter, see `setup.md`). There are no mocks of
repositories and no in-memory database, so what passes here also works in production. The one
thing a requirement test does replace is another slice's interface: see
[A slice that calls another](#a-slice-that-calls-another).

Test code may use any slice's `internal` types: `verify()` and the fullstack4j rules only check
main code, apart from the rule against `@Disabled`. A slice's tests use its own facade and repository; a page test or seed data may use
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
- [When the fullstack4j rules fail](#when-the-fullstack4j-rules-fail)

## Requirement tests

One test class per slice facade, in the slice's **base** test package, bootstrapping only that
slice with `@ApplicationModuleTest`. One `@Test` per requirement id, with the id first in
`@DisplayName`:

```java
package com.acme.shop.ordering;

/// Each test starts with the id of the requirement it proves.
/// The requirements live in this slice's `package-info.java`.
@ApplicationModuleTest
@Import(MongoDbReplicaSetConfiguration.class)
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
@Import(MongoDbReplicaSetConfiguration.class)
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
@Import(MongoDbReplicaSetConfiguration.class)
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
`WicketTester` and a `WicketTestkit` for each test: declare them as parameters of the `@Test`
method (or of a `@BeforeEach`, which then gets the same ones). The tester runs a new instance of
`WicketApplication`, with `@SpringBean` injection, and is destroyed after the test. No servlet
container starts.

The testkit, from wicket-testkit, which the test starter brings, wraps that tester. Find
components with its locators: by type, `wicket:id`, label and the row of a list. Don't address them
by component paths such as `"list:orders:0:ship"`. A path spells out the whole tree on the way to
the component, so a container wrapped around the list breaks it, and a row index silently checks
the wrong row once the order changes. For the testkit's API and what its failure messages ask you
to change, use the `wicket-testkit` skill if it is installed.

```java
package com.acme.shop.ordering;

/// Renders the page in the whole application: proves markup, wicket:ids
/// and injection fit together. The rules themselves are proven in `OrdersTests`.
@WicketTest
@Import(MongoDbReplicaSetConfiguration.class)
class OrdersPageTests {

    @Autowired Orders orders;
    @Autowired OrderRepository repository;

    @BeforeEach
    void startEmpty() {
        repository.deleteAll();
    }

    @Test
    void listsOpenOrders(WicketTester tester, WicketTestkit kit) {
        orders.place("ACME", List.of(new Order.Line("Widget", 1)));

        tester.startPage(OrdersPage.class);

        tester.assertRenderedPage(OrdersPage.class);
        kit.assertRows(locate(ListView.class).withId("orders"), """
            | customer |
            | ACME     |
            """);
    }

    @Test
    void shipsAnOrderFromTheList(WicketTester tester, WicketTestkit kit) {
        var order = orders.place("ACME", List.of(new Order.Line("Widget", 1)));
        tester.startPage(OrdersPage.class);

        kit.click(locate(OatButton.class).withId("ship").inRowOf(order));

        kit.assertOnAjaxResponse(locate(WebMarkupContainer.class).withId("list"));
        assertThat(orders.open()).isEmpty();
    }
}
```

- `inRowOf(order)` finds the row whose element equals the order. Documents are records, so that
  works as long as every field reads back unchanged. MongoDB keeps an `Instant` only to the
  millisecond, so for a document with a timestamp set, match the row by id:
  `inRowOf(Order.class, o -> o.id().equals(id), "has id " + id)`.
- `assertRows` compares a list's rows with a table whose header names the `wicket:id`s to read,
  in the order the page shows them. When the order matters, sort in the query.
- Every locator has to match exactly one component. For "none", use `kit.assertCount(locator, 0)`.
- For a `WicketTester` method the testkit doesn't wrap, pass it `kit.pathOf(locator)`.

The tester runs the application's own `WebApplication` bean, so `WicketApplication` has to be a
`@Component` (see `layout.md`). Without one, the starter's placeholder application answers instead,
and `WicketOats.install` never runs.

A form (see "Forms" in `layout.md`) is filled through the testkit's form helper. An Oat field is a
panel that hands its label to the input inside it, so find the input by its label. An
`OatSubmitButton` is an `AjaxButton`, so `submit` sends the form through Ajax:

```java
@Test
void addsARoomFromTheForm(WicketTester tester, WicketTestkit kit) {
    tester.startPage(RoomsPage.class);

    kit.form(locate(Form.class).withId("add"))
        .set(locate(TextField.class).withLabel("Name"), "Atlas")
        .set(locate(TextField.class).withLabel("Capacity"), "8")
        .submit(locate(OatSubmitButton.class).withId("submit"));

    kit.assertOnAjaxResponse(locate(WebMarkupContainer.class).withId("list"));
    assertThat(rooms.all()).extracting(Room::name, Room::capacity)
        .containsExactly(tuple("Atlas", 8));
}
```

A required field left empty shows its message on the field itself; check it there with
`kit.assertFeedbackOn(locate(TextField.class).withLabel("Name"), "'Name' is required.")`.

## Start page

The root's `StartPage` has no slice of its own, so give it a small test of its own; otherwise a
broken link or missing markup there never shows up in `verify`:

```java
@WicketTest
@Import(MongoDbReplicaSetConfiguration.class)
class StartPageTests {

    @Test
    void linksToEverySlice(WicketTester tester, WicketTestkit kit) {
        tester.startPage(StartPage.class);

        tester.assertRenderedPage(StartPage.class);
        tester.assertBookmarkablePageLink(kit.pathOf(locate(BookmarkablePageLink.class).withId("orders")),
            OrdersPage.class, new PageParameters());
        tester.assertBookmarkablePageLink(kit.pathOf(locate(BookmarkablePageLink.class).withId("invoices")),
            InvoicesPage.class, new PageParameters());
    }
}
```

## Imports

| Type | Package |
|---|---|
| `@ApplicationModuleTest`, `Scenario` | `org.springframework.modulith.test` |
| `@MockitoBean` | `org.springframework.test.context.bean.override.mockito` |
| `AopTestUtils` | `org.springframework.test.util` |
| `MongoDbReplicaSetConfiguration` | `dev.jbaby.fullstack4j.springboot.test` |
| `@WicketTest` | `dev.jbaby.wicket.springboot.test` |
| `WicketTester` | `org.apache.wicket.util.tester` |
| `WicketTestkit` | `dev.jbaby.wicket.testkit` |
| `locate` (static import from `Locator`) | `dev.jbaby.wicket.testkit.locator` |
| `OatButton`, `OatSubmitButton` | `dev.jbaby.wicket.oat.components` |
| `PageParameters` | `org.apache.wicket.request.mapper.parameter` |

## Architecture and traceability

Copy `ArchitectureTests.java` and `SpecTraceabilityTests.java` from `assets/` into the root test
package once per project, and adjust the `package` line and the application class name.

- `slicesStayOutOfEachOthersInternals`: Spring Modulith's `verify()`. Fails on cycles between
  slices, on any use of another slice's `internal` package (from the root too), and on
  dependencies missing from `allowedDependencies`.
- `followsTheFullstack4jRules`: `Fullstack4jRules` from the test starter, run with Taikai. It
  checks what `verify()` leaves alone and reports every violation in one run; the rules and
  their fixes are under [When the fullstack4j rules fail](#when-the-fullstack4j-rules-fail).
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
| `uuidRepresentation has not been specified` | The fullstack4j starter is missing from the pom, or a property overrides its `spring.mongodb.representation.uuid=standard` (see `setup.md`). |
| `Cannot define class using reflection … As of Java 26` / `error while injecting object … Page` | The build runs on Java 26 or later. Switch to JDK 25 (see `setup.md`). |
| A listener test sees no effect, or sees it only sometimes | The listener is asynchronous. Use `Scenario.publish(…).andWaitForStateChange(…)`, or call the target bean as shown under [Repeated delivery](#repeated-delivery). |
| `Could not find a valid Docker environment` | Testcontainers needs a running Docker (or compatible) daemon. |
| `Container startup failed for image mongo:8.0`, and the container log says `Linux kernel versions 6.19 and newer has a known incompatibility` | The kernel running Docker is between 6.19 and 7.0.13, which MongoDB 8 refuses (`uname -r`). Not a code problem: tell the user. Until the kernel is 7.0.14 or later, run the build with `FULLSTACK4J_TEST_MONGODB_IMAGE=mongo:7.0`. Don't switch to MongoDB 9.0 or 8.2; they start but have the same bug. |
| `No ParameterResolver registered for parameter [… WicketTester …]` | The test class has `@SpringBootTest` instead of `@WicketTest`. |
| `MarkupNotFoundException` | The page's HTML is missing, misnamed, or not under `src/main/resources/<package path>/`. A stale copy in `target/classes` can hide this until the next `clean`. |

## When the fullstack4j rules fail

`followsTheFullstack4jRules` fails with `Found N Taikai violations for M rules!`, then one
`Rule: …` line per broken rule, followed by its violations. Find each violation below. Fix the
code: don't exclude classes, drop a rule or edit `ArchitectureTests` to get past it. If a
violation looks deliberate, ask the user.

| Violation | Meaning and fix |
|---|---|
| `… is in slice shared, which isn't named after a business capability` | The package is named after a technical layer (`web`, `shared`, `util`, `config`, …). Move its classes into the slice that owns the concept, or propose a new slice named after a business capability to the user. |
| `… is in ordering.web, but below a slice there is only ordering.internal` | Move the class to `ordering` if other slices need it (see `layout.md`, What goes where), otherwise to `ordering.internal`. |
| `… is in the base package of billing, but is no Wicket component, interface, record or enum` | A facade, helper or other class sits in the slice's public API. Move it to `billing.internal`. |
| `Class <…> does not reside in a package '….*.internal..'` | A `@Document` is outside its slice's `internal`. Move it there. If another slice needs its data, publish an event or offer an interface with a record. |
| `no classes that are in a slice's base package or assignable to org.apache.wicket.Component should depend on classes that are repositories …` | A page, or a type in a slice's base package, uses a repository, Spring Data or the MongoDB driver. Give the facade a method for it and call that. |
| `… listens to …, which is a document` or `…, which isn't a record` | Listen to an event record from the publishing slice's base package. An event carries ids and values, never a document. |
| `… creates com.acme.shop.ordering.OrderShipped, an event of ordering` | Only `ordering` may publish `OrderShipped`. To make `ordering` act, call an interface it offers (`layout.md`, Direct calls between slices). If none fits, ask the user whether the spec is missing an operation. |
| `Field <…Page.order> has raw type a document or a record that isn't Serializable` | The page keeps a document between requests. Keep its id and load it through the facade or a `LoadableDetachableModel`. A field named `val$order` is a local variable captured by an anonymous component: capture the id instead. |
| `….OrdersPage.refresh() is annotated with @Transactional` (or a page with `@Component`, `@Async`, `@EventListener`) | Spring never manages a page, so the annotation does nothing. Move the work to the facade and call it from the page. |
| `Classes should not have names matching .*(Service\|Impl\|Dto)` | Rename the class after what it holds or carries (`Orders`, `OrderSummary`). Update the spec's `## Operations` if it names the class. |
| `No fields should be annotated with …Autowired` | Use constructor injection. Pages get beans through `@SpringBean`. |
| `… the self-invocation bypasses the Spring proxy …` | A bean calls its own `@Transactional`, `@Async` or listener method, so the annotation doesn't apply to that call. Move the shared work into a private method and call that from both places. |
| `Methods should not be annotated with org.junit.jupiter.api.Disabled` (or `Classes …`) | Make the test pass. If its requirement is gone, delete the test and ask the user about the spec. |
