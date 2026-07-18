# frisby-core Util — Quick Reference

**Package:** `software.frisby.core.util`
**Dependency:** `software.frisby.core:util`

This file is designed to be attached to an AI assistant's context window. It covers
both utility classes with call signatures, behavioral notes, and representative examples.

---

## StopWatch

A thread-safe stopwatch that measures elapsed time with nanosecond precision.

### Key behaviors

- **Created in a running state** — there is no separate `start` call after construction.
- **One-way transition** — once stopped, a `StopWatch` cannot be restarted. Calling
  `stop()` a second time (or from a second thread) is a no-op; the duration recorded
  at the first `stop()` call is preserved unchanged.
- **Thread-safe** — `stop()` uses `AtomicLong.compareAndSet` internally. Exactly one
  thread wins the race if two threads call `stop()` concurrently.
- **Live vs. frozen duration** — `duration()` returns a live (increasing) value while
  the stopwatch is running and a frozen value once stopped.

### API

```java
import software.frisby.core.util.StopWatch;

// Start timing
StopWatch watch = StopWatch.start();

// ... do work ...

// Read elapsed time (live — value increases each call while running)
Duration elapsed = watch.duration();

// Stop the stopwatch (freezes the duration)
watch.stop();

// Read elapsed time (frozen — same value on every subsequent call)
Duration total = watch.duration();

// Check whether the stopwatch has been stopped
boolean stopped = watch.isStopped();  // true after stop(), false while running
```

### Typical usage patterns

**Measure a single operation and log:**

```java
StopWatch watch = StopWatch.start();
doSomething();
watch.stop();
log.info("Completed in {}", watch.duration());
```

**Measure across a try/finally block:**

```java
StopWatch watch = StopWatch.start();
try {
    doSomething();
} finally {
    watch.stop();
}
Duration elapsed = watch.duration();
```

**Check whether a deadline has been exceeded while still running:**

```java
StopWatch watch = StopWatch.start();
while (hasMoreWork()) {
    if (watch.duration().compareTo(TIMEOUT) > 0) {
        throw new TimeoutException("Operation exceeded " + TIMEOUT);
    }
    processNext();
}
watch.stop();
```

### What StopWatch does NOT do

- It does not restart. Create a new instance to time a second operation.
- It does not pause. There is no `pause()`/`resume()` mechanism.
- It does not throw. `stop()` and `duration()` are always safe to call in any order.

---

## Decimals

Static utility methods for converting numeric values to and from `BigDecimal`.

### Why this class exists

Constructing a `BigDecimal` from a `double` or `float` with `new BigDecimal(value)`
captures the exact binary floating-point representation — not the decimal value the
programmer intended:

```java
new BigDecimal(0.1)
// → 0.1000000000000000055511151231257827021181583404541015625
```

`Decimals` routes all floating-point conversions through `String.valueOf()` and then
`new BigDecimal(String)`, which produces the shortest decimal that round-trips back to
the same floating-point value:

```java
Decimals.of(0.1)
// → 0.1  (exact)
```

**Always use `Decimals.of(double/float)` instead of `new BigDecimal(double/float)`.**

All methods return values with trailing zeros stripped (`stripTrailingZeros()`) and
normalized to avoid exponent notation in the output.

### API

```java
import software.frisby.core.util.Decimals;
import java.math.RoundingMode;

// --- Conversion from floating-point ---

BigDecimal a = Decimals.of(0.1);                                   // 0.1
BigDecimal b = Decimals.of(1.255, 2);                              // 1.25  (RoundingMode.DOWN)
BigDecimal c = Decimals.of(1.255, 2, RoundingMode.HALF_UP);        // 1.26

BigDecimal d = Decimals.of(1.5f);                                  // 1.5  (float overload)
BigDecimal e = Decimals.of(1.5f, 1);                               // 1.5  (float, scale 1)

// --- Conversion from long ---

BigDecimal f = Decimals.of(42L);                                   // 42

// --- Parsing from String ---

BigDecimal g = Decimals.parse("3.14159");                          // 3.14159
BigDecimal h = Decimals.parse("3.14159", 2);                       // 3.14  (RoundingMode.DOWN)
BigDecimal i = Decimals.parse("3.14159", 2, RoundingMode.HALF_UP); // 3.14

// --- Formatting to plain String (no exponent notation) ---

String s1 = Decimals.toString(Decimals.of(1234567.89));            // "1234567.89"
String s2 = Decimals.toString(Decimals.of(1234567.89), 1);         // "1234567.8"  (DOWN)
String s3 = Decimals.toString(Decimals.of(1234567.89), 1, RoundingMode.HALF_UP); // "1234567.9"
```

