# frisby-core Validation — Quick Reference

**Package:** `software.frisby.core.validation`
**Dependency:** `software.frisby.core:validation`

This file is designed to be attached to an AI assistant's context window. It covers
every validator class with exact call signatures and representative examples.

---

## Core Pattern

Every method takes `(String name, T value, ...)` and returns `value` unchanged if valid,
or throws immediately if not. The `name` appears verbatim in exception messages:

```java
username = Strings.notBlankWithMaxLength("username", username, 50);
age      = Numbers.range("age", age, 0, 150);
// Throws: "The 'age' value of '200' is invalid. The value must be less than or equal to '150'."
```

`optional*` variants pass `null` through without validation — use for fields that are
genuinely optional:

```java
String subtitle = Strings.optionalNotBlankWithMaxLength("subtitle", subtitle, 200);
```

---

## Validator Selection Guide

Choose the validator class based on the **type** of the value being validated:

| Value type                                                 | Class                                            | Notes                                                                                                                        |
|------------------------------------------------------------|--------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------|
| Any single reference type                                  | `Values`                                         | Use for non-string, non-collection, non-map objects                                                                          |
| `String`                                                   | `Strings` / `TrimmedStrings`                     | `TrimmedStrings` trims whitespace before length checks and returns the trimmed value                                         |
| Numeric (`int`, `long`, `double`, `BigDecimal`, …)         | `Numbers`                                        | Primitive and boxed overloads for all 8 types                                                                                |
| `Duration`                                                 | `Durations`                                      |                                                                                                                              |
| `Period`                                                   | `Periods`                                        | No min/max/range — `Period` has no natural total ordering                                                                    |
| `Instant`, `LocalDate`, `LocalDateTime`, etc.              | `Instants`, `LocalDates`, `LocalDateTimes`, etc. |                                                                                                                              |
| `Collection<T>` or `T[]`                                   | `Sequences`                                      |                                                                                                                              |
| `Collection<String>` or `String[]` (validate each element) | `StringSequences`                                | Validates every string element within the collection; compose with `Sequences` size methods when a size bound is also needed |
| `Map<K, V>`                                                | `Maps`                                           |                                                                                                                              |

### Collection / array rules

**Never use `Values.notNull()` on a `Collection` or array.**  `Values.notNull()` only
rejects a null container — it silently allows an empty collection and null elements.
Use `Sequences.notEmpty()` instead; it atomically enforces all three constraints in one
call:
- `NullValueException` — container is null
- `MissingElementsException` — container is empty
- `NullElementException` — container contains a null element

**`Sequences.notNull()` vs `Sequences.noNullElements()` vs `Sequences.notEmpty()`:**
- `notNull` — rejects null container only; empty is accepted, null elements are accepted.
  Use only when an empty collection is genuinely valid and null elements are also acceptable.
- `noNullElements` — rejects null container and null elements; empty is accepted.
  Use when an empty collection is valid but null elements are not.
- `notEmpty` (and all size/duplicate methods) — rejects null, empty, and null elements.
  Use this in almost all cases.

**Don't call `notEmpty` before a size or duplicate method.**  `minSize()`, `maxSize()`,
`size()`, and `noDuplicates()` all include full `notEmpty` semantics.  A separate
`notEmpty()` call before any of these is redundant.

### Map rules

The same layering applies.  `Maps.notEmpty()` atomically rejects: null map, empty map,
null keys (`NullMapKeyException`), and null values (`NullMapValueException`).  Use it
instead of `Values.notNull()` + manual entry iteration.  All size methods on `Maps`
include these same checks.

To validate the content of keys or values as strings, compose `Maps.*` with
`StringSequences.*` on `map.keySet()` / `map.values()`:

```java
Maps.maxSize("headers", headers, 100);
StringSequences.notBlankWithMaxLength("headers.keys", headers.keySet(), 64);
StringSequences.notBlankWithMaxLength("headers.values", headers.values(), 256);
```

---

## Strings

