# The spec

Each slice's spec is the `///` Markdown Javadoc (JEP 467, Java 23+) of its base package's
`package-info.java`:

```
src/main/java/<base package>/<slice>/package-info.java
```

It travels with the code it describes, refactors with it, and renders with `javadoc`. There is no
separate `specs/` folder and no Markdown copy to keep in sync.

## Contents

- [A complete spec](#a-complete-spec)
- [Sections](#sections)
- [Requirements in EARS](#requirements-in-ears)
- [Ids](#ids)
- [Listening slices](#listening-slices)
- [Direct calls](#direct-calls) (offering and using them)
- [The system doc in the root package](#the-system-doc-in-the-root-package)
- [What the build checks](#what-the-build-checks)

## A complete spec

```java
/// # Ordering
/// > Take a customer's order and see it through until it ships.
///
/// ## Operations
/// - `place-order`: take an order for a customer (`Orders.place`)
/// - `open-orders`: list what still has to ship (`Orders.open`)
/// - `ship-order`: hand an open order over to shipping (`Orders.ship`)
///
/// ## Requirements
/// ### R1: Place an order
/// - R1.1 — When a customer places an order with at least one line,
///   the slice shall list it as open.
/// - R1.2 — If an order has no lines, then the slice shall reject it.
///
/// ### R2: Ship an order
/// - R2.1 — When an open order is shipped, the slice shall no longer
///   list it as open.
/// - R2.2 — When an open order is shipped, the slice shall publish
///   `OrderShipped`.
/// - R2.3 — If the order has already shipped, then the slice shall
///   reject the request.
///
/// ## Events
/// - publishes `OrderShipped` (R2.2)
/// - listens to nothing
///
/// ## Entities
/// - `Order`: one document with its customer, lines and status
///
/// ## Out of scope
/// - prices and invoices (`billing`)
/// - parcels and carriers (`shipping`)
@ApplicationModule(displayName = "Ordering", allowedDependencies = {})
package com.acme.shop.ordering;

import org.springframework.modulith.ApplicationModule;
```

## Sections

In this order:

1. **`# Title`** and a `>` line: what the slice is for, in one sentence. If the sentence needs
   an "and" between two unrelated jobs, it is probably two slices.
2. **`## Operations`**: what the slice offers, one bullet per operation. Each has a verb-noun id
   that says nothing about transport (`place-order`, not `POST /orders` or `onSubmit`), a short
   description, and the method that carries it out in backticks (`Orders.place`). Every
   operation is covered by at least one requirement group. Pages are not operations: they are
   one way to reach the operations. The spec says nothing about the UI unless a requirement is
   genuinely about it, so page tests normally carry no requirement id.
3. **`## Requirements`**: groups `### Rn: <title>`, each with statements `- Rn.m — <EARS
   statement>`. See below.
4. **`## Events`**: `publishes <Event> (<ids>)` and `listens to <Event> from <slice> (<ids>)`,
   or `publishes nothing` / `listens to nothing`. Write both lines even when one is "nothing",
   so a reader knows the question was considered.
5. **`## Calls`**, only if the slice calls another slice directly: one line per interface method
   it uses, with the requirements that need it, for example
   ``- `OrderSummaries.find` from `ordering` (R3)``. A reader of this one spec then sees
   everything the slice depends on.
6. **`## Entities`**: the documents the slice owns, each with a one-line description. Types
   that live only in code (a value record, an enum) don't need to be listed.
7. **`## Out of scope`**: what this slice deliberately does not do, naming the slice that does
   if there is one. This is where most scope arguments end.

Below the doc comes the module annotation:

```java
@ApplicationModule(displayName = "Ordering", allowedDependencies = {})
```

`allowedDependencies` lists the slices whose base package this slice may use: `{}` for none,
`"ordering"` to listen to `OrderShipped` or call an interface in `ordering`. Keep it exact.
`verify()` then fails on any dependency nobody declared.

A spec says *what* the slice promises, never *how*: no class names beyond the operation's
method and the entity names, no MongoDB, Wicket or Spring terms, no field-by-field data model.

## Requirements in EARS

Every statement follows one of the six EARS patterns, with "the slice" as the system:

| Pattern | Template |
|---|---|
| Ubiquitous | The slice shall `<response>`. |
| Event-driven | When `<trigger>`, the slice shall `<response>`. |
| State-driven | While `<state>`, the slice shall `<response>`. |
| Unwanted behaviour | If `<trigger>`, then the slice shall `<response>`. |
| Optional feature | Where `<feature is included>`, the slice shall `<response>`. |
| Complex | While `<state>`, when `<trigger>`, the slice shall `<response>`. |

- Every statement uses **shall**, is mandatory, and has a test. There is no "should" or "may":
  a test either passes or it doesn't. Express optional behaviour with the `Where` pattern.
- Cover the unhappy paths. Most bugs live in `If … then` (invalid input, wrong state, duplicate
  delivery) and `While …` (rules that only apply in some states).
- One behaviour per statement. If a statement needs "and" to describe two outcomes that could be
  tested separately, split it.
- A statement may end with `_(why: …)_`, a short note on where the rule comes from. It is not
  tested and not traced; it explains the rule to the next reader:

  ```java
  /// - R1.2 — If the shipped order has an invoice already, then the slice
  ///   shall not raise a second one.
  ///   _(why: events are delivered at least once)_
  ```

## Ids

- `Rn` names a group, `Rn.m` a statement inside it. Groups usually follow operations
  (`R1: Place an order`, `R2: Ship an order`).
- The id is the first thing on its bullet: `/// - R2.3 — …`. The traceability test finds
  requirements by exactly that shape.
- Ids are stable. Never renumber when you reorder or insert; add the next free number instead.
  A removed statement's id is retired and never reused, because a test or a commit somewhere may
  still name it.

## Listening slices

A slice that reacts to another slice's event names the operation after what it does, and points
to the listener method:

```java
/// # Billing
/// > Bill every order that ships.
///
/// ## Operations
/// - `bill-order`: raise an invoice for a shipped order (`Invoices.on(OrderShipped)`)
/// - `list-invoices`: list every invoice raised so far (`Invoices.all`)
///
/// ## Requirements
/// ### R1: Bill a shipped order
/// - R1.1 — When an order is shipped, the slice shall raise an invoice
///   for it.
/// - R1.2 — If the shipped order has an invoice already, then the slice
///   shall not raise a second one.
///   _(why: events are delivered at least once)_
///
/// ## Events
/// - publishes nothing
/// - listens to `OrderShipped` from `ordering` (R1)
///
/// ## Entities
/// - `Invoice`: one document per shipped order
///
/// ## Out of scope
/// - prices and payments
/// - sending invoices to customers
@ApplicationModule(displayName = "Billing", allowedDependencies = "ordering")
package com.acme.shop.billing;
```

Events are delivered at least once, so a listening slice almost always needs an `If … then`
statement for a repeated delivery.

## Direct calls

When another slice has to ask this slice something directly (see `layout.md`), the interface
method is an operation of the called slice like any other, with requirements and tests:

```java
/// - `find-order`: tell other slices who an order is for (`OrderSummaries.find`)
```

The calling slice lists the call under `## Calls`, adds the called slice to its
`allowedDependencies`, and the root's system doc records the call under `## Components`. A call
needs a reason; if an event would do, use the event.

Give the interface's methods names the facade doesn't already use. The facade usually implements
the interface, and Java can't have `Rooms.all()` returning documents next to an inherited
`all()` returning records. `BookableRooms.list` next to `Rooms.all` works.

## The system doc in the root package

The application's root `package-info.java` describes what spans slices. Keep it short, and add a
section only when there is something real to put in it:

```java
/// # Shop
/// > Take customers' orders, bill them and ship them.
///
/// ## Components
/// - `billing` listens to `ordering`'s `OrderShipped`; `ordering` knows no other slice.
/// - The root links to each slice's page and to nothing else in it.
///
/// ## Ubiquitous language
/// - Order — what a customer asked for, line by line. Owned by `ordering`.
/// - Invoice — what a shipped order is billed with. Owned by `billing`.
///
/// ## Stack
/// - fullstack4j · base package `com.acme.shop`
package com.acme.shop;
```

- **Components**: who listens to whom and who calls whom. It is the human-readable side of the
  `allowedDependencies` annotations; keep the two in step.
- **Ubiquitous language**: domain words used by more than one slice, each defined once with the
  slice that owns it.
- **Stack**: tells the next agent which skill and base package this project uses.

Don't list the slices with their one-liners here. That list drifts; each slice's own `>` line is
the source.

## What the build checks

`SpecTraceabilityTests` (in `assets/`) walks `src/main/java` for `package-info.java` files that
contain requirement ids and, for each such slice, compares them with the `@DisplayName` ids of
the tests under the same package in `src/test/java`:

- an id in the spec with no test is a **gap**: "requirements without a test";
- an id in a test that the spec doesn't have is **drift**: "tests without a requirement".

Either one fails the build. Operations and entities are not checked mechanically; keeping them
in step with the code is your job during `apply`.
