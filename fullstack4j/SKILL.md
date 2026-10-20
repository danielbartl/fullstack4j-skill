---
name: fullstack4j
description: Build and change features in a fullstack4j application (Spring Boot, Apache Wicket, Spring Modulith, MongoDB), where every feature is one slice that carries its own spec in package-info.java. Use it whenever you add or change a feature, page, use case, MongoDB document, event or call between slices in such a project; when you write or update requirements (EARS statements with ids like R2.3); when you set up a new fullstack4j project; or when ArchitectureTests or SpecTraceabilityTests fail, even if the user never says "fullstack4j". You can recognise such a project by Wicket pages inside Spring Modulith modules and package-info.java files with a "## Requirements" section. Invoke as `/fullstack4j new <slice-or-feature>` to declare a spec, or `/fullstack4j apply <slice>` to make the code match it.
argument-hint: "new|apply <slice-or-feature>"
---

# fullstack4j

fullstack4j lets a small team build business applications in plain Java. One feature is one
**slice**: a Spring Modulith module that holds its Wicket page, its use cases, its MongoDB
documents and its events. Each slice states what it promises in its own `package-info.java`,
tests prove every promise by its id, and Spring Modulith keeps the slices apart. This skill runs
that loop: declare the spec with the user, then change the code until `./mvnw verify` is green.

## The rules, and why they exist

1. **One feature, one slice.** A slice is a direct subpackage of the application's base package
   (`com.acme.shop.ordering`) and a Spring Modulith module. Name it after a business capability,
   as one lowercase word (`ordering`, `billing`), never after a technical layer (`web`,
   `service`, `persistence`). One person can then own a feature from button to database without
   waiting on anyone else's layer.
2. **The spec lives in the slice.** The slice's `package-info.java` holds its spec as `///`
   Markdown: purpose, operations, numbered requirements, events, entities, out of scope. It is
   the source of truth. When code and spec disagree, change the code, or ask the user whether the
   spec should change; never quietly edit the spec to match the code. Format:
   [references/spec.md](references/spec.md).
3. **Base package public, `internal` hidden.** A slice's base package holds what others may use:
   the spec, its pages, its events, and, only when needed, interfaces plus the records they
   return. Everything else (the use-case facade, documents, repositories, helpers) goes in
   `<slice>.internal`. This is Spring Modulith's default, and `verify()` fails as soon as code
   outside a slice, the application's root package included, touches its `internal` package.
   The fullstack4j rules in `ArchitectureTests` fail when anything else lands in the base
   package. Test code is exempt: both only check main code, so a slice's tests use its
   `internal` types freely.
4. **Events first, calls when needed.** Slices react to each other's events, so the publisher
   never needs to know who listens. When an event doesn't fit (a page has to show another
   slice's data right now), the called slice offers an interface and the records it returns in
   its base package and implements the interface in `internal`. Documents never cross a slice
   boundary, so each slice can change its storage without breaking anyone.
5. **Every requirement has a test, every test has a requirement.** Each id `Rn.m` in a spec has
   at least one test whose `@DisplayName` starts with that id, and no test names an id the spec
   doesn't have. `SpecTraceabilityTests` checks both directions and fails the build on a gap or
   on drift. Fix a failing test or delete it with its requirement; never switch it off.
   `ArchitectureTests` fails on `@Disabled`, because a disabled test would still count as
   proving its id.
6. **Done means `./mvnw verify` is green on JDK 25.** Not your own judgement: run it, read the
   failures, fix, run again. Use JDK 25 specifically, because Wicket 10.11 cannot create its
   `@SpringBean` proxies on Java 26 and later. Your shell may not keep state between commands,
   so set the JDK on every Maven call:
   `JAVA_HOME=<path to JDK 25> PATH=<path to JDK 25>/bin:$PATH ./mvnw verify`
   ([references/setup.md](references/setup.md) shows how to find it and how to make the build
   refuse other JDKs).

## Modes

Read the mode and the slice or feature from the invocation (`/fullstack4j apply ordering`). If
the mode is missing, infer it: no spec yet means `new`; a spec whose tests are missing or red
means `apply`; otherwise ask.

### `new`: declare a slice or extend one

The input is a slice name (`/fullstack4j new returns`) or a feature description in plain words.

1. **Read what exists.** Read every slice's `package-info.java` and the root package's
   `package-info.java`. Decide whether the feature is a new slice, an extension of an existing
   one, or spans several.
2. **Propose the carving and get it confirmed** before writing anything: the slice names, a
   one-line purpose for each, and whether each is new or extended. No test can tell you whether
   a carving is right, so the user decides.
