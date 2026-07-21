# Serialization — `software.frisby.web`

This document describes the serialization API for `frisby-web`. Every module that sends
or receives JSON bodies — the HTTP client, the HTTP server, and the OAuth 2 token provider
— requires a `JsonSerializer`.

---

## `JsonSerializer` (`software.frisby.web.serial`)

A pluggable interface backed by any JSON library.  The library provides no default
implementation; callers must supply one.

```java
public interface JsonSerializer {
    byte[] serialize(Object value);
    <T> T deserialize(byte[] content, Class<T> type);
    <T> T deserialize(byte[] content, GenericType<T> genericType);
}
```

All `byte[]` content is UTF-8 encoded JSON (RFC 8259 §8.1).

**Maven coordinates** — `serial` module:
```xml
<dependency>
    <groupId>software.frisby.web</groupId>
    <artifactId>serial</artifactId>
</dependency>
```

---

## `GenericType<T>` (`software.frisby.web.serial`)

A type token that captures a generic type at runtime, working around Java type erasure.
Required when the target type is a generic container such as `List<Order>` or
`Map<String, User>`.

**Usage — anonymous subclass (most common):**
```java
List<Order> orders = client.get()
        .path("/orders")
        .send(new GenericType<List<Order>>() {})
        .body();
```

**Usage — named subclass (reusable):**
```java
public final class OrderListType extends GenericType<List<Order>> {}

List<Order> orders = client.get()
        .path("/orders")
        .send(new OrderListType())
        .body();
```

**Methods:**
- `type()` — returns the full `java.lang.reflect.Type` including type arguments.
- `rawType()` — returns the raw erased `Class<T>` (e.g. `List.class`).

---

## `JacksonSerializer` (`software.frisby.web.serial.jackson`)

A ready-to-use `JsonSerializer` backed by Jackson 2.x.

**Maven coordinates** — `jackson-serializer` module:
```xml
<dependency>
    <groupId>software.frisby.web</groupId>
    <artifactId>jackson-serializer</artifactId>
</dependency>
```

**Default `ObjectMapper` configuration:**

| Setting | Value |
|---|---|
| `FAIL_ON_UNKNOWN_PROPERTIES` | `false` — tolerant reading; new API fields don't break deserialization |
| `WRITE_DATES_AS_TIMESTAMPS` | `false` — dates as ISO-8601 strings |
| `WRITE_DURATIONS_AS_TIMESTAMPS` | `false` — durations as ISO-8601 strings |
| `WRITE_BIGDECIMAL_AS_PLAIN` | `true` — no scientific notation |
| Field visibility | `ANY` — serializes private fields; no getter boilerplate needed |
| Inclusion | `NON_EMPTY` — omits `null` and empty collections/strings |
| Modules | `Jdk8Module` (Optional etc.) + `JavaTimeModule` (LocalDate, Instant etc.) |

Jackson processing failures (malformed JSON, unserializable types, circular references)
are wrapped and rethrown as `UncheckedIOException`.

**Usage — default configuration:**
```java
JacksonSerializer serializer = JacksonSerializer.builder().build();
```

**Usage — custom `ObjectMapper`:**
```java
ObjectMapper mapper = new ObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);

JacksonSerializer serializer = JacksonSerializer.builder()
        .mapper(mapper)
        .build();
```

> When a custom `ObjectMapper` is supplied, no additional modules or features are
> registered on it — it is used exactly as provided.

---

## Custom `JsonSerializer` implementations

Implement `JsonSerializer` directly to use Gson, a custom Jackson setup, or any other
JSON library:

```java
// Jackson — zero intermediate allocation
public final class JacksonSerializer implements JsonSerializer {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public byte[] serialize(Object value) {
        try {
            return MAPPER.writeValueAsBytes(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Serialization failed.", e);
        }
    }

    @Override
    public <T> T deserialize(byte[] content, Class<T> type) {
        try {
            return MAPPER.readValue(content, type);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Deserialization failed.", e);
        }
    }

    @Override
    public <T> T deserialize(byte[] content, GenericType<T> genericType) {
        try {
            JavaType javaType = MAPPER.getTypeFactory().constructType(genericType.type());
            return MAPPER.readValue(content, javaType);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Deserialization failed.", e);
        }
    }
}
```

