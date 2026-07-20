# AGENTS.md — jweb (`frisby-web`)

## ⚠️ Session Setup — Attach These Files

At the start of **every** session, attach the following three reference documents.
They are not fetched automatically — they must be explicitly attached to the conversation:

- `.github/docs/validation.md` — full API reference for `software.frisby.core.validation`
- `.github/docs/util.md` — full API reference for `software.frisby.core.util`
- `.github/docs/java-coding-style.md` — full Java coding style guide

Without these files in context, the AI will not have the complete validation method
signatures, utility class behavior, or style rules needed to generate correct code.

---

## Project Overview

Maven multi-module library (`software.frisby.web`, v1.0.0, Java 17). Two publishable artifacts:
- **`bom`** — Bill of Materials for downstream consumers to import
- **`client`** — the main JAR (currently being scaffolded)

Depends on the sibling library `software.frisby.core:bom` v1.1.0, which provides the `software.frisby.core:validation` module used throughout.

## Module Structure

```
pom.xml               ← aggregator + plugin/dependency version management
bom/pom.xml           ← BOM artifact (flattenMode=bom, strips properties block on publish)
client/pom.xml        ← JAR artifact; Automatic-Module-Name must be kept up-to-date
basic-security/pom.xml   ← Basic Auth provider; depends on client
oauth2-security/pom.xml  ← OAuth2 client-credentials provider; depends on client
```

The root POM enforces convergence (`dependencyConvergence`, `requireUpperBoundDeps`, `banDuplicatePomDependencyVersions`). Any dependency version change must satisfy all three rules.

## Build & Test

```bash
mvn verify                  # compile + unit tests + JaCoCo coverage report
mvn clean verify            # full clean build
mvn install                 # installs all modules to local repo (needed before depending on them locally)
mvn -pl client test         # run tests for client module only
```

Surefire is configured in `client/pom.xml` to pass `@{argLine}` (JaCoCo agent) and a `logging.properties` file to the test JVM. Do not remove `@{argLine}` — it is required for coverage to work.

## Validation Library (`software.frisby.core.validation`)

All validation calls follow `(String name, T value, ...)` → returns `value` unchanged or throws. Full API reference: attach `.github/docs/validation.md` at session start.

Key rules:
- Use `Sequences.notEmpty()` / `Maps.notEmpty()` — never `Values.notNull()` — for collections and maps.
- `minSize()`, `maxSize()`, `size()`, `noDuplicates()` already include `notEmpty` semantics — don't call `notEmpty` first.
- `optional*` variants pass `null` through; use them only for genuinely optional fields.
- `FieldGroups.onlyOne()`/`atLeastOne()` etc. handle mutually exclusive / co-required field groups.
- Thrown exceptions are **not** `IllegalArgumentException` / `NullPointerException` — they are specific
  subtypes (`NullValueException`, `BlankValueException`, etc.). Document `@throws` with the exact type.

## Utility Library (`software.frisby.core.util`)

Full reference: attach `.github/docs/util.md` at session start.

### StopWatch

- `duration()` returns a **live** (increasing) value while the watch is running, and a **frozen**
  value once `stop()` has been called. Call `stop()` first when you want a final point-in-time
  latency snapshot; omit it when reading elapsed time mid-operation is intentional.
- `stop()` called on an already-stopped watch is a no-op, so calling it in a `finally` block is
  always safe and ensures the watch is not left running if an exception is thrown:

  ```java
  StopWatch watch = StopWatch.start();
  try {
      // ... work ...
  } finally {
      watch.stop();
  }
  Duration latency = watch.duration();
  ```

- A `StopWatch` cannot be restarted. Create a new instance for each operation.

### Decimals

- **Never** construct `BigDecimal` directly from `double` or `float` — use `Decimals.of(value)` instead.
  `new BigDecimal(0.1)` captures the binary floating-point representation, not the intended decimal value.
- `Decimals.of(double/float)` routes through `String.valueOf()` and produces the exact decimal intended.

## Java Coding Style

Full guide: attach `.github/docs/java-coding-style.md` at session start. Essential rules:

- **Null checks**: Yoda style — `if (null == value)`, never `if (value == null)`.
- **Accessors**: no `get` prefix — `duration()` not `getDuration()`; `boolean isRunning()` is fine.
- **Multi-line calls**: fully expanded — each argument and each closing `)` on its own line; never stack `));`.
- **Blank lines**: one blank line between logically distinct statements inside method bodies.
- **No blank line** after an opening brace `{`.
- **Static utility classes**: `public final class` with `private` no-arg constructor; all-static methods; repeated string literals extracted to `private static final String`.
- **Field initialization**: if any field is initialized in the constructor, initialize *all* fields there.
- **`Runnable` nested classes**: always `private static`; all dependencies passed via constructor.

## Exception Message Format

Value failures: `"The '<name>' value is invalid.  <Sentence>."`  (two spaces between sentences)

Configuration failures: `"The '<name>' value of '<actual>' is invalid.  <Sentence>."`

## Test Conventions

- JUnit 5 (`@Test`, `@Nested`); static-import `assertEquals`, `assertThrows` from `org.junit.jupiter.api.Assertions`.
- Method naming: `<condition>_<expectedOutcome>()` — e.g. `nullValue_throwsNullValueException()`.
- Expected message strings declared as `private static final String` constants at the top of the test class.
- `@Nested` classes group by method family or constraint type (e.g. `Primitive` / `Boxed` sub-nesting for numeric overloads).

## Publishing Notes

`flatten-maven-plugin` rewrites POMs at `process-resources` phase. The committed `.flattened-pom.xml` files are build artifacts — do not edit them manually. `bom/pom.xml` uses `flattenMode=bom` and removes the `<properties>` block from the published BOM.

