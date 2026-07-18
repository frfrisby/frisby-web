# Architecture and Design Decisions

This document captures the key design decisions behind `frisby-web` — why the library is
structured the way it is, what tradeoffs were made, and where the extension points are.

---

## Module Structure

| Module                   | Depends on          | Purpose                                        |
|--------------------------|---------------------|------------------------------------------------|
| `serial`                 | *(validation only)* | `JsonSerializer` and `GenericType` interfaces  |
| `client`                 | `serial`            | HTTP client built on JDK `HttpClient`          |
| `basic-security`         | `client`            | Client-side HTTP Basic Auth and Bearer Token   |
| `oauth2-security`        | `client`            | Client-side OAuth 2.0 client-credentials       |
| `server`                 | `serial`            | Embedded HTTP server (Jersey 3.x + Jetty 12)   |
| `server-basic-security`  | `server`            | Server-side Basic Auth                         |
| `server-oauth2-security` | `server`            | Server-side Bearer Token                       |
| `jackson-serializer`     | `serial`            | Jackson-backed `JsonSerializer` implementation |

**Why is `serial` a separate module?**  
Both `client` and `server` need `JsonSerializer` and `GenericType`. Extracting them into a
minimal `serial` module avoids a circular dependency (`server` → `client` → `serial`) and
lets either side be used independently. The `serial` module has no runtime dependencies
beyond the `frisby-core` validation library.

**Why are the security modules separate?**  
Consumers should only pull in what they use. An application using only OAuth2 should not
have Basic Auth code on its classpath, and vice versa. The same principle applies to the
server-side security modules.

**Why is `jackson-serializer` separate?**  
The core `client` and `server` modules have zero hard serialization dependency — callers
bring their own `JsonSerializer` implementation. Separating `jackson-serializer` means
consumers who use Gson, JSON-B, or a custom serializer do not pay for Jackson.

---

## HTTP Client Design

### Fluent per-verb specs instead of an overload table

A single client class with overloads for every verb × response type × body type
combination grows quickly and becomes difficult to read and impossible to discover.

Instead: **fluent per-verb spec types** (`GetSpec`, `PostSpec`, `PutSpec`,
`PatchSpec`, `DeleteSpec`, `HeadSpec`). Each spec exposes only the options relevant to
that verb. `send()` and `sendAsync()` live directly on each spec. There is no separate
`Target` or `BodySpec` intermediary — every method returns the same spec type, enabling
fluent chains without CRTP visible to callers.

### JDK `HttpClient`

`java.net.http.HttpClient` (Java 11+) is used exclusively for transport. This keeps the
mandatory dependency footprint at zero — no OkHttp, no Apache HttpClient, no Netty.
The executor and HTTP version are configurable to preserve the same extension points that
OkHttp or Apache would offer.

### Pluggable serialization

`JsonSerializer` is a three-method interface in the `serial` module. The client's `build()`
throws immediately if no serializer is provided — there is no silent default. This forces
an explicit choice and makes the serialization dependency transparent in the application's
POM rather than hidden as a transitive pull.

### Extensible compression

Compression is modeled as `ContentCompressor` and `ContentDecompressor` — combined
interfaces that bind an encoding name (`"gzip"`, `"br"`, `"zstd"`) to a
compress/decompress function. The built-in gzip support is just the first registered
compressor; it is not special-cased in the engine.

The decision against shipping brotli or zstd built-in: both require JNI native libraries
with platform-specific classifiers. Adding them as transitive dependencies would break
GraalVM native-image, Alpine Linux (musl libc), and Docker multi-arch builds for all
consumers. The interface design lets callers plug their own library in without any changes
to this one.

### `ClientEventListener` for observability

There is no StatsD, OpenTelemetry, or Micrometer in the core. `ClientEventListener`
fires `onRequestCompleted` with method, URI, status, and latency after every request.
Callers forward these events to whatever metrics backend they use, or ignore them. A
`NoOpClientEventListener` is the default.

---

## HTTP Server Design

### Jersey 3.x + Jetty 12

Jersey 3.x is the Jakarta EE 10 release line and the current production-stable choice
for Java 17+ deployments. Jersey 4.x targets Jakarta EE 11 and requires Java 21 as a
minimum — adopting it would raise this library's minimum Java version requirement from
17 to 21, which is an unnecessary constraint for consumers still on Java 17 or 18.

Jersey is the JAX-RS 3.1 reference implementation — mature, actively maintained, and
compatible with standard `@Path`-annotated resource classes that work with any JAX-RS
implementation. Jetty 12 is the preferred embedded container for Jersey 3.x; it is more
actively maintained than Grizzly and has first-class ALPN support for HTTP/2.

Spring Boot, Quarkus, and Micronaut all bundle frameworks (DI containers, auto-config,
annotation processors, build-time augmentation) that are orthogonal to the goal of
providing an embedded HTTP server. This library provides the server and nothing else.

### `JsonMessageBodyProvider` — no hard Jackson dependency in `server`

A package-private `@Provider` that implements `MessageBodyReader` and `MessageBodyWriter`
bridges JAX-RS entity serialization to the caller-supplied `JsonSerializer`. The server
module has no compile-time dependency on any serialization library.

### Always-on concurrency gate

A `Semaphore`-based `ConcurrencyLimitHandler` is always registered — no opt-in required.
The default limit is `availableProcessors × 20`. When the limit is exhausted, the server
responds with `503 Service Unavailable` immediately at the Jetty handler layer — before
Jersey is involved — so rejected requests consume no heap for body parsing or response
serialization.

The alternative (an unbounded queue) lets the JVM OOM under sustained overload with no
clear rejection signal to upstream callers. Immediate 503 with `Retry-After: 1` is better
operational behavior.