```java
import software.frisby.core.validation.Strings;
import software.frisby.core.validation.TrimmedStrings;

Strings.notNull("field", value);
Strings.notEmpty("field", value);                                    // rejects null and ""
Strings.notBlank("field", value);                                    // rejects null, "", and whitespace-only
Strings.maxLength("field", value, 100);
Strings.minLength("field", value, 2);
Strings.length("field", value, 2, 100);
Strings.length("field", value, 8);                                   // exact length
Strings.notBlankWithMaxLength("field", value, 100);
Strings.notBlankWithMinLength("field", value, 2);
Strings.notBlankWithLength("field", value, 2, 100);
Strings.notBlankWithMatches("field", value, PATTERN);
Strings.notBlankWithMaxLengthAndMatches("field", value, 100, PATTERN);

// Optional variants
Strings.optionalNotBlank("field", value);
Strings.optionalNotBlankWithMaxLength("field", value, 100);
Strings.optionalMatches("field", value, PATTERN);
Strings.optionalNotBlankWithMatches("field", value, PATTERN);
Strings.optionalNotBlankWithMaxLengthAndMatches("field", value, 100, PATTERN);

// TrimmedStrings — trims whitespace before applying length constraints; returns trimmed value
TrimmedStrings.notBlankWithMaxLength("field", value, 100);
TrimmedStrings.notBlankWithLength("field", value, 2, 100);
TrimmedStrings.optionalNotBlankWithMaxLength("field", value, 100);
```

Length is measured in **Unicode code points**, not `char` values.

### Throws — Strings / TrimmedStrings

| Method family                     | `NullValueException`    | `EmptyValueException`            | `BlankValueException`            | `StringLengthOutsideRangeException` | `PatternMismatchException`       | `IllegalConfigurationException` |
|-----------------------------------|-------------------------|----------------------------------|----------------------------------|-------------------------------------|----------------------------------|---------------------------------|
| `notNull`                         | value is null           | —                                | —                                | —                                   | —                                | —                               |
| `notEmpty`                        | value is null           | value is empty                   | —                                | —                                   | —                                | —                               |
| `notBlank`                        | value is null           | —                                | value is blank                   | —                                   | —                                | —                               |
| `maxLength`                       | value is null           | —                                | —                                | value exceeds maxLength             | —                                | maxLength < 1                   |
| `minLength`                       | value is null           | —                                | —                                | value is shorter than minLength     | —                                | minLength < 1                   |
| `length(min,max)`                 | value is null           | —                                | —                                | value length outside range          | —                                | either bound < 1, or max < min  |
| `length(exact)`                   | value is null           | —                                | —                                | value is not exactly `length`       | —                                | length < 1                      |
| `notBlankWithMaxLength`           | value is null           | —                                | value is blank                   | value exceeds maxLength             | —                                | maxLength < 1                   |
| `notBlankWithMinLength`           | value is null           | —                                | value is blank                   | value is shorter than minLength     | —                                | minLength < 1                   |
| `notBlankWithLength`              | value is null           | —                                | value is blank                   | value length outside range          | —                                | bound invalid                   |
| `notBlankWithMatches`             | value is null           | —                                | value is blank                   | —                                   | value does not match             | pattern is null                 |
| `notBlankWithMaxLengthAndMatches` | value is null           | —                                | value is blank                   | value exceeds maxLength             | value does not match             | maxLength < 1 or pattern null   |
| `optional*` variants              | — (null passes through) | same as non-optional if non-null | same as non-optional if non-null | same as non-optional if non-null    | same as non-optional if non-null | same bound checks always apply  |

---

## Numbers

```java
import software.frisby.core.validation.Numbers;

// Ten constraint families — shown here for int; identical families exist for
// long, short, byte, float, double, BigDecimal, and BigInteger.
// Primitive and boxed overloads exist for the first six types.

Numbers.positive("count", count);                     // value > 0
Numbers.notNegative("scale", scale);                  // value >= 0
Numbers.min("count", count, 1);                       // value >= 1
Numbers.max("count", count, 100);                     // value <= 100
Numbers.range("age", age, 0, 150);                    // 0 <= value <= 150
Numbers.minExclusive("ratio", ratio, 0);              // value > 0
Numbers.maxExclusive("ratio", ratio, 1);              // value < 1
Numbers.exclusiveRange("ratio", ratio, 0.0, 1.0);    // 0.0 < value < 1.0
Numbers.rangeExclusiveMax("index", index, 0, size);  // 0 <= value < size
Numbers.rangeExclusiveMin("index", index, 0, size);  // 0 < value <= size
Numbers.notNull("count", count);                      // boxed types only

// Optional variants — null passes through unchanged
Integer page   = Numbers.optionalPositive("page", page);
Integer scale  = Numbers.optionalNotNegative("scale", scale);
Double  weight = Numbers.optionalRange("weight", weight, 0.0, 1.0);
```

