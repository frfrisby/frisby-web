# Java Coding Style Guide

Portable style guide for Java projects. Copy this file into any project as a starting
point. Project-specific details (package names, module structure, class patterns) belong
in `.github/copilot-instructions.md`.

> **Java version baseline:** this guide assumes **Java 17 or later**.  Sections that
> require a newer release note the minimum version explicitly.  For projects on older
> versions, skip or adapt those sections.

---

## Formatting & Whitespace

### Blank lines between statements

Always use a **blank line to separate logically distinct statements** within a method
body. Every validation step, every guard clause, and the final `return` should each be
visually separated:

```java
// Correct
public static String notBlank(String name, String value) {
    Throws.ifInValidName(name);

    if (null == value) {
        throw new NullValueException(...);
    }

    if (value.isBlank()) {
        throw new BlankValueException(...);
    }

    return value;
}

// Wrong — no breathing room
public static String notBlank(String name, String value) {
    Throws.ifInValidName(name);
    if (value == null) throw new NullValueException(...);
    if (value.isBlank()) throw new BlankValueException(...);
    return value;
}
```

### Braces

When the body of an `if` block **constructs an exception inline**, always use braces and
break across multiple lines:

```java
if (null == value) {
    throw new NullValueException(
            String.format(
                    "The '%s' value is invalid. The value must not be null.",
                    name
            )
    );
}
```

When the body delegates to a **private helper method** and fits on a single readable
line, braces may be omitted. A blank line must still separate the guard group from the
surrounding statements:

```java
public static int min(String name, int value, int min) {
    Throws.ifInValidName(name);

    if (value < min) throw tooSmall(name, min);

    return value;
}

// Multiple guards — no blank line *between* related guards, but blank before and after:
public static int range(String name, int value, int min, int max) {
    Throws.ifInValidName(name);

    if (max < min) throw maxLtMin(max, min);
    if (value < min) throw tooSmall(name, min);
    if (value > max) throw tooLarge(name, max);

    return value;
}
```

For all other uses of `for`, `while`, and `try` — always use braces.

### Class and method body opening

Never place a blank line immediately after the opening brace (`{`) of a class, interface,
enum, or method body. The first statement or declaration should appear on the very next
line:

```java
// Correct
public final class Example {
    private static final String MIN = "min";

    private Example() {
    }
}

// Wrong — spurious blank line after opening brace
public final class Example {

    private static final String MIN = "min";

    private Example() {
    }
}
```

This rule applies equally to method bodies, constructors, initializer blocks, and test
classes/methods.

### Long lines

Prefer lines under ~120 characters. If a call fits within the limit on a **single line**,
keep it on one line and do not break it.

### Multi-line call expansion

Once a method call is broken across multiple lines, apply the **fully expanded** form
throughout:

- Each argument on its own line.
- Each closing `)` on its own line, indented to match the start of its corresponding
  opening call.
- Never stack multiple closing parentheses (`));`, `)));`, etc.) on the same line.

This rule applies uniformly in every context — `throw`, `return`, assignment, and so on.

```java
// Correct
return new NumericValueOutsideRangeException(
        String.format(
                "The '%s' value is invalid. The value must be greater than or equal to '%s'.",
                name,
                min
        )
);

// Wrong — stacked closing parens, arguments crammed onto one line
return new NumericValueOutsideRangeException(String.format(
        "The '%s' value is invalid. The value must be greater than or equal to '%s'.",
        name, min));
```

---

## Java Idioms

### Null checks — Yoda conditions

Always place `null` on the **left** side of a null-equality test. This convention
carries over from C/C++ (where `if (x = NULL)` is a silent bug) and is kept for
consistency:

```java
// Correct
if (null == value) { ... }

// Wrong
if (value == null) { ... }
```

### String.format style

Break `String.format` across lines when the format string or argument list is non-trivial.
Keep the format string on its own line and each argument on its own line:

```java
throw new SomeException(
        String.format(
                "The '%s' value of '%s' is invalid. The value must be >= '%s'.",
                name,
                value,
                min
        )
);
```

### `var` — local variable type inference

> **Minimum version:** Java 10

Use `var` when the type is **immediately obvious** from the right-hand side of the
assignment — typically a constructor call or a cast — and repeating it adds no
information:

```java
// Fine — type is self-evident from the constructor call
var builder = new DefaultConfigurationBuilder();
var events  = new ArrayList<RequestCompletedEvent>();

// Fine — type is explicit in the cast
var handler = (RequestHandler) context.getAttribute("handler");
```

Do **not** use `var` when the type is not obvious from the expression alone.  A reader
should never have to resolve the return type of a method call or a generic utility to
understand what a variable holds:

```java
// Wrong — type is not visible without looking up process()
var result = process(input);

// Wrong — the element type of the stream is unclear
var items = repository.findAll().stream().filter(Item::isActive);

// Correct — explicit type makes the code self-contained
List<Item> items = repository.findAll().stream().filter(Item::isActive).toList();
```

Never use `var` for fields — it is only valid for local variables.

---

## Class Design

### Static utility classes

- Declared `public final class` with a `private` no-arg constructor.
- All methods are `public static`.
- Private `static final String` constants for **any string literal used more than once**.

```java
public final class Example {
    private static final String MAX_LENGTH = "maxLength";

    private Example() {
    }

    // methods...
}
```

### Enums

Constants are `UPPER_SNAKE_CASE`.  Enums that carry data declare fields as `private final`
and initialize them in a constructor; accessor methods follow the no-`get`-prefix rule:

```java
public enum ResponseStatus {
    OK(200, "OK"),
    NOT_FOUND(404, "Not Found"),
    INTERNAL_SERVER_ERROR(500, "Internal Server Error");

    private final int code;
    private final String reason;

    ResponseStatus(int code, String reason) {
        this.code = code;
        this.reason = reason;
    }

    public int code() {
        return code;
    }

    public String reason() {
        return reason;
    }
}
```

Enums with per-constant behavior declare an `abstract` method and override it in each
constant's body:

```java
public enum Strategy {
    FAST {
        @Override
        public Result execute(Input input) { ... }
    },
    THOROUGH {
        @Override
        public Result execute(Input input) { ... }
    };

    public abstract Result execute(Input input);
}
```

### Sealed interfaces and classes

> **Minimum version:** Java 17 for `sealed` types and `permits`; Java 21 for `switch`
> pattern matching (`case FilePart f ->`).  On Java 17–20, declare sealed hierarchies
> as shown but branch with `instanceof` until you can upgrade.

Use `sealed` to model a **fixed, closed set of subtypes** — typically algebraic data
types where the compiler should enforce exhaustiveness.  Declare the `permits` clause
explicitly rather than relying on implicit permitting (same-file subtypes), so the
complete set of subtypes is visible at the top of the hierarchy:

```java
public sealed interface FormPart permits FilePart, JsonPart, ContentPart {
    String name();
}

public record FilePart(String name, InputStream stream, String fileName, MediaType contentType)
        implements FormPart {}

public record JsonPart(String name, Object body)
        implements FormPart {}

public record ContentPart(String name, String content, MediaType mediaType)
        implements FormPart {}
```

Use `switch` expressions over `instanceof` chains when branching on a sealed type —
the compiler guarantees exhaustiveness and will flag unhandled subtypes as an error:

```java
// Correct — exhaustiveness checked at compile time
String boundary = switch (part) {
    case FilePart  f -> buildFilePart(f, boundary);
    case JsonPart  j -> buildJsonPart(j, boundary);
    case ContentPart c -> buildContentPart(c, boundary);
};

// Wrong — instanceof chain gives no exhaustiveness guarantee
if (part instanceof FilePart f) { ... }
else if (part instanceof JsonPart j) { ... }
// silent bug: ContentPart falls through with no handling
```

Permitted subtypes must be `final`, `sealed`, or `non-sealed`.  Prefer `final` records
for leaf types — they are immutable, concise, and naturally `final`.

---

### Validation placement — builders vs. constructors

Where validation lives depends on how the constructor is accessed:

**Private constructor (nested Builder pattern)**

The constructor is `private` — only the nested `Builder` can call it. Validate in
`Builder.build()`, immediately before `return new Thing(this)`. The constructor just
assigns fields; the builder is the sole entry point and owns the invariants.

```java
public static final class Builder {
    public Rules build() {
        Numbers.range("weight", weight, 0.0, 1.0);  // validate here
        // ...
        return new Rules(this);
    }
}

private Rules(Builder builder) {
    this.weight = builder.weight;  // just assign — no validation
}
```

