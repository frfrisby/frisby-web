# frisby-web — Server-Sent Events (SSE)

Server-Sent Events (SSE) is a standard HTTP mechanism (`text/event-stream`) for
server-to-client streaming over a single long-lived connection. `frisby-web` supports
SSE on **both sides** of the wire:

- **Client** (`client` + `client-sse` modules) — subscribe to an SSE stream with typed,
  per-event-type callback dispatch, automatic reconnection, `Last-Event-ID` replay, and
  configurable backpressure. **Available now.**
- **Server** (`server-sse` module) — emit SSE events from a Jersey resource method with
  a typed, builder-based API and optional heartbeat. **Available now.**

Both sides are documented here because they share a wire format and reconnect contract —
`Last-Event-ID`, server `retry` hints, and heartbeat comments — so end-to-end streaming
requires understanding both.

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
9. [Observability — `SseListenerObserver`](#9-observability--sselistenerobserver)
10. [`pipelineStats()` — `SsePipelineSnapshot` / `SsePipelineStats`](#10-pipelinestats--ssepipelinesnapshot--ssepipelinestats)
11. [Reconnection and `Last-Event-ID` replay](#11-reconnection-and-last-event-id-replay)
12. [Executor, virtual threads, and shutdown](#12-executor-virtual-threads-and-shutdown)
13. [Complete example](#13-complete-example)
14. [Server-side SSE — `server-sse`](#14-server-side-sse--server-sse)

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

(See [Section 14](#14-server-side-sse--server-sse) for the server-side `server-sse` module.)

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
        .observer(new SseListenerObserver() {
            @Override
            public void onError(SseErrorEvent error) {
                log.error("SSE stream error", error.cause());
            }
        })
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

`onEvent` throws `DuplicateElementsException` if `event` is already registered, via
either overload.

### Backpressure, observability, reconnection, executor, shutdown

| Method                                | Default                                                   | Notes                                                                                                                                |
|---------------------------------------|-----------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------|
| `onBufferFull(BufferFullPolicy)`      | `BLOCK`                                                   | See [§8](#8-backpressure--bufferfullpolicy).                                                                                         |
| `observer(SseListenerObserver)`       | —                                                         | Single registration point for failures, drops, reconnects, and per-event telemetry. See [§9](#9-observability--sselistenerobserver). |
| `reconnectDelay(RetryDelay strategy)` | Server `retry` field when present, else `exponential(3s)` | See [§11](#11-reconnection-and-last-event-id-replay).                                                                                |
| `executor(ExecutorService executor)`  | A dedicated `NamedExecutorService` per connection         | See [§12](#12-executor-virtual-threads-and-shutdown).                                                                                |
| `closeTimeout(Duration timeout)`      | 30 seconds                                                | See [§12](#12-executor-virtual-threads-and-shutdown).                                                                                |

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

SsePipelineSnapshot pipelineStats();   // on-demand occupancy snapshot; throws
                                       // IllegalStateException before connectAsync()
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
- `SseListenerObserver#onDropped` fires once per dropped event, unsummarized, for
  callers who want per-item granularity (a metrics counter, custom sampling, etc.) —
  regardless of the built-in logging above. Registered via
  [`observer(SseListenerObserver)`](#9-observability--sselistenerobserver).
- `onDropped` only ever fires under `DROP` — never under `BLOCK` or `DISCONNECT`
  (`DISCONNECT` never actually discards an event; it reconnects and relies on
  `Last-Event-ID` replay instead — see [§9](#9-observability--sselistenerobserver)'s
  `onReconnect` for that case).

### ⚠️ Pitfall: `DISCONNECT` can turn into a reconnect storm

`DISCONNECT` looks like clean backpressure in isolation — buffer fills, connection
resets, server replays from `Last-Event-ID`. But if the *handler itself* is
persistently slower than the incoming event rate, it never gets a chance to actually
drain under this policy: the buffer fills again almost immediately after each
reconnect, so the connection disconnects and reconnects again, indefinitely. In
aggregate this is an unbounded reconnect loop hammering the server, not genuine
relief. Two settings determine how bad it gets:

- **`capacity(int)`** (on the handler, not the listener) — too small a capacity for
  the handler's real throughput turns brief, ordinary bursts into constant disconnects.
  Size it to the handler's actual sustained throughput, not just enough to survive a
  momentary spike.
- **`reconnectDelay(RetryDelay)`** — a non-escalating strategy such as
  `RetryDelay.fixed(...)` never gives a persistently overwhelmed handler any breathing
  room; every disconnect immediately triggers the next reconnect at the same fixed
  interval, forever. Prefer an escalating strategy — the builder's default,
  `exponential(3s)`, already escalates — so a genuine storm backs off over time instead
 of retrying at a constant rate.

If a handler is fundamentally too slow for the stream's volume, no `reconnectDelay`
tuning fixes that on its own — consider `BLOCK` (bounded, no data loss, but may
propagate backpressure to the server) or `DROP` (bounded, lossy, keeps the connection
healthy) instead, or increase `concurrency` on the handler to actually raise its
drain rate.

---

## 9. Observability — `SseListenerObserver`

A single, optional registration point covering every observability concern
`client-sse` reports — failures, drops, reconnects, and per-event telemetry. Registered
via `SseListenerBuilder.observer(SseListenerObserver)`. Every method is a `default`
no-op — implement only the ones you care about; they are not mutually exclusive with
each other (e.g. `onEventReceived` always fires before `onEventProcessed` for a
dispatched event, and `onReconnect` fires alongside `onError` for the same transport
failure).

```java
default void onError(SseErrorEvent event)
default void onDropped(SseMessage<String> event)
default void onReconnect(SseReconnectEvent event)
default void onEventReceived(SseEventReceived event)
default void onEventProcessed(SseEventProcessed event)
```

Every method is invoked from whichever internal thread produced the event (typically
the reader thread for `onError`/`onReconnect`/`onDropped`/`onEventReceived`, or a
handler's own dispatch-pipeline worker thread for `onEventProcessed`) and is isolated
from any exception it throws — a misbehaving observer is logged at `WARNING` and cannot
take down a reader or worker thread. Optional overall; if never registered, failures
are still logged at `Error` and drop-episode summaries at `WARNING` internally, but no
programmatic callback fires.

```java
SseListener.builder().client(client)
        // ...
        .observer(new SseListenerObserver() {
            @Override
            public void onEventProcessed(SseEventProcessed event) {
                latencyTimer.record(event.totalLatency());
            }

            @Override
            public void onReconnect(SseReconnectEvent event) {
                metrics.increment("sse.reconnect." + event.cause());
            }
        })
        .build();
```

### `onError` / `SseErrorEvent`

Fires for every failed connect/reconnect attempt (including permanently unrecoverable
ones) and every deserialization failure or handler callback exception. Always logged at
`Error` level regardless of whether an observer is registered. Does **not** stop the
pipeline or close the connection by itself — see
[§11](#11-reconnection-and-last-event-id-replay). An exception thrown by `onError`
itself is caught and logged at `WARNING` — it never kills the reader task or a dispatch
pipeline's worker thread.

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
AtomicReference<SseListener> listenerRef = new AtomicReference<>();

SseListener listener = SseListener.builder().client(client)
        // ...
        .observer(new SseListenerObserver() {
            @Override
            public void onError(SseErrorEvent error) {
                metrics.increment("sse.errors");

                // listener isn't assigned until build() returns — read it back
                // through a reference set immediately after build() completes below.
                if (isUnrecoverable(error.cause())) {
                    SseListener current = listenerRef.get();

                    if (null != current) {
                        current.close();
                    }
                }
            }
        })
        .build();

listenerRef.set(listener);
```

`onError` is the caller's **only** mechanism for ever stopping a connection: track
state (a failure count, or a specific unrecoverable status) inside the observer, and
call `SseListener.close()` once a condition is met. Omitting an `observer` entirely for
a connection prone to permanent failure results in silent, indefinite reconnects (still
logged internally, but with no programmatic hook).

### `onDropped`

Only relevant under `BufferFullPolicy.DROP` — see [§8](#8-backpressure--bufferfullpolicy).
Fires once per dropped event, unsummarized; never fires under `BLOCK` or `DISCONNECT`.

### `onReconnect` / `SseReconnectEvent` / `SseReconnectCause`

Fires immediately before every reconnect attempt that follows a **setback** — either a
genuine transport failure or a policy-driven `BufferFullPolicy.DISCONNECT` — never for
a clean end-of-stream reconnect, since that does not advance the backoff strategy's
attempt count.

```java
public record SseReconnectEvent(SseReconnectCause cause, Optional<String> eventType, int attempt, Duration delay)

public enum SseReconnectCause { FAILURE, BUFFER_FULL }
```

| Field       | Meaning                                                                                                                                                                                                                     |
|-------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `cause`     | `FAILURE` — a genuine transport failure or the initial connect attempt failing; always paired with the same failure also reported via `onError`. `BUFFER_FULL` — a policy-driven `DISCONNECT`; never paired with `onError`. |
| `eventType` | The handler event type whose dispatch buffer triggered the reconnect — present only when `cause == BUFFER_FULL` **and** the buffer belonged to a named handler; empty for `FAILURE`, and empty for the catch-all pipeline.  |
| `attempt`   | The 1-based consecutive-setback count — the same counter driving the configured `reconnectDelay` strategy's escalation.                                                                                                     |
| `delay`     | The computed delay before this reconnect attempt.                                                                                                                                                                           |

This is the programmatic way to detect a `DISCONNECT` reconnect storm forming (see the
pitfall in [§8](#8-backpressure--bufferfullpolicy)) — wire `onReconnect` (with
`cause == BUFFER_FULL`) into metrics/alerting instead of only log-scraping.

### `onEventReceived` / `onEventProcessed` / `SseEventReceived` / `SseEventProcessed`

Real-time, per-event telemetry — as distinct from [`pipelineStats()`](#10-pipelinestats--ssepipelinesnapshot--ssepipelinestats)'s
on-demand occupancy snapshot. `onEventReceived` always fires before `onEventProcessed`
for an event that is actually dispatched.

```java
public record SseEventReceived(Optional<String> event,
                               Optional<String> registeredAs,
                               Optional<String> id,
                               Instant receivedAt)

public record SseEventProcessed(Optional<String> event,
                                Optional<String> registeredAs,
                                Optional<String> id,
                                Instant receivedAt,
                                Duration totalLatency,
                                Duration processingDuration,
                                boolean succeeded)
```

- **`onEventReceived`** fires the moment a raw event is accepted into a dispatch
  pipeline, before deserialization — mirrors `SseMessage` minus `body()`. Fired only
  when the event is actually accepted; an event rejected under `DROP` or one that
  triggers `DISCONNECT` never reaches this callback (already reported via `onDropped`/
  `onReconnect` respectively).
- **`registeredAs()`** distinguishes a named handler's pipeline from the catch-all
  unhandled-event pipeline — present when a named `onEvent` handler matched; empty when
  routed to the catch-all pipeline, regardless of whether `event()` itself was present:

  | Case                  | `event()`          | `registeredAs()`   |
  |-----------------------|--------------------|--------------------|
  | Handled               | `Some("foo")`      | `Some("foo")`      |
  | Unhandled but named   | `Some("foo")`      | `Optional.empty()` |
  | Unhandled and unnamed | `Optional.empty()` | `Optional.empty()` |

- **`onEventProcessed`** fires the moment a handler's callback returns or throws.
  `succeeded()` is `false` whenever the callback threw, rather than suppressing the
  event entirely, so telemetry built purely on this callback never has a blind spot for
  failed invocations. A failed callback is still fully reported via `onError` as well.
  - `totalLatency()` — elapsed time from `receivedAt()` to the callback returning or
    throwing; includes queueing, deserialization, and the callback's own execution time.
  - `processingDuration()` — the callback's own wall-clock execution time only.
  - For a batch handler, one `SseEventProcessed` fires per item in the delivered batch
    — each with its own accurate `totalLatency()`, but all sharing the same
    `processingDuration()`/`succeeded()`, since the callback ran once for the whole batch.

---

## 10. `pipelineStats()` — `SsePipelineSnapshot` / `SsePipelineStats`

`SseListener#pipelineStats()` returns a point-in-time occupancy snapshot of every
dispatch pipeline — one entry per named `onEvent` handler, plus one for the catch-all
unhandled-event pipeline. Policy-agnostic — useful under `BLOCK`, `DROP`, or
`DISCONNECT` alike. Intended for polling on a caller-chosen cadence (e.g. to feed a
"pipeline X has been over 80% full for 30s" alert); for real-time, per-event telemetry
instead of a snapshot, see [§9](#9-observability--sselistenerobserver) above. Throws
`IllegalStateException` if called before `connectAsync()`.

```java
public record SsePipelineSnapshot(Map<String, SsePipelineStats> handlers, SsePipelineStats unhandled)

public record SsePipelineStats(int capacity, int concurrency, int inFlight)
```

- **`handlers`** — keyed by the same event type string passed to `onEvent(String, ...)`.
- **`unhandled`** — always present, even when no `onUnhandledEvent` handler was
  explicitly registered (a no-op fallback pipeline is always built). A dedicated field
  rather than a sentinel map entry — there is no `String` event-type key of its own.
- **`capacity`** — the total capacity across every concurrent worker arm
  (`handler.capacity() × handler.concurrency()`), not the per-arm `capacity()` value
  configured on `SseHandler`/`SseBatchHandler`.
- **`concurrency`** — included so a caller can see how `capacity` was derived.
- **`inFlight`** — items currently queued or being processed anywhere in the pipeline,
  summed across every worker arm. Can never exceed `capacity`.

```java
SsePipelineSnapshot snapshot = listener.pipelineStats();
SsePipelineStats fileReadyStats = snapshot.handlers().get("file-ready");

if (fileReadyStats.inFlight() >= fileReadyStats.capacity()) {
    log.warn("file-ready pipeline is at capacity");
}
```

---

## 11. Reconnection and `Last-Event-ID` replay

```
Connection drops (clean EOF, an I/O failure, or a policy-driven DISCONNECT)
    ↓
Not a clean close/deliberate DISCONNECT? → log at Error, invoke onError
    ↓
Setback (failure or DISCONNECT)? → invoke onReconnect with the computed delay
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
  inside a registered [`observer`](#9-observability--sselistenerobserver), and call
  `SseListener.close()` once a condition is met. Omitting an `observer` for a connection
  prone to permanent failure results in a silent, indefinitely reconnecting connection.
- Because every reconnect is an ordinary request through the standard `client` request
  path, a `SecurityProvider` with a dynamic token supplier (e.g. OAuth2
  client-credentials) is re-consulted on every attempt — token refresh on reconnect
  requires no special handling.
- A clean end-of-stream (the server finished writing normally) reconnects **silently** —
  no `Error` log, no `onError`/`onReconnect` call. Only an actual `IOException`/
  unexpected failure, or an unrecoverable HTTP status (404, 401/403, unresolvable
  host), or a policy-driven `DISCONNECT`, triggers `onReconnect`; only the former
  triggers logging and `onError` as well.
- `reconnectDelay(RetryDelay strategy)` reuses `client`'s existing `RetryDelay`
  abstraction — `RetryDelay.fixed(Duration)`, `.linear(Duration)`,
  `.exponential(Duration)`, or a custom lambda. A server-supplied `retry` field takes
  precedence over this strategy for the very next reconnect attempt only.

---

## 12. Executor, virtual threads, and shutdown

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

## 13. Complete example

```java
AtomicReference<SseListener> listenerRef = new AtomicReference<>();

SseListener listener = SseListener.builder().client(client)
        .path("/notifications/stream")
        .parameter("clientId", myClientId)
        .lastEventId(lastKnownEventId)               // resume after a process restart
        .executor(Executors.newVirtualThreadPerTaskExecutor())
        .onBufferFull(BufferFullPolicy.DISCONNECT)
        .reconnectDelay(RetryDelay.exponential(Duration.ofSeconds(1), Duration.ofSeconds(60)))
        .closeTimeout(Duration.ofSeconds(10))
        .onEvent("file-ready", SseHandler.of(FileReadyPayload.class, message -> processFile(message.body()))
                .capacity(4096)
                .concurrency(8))
        .onEvent("price-update", SseBatchHandler.of(PriceUpdate.class, updates -> processBatch(updates))
                .batchSize(50)
                .batchTimeout(Duration.ofMillis(100)))
        .onUnhandledEvent(message -> log.warn("Unknown event type: {}", message.event()))
        .observer(new SseListenerObserver() {
            @Override
            public void onError(SseErrorEvent error) {
                metrics.increment("sse.errors");

                // listener isn't assigned yet at builder-construction time — read it
                // back through a reference set immediately after build() completes below.
                if (isUnrecoverable(error.cause())) {
                    SseListener current = listenerRef.get();

                    if (null != current) {
                        current.close();
                    }
                }
            }

            @Override
            public void onDropped(SseMessage<String> event) {
                metrics.increment("sse.dropped");
            }

            @Override
            public void onReconnect(SseReconnectEvent event) {
                metrics.increment("sse.reconnect." + event.cause());
            }

            @Override
            public void onEventProcessed(SseEventProcessed event) {
                metrics.recordLatency("sse.event.latency", event.totalLatency());
            }
        })
        .build();

listenerRef.set(listener);
listener.connectAsync();

// ... application shutdown ...
listener.close();
```

---

## 14. Server-side SSE — `server-sse`

`server-sse` is the server-side companion to the client APIs above. It keeps the public
surface wire-focused:

- `SseEvent` / `SseEventBuilder` model outbound `text/event-stream` fields.
- `SseEmitter` / `SseEmitterBuilder` wrap Jersey's `SseEventSink` and `Sse`.
- `SseEvents` is a convenience helper for serializer-backed typed payloads.

### Dependency

```xml
<dependency>
    <groupId>software.frisby.web</groupId>
    <artifactId>server-sse</artifactId>
</dependency>
```

### Outbound event model

Build wire-ready events with `SseEvent.builder()`:

```java
SseEvent event = SseEvent.builder()
        .id("42")
        .event("price-update")
        .data("{\"symbol\":\"ACME\",\"price\":101.25}")
        .retry(Duration.ofSeconds(2))
        .build();
```

Field behavior:

- `data` is required.
- `id`, `event`, and `retry` are optional.
- `retry` accepts non-negative durations (`Duration.ZERO` is valid).

### `SseEmitter`

`SseEmitter` is intentionally wire-level only: `send(SseEvent)`.

- `send(...)` returns `CompletableFuture<Void>` and completes exceptionally on send
  failure.
- `isOpen()` reflects `SseEventSink` state.
- `close()` is idempotent and also stops the optional heartbeat scheduler.
- `isOpen()` is a point-in-time signal only; the connection may still close immediately
  after it returns `true`, so callers should still handle exceptional completion from
  `send(...).join()`.

### Typed data convenience with `SseEvents`

For typed payloads, serialize at event construction time:

```java
SseEvent event = SseEvents.of(serializer)
        .id("42")
        .event("price-update")
        .data(new PriceUpdate("ACME", 101.25))
        .retry(Duration.ofSeconds(2))
        .toEvent();
```

### Heartbeat behavior

`heartbeat(Duration)` emits SSE comment frames (for example `: keep-alive`).

- Heartbeats are transport keep-alive events, not application events.
- They do not include `id`, `event`, `data`, or `retry` fields.
- As documented in the client parser behavior, comment frames are ignored.
- Heartbeat send is best-effort: if the sink is already closed, the heartbeat is skipped;
  if a heartbeat send races with close and fails, the failure is logged internally and
  is not propagated to resource code.

### Handling disconnect races in resource methods

- For long-lived streams, loop while `emitter.isOpen()` is `true`.
- Wrap each `emitter.send(...).join()` in try/catch (`CompletionException`), log as
  appropriate, and exit the resource method cleanly on failure.
- Treat a failed send as terminal for that stream; do not rely on heartbeat failures for
  control flow.

### Worked resource-method example

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

### Server/client interaction notes

- The server can send `retry` hints per event; the client uses that as documented in
  [Section 11](#11-reconnection-and-last-event-id-replay).
- If a client reconnects with `Last-Event-ID`, expose it via `@HeaderParam` and replay
  only newer events.
- Heartbeat comment lines help keep idle connections alive and are ignored by client
  dispatch, as documented in [Section 8](#8-backpressure--bufferfullpolicy) and
  [Section 11](#11-reconnection-and-last-event-id-replay).