### Throws — Numbers

All constraint methods throw `NullPointerException` if `name` is null or blank.

| Overload / situation                     | `NullValueException`    | `NumericValueOutsideRangeException`  | `IllegalConfigurationException`                            |
|------------------------------------------|-------------------------|--------------------------------------|------------------------------------------------------------|
| Primitive overload (`int`, `long`, etc.) | —                       | value violates the bound             | range methods only: max < min (or max ≤ min for exclusive) |
| Boxed overload (`Integer`, `Long`, etc.) | value is null           | value violates the bound             | same as above                                              |
| `optional*` variants                     | — (null passes through) | value violates the bound if non-null | same bound checks always apply                             |
| `notNull` (boxed only)                   | value is null           | —                                    | —                                                          |

---

## Durations & Periods

```java
import software.frisby.core.validation.Durations;
import software.frisby.core.validation.Periods;

Durations.positive("timeout", timeout);              // > ZERO
Durations.notNegative("delay", delay);               // >= ZERO
Durations.min("window", window, Duration.ofSeconds(1));
Durations.range("window", window, Duration.ofSeconds(1), Duration.ofMinutes(5));
Durations.optionalPositive("timeout", timeout);

Periods.positive("tenure", tenure);                  // no component negative, at least one positive
Periods.notNegative("grace", grace);                 // no component negative
```

### Throws — Durations

All methods throw `NullPointerException` if `name` is null or blank, or if any bound argument is null.

| Method family        | `NullValueException`    | `DurationOutsideRangeException`  | `IllegalConfigurationException` |
|----------------------|-------------------------|----------------------------------|---------------------------------|
| `notNull`            | value is null           | —                                | —                               |
| `positive`           | value is null           | value is not > ZERO              | —                               |
| `notNegative`        | value is null           | value is negative                | —                               |
| `min`                | value is null           | value < min                      | —                               |
| `max`                | value is null           | value > max                      | —                               |
| `minExclusive`       | value is null           | value ≤ min                      | —                               |
| `maxExclusive`       | value is null           | value ≥ max                      | —                               |
| `range`              | value is null           | value outside [min, max]         | max < min                       |
| `exclusiveRange`     | value is null           | value outside (min, max)         | max ≤ min                       |
| `rangeExclusiveMax`  | value is null           | value outside [min, max)         | max ≤ min                       |
| `rangeExclusiveMin`  | value is null           | value outside (min, max]         | max ≤ min                       |
| `optional*` variants | — (null passes through) | same as non-optional if non-null | same bound checks always apply  |

### Throws — Periods

All methods throw `NullPointerException` if `name` is null or blank.

| Method family        | `NullValueException`    | `PeriodOutsideRangeException`              |
|----------------------|-------------------------|--------------------------------------------|
| `notNull`            | value is null           | —                                          |
| `positive`           | value is null           | value is zero or any component is negative |
| `notNegative`        | value is null           | any component is negative                  |
| `optional*` variants | — (null passes through) | same as non-optional if non-null           |

---

## Temporal Types

```java
import software.frisby.core.validation.Instants;        // java.time.Instant
import software.frisby.core.validation.LocalDates;      // java.time.LocalDate
import software.frisby.core.validation.LocalDateTimes;  // java.time.LocalDateTime
import software.frisby.core.validation.LocalTimes;      // java.time.LocalTime
import software.frisby.core.validation.OffsetDateTimes; // java.time.OffsetDateTime
import software.frisby.core.validation.OffsetTimes;     // java.time.OffsetTime
import software.frisby.core.validation.ZonedDateTimes;  // java.time.ZonedDateTime

// Range — all eight families on every type (min, max, range, exclusiveRange, etc.)
Instants.min("start", start, Instant.EPOCH);
LocalDates.max("dob", dob, LocalDate.now());
OffsetDateTimes.range("scheduledAt", scheduledAt, now, now.plusYears(1));

// Clock-relative — available on Instants, LocalDates, LocalDateTimes, OffsetDateTimes, ZonedDateTimes
Instants.past("recordedAt", recordedAt);
Instants.future("expiresAt", expiresAt);
Instants.pastOrPresent("timestamp", timestamp);
Instants.futureOrPresent("scheduledAt", scheduledAt);

// Explicit Clock (required for deterministic tests)
Instants.past("recordedAt", recordedAt, clock);
Instants.future("expiresAt", expiresAt, clock);

// Tolerance overloads — Instants, LocalDateTimes, OffsetDateTimes, ZonedDateTimes only
// Useful when validating timestamps from remote callers whose clocks may drift slightly
Instants.pastOrPresent("ts", ts, Duration.ofSeconds(2));          // accepts up to now + 2s
Instants.futureOrPresent("ts", ts, Duration.ofSeconds(2));        // accepts down to now - 2s
Instants.pastOrPresent("ts", ts, Duration.ofSeconds(2), clock);  // tolerance + explicit clock

// LocalTime and OffsetTime have the range family only — no clock-relative methods
LocalTimes.min("openTime", openTime, LocalTime.of(8, 0));
OffsetTimes.range("window", window, start, end);

// Optional variants — null passes through unchanged
Instants.optionalPast("recordedAt", recordedAt);
LocalDates.optionalFuture("expiresOn", expiresOn);
```