**Package-private constructor (interface + separate Builder pattern)**

The constructor is package-private — any class in the same package can call it, not
just the builder. Validate in the constructor so the object defends its own invariants
regardless of who constructs it. The builder's `build()` simply delegates with no
validation of its own.

```java
// Builder — no validation here; just delegates
@Override
public UserRepository build() {
    return new DefaultUserRepository(dataSource);
}

// Implementation — validates and assigns in one step
DefaultUserRepository(DataSource dataSource) {
    this.dataSource = Values.notNull("dataSource", dataSource);  // project validation library
}
```

> **Note:** `Values.notNull()` is from `software.frisby.core:validation`.  Substitute
> the equivalent call from your project's validation library (e.g. `Objects.requireNonNull()`,
> Guava's `Preconditions.checkNotNull()`, etc.).

**The rule:** if the constructor is `private`, validate in the builder; if the
constructor is package-private (or more accessible), validate in the constructor.

---

### Field initialization

When any field must be initialized in a constructor — because its value depends on a
constructor argument or is captured at instantiation time (e.g. `System.nanoTime()`) —
initialize **all** fields in the constructor.  Mixing declaration-site initialization
with constructor initialization forces the reader to look in two places to understand
the complete initial state of the object.

```java
// Correct — all fields initialized in one place
public final class StopWatch {
    private final long startTime;
    private final AtomicLong stopTime;

    private StopWatch() {
        startTime = System.nanoTime();
        stopTime = new AtomicLong(-1);
    }
}

// Wrong — initial state split across two locations
public final class StopWatch {
    private final long startTime;
    private final AtomicLong stopTime = new AtomicLong(-1);  // ← here

    private StopWatch() {
        startTime = System.nanoTime();                        // ← and here
    }
}
```

Declaration-site initialization is appropriate when *all* fields of a class can be
initialized that way — the field declaration then serves as the complete record of
initial state and no constructor body is needed.

### Accessor and mutator naming — record style

Drop the `get` prefix from accessor methods.  Java records use this convention natively,
and modern Java APIs (e.g. `Optional`, `Stream`, `Duration`) follow the same pattern.
Use the value name directly as the method name:

```java
// Correct
int capacity()
Duration timeout()
int poolSize()

// Wrong
int getCapacity()
Duration getTimeout()
int getPoolSize()
```

Boolean accessors that read naturally with the `is` prefix may keep it:

```java
boolean isShutdown()   // reads naturally — keep as-is
boolean isRunning()    // reads naturally — keep as-is
```

Mutator methods in builders and fluent APIs follow the same convention — no `set` prefix.
Use the parameter name directly as the method name:

```java
// Correct
builder.capacity(1024)
builder.threadPrefix("pipeline")

// Wrong
builder.setCapacity(1024)
builder.setThreadPrefix("pipeline")
```

### Optional return types

Accessor methods for fields that may be absent must return `Optional<T>` rather than the
raw type.  This makes the nullability contract part of the method signature — callers
cannot accidentally dereference a missing value.

**Rules:**

- Fields that are **always present** (never null) → return `T` directly.
- Fields that are **genuinely optional** (may be null) → return `Optional<T>`.
- Store the field as the **raw type** (`String`, `BigDecimal`, etc.) internally.
  Never store `Optional` as a field — it is not `Serializable` and adds unnecessary
  heap overhead.
- Builder setter methods accept the **raw type** — the `Optional` wrapping is the
  reader's concern only.

```java
// Field stored as raw type
private final String uri;

// Accessor wraps at the boundary
public Optional<String> uri() {
    return Optional.ofNullable(uri);
}

// Builder setter accepts raw type — natural to construct
public Builder uri(String uri) {
    this.uri = uri;
    return this;
}
```

Javadoc for optional accessors describes the absent case via the `Optional`:

```java
/**
 * Returns the CPE 2.3 URI for this entry, or an empty {@code Optional} if not available.
 *
 * @return The CPE URI.
 */
public Optional<String> uri() {
    return Optional.ofNullable(uri);
}
```

---

### Immutable list copies in constructors

When a constructor or `Builder.build()` needs to store a defensive, unmodifiable copy of
a `List`, use `List.copyOf()`:

```java
this.tags = List.copyOf(builder.tags);
```

