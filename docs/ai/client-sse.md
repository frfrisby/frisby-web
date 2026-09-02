# Server-Sent Events (SSE) Client — `software.frisby.web`

This document describes the complete public API for Server-Sent Events (SSE) support on
the client side, spanning two modules:

- **`client`** — `SseSpec` / `Client.sse()`. Raw stream access, zero dependency beyond
  the core client module. Use this if you want to write your own SSE reader.
- **`client-sse`** — `SseListener` / `SseListenerBuilder`. Typed, per-event-type callback
  dispatch with automatic reconnection, `Last-Event-ID` replay, and backpressure
  handling, built on `software.frisby.core:concurrency`.

Attach this document (and `docs/ai/concurrency.md`, if working on `client-sse`
internals) when writing code that consumes an SSE stream with `frisby-web`.

---

## Maven coordinates

Raw stream access only — no additional dependency beyond `client`:

```xml
<dependency>
    <groupId>software.frisby.web</groupId>
    <artifactId>client</artifactId>
</dependency>
```

Typed callback dispatch — also add:

```xml
<dependency>
    <groupId>software.frisby.web</groupId>
    <artifactId>client-sse</artifactId>
</dependency>
```

---

## Quick start — typed callback dispatch (`client-sse`)

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

## Quick start — bring your own reader (`client` only, no `client-sse` dependency)

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

---

## `SseSpec` (module: `client`)

Obtained from `Client.sse()`. Structurally identical to `GetSpec`, swapping the typed
`send()` terminals for raw stream terminals.

```java
SseSpec path(String path)
SseSpec path(String path, String parameterId, String parameterValue)
SseSpec path(String path, PathParameter... parameters)
SseSpec parameter(String name, String value)
SseSpec parameter(String name, String... values)
SseSpec header(String name, String value)
SseSpec header(String name, String... values)
SseSpec cookie(HttpCookie cookie)
SseSpec security(SecurityProvider provider)

HttpResponse<InputStream>                    stream()
CompletableFuture<HttpResponse<InputStream>> streamAsync()
```

- `stream()` / `streamAsync()` automatically set `Accept: text/event-stream` unless the
  caller already set an `Accept` header.
- The caller is responsible for parsing the SSE wire format and for closing the
  returned stream.
- The same client-managed-header restrictions as every other verb spec apply:
  `Accept`, `Accept-Encoding`, `Content-Type`, `Content-Length`, `Content-Encoding`,
  `Transfer-Encoding` throw `IllegalArgumentException` if set manually.
- Resuming a stream after a drop is just another request — set `Headers.LAST_EVENT_ID`:
  ```java
  client.sse()
          .path("/notifications/stream")
          .header(Headers.LAST_EVENT_ID, lastReceivedId)
          .stream();
  ```
- `readTimeout` (from `ClientConfiguration`) bounds only time-to-headers, not the full
  body read — a long-held SSE connection is never prematurely closed by it, exactly
  like `GetSpec.download()`.

---

## `SseListener` (module: `client-sse`)

A live, typed-callback connection to an SSE stream.

```java
static SseListenerBuilder builder()

void    connectAsync()   // non-blocking; starts the reader task, dispatch pipelines,
                          // and reconnect loop; returns immediately
boolean isOpen()          // true from connectAsync() until close(); unaffected by
                          // reconnect attempts, no matter how many consecutive failures
void    close()           // the ONLY way a connection ever stops; blocks until
                          // in-flight dispatch work completes; idempotent
```

- Once closed, an `SseListener` cannot be reopened — build a fresh instance via
  `SseListener.builder()`.