### Throws — Temporal Types

All methods throw `NullPointerException` if `name` is null or blank, or if any bound or `clock` argument is null. Each type uses its own dedicated exception class for all failures (range and clock-relative alike).

| Type              | Value failure exception               |
|-------------------|---------------------------------------|
| `Instants`        | `InstantOutsideRangeException`        |
| `LocalDates`      | `LocalDateOutsideRangeException`      |
| `LocalDateTimes`  | `LocalDateTimeOutsideRangeException`  |
| `LocalTimes`      | `LocalTimeOutsideRangeException`      |
| `OffsetDateTimes` | `OffsetDateTimeOutsideRangeException` |
| `OffsetTimes`     | `OffsetTimeOutsideRangeException`     |
| `ZonedDateTimes`  | `ZonedDateTimeOutsideRangeException`  |

| Method family                                                          | `NullValueException`    | `*OutsideRangeException`                                               | `IllegalConfigurationException`          |
|------------------------------------------------------------------------|-------------------------|------------------------------------------------------------------------|------------------------------------------|
| `notNull`                                                              | value is null           | —                                                                      | —                                        |
| `min` / `max` / `minExclusive` / `maxExclusive`                        | value is null           | value violates the bound                                               | —                                        |
| `range` / `exclusiveRange` / `rangeExclusiveMax` / `rangeExclusiveMin` | value is null           | value outside the range                                                | max invalid relative to min              |
| `past` / `future`                                                      | value is null           | value is not in the required direction                                 | —                                        |
| `pastOrPresent` / `futureOrPresent`                                    | value is null           | value is not in the required direction (outside tolerance if provided) | tolerance is null                        |
| `optional*` variants                                                   | — (null passes through) | same as non-optional if non-null                                       | same bound/tolerance checks always apply |

---

## Values, Sequences & Maps