**Do not** use `Collections.unmodifiableList(new ArrayList<>(list))` — it is more verbose
and produces an intermediate `ArrayList` that is immediately discarded.

`List.copyOf()` is the right tool because it:
- Creates a new, independent copy of the list (caller mutations cannot affect the stored value).
- Returns an unmodifiable view (internal mutations are rejected at runtime).
- Throws `NullPointerException` if the input list is `null` or contains a `null` element —
  both are programming errors that should fail fast.

Builder fields that accumulate list elements still use `new ArrayList<>()` at
declaration and `new ArrayList<>(input)` in the bulk-setter (to take a defensive copy of
the caller's list before further mutation).  `List.copyOf()` is applied only at the
final construction step:

```java
// Builder field — mutable accumulator
private List<Tag> tags = new ArrayList<>();

// Bulk setter — defensive copy so the caller's list can't affect the builder
public Builder tags(List<Tag> tags) {
    this.tags = new ArrayList<>(tags);
    return this;
}

// Construction — immutable snapshot handed to the model
private MyModel(Builder builder) {
    this.tags = List.copyOf(builder.tags);
}
```

---

### Builder list setter and adder validation

Validate list inputs at the point of mutation — in the setter and adder methods — rather
than deferring everything to `build()`.  This gives the caller an immediate error at the
exact call site rather than a delayed one when `build()` is finally invoked.

> **Note:** the examples below use `software.frisby.core:validation` (`Sequences.notEmpty()`,
> `Values.notNull()`, `Strings.notBlank()`).  Substitute the equivalent calls from your
> project's validation library; the structural pattern is the same regardless.

**Bulk setter** — validate before copying to reject a null container, an empty container,
or any null elements in a single call:

```java
public Builder tags(List<Tag> tags) {
    this.tags = new ArrayList<>(Sequences.notEmpty("tags", tags));
    return this;
}
```

**Single-item adder** — for object types use `Values.notNull()`; for `String` types use
`Strings.notBlank()` (a blank string is as useless as a null one):

```java
public Builder addTag(Tag tag) {
    this.tags.add(Values.notNull("tag", tag));
    return this;
}

public Builder addLabel(String label) {
    this.labels.add(Strings.notBlank("label", label));
    return this;
}
```

With these guards in place, the `build()` method does not need to re-validate list
fields — the builder fields are always initialized to `new ArrayList<>()` (never null),
and the guards ensure no null elements can be added.  `List.copyOf()` at construction
provides a final safety net regardless.

---

### Worker / Runnable nested classes

**Long-lived or reusable workers in non-static contexts** must be declared `private static`
with all collaborators passed as explicit constructor parameters — never let the worker
access outer class state via an implicit `OuterClass.this.*` reference.

- **Explicitness** — the constructor signature is the complete record of everything the
  thread can touch.  No reader needs to scan the outer class to understand the worker's
  dependencies.
- **No hidden outer reference** — a non-static inner class always holds a reference to
  its enclosing instance, keeping it alive for the full lifetime of the worker thread.
- **Isolation** — the class can be reasoned about and tested independently.

```java
// Correct — static; all dependencies declared in the constructor
private static final class Worker<T> implements Runnable {
    private final BlockingQueue<T> queue;
    private final CapacityGate capacityGate;

    private Worker(BlockingQueue<T> queue, CapacityGate capacityGate) {
        this.queue = queue;
        this.capacityGate = capacityGate;
    }

    @Override
    public void run() {
        // ...
        this.capacityGate.release(); // ← own field; no outer reference needed
    }
}

// Wrong — non-static; outer state reached via implicit reference
private final class Worker implements Runnable {
    @Override
    public void run() {
        OuterClass.this.capacityGate.release(); // ← implicit outer reference
    }
}
```

If a dependency only exists to be handed off to the worker, keep it as a constructor
local variable rather than promoting it to a field on the outer class:

```java
// Correct — CapacityGate never stored on the outer class
CapacityGate capacityGate = new CapacityGate(capacity);
this.worker = new Worker<>(queue, capacityGate);

// Wrong — field exists solely to pass to Worker
this.capacityGate = new CapacityGate(capacity);
this.worker = new Worker<>(queue, this.capacityGate);
```

**Simple one-shot tasks in static contexts** (e.g. a JVM shutdown hook registered from
a `static void main`) may use a lambda when the captured variables are few, obvious, and
local to the enclosing static method.  There is no enclosing instance to capture, so the
hidden-reference concern does not apply:

```java
// Fine — static context, two obvious local variables, runs once
Runtime.getRuntime().addShutdownHook(new Thread(() -> {
    server.stop();
    application.close();
}));

// Also fine — named class adds clarity when the intent benefits from a name
Runtime.getRuntime().addShutdownHook(new Thread(new ShutdownTask(server, application)));
```

---

### Private guard helpers

When a guard block — a conditional check that throws — consists of **two or more statements**
and appears **four or more times** in the same class, extract it to a `private static void`
helper named `throwIf<Condition>`.

Single-line guards (e.g. `if (null == value) throw nullValue(name)`) may be repeated inline
regardless of frequency; they are already at minimum verbosity.

```java
// Extracted — 3-statement block that would otherwise appear 6 times
private static void throwIfExclusiveBoundsInvalid(Duration min, Duration max) {
    Throws.ifNull(MIN, min);   // project validation library
    Throws.ifNull(MAX, max);   // project validation library
    if (max.compareTo(min) <= 0) throw maxLeMin(max, min);
}

// At each of the 6 call sites, the method body becomes a single readable line:
throwIfExclusiveBoundsInvalid(min, max);

// Fine to repeat inline — single-line guard
if (null == value) throw nullValue(name);
```

> **Note:** `Throws.ifNull()` is from `software.frisby.core:validation`.  The pattern —
> a named `private static void` helper that encapsulates a multi-statement guard — applies
> regardless of which validation library is in use.

---

## JavaDoc

### Coverage

Every `public` method and every `public` or `protected` class must have a Javadoc
comment. Package-private and private members do not require Javadoc.

### Sentences

`@param` and `@return` descriptions use **complete sentences**: capitalize the first word
and end with a period.

`@throws` descriptions follow the Oracle convention: begin with a **lowercase** word
(typically `if`) and end with a period.

```java
/**
 * Validates that {@code value} is not null.
 *
 * @param name  The name of the argument being validated; used in exception messages.
 * @param value The value to validate.
 * @return      The validated {@code value}, unchanged.
 * @throws NullPointerException if {@code name} is null or blank.
 * @throws NullValueException   if {@code value} is null.
 */
public static String notNull(String name, String value) { ... }
```

### Class-level Javadoc

Describe what the class is for, what problem it solves, and provide a brief usage
example where helpful. Use `@see` to link to closely related classes.

### Summary fragment

The first sentence of any Javadoc comment (before the first period followed by a space
or newline) is used as the summary fragment in generated API docs. Keep it concise and
self-contained.

### Language

All prose — Javadoc comments, inline comments, and commit messages — must use
**American English** spelling. When in doubt, [Merriam-Webster](https://www.merriam-webster.com)
is the authoritative reference.

Most British/American divergence follows a small set of predictable patterns.  Apply
these rules to any word, including ones not listed below:

| Pattern                          | British ending            | American ending           | Examples                                                                                                                           |
|----------------------------------|---------------------------|---------------------------|------------------------------------------------------------------------------------------------------------------------------------|
| Verb/adjective suffix            | `-ise`, `-ising`, `-ised` | `-ize`, `-izing`, `-ized` | organise → organize, initialise → initialize, normalise → normalize, analyse → analyze, optimise → optimize, recognise → recognize |
| `-our` nouns                     | `-our`                    | `-or`                     | behaviour → behavior, colour → color, honour → honor                                                                               |
| Consonant doubling before suffix | doubled (`-ll-`, `-tt-`)  | single (`-l-`, `-t-`)     | signalled → signaled, signalling → signaling, travelling → traveling, labelled → labeled, cancelled → canceled                     |
| Noun ending                      | `-re`                     | `-er`                     | centre → center, theatre → theater                                                                                                 |
| Noun ending                      | `-ogue`                   | `-og`                     | catalogue → catalog, dialogue → dialog                                                                                             |
| Standalone exception             | `artefact`                | `artifact`                | —                                                                                                                                  |

Common words that come up in technical writing — shown here as a quick-reference
reminder of the rules above:

| Use (American) | Avoid (British) |
|----------------|-----------------|
| artifact       | artefact        |
| behavior       | behaviour       |
| behavioral     | behavioural     |
| color          | colour          |
| honor          | honour          |
| initialize     | initialise      |
| recognize      | recognise       |
| organize       | organise        |
| analyze        | analyse         |
| optimize       | optimise        |
| normalize      | normalise       |
| signaled       | signalled       |
| signaling      | signalling      |
| canceled       | cancelled       |
| labeled        | labelled        |
| traveling      | travelling      |
| center         | centre          |
| dialog         | dialogue        |
| catalog        | catalogue       |
| unrecognized   | unrecognised    |

---

## Exception Messages

> **Project convention, not universal Java:** the formats below are used on projects that
> depend on `software.frisby.core:validation`, whose exception types produce messages in
> this structure.  On projects using a different validation library or no validation library,
> adopt a consistent message format for your own thrown exceptions and document it here in
> place of these templates.

### Value failure messages

```
"The '<name>' value is invalid. <Sentence describing what is required>."
```

**Two spaces** between the first sentence and the description — intentional; must be
preserved exactly.

Examples:
- `"The 'email' value is invalid. The value must not be null."`
- `"The 'count' value is invalid. The value must be greater than or equal to '1'."`

### Configuration failure messages

```
"The '<param>' value of '<actual>' is invalid. <Sentence describing what is required>."
```

Examples:
- `"The 'maxLength' value of '0' is invalid. The value must be greater than or equal to '1'."`
- `"The 'max' value of '3' is invalid. The value must be greater than or equal to the 'min' value of '5'."`

---

## Testing Conventions

### Framework

- JUnit 5 (`@Test`, `@Nested`)
- Static imports: `assertEquals`, `assertNull`, `assertThrows` from
  `org.junit.jupiter.api.Assertions`

### Structure

- `@Nested` classes mirror method families or constraint families.
- When a class is large, split test classes by type or concern.
- Primitive + boxed overloads use `@Nested class Primitive` / `@Nested class Boxed`
  sub-nesting where applicable.

### Test method naming

```
<condition>_<expectedOutcome>()
```

Examples:
- `nullName_throwsNullPointerException()`
- `blankName_throwsNullPointerException()`
- `valueBelowMin_throwsNumericValueOutsideRangeException()`
- `valueAtMin_returnsValue()`
- `nullValue_returnsNull()` — for `optional*` / nullable methods

### Shared message constants

Declare expected exception message strings as `private static final String` constants at
the top of each test class rather than inlining them in assertions:

```java
private static final String NULL_NAME_MSG  = "The 'name' value was not provided.";
private static final String BLANK_NAME_MSG = "The 'name' value is invalid. The value must be non null and cannot contain only white space characters.";
private static final String NULL_VALUE_MSG = "The 'field' value is invalid. The value must not be null.";
```

### Exception assertions — type, detail, and message

`assertThrows(SomeException.class, () -> ...)` is a minimum viable assertion: it only
confirms that *a* matching exception was thrown, not that it was thrown *for the right
reason*.  Always capture the return value and assert the most meaningful diagnostic
property available.

```java
// Wrong — type assertion only; hides the reason the exception was thrown
assertThrows(BadRequestException.class, () -> service.create(null));

// Correct — type plus detail
BadRequestException ex = assertThrows(BadRequestException.class, () -> service.create(null));
assertEquals(EXPECTED_MSG, ex.getMessage());
```

**What to assert beyond type** depends on the exception's origin:

**Exceptions you own** — those your own code constructs and throws — carry a message that
is part of the API contract.  Assert it.  Declare the expected string as a
`private static final String` constant so it is maintained in one place and reads
clearly at every assertion site.

```java
private static final String INVALID_PATH_MSG =
        "The 'path' value is invalid.  The placeholder '{id}' is not present in the template '/orders'.";

UriSyntaxException ex = assertThrows(
        UriSyntaxException.class,
        () -> builder.path("/orders", "id", "42")
);
assertEquals(INVALID_PATH_MSG, ex.getMessage());
```

**Exceptions mapping an external signal** — those created by mapping an external code
(HTTP status, OS error number, database error code, etc.) to a typed exception — carry
a message derived from that signal.  The signal value is deterministic and meaningful;
the message text is secondary.  Assert the signal instead.

```java
// HTTP example — assert the status code, not the message
MethodNotAllowedException ex = assertThrows(
        MethodNotAllowedException.class,
        () -> client.post().path("/orders/{id}", "id", "42").send(Order.class)
);
assertEquals(405, ex.statusCode());
```

**Exceptions from third-party libraries** — those thrown internally by the JDK, a
framework, or any other dependency you do not own — may have messages that change across
library versions without being a bug.  A type assertion is usually sufficient; never
assert the message text.

```java
// Fine — type-only for a JDK or framework exception
assertThrows(CompletionException.class, () -> future.join());
```

**When detail assertions are intentionally omitted**, explain why in the Javadoc comment
so the next reader understands the omission is deliberate rather than lazy:

```java
/**
 * Sends a request with no body — exercises the {@code null == jsonBody} branch in
 * {@code buildJsonRequest()}.  The server NPEs on the null entity and returns 500;
 * that outcome is a test-design side effect, not the behavior under test.
 */
@Test
void noBody_buildRequestUsesNoBodyPublisher() {
    assertThrows(
            InternalServerErrorException.class,
            () -> client.post().path("/orders").send(Order.class)
    );
}
```

---

## Cross-Constant Relationships

### When constants in separate classes must agree

Sometimes a field in one model stores data that originates from another model — for
example, a history table that captures a user's display name at the time of an action.
The column width of the history table's `changed_by_name` column must accommodate the
maximum length of `User.displayName()`.  These two constants are **not** the same
constant; they live in separate model classes and map to separate DB columns.  But they
must stay in sync, and that relationship is invisible by default.

Make the relationship explicit with two mechanisms applied together:

**1. Cross-reference comment on the constant**

```java
/**
 * Maximum length of {@link #changedByName()} — matches
 * {@code status_history.changed_by_name} column width.
 * Intentionally sized to accommodate {@link User#DISPLAY_NAME_MAX_LENGTH}.
 */
public static final int CHANGED_BY_NAME_MAX_LENGTH = 255;
```

This tells the next developer exactly why the value is 255 and where to look if they
change `User.DISPLAY_NAME_MAX_LENGTH`.

**2. A test that asserts equality**

```java
@Test
void changedByNameMaxLength_matchesUserDisplayNameMaxLength() {
    assertEquals(User.DISPLAY_NAME_MAX_LENGTH, StatusHistoryEntry.CHANGED_BY_NAME_MAX_LENGTH);
}
```

This test will fail at compile/test time — before any deployment — if someone changes
one constant without updating the other.  It is the only reliable way to catch this
class of drift.

**The rule:** any time a `*_MAX_LENGTH` constant is intentionally sized to match a
constant on another model class, add both the comment and the equality test in the same
commit.  One without the other is incomplete.

---

## Validation Defense-in-Depth

### Validate at every layer — each for its own purpose

In a layered application (HTTP resource → service / model → database), validation must
happen at **each layer** independently.  Relying on a single layer is fragile:

| Layer               | Purpose                                                  | Failure mode if absent                                                           |
|---------------------|----------------------------------------------------------|----------------------------------------------------------------------------------|
| HTTP resource       | Return a descriptive `400` with the field name and limit | Model exception leaks as a `500`; caller gets no actionable message              |
| Model builder       | Enforce class invariants regardless of caller            | Repository or service bypasses the resource and passes bad data straight through |
| Database constraint | Final backstop against any application bug               | Silent data corruption if both upper layers are bypassed                         |

**Concrete example — user-provided text fields:**

```java
// HTTP resource layer — early, descriptive rejection
if (request.body().length() > Note.BODY_MAX_LENGTH) {
    throw new BadRequestException(
            "'body' must not exceed " + Note.BODY_MAX_LENGTH + " characters"
    );
}

// Model builder layer — class invariant
Strings.notBlankWithMaxLength("body", body, BODY_MAX_LENGTH);

// Database layer — schema constraint (VARCHAR(4000))
body VARCHAR(4000) NOT NULL
```

Each layer has a different audience:
- The HTTP resource speaks to the **API caller** — fast, informative, no stack trace.
- The model builder speaks to the **developer** — immediate failure at the construction
  site if the invariant is violated.
- The DB constraint speaks to **data integrity** — the absolute last line of defense
  against a bug anywhere in the application stack.

**The rule:** when a field has a meaningful size constraint, implement all three layers.
Never assume the layer above will catch it — it may be called from a different code path
that bypasses the resource entirely (a batch job, a migration script, a test fixture).