- Reconnects **unconditionally and indefinitely** on any connect/reconnect failure —
  there is no configurable retry limit and no way to disable reconnection. See
  [Reconnection and `Last-Event-ID` replay](#reconnection-and-last-event-id-replay).

---

## `SseListenerBuilder` (module: `client-sse`)

Obtained via `SseListener.builder()`. `.client(Client)` is required (may be called at
any point in the fluent chain — navigation calls made before it are validated the
moment it is called). `.build()` throws `IllegalStateException` if no `onEvent`/
`onUnhandledEvent` handler of any kind was ever registered.

### Navigation

Re-declared rather than delegating to a single `SseSpec` instance — the builder
captures navigation as an immutable template, replayed against a fresh `client.sse()`
call on every connection attempt (including reconnects, where `Last-Event-ID` must
reflect the most recently processed event).

```java
SseListenerBuilder client(Client client)                                         // required
SseListenerBuilder path(String path)
SseListenerBuilder path(String path, String parameterId, String parameterValue)
SseListenerBuilder path(String path, PathParameter... parameters)
SseListenerBuilder parameter(String name, String value)
SseListenerBuilder parameter(String name, String... values)
SseListenerBuilder header(String name, String value)
SseListenerBuilder header(String name, String... values)
SseListenerBuilder cookie(HttpCookie cookie)
SseListenerBuilder security(SecurityProvider provider)                           // re-invoked on every reconnect
SseListenerBuilder lastEventId(String id)                                        // initial value only; see below
```

Use `lastEventId(String)` — not `header(Headers.LAST_EVENT_ID, ...)` — to resume a
stream after a process restart, when no in-memory record of the last received id is
available. Once connected, the connection tracks the most recently processed event's
`id` itself and applies it automatically to every subsequent reconnect attempt.

### Dispatch registration

| Method | Notes |
|---|---|
| `onEvent(String event, SseHandler handler)` | Registers a single-event handler for `event`. |
| `onEvent(String event, SseBatchHandler handler)` | Registers a batch handler for `event`. Overload disambiguated by `handler`'s type. |
| `onUnhandledEvent(Consumer<SseMessage<String>> handler)` | Catch-all shorthand for `onUnhandledEvent(SseHandler.of(handler))` — a real dispatch pipeline with default tuning, not a degraded path. |
| `onUnhandledEvent(SseHandler handler)` | Catch-all with custom tuning. Must be raw — throws `IllegalArgumentException` if `handler` carries a `type()`/`genericType()`. |
| `onUnhandledEvent(SseBatchHandler handler)` | Catch-all, batched. Same raw-only restriction. Only one of the three `onUnhandledEvent` overloads is active at a time — the most recent call wins. |

`event` is matched against an event's **explicit** `event` field only — an event with
no `event` field at all is never matched here, even against a handler registered for
the literal string `"message"`; it is always routed to `onUnhandledEvent` instead. See
[`SseMessage.event()`](#ssemessaget).

`onEvent`/`onEventBatch` registrations throw `DuplicateElementsException` if `event` is
already registered — via either method.

### Backpressure

```java
SseListenerBuilder onBufferFull(BufferFullPolicy policy)           // default: BLOCK
SseListenerBuilder onDropped(Consumer<SseMessage<String>> handler) // only relevant for DROP
```

See [`BufferFullPolicy`](#bufferfullpolicy).

### Executor and reader task

```java
SseListenerBuilder executor(ExecutorService executor)   // default: a dedicated
                                                          // NamedExecutorService,
                                                          // shut down on close()
```

This one `ExecutorService` backs **both** the single dedicated reader task (which reads
the stream and manages reconnect) and every registered handler's own dispatch pipeline
— each event type gets its own independent pipeline; this executor is simply the
thread pool they all draw worker threads from, and does not itself control ordering,
capacity, or concurrency (those are configured per handler — see
[`SseHandler`](#ssehandler-and-ssebatchhandler)).

The reader task is submitted via `ExecutorService.submit(Runnable)`, not created as a
dedicated `Thread` — `close()` cancels it individually and precisely via the returned
`Future`, independent of whatever else (dispatch pipelines mid-graceful-drain, or
another `SseListener` sharing the same executor) is also running on it.
`client-sse` **never** calls `shutdown()`/`shutdownNow()` on a caller-supplied executor
— only `submit()`/`execute()`.

**Virtual threads (Java 21+):** supply `Executors.newVirtualThreadPerTaskExecutor()`
directly — no other change required, for both the reader task and every dispatch
pipeline:

```java
SseListener.builder().client(client)
        .executor(Executors.newVirtualThreadPerTaskExecutor())
        // ...
        .build();
```

### Error handling, reconnection, and shutdown

```java
SseListenerBuilder onError(Consumer<SseErrorEvent> handler)
SseListenerBuilder reconnectDelay(RetryDelay strategy)   // default: server retry field when
                                                          // present, else exponential(3s)
SseListenerBuilder closeTimeout(Duration timeout)        // default: 30 seconds
```

See [Error handling — `onError`](#error-handling--onerror),
[Reconnection and `Last-Event-ID` replay](#reconnection-and-last-event-id-replay), and
[`closeTimeout`](#closetimeout).

### Terminal

```java
SseListener build()   // validates + assembles; no I/O, no threads started
```

---

## `SseHandler` and `SseBatchHandler` (module: `client-sse`)

Self-fluent, per-handler dispatch configuration objects — one per registered
`onEvent`/`onUnhandledEvent` handler, obtained via a static `of(...)` factory (no
separate builder type; the callback is required up front, mirroring
`software.frisby.core.concurrency.fluent`'s `Buffer.of(X.class).capacity(...)` idiom).
**Not intended to be reused** across multiple registrations — create a fresh instance
per `onEvent` call.

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
SseBatchHandler batchSize(int batchSize)            // default 100 — a ceiling, not a
                                                     // target; low volume still delivers
                                                     // smaller batches, down to size 1
SseBatchHandler batchTimeout(Duration batchTimeout) // default 250ms
```

A batch is delivered as soon as either `batchSize` is reached or `batchTimeout`
elapses, whichever comes first. An individual item's deserialization failure is
omitted from the delivered batch — logged and routed to `onError` with that event's own
raw context — rather than discarding the whole batch.

### `capacity` / `concurrency` — apply per handler, independently

Each registered handler's `capacity`/`concurrency` govern **only its own** dedicated
dispatch pipeline — completely independent of every other event type's handler.
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

## `SseMessage<T>` (module: `client-sse`)

The unified value delivered to **every** registered handler — typed or raw alike.

```java
Optional<String> id()          // sent back as Last-Event-ID on reconnect once processed
Optional<String> event()       // see below
T                body()        // deserialized payload (typed), or the raw data string (raw)
Instant          receivedAt()  // stamped at parse time by the reader thread, not at
                                // dispatch time — unaffected by buffering/batching delay
```

`event()` reflects **exactly** what was received on the wire — empty means the server
sent no `event:` line at all, a condition distinct from an explicit `event: message`
field. An event with no `event` field is never matched against a handler registered
via `onEvent(String, ...)` — including one registered for the literal string
`"message"` — and is always routed to `onUnhandledEvent` instead.

---

## `SseErrorEvent` (module: `client-sse`)

The value delivered to a registered `onError` handler, pairing the failure with
whatever raw event context was available.

```java
public record SseErrorEvent(Optional<SseMessage<String>> message, Throwable cause)
```

| Scenario | `message()` |
|---|---|
| Deserialization failure for a specific event | Present — the untouched wire-format `data` string, never a typed payload |
| A registered handler's own callback throws | Present — same raw string |
| Connect/reconnect failure | Empty — not attributable to any single event |
| A batch handler's whole-batch callback throws | Empty — not attributable to any single item in the batch |

---

## `BufferFullPolicy` (module: `client-sse`)

Determines how a handler's dispatch buffer behaves when it fills faster than the
handler can drain it. Set via `SseListenerBuilder.onBufferFull(BufferFullPolicy)`,
default `BLOCK`.

| Value | Behavior |
|---|---|
| `BLOCK` | The reader task stalls when the buffer is full. Backpressure may propagate to the server via TCP flow control once OS socket buffers also fill. Safe from memory explosion. |
| `DROP` | Overflow events are silently discarded. The reader task stays healthy; the server is unaffected. Suitable for dashboards/metrics where occasional loss is acceptable. |
| `DISCONNECT` | The stream is closed and reconnected when the buffer fills. The last successfully processed event's `id` is sent as `Last-Event-ID` on reconnect, so the server can replay what was missed — clean "I'm not ready" backpressure paired with at-least-once delivery. |

### `DROP` observability

- Built-in logging is **edge-triggered**, not per-event — one `WARNING` when a run of
  drops begins, another when the buffer recovers (or the connection ends while a drop
  episode is still in progress), summarizing the count and duration. This avoids log
  floods under the sustained-high-volume conditions `DROP` is meant for.
- `onDropped(Consumer<SseMessage<String>> handler)` fires once per dropped event,
  unsummarized, for callers who want per-item granularity (a metrics counter, custom
  sampling, etc.) — regardless of the built-in logging above.
- `onDropped` is only ever invoked under `DROP`; never under `BLOCK` or `DISCONNECT`
  (`DISCONNECT` never actually discards an event — it reconnects and relies on
  `Last-Event-ID` replay instead).

---

## Reconnection and `Last-Event-ID` replay

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

- Reconnection is **unconditional and indefinite** — no retry limit, no way to disable
  it. `onError` is the caller's **only** mechanism for ever stopping a connection: track
  state (e.g. a failure count, or a specific unrecoverable status) inside the handler
  and call `SseListener.close()` once a condition is met. Omitting `onError` entirely
  for a connection prone to permanent failure results in silent, indefinite reconnects.
- Because every reconnect is an ordinary request through the standard `client` request
  path, a `SecurityProvider` with a dynamic token supplier (e.g. OAuth2
  client-credentials) is re-consulted on every attempt — token refresh on reconnect
  requires no special handling.
- A clean end-of-stream (the server finished writing normally) reconnects **silently**
  — no `Error` log, no `onError` call. Only an actual `IOException`/unexpected failure,
  or an unrecoverable HTTP status (404, 401/403, unresolvable host), triggers logging
  and `onError` — every such failure, even ones that can never succeed on retry.

---

## Error handling — `onError`

```java
SseListenerBuilder onError(Consumer<SseErrorEvent> handler)
```

- Fires for every failed connect/reconnect attempt (including permanently unrecoverable
  ones) and every deserialization failure or handler callback exception. Always logged
  at `Error` level regardless of whether a handler is registered.
- Does **not** stop the pipeline or close the connection by itself — see
  [Reconnection](#reconnection-and-last-event-id-replay) above.
- An exception thrown by the `onError` handler itself is caught and logged at
  `WARNING` — it never kills the reader task or a dispatch pipeline's worker thread.

---

## `closeTimeout`

```java
SseListenerBuilder closeTimeout(Duration timeout)   // default 30 seconds
```

Bounds how long `close()` waits for every handler's dispatch pipeline to finish
draining before giving up and returning anyway. Exists specifically to guard against a
scenario a pipeline's own completion signal cannot distinguish on its own: if a caller
supplies their own `ExecutorService` via `.executor(...)` and shuts it down
independently — without ever calling `close()` first — no worker thread remains to ever
resolve that pipeline's completion, and an unbounded wait would hang `close()` forever.
A timeout in that situation logs a `WARNING` rather than throwing, since `close()`
declares no checked exception.

---

## Complete example — production-grade `SseListener`

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

            // listener itself isn't assigned yet at builder-construction time — read it
            // back through a reference set immediately after build() completes below.
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




