# Slice layout and code shapes

## Contents

- [What goes where](#what-goes-where)
- [Naming](#naming)
- [Use-case facade](#use-case-facade)
- [Documents and repositories](#documents-and-repositories)
- [Pages and markup](#pages-and-markup)
- [Events](#events)
- [Listeners](#listeners)
- [Direct calls between slices](#direct-calls-between-slices)
- [The root package](#the-root-package)
- [Imports](#imports)

## What goes where

A slice is a direct subpackage of the application's base package, and Spring Modulith treats it
as a module. Its **base package** is its public API; its **`internal`** subpackage is invisible
to everything outside the slice.

| Base package (`ordering`) | `ordering.internal` |
|---|---|
| `package-info.java` (spec + `@ApplicationModule`) | the use-case facade (`Orders`) |
| pages (`OrdersPage`) | documents (`Order`) |
| events (`OrderShipped`) | repositories (`OrderRepository`) |
| interfaces + records for direct calls, only if needed | listeners, helpers, everything else |

Ask "who outside this slice needs this type?" Nobody: `internal`. The start page links to it:
base package (pages). Another slice reacts to it: base package (events). Another slice has to
call it: an interface and its records in the base package, with the implementation in
`internal`.

Inside a slice, everything may use everything; the base package and `internal` are not layers.
The page in the base package uses the facade and the document from `internal`, which makes those
types `public`. That is fine: `public` only lets other slices *compile* against them, and
`verify()` then fails them.

Use one `internal` package per slice; don't create further subpackages until a slice really has
dozens of classes. If it does, consider splitting the slice instead.

## Naming

- Slice: one lowercase business word (`ordering`, `billing`, `shipping`).
- Facade: the plural of what the slice manages (`Orders`, `Invoices`). No `*Service`,
  `*Manager` or `*Impl`: the name says what the class is about, not what technical role it plays.
- Document: the singular noun (`Order`, `Invoice`). Repository: `<Document>Repository`.
- Page: `<Thing>Page` (`OrdersPage`), markup `<Thing>Page.html`.
- Event: what happened, in the past tense (`OrderShipped`, `InvoiceRaised`).
- Direct-call interface: named after what it answers (`OrderSummaries`), returning records named
  after what they carry (`OrderSummary`).

## Use-case facade

One class per slice in `internal`, holding the slice's operations. It is the transaction
boundary and the only place that publishes events.

```java
package com.acme.shop.ordering.internal;

@Service
@Transactional
public class Orders {

    private final OrderRepository repository;
    private final ApplicationEventPublisher events;

    Orders(OrderRepository repository, ApplicationEventPublisher events) {
        this.repository = repository;
        this.events = events;
    }

    public Order place(String customer, List<Order.Line> lines) {
        return repository.save(Order.open(customer, lines));
    }

    public List<Order> open() {
        return repository.findByShippedAtIsNull();
    }

    public void ship(String id) {
        var order = repository.findById(id).orElseThrow().ship();
        repository.save(order);
        // billing and shipping react to this; ordering never calls them
        events.publishEvent(new OrderShipped(order.id()));
    }
}
```

- Constructor injection with a package-private constructor; no field injection in Spring beans.
- Public methods are the operations from the spec. A public method with no operation is drift.
- The facade may hand documents to its own slice's page. It never hands them to other slices.

## Documents and repositories

A document is a Java record stored as one MongoDB document, shaped like the code: an order with
its lines inside, not two collections joined together. The rules live on the record, so the
facade stays thin and the rules can't be skipped.

```java
package com.acme.shop.ordering.internal;

/// Stored as one document, lines included: no join table, no mapping layer,
/// no migration script for a new field.
@Document("orders")
public record Order(@Id String id, String customer, List<Line> lines, Instant shippedAt) {

    public record Line(String product, int quantity) {}

    public static Order open(String customer, List<Line> lines) {
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("an order needs lines");
        }
        return new Order(null, customer, List.copyOf(lines), null);
    }

    public Order ship() {
        if (shippedAt != null) {
            throw new IllegalStateException("already shipped");
        }
        return new Order(id, customer, lines, Instant.now());
    }
}
```

- Rejections use plain JDK exceptions, and tests for `If … then` requirements assert exactly
  these:
  - input that is wrong in itself (blank name, no lines, a day in the past):
    `IllegalArgumentException`;
  - a request that conflicts with the current state (already shipped, already booked, a name
    that's taken): `IllegalStateException`;
  - an id that doesn't exist: the `NoSuchElementException` from `findById(id).orElseThrow()`.
    If a requirement covers unknown ids, assert that.
  When the slice can't tell two cases apart (another slice's interface returns an empty
  `Optional` for both "retired" and "unknown"), the requirement treats them as one case and
  picks one exception.
- Records are immutable: a state change returns a new record, and the facade saves it.
- New fields need no migration. Old documents read the field as `null`, so handle `null` where
  it matters.

```java
public interface OrderRepository extends MongoRepository<Order, String> {

    List<Order> findByShippedAtIsNull();
}
```

Prefer derived query methods; reach for `@Query` or `MongoTemplate` only when a derived name
gets unreadable.

## Pages and markup

Pages are plain Wicket `WebPage`s in the slice's base package. They get the facade through
`@SpringBean`, read through `LoadableDetachableModel` so lists reload on every request, and update
parts of the page with Ajax.

```java
package com.acme.shop.ordering;

public class OrdersPage extends WebPage {

    @SpringBean
    private Orders orders;

    public OrdersPage() {
        var list = new WebMarkupContainer("list");
        add(list.setOutputMarkupId(true));

        var open = LoadableDetachableModel.of(orders::open);
        list.add(new ListView<>("orders", open) {
            @Override
            protected void populateItem(ListItem<Order> item) {
                Order order = item.getModelObject();
                String id = order.id();
                item.add(new Label("customer", order.customer()));
                item.add(OatButton.onClick("ship", "Ship", ajax -> {
                    orders.ship(id);
                    Oat.toast(ajax, "Shipped", OatVariant.SUCCESS);
                    ajax.add(list);
                }));
            }
        });
    }
}
```

Markup lives in `src/main/resources/<package path>/<Page>.html`, not next to the Java file,
because the build only copies resources from `src/main/resources`:

```html
<!DOCTYPE html>
<html xmlns:wicket="http://wicket.apache.org">
<head>
    <title>Open orders</title>
</head>
<body>
<main class="container">
    <h1>Open orders</h1>

    <ul wicket:id="list">
        <li wicket:id="orders">
            <span wicket:id="customer">Jane Doe</span>
            <button wicket:id="ship">Ship</button>
        </li>
    </ul>
</main>
</body>
</html>
```

- Pages call the facade and never a repository; `ArchitectureTests` enforces that.
- Wicket stores pages serialised between requests. Capture ids, not documents, in Ajax lambdas
  (`String id = order.id()`), read lists through `LoadableDetachableModel` (it drops its value
  after each request), and keep only `Serializable` values in other models. Documents and
  another slice's records aren't `Serializable`; for a dropdown of them, map to a small
  page-local `record Choice(String id, String label) implements Serializable`.
- Mount each page in `WicketApplication` and link it from `StartPage` (see below).
- To link back to the start page, use
  `new BookmarkablePageLink<>("home", getApplication().getHomePage())`. Don't import `StartPage`:
  the root depends on the slices, not the other way round.
- For Oat components, theming and layout classes, use the `wicket-oat` skill if it is
  installed.

### Forms

A form with Oat fields and an Ajax submit button. Oat fields are panels; the actual input sits
at `<field id>:input`, which matters in page tests:

```java
var name = new Model<String>();
var capacity = new Model<Integer>();
var form = new Form<Void>("add");
add(form.setOutputMarkupId(true));
form.add(new OatTextField<>("name", "Name", name).setRequired(true));
var capacityField = new OatNumberField<>("capacity", "Capacity", capacity).setMin(1).setRequired(true);
capacityField.getInput().setType(Integer.class);
form.add(capacityField);
form.add(OatSubmitButton.onSubmit("submit", "Add room", ajax -> {
    try {
        var room = rooms.add(name.getObject(), capacity.getObject());
        Oat.toast(ajax, room.name() + " added", OatVariant.SUCCESS);
        name.setObject(null);
        capacity.setObject(null);
        ajax.add(list, form);
    } catch (IllegalArgumentException | IllegalStateException rejected) {
        Oat.toast(ajax, rejected.getMessage(), OatVariant.DANGER);
    }
}, ajax -> ajax.add(form)));
```

```html
<form wicket:id="add">
    <div wicket:id="name"></div>
    <div wicket:id="capacity"></div>
    <button wicket:id="submit">Add room</button>
</form>
```

The second lambda runs when validation fails (a required field is empty) and re-renders the
form with its messages. The page catches the slice's rejections and shows them; the rules
themselves stay in the facade and the documents.

## Events

An event is a record in the publishing slice's base package. It carries ids and whatever data
listeners need to react without calling back.

```java
package com.acme.shop.ordering;

/// Lives in the base package, so other slices may use it; everything in `internal` stays hidden.
public record OrderShipped(String orderId) {}
```

Publish it from the facade with `ApplicationEventPublisher`, inside the transaction that changed
the state. Spring Modulith stores each publication in its event publication registry (in
MongoDB) and delivers it to listeners after the commit. If a listener fails, the publication
stays incomplete and can be redelivered.

## Listeners

The listening slice reacts in its own facade with `@ApplicationModuleListener`:

```java
package com.acme.shop.billing.internal;

@Service
@Transactional
public class Invoices {

    private final InvoiceRepository repository;

    Invoices(InvoiceRepository repository) {
        this.repository = repository;
    }

    @ApplicationModuleListener
    public void on(OrderShipped event) {
        if (!repository.existsByOrderId(event.orderId())) {
            repository.save(Invoice.raise(event.orderId()));
        }
    }

    public List<Invoice> all() {
        return repository.findAll();
    }
}
```

`@ApplicationModuleListener` means: run asynchronously, in a new transaction, after the
publisher's transaction committed. Two consequences:

- **Idempotency.** Delivery is at least once, so the listener must cope with seeing the same
  event twice (the `If … then` requirement in the spec).
- **Tests.** Calling `invoices.on(...)` through the Spring bean returns before anything happened.
  `testing.md` shows how to test listeners.

The listening slice declares `allowedDependencies = "ordering"` in its `package-info.java`.

## Direct calls between slices

Use an event whenever you can. When a slice really must ask another one something now (say a
page shows the customer of an order owned by `ordering`), the called slice exposes an
**interface and the records it returns** in its base package, and implements the interface in
`internal`, usually on its facade:

```java
package com.acme.shop.ordering;

public interface OrderSummaries {

    Optional<OrderSummary> find(String id);
}
```

```java
package com.acme.shop.ordering;

public record OrderSummary(String id, String customer) {}
```

```java
package com.acme.shop.ordering.internal;

@Service
@Transactional
public class Orders implements OrderSummaries {
    // ...
    @Override
    public Optional<OrderSummary> find(String id) {
        return repository.findById(id).map(order -> new OrderSummary(order.id(), order.customer()));
    }
}
```

The caller injects `OrderSummaries` and sees nothing but the interface and the record. Never
return a document (`Order`) or take one as a parameter: the caller would depend on `ordering`'s
internals, and `verify()` fails with "depends on non-exposed type".

Records are made for the caller: only the fields it needs, named in its terms. Add the operation
to the called slice's spec and the call to the caller's `## Calls`, add the dependency to the
caller's `allowedDependencies`, and record the call under `## Components` in the root
`package-info.java`.

Name the interface's methods so they don't clash with the facade's own: Java can't overload on
the return type, so a facade with `all()` returning documents can't also implement an `all()`
returning records. If the two really want the same name, implement the interface on a separate
class in `internal` that delegates to the facade.

## The root package

The application's base package (`com.acme.shop`) is not a slice, but Spring Modulith (2.1 at
least) still checks it as a module (`root:com.acme.shop`). It may use slices' base packages only.

- `ShopApplication`: the `@SpringBootApplication` class.
- `WicketApplication`: `@Component` extending `WebApplication`; `WicketOats.install(this)` and
  one `mountPage` per slice page.
- `StartPage`: links to each slice's page with `BookmarkablePageLink`.
- `package-info.java`: the system doc (see `spec.md`).

```java
@Component
public class WicketApplication extends WebApplication {

    @Override
    public Class<? extends Page> getHomePage() {
        return StartPage.class;
    }

    @Override
    protected void init() {
        super.init();
        WicketOats.install(this);
        mountPage("/orders", OrdersPage.class);
        mountPage("/invoices", InvoicesPage.class);
    }
}
```

```java
public class StartPage extends WebPage {

    public StartPage() {
        add(new BookmarkablePageLink<>("orders", OrdersPage.class));
        add(new BookmarkablePageLink<>("invoices", InvoicesPage.class));
    }
}
```

```html
<!DOCTYPE html>
<html xmlns:wicket="http://wicket.apache.org">
<head>
    <title>Shop</title>
</head>
<body>
<main class="container">
    <h1>Shop</h1>
    <nav>
        <ul>
            <li><a wicket:id="orders">Open orders</a></li>
            <li><a wicket:id="invoices">Open invoices</a></li>
        </ul>
    </nav>
</main>
</body>
</html>
```

Before the first slice exists, the start page is just the heading with an empty `<nav>`; each
new slice adds its link. Keep business logic out of the root. Anything with business meaning
belongs in a slice.

## Imports

The code shapes above leave out imports. The ones that aren't obvious:

| Type | Package |
|---|---|
| `@ApplicationModule` | `org.springframework.modulith` |
| `@ApplicationModuleListener` | `org.springframework.modulith.events` |
| `@Document` | `org.springframework.data.mongodb.core.mapping` |
| `@Id` | `org.springframework.data.annotation` |
| `MongoRepository` | `org.springframework.data.mongodb.repository` |
| `@SpringBean` | `org.apache.wicket.spring.injection.annot` |
| `WicketOats`, `Oat`, `OatVariant` | `dev.jbaby.wicket.oat` |
| `OatButton`, `OatSubmitButton` | `dev.jbaby.wicket.oat.components` |
| `OatTextField`, `OatNumberField` | `dev.jbaby.wicket.oat.components.form` |