```java
import software.frisby.core.validation.Values;
import software.frisby.core.validation.Sequences;
import software.frisby.core.validation.StringSequences;
import software.frisby.core.validation.Maps;

// Values — any reference type
Values.notNull("status", status);
Values.oneOf("role", role, Set.of("admin", "editor", "viewer"));
Values.notOneOf("username", username, Set.of("root", "system"));
Values.optionalOneOf("role", role, Set.of("admin", "editor"));

// Sequences — Collection<T> and T[] overloads exist for every method.
// notNull          → rejects null container only; empty and null elements are allowed.
// noNullElements   → rejects null container and null elements; empty is allowed.
// notEmpty         → rejects null, empty, and null elements.  Use this in almost all cases.
// All size and duplicate methods include full notEmpty semantics — do NOT call notEmpty first.
Sequences.notNull("queue", queue);                    // container must not be null; empty is accepted
Sequences.noNullElements("tags", tags);               // not null, no null elements; empty is accepted
Sequences.notEmpty("tags", tags);                     // not null, not empty, no null elements
Sequences.minSize("tags", tags, 1);                   // notEmpty + size >= 1
Sequences.maxSize("tags", tags, 10);                  // notEmpty + size <= 10
Sequences.size("tags", tags, 1, 10);                  // notEmpty + 1 <= size <= 10
Sequences.noDuplicates("ids", ids);                   // notEmpty + no duplicates (by equals)
Sequences.noDuplicates("users", users, User::getId);  // notEmpty + no duplicate keys

// Optional variants — null passes through unchanged; non-null values are fully validated
Sequences.optionalNoNullElements("tags", tags);
Sequences.optionalNotEmpty("tags", tags);
Sequences.optionalMinSize("tags", tags, 1);
Sequences.optionalMaxSize("tags", tags, 10);
Sequences.optionalSize("tags", tags, 1, 10);
Sequences.optionalNoDuplicates("ids", ids);
Sequences.optionalNoDuplicates("users", users, User::getId);

// StringSequences — validates each String element within the collection/array.
// notEmpty / notBlank / maxLength etc. reject an empty container.
// noEmptyElements / noBlankElements accept an empty container but validate non-empty ones.
StringSequences.notEmpty("tags", tags);                        // not null, not empty, elements not empty
StringSequences.notBlank("tags", tags);                        // not null, not empty, elements not blank
StringSequences.noEmptyElements("tags", tags);                 // not null, empty OK, elements not empty
StringSequences.noBlankElements("tags", tags);                 // not null, empty OK, elements not blank
StringSequences.notBlankWithMaxLength("tags", tags, 64);
StringSequences.notBlankWithMatches("codes", codes, CODE_PATTERN);
StringSequences.optionalNoEmptyElements("tags", tags);         // null OK, empty OK, elements not empty
StringSequences.optionalNoBlankElements("tags", tags);         // null OK, empty OK, elements not blank

// Maps — notEmpty also rejects null keys (NullMapKeyException) and null values (NullMapValueException).
// All size methods include the same null-key and null-value checks.
Maps.notNull("config", config);                    // map must not be null; empty is accepted
Maps.notEmpty("headers", headers);                 // not null, not empty, no null keys, no null values
Maps.minSize("headers", headers, 1);               // notEmpty + entry count >= 1
Maps.maxSize("headers", headers, 50);              // notEmpty + entry count <= 50
Maps.size("headers", headers, 1, 50);              // notEmpty + 1 <= entry count <= 50

// Optional variants — null passes through unchanged
Maps.optionalNotEmpty("metadata", metadata);
Maps.optionalMinSize("metadata", metadata, 1);
Maps.optionalMaxSize("metadata", metadata, 50);
Maps.optionalSize("metadata", metadata, 1, 50);
```

### Throws — Values

All methods throw `NullPointerException` if `name` is null or blank, or if the `allowed`/`disallowed` set argument is null.

| Method             | `NullValueException`    | `DisallowedValueException`           | `IllegalConfigurationException` |
|--------------------|-------------------------|--------------------------------------|---------------------------------|
| `notNull`          | value is null           | —                                    | —                               |
| `oneOf`            | value is null           | value is not in the allowed set      | allowed set is empty            |
| `notOneOf`         | value is null           | value is in the disallowed set       | disallowed set is empty         |
| `optionalOneOf`    | — (null passes through) | value not in allowed set if non-null | allowed set is empty            |
| `optionalNotOneOf` | — (null passes through) | value in disallowed set if non-null  | disallowed set is empty         |

### Throws — Sequences

All methods throw `NullPointerException` if `name` is null or blank.

| Method family        | `NullValueException`    | `MissingElementsException` | `NullElementException` | `SequenceSizeOutsideRangeException` | `DuplicateElementsException` | `IllegalConfigurationException` |
|----------------------|-------------------------|----------------------------|------------------------|-------------------------------------|------------------------------|---------------------------------|
| `notNull`            | container is null       | —                          | —                      | —                                   | —                            | —                               |
| `noNullElements`     | container is null       | —                          | element is null        | —                                   | —                            | —                               |
| `notEmpty`           | container is null       | container is empty         | element is null        | —                                   | —                            | —                               |
| `minSize`            | container is null       | container is empty         | element is null        | size < minSize                      | —                            | minSize < 1                     |
| `maxSize`            | container is null       | container is empty         | element is null        | size > maxSize                      | —                            | maxSize < 1                     |
| `size`               | container is null       | container is empty         | element is null        | size outside [min,max]              | —                            | bound < 1, or max < min         |
| `noDuplicates`       | container is null       | container is empty         | element is null        | —                                   | duplicate element found      | —                               |
| `optional*` variants | — (null passes through) | same if non-null           | same if non-null       | same if non-null                    | same if non-null             | same bound checks always apply  |