### Method reference

#### `of` — convert to BigDecimal

| Signature                                   | Description                                                                                       |
|---------------------------------------------|---------------------------------------------------------------------------------------------------|
| `of(long value)`                            | Converts a `long` to `BigDecimal`. No precision concern; delegates to `BigDecimal.valueOf(long)`. |
| `of(float value)`                           | Converts a `float` via `String.valueOf` to avoid binary precision artifacts.                      |
| `of(float value, int scale)`                | Same, then rounds to `scale` decimal places using `RoundingMode.DOWN`.                            |
| `of(float value, int scale, RoundingMode)`  | Same, with explicit rounding mode.                                                                |
| `of(double value)`                          | Converts a `double` via `String.valueOf` to avoid binary precision artifacts.                     |
| `of(double value, int scale)`               | Same, then rounds to `scale` decimal places using `RoundingMode.DOWN`.                            |
| `of(double value, int scale, RoundingMode)` | Same, with explicit rounding mode.                                                                |

#### `parse` — parse String to BigDecimal

| Signature                                      | Description                                                      |
|------------------------------------------------|------------------------------------------------------------------|
| `parse(String value)`                          | Parses an exact decimal string. Value must not be null or empty. |
| `parse(String value, int scale)`               | Parses and rounds to `scale` places using `RoundingMode.DOWN`.   |
| `parse(String value, int scale, RoundingMode)` | Parses with explicit scale and rounding mode.                    |

#### `toString` — format BigDecimal to plain String

| Signature                                             | Description                                                                                             |
|-------------------------------------------------------|---------------------------------------------------------------------------------------------------------|
| `toString(BigDecimal value)`                          | Returns the value as a plain decimal string with trailing zeros stripped. Never uses exponent notation. |
| `toString(BigDecimal value, int scale)`               | Same, rounded to `scale` decimal places using `RoundingMode.DOWN`.                                      |
| `toString(BigDecimal value, int scale, RoundingMode)` | Same, with explicit rounding mode.                                                                      |

### Default rounding mode

All overloads that accept a `scale` but no `RoundingMode` use **`RoundingMode.DOWN`**
(truncation toward zero). Pass an explicit `RoundingMode` when a different behavior
is required.

### Throws

| Method                                    | `NullValueException`          | `EmptyValueException` |
|-------------------------------------------|-------------------------------|-----------------------|
| `toString(BigDecimal)`                    | value is null                 | —                     |
| `toString(BigDecimal, int)`               | value is null                 | —                     |
| `toString(BigDecimal, int, RoundingMode)` | value or roundingMode is null | —                     |
| `parse(String)`                           | value is null                 | —                     |
| `parse(String, int)`                      | value is null                 | value is empty        |
| `parse(String, int, RoundingMode)`        | value is null                 | value is empty        |
| `of(long/float/double)`                   | —                             | —                     |

`NullValueException` and `EmptyValueException` are subtypes of `IllegalArgumentException`
from the `software.frisby.core:validation` module.

### Anti-patterns

#### ❌ Constructing BigDecimal directly from double or float

```java
// Wrong — captures the binary floating-point representation
BigDecimal price = new BigDecimal(19.99);
// → 19.9900000000000002842170943040512...

// Correct
BigDecimal price = Decimals.of(19.99);
// → 19.99
```

#### ❌ Using BigDecimal.valueOf(double) as a substitute

```java
// BigDecimal.valueOf(double) is correct for simple values because it also routes
// through the string representation internally, BUT it does not strip trailing
// zeros or normalize the scale. Prefer Decimals.of() for consistent output.
BigDecimal a = BigDecimal.valueOf(1.50);  // scale = 2, toString() → "1.50"
BigDecimal b = Decimals.of(1.50);        // scale normalized, toString() → "1.5"
```

---

## Uris

