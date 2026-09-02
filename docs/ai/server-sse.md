# Server SSE (`server-sse`) - AI Reference

Purpose: condensed API reference for generating correct code with `software.frisby.web:server-sse`.

---

## Module

Maven:

```xml
<dependency>
    <groupId>software.frisby.web</groupId>
    <artifactId>server-sse</artifactId>
</dependency>
```

`server-sse` is the server-side companion to `client`/`client-sse`.

- Build outbound wire events with `SseEvent` / `SseEventBuilder`.
- Send over a Jersey stream using `SseEmitter`.
- Use `SseEvents` when serializing typed payloads to JSON.

---

## Core Design

- `SseEmitter` is wire-level only: `send(SseEvent)`.
- There is no typed `send(String, T)` overload.
- `SseEvent.data()` is `String` and is required.
- `SseEvent.id()`, `event()`, and `retry()` are optional.
- Heartbeats are SSE comments (`: keep-alive`), not named events.

---

## `SseEvent`

Factory:

```java
SseEventBuilder builder = SseEvent.builder();
```

Accessors:

- `Optional<String> id()`
- `Optional<String> event()`
- `String data()`
- `Optional<Duration> retry()`

Semantics:

- `data` always present.
- `id`/`event`/`retry` may be absent.

### `SseEventBuilder`

Methods:

- `id(String id)`
  - optional
  - rejects blank
  - rejects NUL (`'\0'`)
  - max length 512
- `event(String event)`
  - optional
  - rejects blank
  - rejects `\n` and `\r`
- `data(String data)`
  - required before `build()`
  - rejects `null`
- `retry(Duration retry)`
  - optional
  - must be non-negative (`Duration.ZERO` allowed)
- `build()`
  - rejects missing `data`

---

## `SseEmitter`

Factory:

```java
SseEmitterBuilder builder = SseEmitter.builder();
```

Methods:

- `CompletableFuture<Void> send(SseEvent event)`
  - rejects null event
  - completes when sink send completes
  - completes exceptionally on send failures
  - unwraps `CompletionException` when it has a non-null cause
- `boolean isOpen()`
  - reflects `!sink.isClosed()`
- `void close()`
  - idempotent
  - cancels heartbeat future
  - shuts down heartbeat executor
  - closes sink

### `SseEmitterBuilder`

Required:

- `sink(SseEventSink sink)`
- `sse(Sse sse)`

Optional:

- `heartbeat(Duration heartbeatInterval)`
  - positive duration required when set
  - if not called, heartbeat disabled
  - emits comment frames (for example `: keep-alive`)
  - does not emit `id`/`event`/`data`/`retry`

Terminal:

- `build()`

---

## `SseEvents` (typed convenience)

Purpose: build a wire-ready `SseEvent` while serializing typed payloads via `JsonSerializer`.

Factory:

```java
SseEvents helper = SseEvents.of(serializer);
```

Methods:

- `id(String id)`
- `event(String event)`
- `data(String data)` (raw)
- `<T> data(T value)` (serialize to UTF-8 JSON string)
- `retry(Duration retry)`
- `toEvent()`

Notes:

- Create a fresh `SseEvents` per outbound event.
- `data(T)` rejects null and may throw `IllegalArgumentException` if serialization fails.

---

## Resource Method Pattern

```java
@Path("/notifications")
public final class NotificationResource {
    private final JsonSerializer serializer;
    private final NotificationService service;

    public NotificationResource(JsonSerializer serializer, NotificationService service) {
        this.serializer = serializer;
        this.service = service;
    }

    @GET
    @Path("/stream")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    public void stream(@Context SseEventSink sink,
                       @Context Sse sse,
                       @HeaderParam("Last-Event-ID") String lastEventId) {
        List<Notification> pending = service.eventsAfter(lastEventId);

        try (SseEmitter emitter = SseEmitter.builder()
                .sink(sink)
                .sse(sse)
                .heartbeat(Duration.ofSeconds(15))
                .build()) {
            for (Notification notification : pending) {
                emitter.send(
                        SseEvents.of(serializer)
                                .id(notification.id())
                                .event(notification.type())
                                .data(notification)
                                .retry(Duration.ofSeconds(2))
                                .toEvent()
                ).join();
            }
        }
    }
}
```

---

## Interop with `client-sse`

- Client reconnect replay uses `Last-Event-ID`; server should replay events newer than that id.
- Server `retry` hints are consumed by client reconnect delay logic.
- Heartbeat comments are ignored by the client parser and are not dispatched to handlers.

---

## Common Pitfalls

- Do not expect `SseEmitter` to serialize typed values directly; use `SseEvents` first.
- Do not send heartbeats as business events unless you want clients to handle them.
- Do not omit `data` on `SseEventBuilder`; `build()` will fail.
- Do not pass negative retry durations.