### Throws — StringSequences

Every method first applies the full `notEmpty` check on the container, then validates each string element. All methods throw `NullPointerException` if `name` is null or blank.

| Method family           | Container failures                                                       | Per-element failures                                       | `IllegalConfigurationException` |
|-------------------------|--------------------------------------------------------------------------|------------------------------------------------------------|---------------------------------|
| `notEmpty`              | `NullValueException`, `MissingElementsException`, `NullElementException` | `EmptyValueException`                                      | —                               |
| `noEmptyElements`       | `NullValueException`, `NullElementException`                             | `EmptyValueException`                                      | —                               |
| `notBlank`              | `NullValueException`, `MissingElementsException`, `NullElementException` | `BlankValueException`                                      | —                               |
| `noBlankElements`       | `NullValueException`, `NullElementException`                             | `BlankValueException`                                      | —                               |
| `maxLength`             | `NullValueException`, `MissingElementsException`, `NullElementException` | `StringLengthOutsideRangeException`                        | maxItemLength < 1               |
| `minLength`             | `NullValueException`, `MissingElementsException`, `NullElementException` | `StringLengthOutsideRangeException`                        | minItemLength < 1               |
| `length`                | `NullValueException`, `MissingElementsException`, `NullElementException` | `StringLengthOutsideRangeException`                        | bound < 1, or max < min         |
| `matches`               | `NullValueException`, `MissingElementsException`, `NullElementException` | `PatternMismatchException`                                 | pattern is null                 |
| `notBlankWithMaxLength` | `NullValueException`, `MissingElementsException`, `NullElementException` | `BlankValueException`, `StringLengthOutsideRangeException` | maxItemLength < 1               |
| `notBlankWithMatches`   | `NullValueException`, `MissingElementsException`, `NullElementException` | `BlankValueException`, `PatternMismatchException`          | pattern is null                 |
| `optional*` variants    | — (null passes through)                                                  | same as non-optional if non-null                           | same bound checks always apply  |

### Throws — Maps

All methods throw `NullPointerException` if `name` is null or blank.

| Method family        | `NullValueException`    | `MissingElementsException` | `NullMapKeyException` | `NullMapValueException` | `MapSizeOutsideRangeException` | `IllegalConfigurationException` |
|----------------------|-------------------------|----------------------------|-----------------------|-------------------------|--------------------------------|---------------------------------|
| `notNull`            | map is null             | —                          | —                     | —                       | —                              | —                               |
| `notEmpty`           | map is null             | map is empty               | null key found        | null value found        | —                              | —                               |
| `minSize`            | map is null             | map is empty               | null key found        | null value found        | entry count < minSize          | minSize < 1                     |
| `maxSize`            | map is null             | map is empty               | null key found        | null value found        | entry count > maxSize          | maxSize < 1                     |
| `size`               | map is null             | map is empty               | null key found        | null value found        | count outside [min,max]        | bound < 1, or max < min         |
| `optional*` variants | — (null passes through) | same if non-null           | same if non-null      | same if non-null        | same if non-null               | same bound checks always apply  |

---

## FieldGroups

`FieldGroup` is a reusable descriptor that maps field names to value positions.
Declare it `private static final` — construction happens once at class initialization;
`FieldGroups` method calls allocate nothing.

```java
import software.frisby.core.validation.FieldGroup;
import software.frisby.core.validation.FieldGroups;

private static final FieldGroup PAYMENT_FIELDS =
        FieldGroup.of("creditCardToken", "bankAccountId", "walletId");

// Field names map positionally to value arguments
FieldGroups.atLeastOne(PAYMENT_FIELDS, creditCardToken, bankAccountId, walletId);
FieldGroups.onlyOne(PAYMENT_FIELDS, creditCardToken, bankAccountId, walletId);
FieldGroups.atMostOne(PAYMENT_FIELDS, creditCardToken, bankAccountId, walletId);

private static final FieldGroup ADDRESS_FIELDS =
        FieldGroup.of("street", "city", "postalCode", "country");

FieldGroups.noneOrAll(ADDRESS_FIELDS, street, city, postalCode, country);
```

### Throws — FieldGroups

All methods throw `NullPointerException` if the `FieldGroup` argument is null.

