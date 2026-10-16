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
- Use `ScheduledEvent` / `ScheduledEventBuilder` for recurring, application-visible events
  independent of whatever the resource method's own main loop is doing.

---

## Core Design

- `SseEmitter` is wire-level only: `send(SseEvent)`.
- There is no typed `send(String, T)` overload.
- `SseEvent.data()` is `String` and is required.
- `SseEvent.id()`, `event()`, and `retry()` are optional.
- Heartbeats are SSE comments (`: keep-alive`), not named events.
- A newly built emitter always writes one leading comment frame immediately, whether or
  not `heartbeat(...)` was ever configured — see `heartbeat(Duration)` below.

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
  - treat exceptional completion as terminal for the current stream; log/cleanup and return from the resource method
- `boolean isOpen()`
  - reflects `!sink.isClosed()`
  - point-in-time only; always still handle send failure races after an `isOpen()` check
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
  - if not called, no *recurring* heartbeat is scheduled — but the emitter still writes
    a single leading comment frame immediately on `build()` regardless (see below)
  - emits comment frames (for example `: keep-alive`)
  - does not emit `id`/`event`/`data`/`retry`
  - heartbeat send is best-effort: closed sinks are skipped; heartbeat send failures are logged internally and not thrown to callers
  - safe to race: a resource method's own `send()` calls and the recurring heartbeat tick may hit the same sink concurrently with no caller-side synchronization or settling delay needed; the underlying transport (Jersey's `ChunkedOutput`) serializes writes internally

Terminal:

- `build()`

---

## `ScheduledEvent` (recurring application-level push)

Purpose: have an `SseEmitter` periodically send a real, application-visible `SseEvent` on
its own schedule, independent of whatever the resource method's own main loop is doing.
Unlike `heartbeat(Duration)` — an invisible SSE *comment* frame purely for keep-alive —
a scheduled event is dispatched through the exact same `onEvent(name, handler)` pipeline
on the client as any event a resource method sends manually. No client-side changes are
needed to consume one.

Factory:

```java
ScheduledEventBuilder builder = ScheduledEvent.builder();
```

Accessors:

- `Duration interval()`
- `Supplier<SseEvent> supplier()`

### `ScheduledEventBuilder`

- `interval(Duration interval)`
  - required before `build()`
  - must be positive (same validation as `heartbeat(Duration)`)
- `supplier(Supplier<SseEvent> supplier)`
  - required before `build()`
  - rejects `null`
  - invoked once per tick; returning `null` from the supplier is a first-class "skip this
    tick" signal — not an error, not logged as a failure
  - a supplier that throws does not prevent subsequent ticks from firing
- `build()`
  - rejects missing `interval` or `supplier`

### `SseEmitterBuilder.scheduledEvent(ScheduledEvent)`

```java
SseEmitterBuilder scheduledEvent(ScheduledEvent event);
```

- optional; additive — may be called more than once to register multiple independently
  -scheduled events; the common case is a single scheduled event, but nothing prevents
  configuring more
- independent of, and composable with, `heartbeat(Duration)` — the two serve different
  purposes and are commonly configured at different cadences (for example a 15 second
  keep-alive alongside a 15 minute "report your stats" prompt)
- a scheduled event's first tick fires after one full `interval` has elapsed, not
  immediately — unlike `heartbeat(Duration)`'s first tick, which exists specifically to
  beat a client's time-to-first-byte read timeout; that guard is already unconditionally
  satisfied by the leading comment frame `build()` always writes, independent of whether
  any scheduled event is configured
- every scheduled event and the comment heartbeat (if configured) share the same
  single-thread scheduler owned by the `SseEmitter` — **keep suppliers fast**; a slow
  supplier delays every other tick due to fire around the same moment. If slow I/O is
  needed, do it asynchronously elsewhere and have the supplier return a
  precomputed/cached snapshot
- a supplier that throws, or a `sink.send(...)` failure, is logged at best-effort (`INFO`)
  and does not prevent subsequent ticks — exactly as resilient as a heartbeat send
  failure
- `close()` cancels every scheduled-event future, not just the heartbeat's
- don't give scheduled "wake up and act" events an `id` unless you specifically want them
  replayed on client reconnect via `Last-Event-ID` — they represent "current state, right
  now," not a historical fact worth replaying

```java
ScheduledEvent statsPrompt = ScheduledEvent.builder()
        .interval(Duration.ofMinutes(15))
        .supplier(() -> SseEvents.of(serializer)
                .event("stats-request")
                .data(buildStatsPrompt())
                .toEvent())
        .build();

try (SseEmitter emitter = SseEmitter.builder()
        .sink(sink)
        .sse(sse)
        .heartbeat(Duration.ofSeconds(15))
        .scheduledEvent(statsPrompt)
        .build()) {
    // ...
}
```

---



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

## Why `SseEmitter` always writes an immediate leading comment

A client-side per-request read timeout (e.g. `Client`'s `readTimeout()`, or
`SseSpec`/`SseListenerBuilder`'s `idleTimeout(Duration)`) typically bounds only
time-to-first-byte, not an already-open stream. If a resource method builds an
`SseEmitter` but doesn't write its own first event right away (e.g. it's waiting on a
slow upstream call), a client could otherwise see the connection fail before anything
was ever actually wrong — indistinguishable from a genuinely dead service. `SseEmitter`
closes this gap unconditionally: `build()` writes one leading comment frame
synchronously, whether or not `heartbeat(Duration)` was configured. When a heartbeat
interval *is* configured, that interval's own first tick (already scheduled with
`initialDelay == 0`) serves as this same leading frame — no duplicate frame is written.
This is a one-time frame, not a substitute for a recurring heartbeat; a stream that
stays genuinely idle afterward still needs `heartbeat(Duration)` configured to survive
intermediary proxies/load balancers.

---

## Disconnect Handling Guidance

- For long-lived streams (for example SQS long polling), loop while `emitter.isOpen()` is true.
- Still guard every `send(...).join()` with try/catch (`CompletionException`) because the sink can close after the `isOpen()` check.
- On send failure, treat the stream as closed/terminal, perform any logging/cleanup, and exit the resource method.
- Do not use heartbeat failures as control flow; heartbeat is internal best-effort keep-alive behavior.

---

## Common Pitfalls

- Do not expect `SseEmitter` to serialize typed values directly; use `SseEvents` first.
- Do not send heartbeats as business events unless you want clients to handle them.
- Do not omit `data` on `SseEventBuilder`; `build()` will fail.
- Do not pass negative retry durations.