3. **Clarify until the spec writes itself.** For each operation: what triggers it, what it
   returns or changes, which inputs are invalid, which states forbid it. Across the slice: the
   entities and their fields, what is in and out of scope, which events it publishes or listens
   to, and whether it needs a direct call. Ask one specific question at a time and offer options.
   If you would lean on a default, name it ("I'd assume an order can't be edited after
   shipping; right?") instead of assuming silently. Stop when another engineer would write the
   same spec from the answers. For a small, well-understood feature you may instead show a
   complete draft spec with every default you chose marked, and let the user correct it; that
   saves a long series of questions.
4. **Write the spec** into the slice's `package-info.java` following
   [references/spec.md](references/spec.md). When extending, add new ids and never renumber or
   reuse old ones.
5. **For a new slice**, create its package with only the `package-info.java`, including the
   `@ApplicationModule` annotation. Write no other code in this mode.
6. **Update the root `package-info.java`** if slices now listen to or call each other
   (`## Components`) or if a new shared term appears (`## Ubiquitous language`).
7. **Report** the operations and requirement ids you declared, and suggest
   `/fullstack4j apply <slice>`.

### `apply`: make the code match the spec

1. Find the slice's `package-info.java`. If there is none, suggest `/fullstack4j new` and stop.
2. Run `./mvnw verify`. If it is green and there is no gap in either direction (below), report
   that the slice already matches its spec and stop.
3. Read the gap in both directions and close it:
   - **Spec to code:** an operation with no method, or a requirement id with no test. Work test
     first: write the test named by the id ([references/testing.md](references/testing.md)),
     check it fails for the right reason, then write the code
     ([references/layout.md](references/layout.md) says where it goes and how it looks). In a
     brand-new slice, first create the facade and documents with methods that throw
     `UnsupportedOperationException`, so the tests compile and fail for the right reason.
   - **Code to spec:** a public method on the slice's facade or base-package API with no
     operation, a test naming an id the spec doesn't have, or a document missing from
     `## Entities`. Don't add it to the spec yourself. Report it and let the user decide whether
     to declare it (`/fullstack4j new`) or delete it.
4. Run `./mvnw verify` again. Repeat steps 3 and 4 at most three times; after that, report what
   is still failing, quoting the actual output.

When `new` declared several slices, apply them one at a time, starting with the slices others
call or listen to. While you work on one, run just its tests for quick feedback
(`./mvnw test -Dtest=OrdersTests`); `SpecTraceabilityTests` keeps failing for the slices you
haven't applied yet, and that's expected until the last one is done. The definition of done is
still a green `./mvnw verify` for the whole project.

Outside the two modes, for a quick fix or a refactoring, the same rules apply. If a change alters
behaviour, the spec and its test change with it in the same commit.

## Where things go

```
src/main/java/com/acme/shop/
├── ShopApplication.java       Spring Boot main class
├── WicketApplication.java     mounts the slices' pages
├── StartPage.java             links to each slice's page
├── package-info.java          the system: slices, how they talk, shared words
├── ordering/                  one slice = one Spring Modulith module
│   ├── package-info.java      the spec + @ApplicationModule
│   ├── OrdersPage.java        Wicket page (public: the start page links to it)
│   ├── OrderShipped.java      event record (public: other slices listen to it)
│   └── internal/              hidden from other slices and from the root
│       ├── Orders.java        use-case facade, @Service @Transactional
│       ├── Order.java         MongoDB document, a record
│       └── OrderRepository.java
└── billing/
src/main/resources/com/acme/shop/ordering/OrdersPage.html    markup, same package as the page
src/test/java/com/acme/shop/
├── ArchitectureTests.java     Spring Modulith verify() + the fullstack4j rules
├── SpecTraceabilityTests.java every Rn.m has a test, every test has an Rn.m
└── ordering/OrdersTests.java  one @Test per requirement id
```

Wicket markup goes under `src/main/resources`, because the build only copies resources from
there. Details, naming and code shapes are in [references/layout.md](references/layout.md).

## Before you report "done"

- `./mvnw verify` is green on JDK 25, and you ran it after your last change.
- Every requirement id in the spec has a test, and every test id is in the spec
  (`SpecTraceabilityTests` passed).
- `ArchitectureTests` passed: no code outside a slice uses its `internal` package, and the
  fullstack4j rules hold. You fixed what they reported and silenced nothing: no class excluded,
  no rule removed, no test disabled.
- New shared types live in the base package on purpose: events, pages, or interfaces with
  records. Nothing ended up there just to silence `verify()`.
- The spec still describes what the code does. Anything you found that the spec doesn't cover is
  in your report as a question for the user, not quietly added.

## Reference files

Read them when the step calls for them, not all up front.

- [references/spec.md](references/spec.md): the spec format, EARS patterns, ids, and the root
  package's system doc. Read before writing or changing any spec.
- [references/layout.md](references/layout.md): slice anatomy, naming, and the code shape of
  facades, documents, repositories, pages, events, listeners and direct calls. Read before
  adding classes to a slice or wiring slices together.
- [references/testing.md](references/testing.md): test recipes (requirement tests, events,
  asynchronous listeners, pages) and what common failures mean, each fullstack4j rule included.
  Read before writing tests or when `verify` fails.
- [references/setup.md](references/setup.md): starting a new project, the JDK, the pom, MongoDB
  and Testcontainers settings. Read when setting up a project or when the build itself breaks.
- `assets/`: `ArchitectureTests.java` and `SpecTraceabilityTests.java`, ready to copy into a
  project's root test package. Adjust the `package` line and the application class name.

For the page UI itself (buttons, forms, toasts, themes), use the `wicket-oat` skill if it is
installed. The spec format follows SBCE by Adam Bien (sbce.space), without its
boundary/control/entity layout, and the requirements use EARS by Alistair Mavin.