| Constraint   | Accepts                  | `MissingFieldException`              | `TooManyFieldsException`   |
|--------------|--------------------------|--------------------------------------|----------------------------|
| `atLeastOne` | ≥ 1 non-null             | all values are null                  | —                          |
| `onlyOne`    | exactly 1 non-null       | all values are null                  | more than 1 non-null value |
| `atMostOne`  | 0 or 1 non-null          | —                                    | more than 1 non-null value |
| `noneOrAll`  | all null or all non-null | some but not all values are non-null | —                          |

---

## Exception Hierarchy

All value-failure exceptions are in the **`software.frisby.core.validation`** package
(no sub-package) and extend `IllegalArgumentException`. Catch specific types for
precise error handling (e.g. mapping to HTTP 400 response bodies):

```java
catch (NullValueException e)                        { /* value was null */ }
catch (NullElementException e)                      { /* collection/array contained a null element */ }
catch (NullMapKeyException e)                       { /* map contained a null key */ }
catch (NullMapValueException e)                     { /* map contained a null value */ }
catch (EmptyValueException e)                       { /* string was empty */ }
catch (MissingElementsException e)                  { /* collection/array was empty */ }
catch (BlankValueException e)                       { /* string was blank */ }
catch (StringLengthOutsideRangeException e)         { /* string length out of range */ }
catch (PatternMismatchException e)                  { /* string did not match pattern */ }
catch (NumericValueOutsideRangeException e)         { /* number out of range */ }
catch (DurationOutsideRangeException e)             { /* Duration out of range */ }
catch (PeriodOutsideRangeException e)               { /* Period fails sign constraint */ }
catch (InstantOutsideRangeException e)              { /* Instant out of range or wrong direction */ }
catch (LocalDateOutsideRangeException e)            { /* LocalDate out of range or wrong direction */ }
catch (LocalDateTimeOutsideRangeException e)        { /* LocalDateTime out of range or wrong direction */ }
catch (LocalTimeOutsideRangeException e)            { /* LocalTime out of range */ }
catch (OffsetDateTimeOutsideRangeException e)       { /* OffsetDateTime out of range or wrong direction */ }
catch (OffsetTimeOutsideRangeException e)           { /* OffsetTime out of range */ }
catch (ZonedDateTimeOutsideRangeException e)        { /* ZonedDateTime out of range or wrong direction */ }
catch (DisallowedValueException e)                  { /* value not in allowed set, or in disallowed set */ }
catch (SequenceSizeOutsideRangeException e)         { /* collection/array size out of range */ }
catch (DuplicateElementsException e)                { /* collection/array contains duplicates */ }
catch (MapSizeOutsideRangeException e)              { /* map size out of range */ }
catch (MissingFieldException e)                     { /* FieldGroups: too few fields provided */ }
catch (TooManyFieldsException e)                    { /* FieldGroups: too many fields provided */ }
catch (IllegalConfigurationException e)             { /* API misuse — a bound argument was invalid */ }
```

`IllegalConfigurationException` extends `RuntimeException` (not `IllegalArgumentException`)
— it signals a programming error in the calling code, not an invalid user-supplied value.

---

## Anti-patterns

### ❌ Using `Values.notNull()` on a collection or array

```java
// Wrong — only catches null container; silently allows empty list and null elements
Values.notNull("items", items);
for (Item item : items) {
    Values.notNull("item", item);
}

// Correct — one call handles all three failure modes atomically
Sequences.notEmpty("items", items);
```

### ❌ Calling `notEmpty` redundantly before a size or duplicate method

```java
// Wrong — notEmpty is already included in minSize/maxSize/size/noDuplicates
Sequences.notEmpty("tags", tags);
Sequences.size("tags", tags, 1, 10);

// Correct
Sequences.size("tags", tags, 1, 10);
```

### ❌ Using `Values.notNull()` on a map

```java
// Wrong — only catches null map; silently allows empty map, null keys, and null values
Values.notNull("headers", headers);

// Correct — atomically checks null map, empty, null keys, and null values
Maps.notEmpty("headers", headers);
```

### ❌ Manual per-element null checking on a collection

```java
// Wrong — re-implements what Sequences already does, and uses the wrong exception type
// (Values.notNull throws NullValueException, not NullElementException)
for (String tag : tags) {
    Values.notNull("tag", tag);
}

// Correct — Sequences.notEmpty() checks elements and throws NullElementException
Sequences.notEmpty("tags", tags);
```
