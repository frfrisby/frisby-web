# frisby-web — Server-Sent Events (SSE)

Server-Sent Events (SSE) is a standard HTTP mechanism (`text/event-stream`) for
server-to-client streaming over a single long-lived connection. `frisby-web` supports
SSE on **both sides** of the wire:

- **Client** (`client` + `client-sse` modules) — subscribe to an SSE stream with typed,
  per-event-type callback dispatch, automatic reconnection, `Last-Event-ID` replay, and
  configurable backpressure. **Available now.**
- **Server** (`server-sse` module) — emit SSE events from a Jersey resource method with
  a typed, builder-based API and optional heartbeat. **Coming soon** — this section will
  be filled in once `server-sse` ships.

This single document covers the whole feature, both sides, in one place — the two
halves share a wire format and a reconnect contract (`Last-Event-ID`, the server's
`retry` field, heartbeat comment lines the client parser silently ignores), so building
an actual end-to-end streaming feature means understanding both together.

---

## Contents

1. [Why SSE?](#1-why-sse)
2. [Maven dependencies](#2-maven-dependencies)
3. [Quick start](#3-quick-start)
4. [Bring your own reader — raw stream access](#4-bring-your-own-reader--raw-stream-access)
5. [`SseListenerBuilder` reference](#5-sselistenerbuilder-reference)
6. [Per-handler tuning — `SseHandler` / `SseBatchHandler`](#6-per-handler-tuning--ssehandler--ssebatchhandler)
7. [`SseMessage<T>` — the value delivered to every handler](#7-ssemessaget--the-value-delivered-to-every-handler)
8. [Backpressure — `BufferFullPolicy`](#8-backpressure--bufferfullpolicy)
9. [Reconnection and `Last-Event-ID` replay](#9-reconnection-and-last-event-id-replay)
10. [Error handling — `onError` / `SseErrorEvent`](#10-error-handling--onerror--sseerrorevent)
11. [Executor, virtual threads, and shutdown](#11-executor-virtual-threads-and-shutdown)
12. [Complete example](#12-complete-example)
13. [Server-side SSE — `server-sse`](#13-server-side-sse--server-sse)

---

## 1. Why SSE?

| Property                                     | Detail                                                                                                                                                 |
|----------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Simpler than WebSockets for one-way data** | Plain HTTP — no protocol upgrade, works through the same proxies/load balancers/firewalls as any other request                                         |
| **No polling overhead**                      | One open connection per client instead of repeated request/response cycles during quiet periods                                                        |
| **Low per-event latency**                    | An event is delivered as soon as it's written — no poll-interval gap                                                                                   |
| **Built-in reconnect + replay**              | The wire format has a native `Last-Event-ID` mechanism for at-least-once delivery across reconnects — `frisby-web`'s client handles this automatically |
| **HTTP/2 friendly**                          | Multiple SSE streams multiplex over a single TCP connection                                                                                            |

**When plain request/response (or long-polling) is still fine:** low client counts,
corporate networks that aggressively kill idle connections, or deployments where the
stateless request/response model is operationally simpler to reason about.

---

## 2. Maven dependencies

Raw stream access only — no additional dependency beyond `client`:

```xml
<dependency>
    <groupId>software.frisby.web</groupId>
    <artifactId>client</artifactId>
</dependency>
```

Typed callback dispatch, automatic reconnection, and backpressure handling — also add:

```xml
<dependency>
    <groupId>software.frisby.web</groupId>
    <artifactId>client-sse</artifactId>
</dependency>
```

(See [§13](#13-server-side-sse--server-sse) for the server-side `server-sse` module,
once it ships.)

---

## 3. Quick start

```java
Client client = Client.builder()
        .configuration(c -> c
                .uri(URI.create("https://api.example.com"))
                .connectTimeout(Duration.ofSeconds(5))
                .readTimeout(Duration.ofSeconds(30))
                .serializer(JacksonSerializer.builder().build()))
        .build();

SseListener listener = SseListener.builder().client(client)
        .path("/notifications/stream")
        .parameter("clientId", myClientId)
        .onEvent("file-ready", SseHandler.of(FileReadyPayload.class, message ->
                processFile(message.body())))
        .onUnhandledEvent(message -> log.warn("Unknown event type: {}", message.event()))
        .onError(error -> log.error("SSE stream error", error.cause()))
        .build();

listener.connectAsync();   // non-blocking; returns immediately

// ... later, on shutdown ...
listener.close();          // blocks until in-flight dispatch work completes
```

`connectAsync()` starts a dedicated background reader task, one dispatch pipeline per
registered handler, and the reconnect loop, then returns immediately. Registered
handlers fire on the dispatch executor, never on the calling thread.

---

## 4. Bring your own reader — raw stream access

If you don't need typed dispatch, reconnection, or backpressure handling — or you want
to write your own SSE parser — `client`'s `SseSpec` (via `Client.sse()`) gives you the
raw response stream with **no additional dependency**:

```java
HttpResponse<InputStream> response = client.sse()
        .path("/notifications/stream")
        .parameter("clientId", myClientId)
        .stream();

try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body()))) {
    String line;
    while (null != (line = reader.readLine())) {
        // parse the text/event-stream wire format yourself
    }
}
```

`SseSpec` supports the same navigation methods as every other verb spec —
`path`/`parameter`/`header`/`cookie`/`security` — plus `stream()` and its async
counterpart `streamAsync()`. It automatically sets `Accept: text/event-stream` unless
you already set an `Accept` header, and — like `GetSpec.download()` — a configured
`readTimeout` bounds only time-to-headers, so a long-held connection is never
prematurely closed.

Resuming a stream after a drop is just another request — set `Headers.LAST_EVENT_ID`
with the last successfully processed event's `id`:

```java
client.sse()
        .path("/notifications/stream")
        .header(Headers.LAST_EVENT_ID, lastReceivedId)
        .stream();
```

The caller is responsible for parsing the wire format and closing the returned stream.
Everything from here on (§5 onward) requires the `client-sse` module.

---

## 5. `SseListenerBuilder` reference

Obtained via `SseListener.builder()`. `.client(Client)` is required (it may be called at
any point in the fluent chain — navigation calls made before it are validated the
moment it's called). `.build()` throws `IllegalStateException` if no `onEvent`/
`onUnhandledEvent` handler of any kind was ever registered.

### Navigation

Re-declared rather than delegating to a single `SseSpec` instance, because the builder
captures navigation as an immutable template that's replayed against a fresh
`client.sse()` call on every connection attempt — including reconnects, where
`Last-Event-ID` must reflect the most recently processed event.

| Method                                                                             | Notes                                                                                                                                                                                                                              |
|------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `client(Client)`                                                                   | Required.                                                                                                                                                                                                                          |
| `path(String)` / `path(String, String, String)` / `path(String, PathParameter...)` | Same semantics as every other verb spec.                                                                                                                                                                                           |
| `parameter(String, String)` / `parameter(String, String...)`                       | Query parameters.                                                                                                                                                                                                                  |
| `header(String, String)` / `header(String, String...)`                             | Request headers, applied to every connection *and* reconnect attempt.                                                                                                                                                              |
| `cookie(HttpCookie)`                                                               | Adds a cookie.                                                                                                                                                                                                                     |
| `security(SecurityProvider)`                                                       | Re-invoked on **every** reconnect — a dynamic token supplier (e.g. OAuth2 client-credentials) is refreshed automatically.                                                                                                          |
| `lastEventId(String)`                                                              | Sets the *initial* `Last-Event-ID`, for resuming a stream after a process restart. Once connected, the listener tracks the most recently processed event's `id` itself and applies it automatically on every subsequent reconnect. |

### Dispatch registration

| Method                                                                               | Notes                                                                                                                                                                                                                                                                                 |
|--------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `onEvent(String event, SseHandler handler)`                                          | Registers a single-event handler for `event`.                                                                                                                                                                                                                                         |
| `onEvent(String event, SseBatchHandler handler)`                                     | Registers a batch handler for `event` — overload disambiguated by `handler`'s type.                                                                                                                                                                                                   |
| `onUnhandledEvent(Consumer<SseMessage<String>> handler)`                             | Catch-all shorthand for any event type with no `onEvent` registration — a real dispatch pipeline with default tuning, not a degraded path.                                                                                                                                            |
| `onUnhandledEvent(SseHandler handler)` / `onUnhandledEvent(SseBatchHandler handler)` | Catch-all with custom tuning. Must be raw — throws `IllegalArgumentException` if `handler` carries a typed target, since an unhandled event has no known type to deserialize into. Only one of the three `onUnhandledEvent` overloads is active at a time; the most recent call wins. |

`event` is matched against an event's **explicit** `event` field only — an event with
no `event` field at all is never matched here, even against a handler registered for
the literal string `"message"`; it's always routed to `onUnhandledEvent` instead. See
[§7](#7-ssemessaget--the-value-delivered-to-every-handler).

`onEvent`/`onEventBatch` throw `DuplicateElementsException` if `event` is already
registered, via either method.

### Backpressure, error handling, reconnection, executor, shutdown

| Method                                            | Default                                                   | Notes                                                                    |
|---------------------------------------------------|-----------------------------------------------------------|--------------------------------------------------------------------------|
| `onBufferFull(BufferFullPolicy)`                  | `BLOCK`                                                   | See [§8](#8-backpressure--bufferfullpolicy).                             |
| `onDropped(Consumer<SseMessage<String>> handler)` | —                                                         | Only relevant under `DROP`. See [§8](#8-backpressure--bufferfullpolicy). |
| `onError(Consumer<SseErrorEvent> handler)`        | —                                                         | See [§10](#10-error-handling--onerror--sseerrorevent).                   |
| `reconnectDelay(RetryDelay strategy)`             | Server `retry` field when present, else `exponential(3s)` | See [§9](#9-reconnection-and-last-event-id-replay).                      |
| `executor(ExecutorService executor)`              | A dedicated `NamedExecutorService` per connection         | See [§11](#11-executor-virtual-threads-and-shutdown).                    |
| `closeTimeout(Duration timeout)`                  | 30 seconds                                                | See [§11](#11-executor-virtual-threads-and-shutdown).                    |

### Terminal

```java
SseListener build();   // validates + assembles; no I/O, no threads started
```

`SseListener` itself:

```java
void    connectAsync();   // non-blocking; starts the reader task, dispatch pipelines,
                           // and reconnect loop
boolean isOpen();          // true from connectAsync() until close(); unaffected by
                            // reconnect attempts, no matter how many consecutive failures
void    close();           // the ONLY way a connection ever stops; blocks until
                            // in-flight dispatch work completes; idempotent
```

Once closed, an `SseListener` cannot be reopened — build a fresh instance.

---

## 6. Per-handler tuning — `SseHandler` / `SseBatchHandler`

Every registered handler carries its **own**, independent dispatch tuning — obtained via
a static `of(...)` factory (no separate builder; the callback is required up front).
Create a fresh instance per registration — don't reuse one across multiple `onEvent`
calls.

### `SseHandler` — single-event delivery

```java
static <T> SseHandler of(Class<T> type, Consumer<SseMessage<T>> handler)
static <T> SseHandler of(GenericType<T> type, Consumer<SseMessage<T>> handler)
static      SseHandler of(Consumer<SseMessage<String>> handler)   // raw — no deserialization

SseHandler capacity(int capacity)       // default 1024
SseHandler concurrency(int concurrency) // default 1
```

### `SseBatchHandler` — burst-grouped delivery

```java
static <T> SseBatchHandler of(Class<T> type, Consumer<List<SseMessage<T>>> handler)
static <T> SseBatchHandler of(GenericType<T> type, Consumer<List<SseMessage<T>>> handler)
static      SseBatchHandler of(Consumer<List<SseMessage<String>>> handler)   // raw

SseBatchHandler capacity(int capacity)             // default 1024
SseBatchHandler concurrency(int concurrency)        // default 1
SseBatchHandler batchSize(int batchSize)            // default 100
SseBatchHandler batchTimeout(Duration batchTimeout) // default 250ms
```

A batch is delivered as soon as either `batchSize` is reached or `batchTimeout`
elapses, whichever comes first — a ceiling, not a target size to wait for. Under low
event volume, batches are routinely delivered well below `batchSize`, including a
"batch" of a single event. An individual item's deserialization failure is omitted from
the delivered batch — logged and routed to `onError` with that event's own raw context
— rather than discarding the whole batch.

### `capacity` and `concurrency` apply per handler, independently

Each handler's `capacity`/`concurrency` govern only its **own** dedicated dispatch
pipeline, completely independent of every other event type's handler — a hot event
type can scale out without affecting a rare one on the same connection.
`concurrency == 1` (the default) is a plain, serial, in-order pipeline.
**`concurrency > 1` forfeits in-order delivery for that event type** — events (or
batches) are fanned out round-robin/least-busy across `concurrency` independent worker
arms, so a later item may be delivered before an earlier one, and the callback must be
thread-safe:

```java
SseListener.builder().client(client)
        .path("/notifications/stream")
        .onEvent("file-ready", SseHandler.of(FileReadyPayload.class, message -> processFile(message.body()))
                .capacity(4096)
                .concurrency(8))
        .onEvent("heartbeat", SseHandler.of(HeartbeatPayload.class, message -> recordHeartbeat(message.body())))
        .build();
```

---

## 7. `SseMessage<T>` — the value delivered to every handler

The unified value delivered to **every** registered handler — typed or raw alike.

```java
Optional<String> id()          // sent back as Last-Event-ID on reconnect once processed
Optional<String> event()       // see below
T                body()        // deserialized payload (typed), or the raw data string (raw)
Instant          receivedAt()  // stamped at parse time, not at dispatch time — unaffected
                                // by buffering/batching delay before the handler runs
```

`event()` reflects **exactly** what was received on the wire — empty means the server
sent no `event:` line at all, a condition distinct from an explicit `event: message`
field. An event with no `event` field is never matched against a handler registered via
`onEvent(String, ...)` — including one registered for the literal string `"message"` —
and is always routed to `onUnhandledEvent` instead.

---

## 8. Backpressure — `BufferFullPolicy`

Determines how a handler's dispatch buffer behaves when it fills faster than the
handler can drain it. Set via `SseListenerBuilder.onBufferFull(BufferFullPolicy)`,
default `BLOCK`.

| Value        | Behavior                                                                                                                                                                                                                                                            |
|--------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `BLOCK`      | The reader task stalls when the buffer is full. Backpressure may propagate to the server via TCP flow control once OS socket buffers also fill. Safe from memory explosion.                                                                                         |
| `DROP`       | Overflow events are silently discarded. The reader task stays healthy; the server is unaffected. Suitable for dashboards/metrics where occasional loss is acceptable.                                                                                               |
| `DISCONNECT` | The stream is closed and reconnected when the buffer fills. The last successfully processed event's `id` is sent as `Last-Event-ID` on reconnect, so the server can replay what was missed — clean "I'm not ready" backpressure paired with at-least-once delivery. |

### Observing `DROP`

- Built-in logging is **edge-triggered**, not per-event — one `WARNING` when a run of
  drops begins, another when the buffer recovers (or the connection ends while a drop
  episode is still in progress), summarizing the count and duration. This avoids log
  floods under the sustained-high-volume conditions `DROP` is meant for.
- `onDropped(Consumer<SseMessage<String>> handler)` fires once per dropped event,
  unsummarized, for callers who want per-item granularity (a metrics counter, custom
  sampling, etc.) — regardless of the built-in logging above.
- `onDropped` only ever fires under `DROP` — never under `BLOCK` or `DISCONNECT`
  (`DISCONNECT` never actually discards an event; it reconnects and relies on
  `Last-Event-ID` replay instead).

---

## 9. Reconnection and `Last-Event-ID` replay

```
Connection drops (clean EOF, an I/O failure, or a policy-driven DISCONNECT)
    ↓
Not a clean close/deliberate DISCONNECT? → log at Error, invoke onError
    ↓
Wait: the server's most recently received retry field (one attempt only), else
      the configured reconnectDelay(RetryDelay) strategy
    ↓
Reconnect: re-invoke client.sse()...stream() (replaying the stored navigation
           template) with header(Headers.LAST_EVENT_ID, lastReceivedId)
    ↓
Server replays missed events (if it supports it) — normal event flow resumes
```

- Reconnection is **unconditional and indefinite** — there is no retry limit, and no way
  to disable it. `onError` is the caller's **only** mechanism for ever stopping a
  connection: track state (a failure count, or a specific unrecoverable HTTP status)
  inside the handler, and call `SseListener.close()` once a condition is met. Omitting
  `onError` for a connection prone to permanent failure results in a silent, indefinitely
  reconnecting connection.
- Because every reconnect is an ordinary request through the standard `client` request
  path, a `SecurityProvider` with a dynamic token supplier (e.g. OAuth2
  client-credentials) is re-consulted on every attempt — token refresh on reconnect
  requires no special handling.
- A clean end-of-stream (the server finished writing normally) reconnects **silently** —
  no `Error` log, no `onError` call. Only an actual `IOException`/unexpected failure, or
  an unrecoverable HTTP status (404, 401/403, unresolvable host), triggers logging and
  `onError` — every such failure, even ones that can never succeed on retry.
- `reconnectDelay(RetryDelay strategy)` reuses `client`'s existing `RetryDelay`
  abstraction — `RetryDelay.fixed(Duration)`, `.linear(Duration)`,
  `.exponential(Duration)`, or a custom lambda. A server-supplied `retry` field takes
  precedence over this strategy for the very next reconnect attempt only.

---

## 10. Error handling — `onError` / `SseErrorEvent`

```java
SseListenerBuilder onError(Consumer<SseErrorEvent> handler)
```

Fires for every failed connect/reconnect attempt (including permanently unrecoverable
ones) and every deserialization failure or handler callback exception. Always logged at
`Error` level regardless of whether a handler is registered. Does **not** stop the
pipeline or close the connection by itself — see [§9](#9-reconnection-and-last-event-id-replay).
An exception thrown by the `onError` handler itself is caught and logged at `WARNING` —
it never kills the reader task or a dispatch pipeline's worker thread.

`SseErrorEvent` pairs the failure with whatever raw event context was available:

```java
public record SseErrorEvent(Optional<SseMessage<String>> message, Throwable cause)
```

| Scenario                                      | `message()`                                                              |
|-----------------------------------------------|--------------------------------------------------------------------------|
| Deserialization failure for a specific event  | Present — the untouched wire-format `data` string, never a typed payload |
| A registered handler's own callback throws    | Present — same raw string                                                |
| Connect/reconnect failure                     | Empty — not attributable to any single event                             |
| A batch handler's whole-batch callback throws | Empty — not attributable to any single item in the batch                 |

```java
SseListener listener = SseListener.builder().client(client)
        // ...
        .onError(error -> {
            metrics.increment("sse.errors");

            if (isUnrecoverable(error.cause())) {
                listener.close();
            }
        })
        .build();
```

**Watch out:** the `listener` variable above isn't assigned until `.build()` returns,
so it can't be referenced from inside its own `.onError(...)` lambda like this — the
snippet above is illustrative only. In real code, capture a mutable reference (e.g. an
`AtomicReference<SseListener>`) set immediately after `build()` completes, and read it
back from within the handler instead.

---

## 11. Executor, virtual threads, and shutdown

```java
SseListenerBuilder executor(ExecutorService executor)   // default: a dedicated
                                                          // NamedExecutorService,
                                                          // shut down on close()
SseListenerBuilder closeTimeout(Duration timeout)        // default: 30 seconds
```

One `ExecutorService` backs **both** the single dedicated reader task (which reads the
stream and manages reconnect) and every registered handler's own dispatch pipeline —
each event type gets its own independent pipeline; this executor is simply the thread
pool they all draw worker threads from. `client-sse` **never** calls
`shutdown()`/`shutdownNow()` on a caller-supplied executor — only `submit()`/`execute()`.

The reader task is submitted via `ExecutorService.submit(Runnable)`, not created as a
dedicated `Thread` — `close()` cancels it individually and precisely via the returned
`Future`, independent of whatever else (dispatch pipelines mid-graceful-drain, or
another `SseListener` sharing the same executor) is also running on it.

### Virtual threads (Java 21+)

Supply `Executors.newVirtualThreadPerTaskExecutor()` directly — no other change
required, for both the reader task and every dispatch pipeline:

```java
SseListener.builder().client(client)
        .executor(Executors.newVirtualThreadPerTaskExecutor())
        // ...
        .build();
```

### `closeTimeout`

Bounds how long `close()` waits for every handler's dispatch pipeline to finish
draining before giving up and returning anyway. Exists specifically to guard against a
scenario a pipeline's own completion signal cannot distinguish on its own: if you
supply your own `ExecutorService` and shut it down independently — without ever calling
`close()` first — no worker thread remains to ever resolve that pipeline's completion,
and an unbounded wait would hang `close()` forever. A timeout in that situation logs a
`WARNING` rather than throwing, since `close()` declares no checked exception.

---

## 12. Complete example

```java
AtomicReference<SseListener> listenerRef = new AtomicReference<>();

SseListener listener = SseListener.builder().client(client)
        .path("/notifications/stream")
        .parameter("clientId", myClientId)
        .lastEventId(lastKnownEventId)               // resume after a process restart
        .executor(Executors.newVirtualThreadPerTaskExecutor())
        .onBufferFull(BufferFullPolicy.DISCONNECT)
        .onDropped(message -> metrics.increment("sse.dropped"))
        .reconnectDelay(RetryDelay.exponential(Duration.ofSeconds(1), Duration.ofSeconds(60)))
        .closeTimeout(Duration.ofSeconds(10))
        .onEvent("file-ready", SseHandler.of(FileReadyPayload.class, message -> processFile(message.body()))
                .capacity(4096)
                .concurrency(8))
        .onEventBatch("price-update", SseBatchHandler.of(PriceUpdate.class, updates -> processBatch(updates))
                .batchSize(50)
                .batchTimeout(Duration.ofMillis(100)))
        .onUnhandledEvent(message -> log.warn("Unknown event type: {}", message.event()))
        .onError(error -> {
            metrics.increment("sse.errors");

            if (isUnrecoverable(error.cause())) {
                SseListener current = listenerRef.get();

                if (null != current) {
                    current.close();
                }
            }
        })
        .build();

listenerRef.set(listener);
listener.connectAsync();

// ... application shutdown ...
listener.close();
```

---

## 13. Server-side SSE — `server-sse`

Not yet released. Once `server-sse` ships, this section will cover:

- `SseEvent` / `SseEventBuilder` — the outbound event value type (`id`, `event`, `data`,
  `retry`).
- `SseEmitter` / `SseEmitterBuilder` — the server-side wrapper around Jersey's
  `SseEventSink`/`Sse`, including typed `send(String, T)` and optional heartbeat.
- A worked resource-method example, including how an incoming `Last-Event-ID` header
  (via `@HeaderParam`) drives server-side replay of missed events.
- How the server's `retry` field and heartbeat comment lines interact with the client
  behavior documented in [§9](#9-reconnection-and-last-event-id-replay) and
  [§8](#8-backpressure--bufferfullpolicy) above.