Static utility methods for **scheme-agnostic structural manipulation** of `URI`
instances.  All methods operate on the raw structural components of a URI (scheme,
authority, path, query, fragment) and make no assumptions about the protocol.

For HTTP/S-specific operations see [`HttpUris`](#httpuris).

### Key behaviors

- **Hierarchical URIs only** — methods that read or modify path components require
  the URI to have an authority component (i.e., the URI string starts with
  `scheme://`).  Opaque URIs such as `mailto:user@example.com` are rejected with
  `IllegalArgumentException`.
- **Query and fragment not preserved** — `withPath`, `appendPath`, and `withoutPath`
  drop the query string and fragment.  Use `withoutQuery` first if you want to
  strip only the query from an otherwise intact URI.
- **No port normalization** — `withoutPath` and `withoutQuery` preserve the authority
  exactly as it appears in the URI (explicit port is kept even if it matches the
  scheme default).  Use `HttpUris.origin` when default port suppression is required.
- **Decoded userinfo** — `user` and `password` return percent-decoded values.  Only
  the first `:` in the userinfo is treated as the user/password separator; subsequent
  colons are part of the password.

### API

```java
import software.frisby.core.util.Uris;

URI api = URI.create("https://example.com:8443/api/v1/users?page=2#top");

// --- Path manipulation ---

Uris.withoutPath(api);
// → https://example.com:8443

Uris.withPath(api, "/health");
// → https://example.com:8443/health

Uris.appendPath(api, "profile");
// → https://example.com:8443/api/v1/users/profile

Uris.withoutQuery(api);
// → https://example.com:8443/api/v1/users

// --- Parsing ---

URI uri = Uris.parse("mongodb://user:pass@localhost:27017/admin?timeoutMS=5000");
// Throws NullValueException, BlankValueException, or IllegalArgumentException
// (wrapping URISyntaxException) rather than a checked URISyntaxException.

// --- Userinfo ---

Uris.user(uri);      // "user"
Uris.password(uri);  // "pass"

// Password containing a colon
URI db = Uris.parse("postgresql://frank:pa:ss@localhost/mydb");
Uris.user(db);      // "frank"
Uris.password(db);  // "pa:ss"

// No userinfo
Uris.user(URI.create("https://example.com/path"));      // null
Uris.password(URI.create("https://example.com/path"));  // null
```

### Method reference

| Method                    | Description                                                                                  |
|---------------------------|----------------------------------------------------------------------------------------------|
| `parse(String)`           | Parses a URI string; throws project-standard exceptions instead of `URISyntaxException`.     |
| `withoutPath(URI)`        | Returns a new URI with path, query, and fragment removed.                                    |
| `withPath(URI, String)`   | Returns a new URI with the path replaced; query and fragment are not preserved.              |
| `appendPath(URI, String)` | Appends a path segment with exactly one `/` separator; query and fragment are not preserved. |
| `withoutQuery(URI)`       | Returns a new URI with the query string and fragment removed; path is preserved.             |
| `user(URI)`               | Returns the decoded user component of the userinfo, or `null` if absent.                     |
| `password(URI)`           | Returns the decoded password component of the userinfo, or `null` if absent.                 |

### Throws

| Method                    | `NullValueException`   | `BlankValueException` | `IllegalArgumentException`     |
|---------------------------|------------------------|-----------------------|--------------------------------|
| `parse(String)`           | uri is null            | uri is blank          | uri is syntactically invalid   |
| `withoutPath(URI)`        | uri is null            | —                     | uri has no authority component |
| `withPath(URI, String)`   | uri or path is null    | —                     | uri has no authority component |
| `appendPath(URI, String)` | uri or segment is null | segment is blank      | uri has no authority component |
| `withoutQuery(URI)`       | uri is null            | —                     | uri has no authority component |
| `user(URI)`               | uri is null            | —                     | —                              |
| `password(URI)`           | uri is null            | —                     | —                              |

### Anti-patterns

#### ❌ Using URI.create() when you need validation-convention exceptions

```java
// Wrong — throws IllegalArgumentException wrapping URISyntaxException with
// no consistent message format; also accepts null silently on some paths
URI uri = URI.create(userInput);

// Correct — throws NullValueException, BlankValueException, or
// IllegalArgumentException with a project-standard message
URI uri = Uris.parse(userInput);
```

#### ❌ Calling withoutPath for HTTP origin extraction

```java
// Wrong — preserves an explicit default port (e.g. :443 on https)
URI base = Uris.withoutPath(URI.create("https://example.com:443/path"));
// → https://example.com:443   (port not suppressed)

// Correct — suppresses default ports per RFC 6454
URI origin = HttpUris.origin(URI.create("https://example.com:443/path"));
// → https://example.com
```

---

## HttpUris

Static utility methods for working with **HTTP and HTTPS** `URI` instances.  Every
method validates that the URI scheme is `http` or `https` (case-insensitive) before
doing any work.

For scheme-agnostic structural operations see [`Uris`](#uris).

### Key behaviors

- **Scheme enforcement** — all methods throw `NullValueException` if the URI has no
  scheme and `DisallowedValueException` if the scheme is not `http` or `https`.
- **Case-insensitive scheme comparison** — schemes are lowercased with `Locale.ROOT`
  before comparison, so `HTTPS://example.com` is accepted.
- **Default port suppression in `origin`** — port `80` on `http` and port `443` on
  `https` are treated as the scheme default and omitted from the origin string.
- **IPv6 support** — `origin` correctly preserves square brackets around IPv6
  addresses (e.g. `[::1]`).

### API

```java
import software.frisby.core.util.HttpUris;

URI api  = URI.create("https://example.com:8443/api/v1/users?page=2");
URI plain = URI.create("http://example.com/login");

// --- origin ---

HttpUris.origin(api);    // https://example.com:8443  (non-default port kept)
HttpUris.origin(plain);  // http://example.com         (port 80 suppressed)

HttpUris.origin(URI.create("https://example.com:443/path")); // https://example.com
HttpUris.origin(URI.create("https://[::1]:8443/api"));       // https://[::1]:8443

// --- effectivePort ---

HttpUris.effectivePort(api);    // 8443
HttpUris.effectivePort(plain);  // 80   (default for http)
HttpUris.effectivePort(URI.create("https://example.com/path")); // 443

// --- isDefaultPort ---

HttpUris.isDefaultPort(plain);                                    // true  (no port)
HttpUris.isDefaultPort(URI.create("http://example.com:80/path")); // true  (explicit default)
HttpUris.isDefaultPort(api);                                      // false

// --- isHttp / isHttps ---

HttpUris.isHttp(plain);   // true
HttpUris.isHttps(api);    // true
HttpUris.isHttp(null);    // throws NullValueException

// --- toHttps ---

HttpUris.toHttps(plain);  // https://example.com/login
HttpUris.toHttps(api);    // https://example.com:8443/api/v1/users?page=2  (unchanged)
```

### Method reference

| Method               | Description                                                                                 |
|----------------------|---------------------------------------------------------------------------------------------|
| `origin(URI)`        | Returns the RFC 6454 origin: scheme + host + port (omitted when it is the scheme default).  |
| `effectivePort(URI)` | Returns the explicit port, or the scheme default (80 / 443) if no port is specified.        |
| `isDefaultPort(URI)` | Returns `true` if no port is specified or the port equals the scheme default.               |
| `isHttp(URI)`        | Returns `true` if the scheme is `http` (case-insensitive).                                  |
| `isHttps(URI)`       | Returns `true` if the scheme is `https` (case-insensitive).                                 |
| `toHttps(URI)`       | Returns a new URI with the scheme changed to `https`; returns unchanged if already `https`. |

### Throws

All methods throw `NullValueException` if `uri` is null, `NullValueException` if the
URI has no scheme, and `DisallowedValueException` if the scheme is not `http` or
`https`.  `origin` additionally throws `IllegalArgumentException` for URIs with a
registry-based authority where `getHost()` returns null (e.g. hostnames containing
underscores).

### Anti-patterns

#### ❌ Using Uris.withoutPath for HTTP origin extraction

See the antipattern note in [`Uris`](#uris) above.

#### ❌ Passing a non-HTTP URI

```java
// Wrong — throws DisallowedValueException at runtime
HttpUris.origin(URI.create("ftp://files.example.com/data"));

// Correct — use Uris for scheme-agnostic operations
Uris.withoutPath(URI.create("ftp://files.example.com/data"));
// → ftp://files.example.com
```