### `GzipHandler` is intentionally not registered

Jetty 12.0.x has a memory leak in `GzipHandler` (CVE-2026-1605, patched in 12.0.32).
Even on patched versions, the library does not register `GzipHandler` — response
compression is available via the opt-in `GZipResponseFilter` (a JAX-RS
`ContainerResponseFilter`), which compresses only `application/json` responses when the
client advertises `Accept-Encoding: gzip`. This avoids the handler-layer complexity and
keeps binary/streaming responses uncompressed by default.

The `gzip()` configuration knob registers both the `GZipEncoder` (for request
decompression) and `GZipResponseFilter` (for response compression) together. Registering
one without the other is a broken state, so the single knob enforces the correct pairing.

### Graceful shutdown with application-layer drain

`stop()` sets a `shuttingDown` flag first (inside a lock), then waits for the semaphore
to drain (outside the lock, so `isRunning()` returns `true` during the drain window),
then calls `jettyServer.stop()`. During the drain window, the `ConcurrencyLimitHandler`
rejects all new requests with 503 — including health check requests — which signals load
balancers to stop routing traffic before connections are forcibly closed.

This is intentional and distinct from the at-capacity behavior: when the server is
running normally but at its concurrency limit, health check requests *bypass* the
semaphore and return `200` — the server is alive and healthy under load, and rejecting
health checks there would cause the load balancer to incorrectly recycle a healthy
instance. During shutdown, the health check correctly returns `503` because the instance
*should* be removed from rotation.

### Built-in failure-detail logging

4xx and 5xx responses from the JAX-RS layer are logged at `WARNING` and `ERROR`
respectively with full context: request headers (masked), buffered request body
(field-redacted), and response headers. When an exception was thrown and captured,
it is attached to the `ERROR` log record so the full stack trace is available without
any additional configuration. This is always on when the `RequestLogger` is at `WARNING`
or below — no filter needs to be registered to get meaningful failure diagnostics in
production.

Concurrency-gate 503 rejections are produced at the Jetty handler layer (before Jersey
is involved) and are logged separately at `WARNING` with a `[capacity limit]` tag — they
are not errors, they are correct load-shedding behavior.

2xx responses are logged at `INFO` (one-liner) or `TRACE` (full context). Health check
requests are logged at `TRACE` to avoid drowning out meaningful logs from load-balancer
polling.

---

## Security Provider Design

Both client-side and server-side security follow the same pattern: a combined
interface that pairs an `accepts()` predicate with an `authenticate()` action.

**Client side** (`SecurityProvider`): applies credentials to an outgoing request.
Registered on `ClientBuilder` for every request, or overridden per-request via
`.security()` on the verb spec.

**Server side** (`AuthenticationProvider`): evaluates an incoming request.  
- `accepts(ContainerRequestContext)` — cheap opt-out before any credential work.
  Enables clean multi-scheme chaining: Basic Auth checks for `Authorization: Basic`,
  Bearer Token checks for `Authorization: Bearer`.
- `authenticate(ContainerRequestContext)` — validates credentials and returns a
  `SecurityContext`.

If no provider accepts a request, the `SecurityRequestFilter` throws `NotAuthorizedException`
immediately — a silent pass-through would be worse than an explicit 401.

`CredentialsValidator` and `BearerTokenValidator` are `@FunctionalInterface` types that
receive only the parsed credentials or token string. All HTTP header parsing is handled
by the library; callers supply only domain validation logic.

Health check requests bypass authentication unconditionally. Liveness probes must not
require credentials — a probe failure during a credential rotation would incorrectly
remove a healthy instance from the load-balancer pool.

---

## Logging Strategy

`System.Logger` is used throughout all production code. It routes through the standard
JUL infrastructure at runtime, but is compatible with SLF4J (via the JUL-to-SLF4J
bridge), Log4j 2 (via `log4j-jul`), or any custom `System.LoggerFinder` — with no
compile-time dependency on any of them.

Logger names match class names (e.g. `software.frisby.web.client.RequestLogger`,
`software.frisby.web.server.RequestLogger`). This lets operators control verbosity at
the class level using standard logging configuration.

**Sensitive data masking** is always on, regardless of caller configuration:
- `Authorization` header — suppressed entirely in all log output.
- `Cookie` request header — cookie names preserved, values replaced with `[redacted]`.
- `Set-Cookie` response header — cookie name and all attributes preserved, value replaced
  with `[redacted]`.

Additional headers and JSON body fields can be masked via `ClientLoggingConfiguration`
(client) and `LoggingConfiguration` (server).

**Body size limit** — logged bodies are capped at 8 KB by default. Bodies exceeding this
are truncated with an inline note. Pass `maxBodySize(0)` to disable body logging entirely.
Binary and multipart bodies are never buffered — a `[type/subtype — body not logged]`
placeholder appears instead. The limit is configurable on both client and server.

---

## Extension Points

| Concern                               | Extension point                                              |
|---------------------------------------|--------------------------------------------------------------|
| JSON serialization                    | `JsonSerializer` (3-method interface in `serial`)            |
| Request authentication (client)       | `SecurityProvider` in `client.security`                      |
| Request authentication (server)       | `AuthenticationProvider` in `server`                         |
| Metrics / observability (client)      | `ClientEventListener` in `client.event`                      |
| Metrics / observability (server)      | `ServerEventListener` in `server.event`                      |
| Request/response compression (client) | `ContentCompressor` / `ContentDecompressor`                  |
| Custom JAX-RS components (server)     | `ServerBuilder.components(Object...)`                        |
| Custom serializer module              | Implement `JsonSerializer`; declare `serial` as a dependency |
| OAuth2 token fetch metrics            | `TokenEventListener` in `oauth2-security`                    |









