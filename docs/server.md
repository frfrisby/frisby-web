# frisby-web — Embedded HTTP Server

`software.frisby.web:server` provides a lightweight, production-ready embedded HTTP server
built on **Jersey 3.x** (JAX-RS 3.1) and **Jetty 12**.  You write standard JAX-RS resource
classes; the library handles everything else — binding, serialization, TLS, HTTP/2, CORS,
compression, structured failure logging, health checks, graceful shutdown, and virtual-thread
support — with zero framework magic and a minimal, explicit API.

---

## Contents

1. [Why this library?](#1-why-this-library)
2. [Maven dependency](#2-maven-dependency)
3. [Quick start](#3-quick-start)
4. [ServerBuilder reference](#4-serverbuilder-reference)
5. [ServerConfiguration reference](#5-serverconfiguration-reference)
6. [Concurrency configuration](#6-concurrency-configuration)
7. [SSL / TLS](#7-ssl--tls)
8. [HTTP/2 over TLS](#8-http2-over-tls)
9. [GZIP compression](#9-gzip-compression)
10. [CORS](#10-cors)
11. [Authentication](#11-authentication)
12. [Health check endpoint](#12-health-check-endpoint)
13. [Graceful shutdown](#13-graceful-shutdown)
14. [Logging](#14-logging)
15. [Failure-detail logging and redaction](#15-failure-detail-logging-and-redaction)
16. [Observability — ServerEventListener](#16-observability--servereventlistener)
17. [Advanced: custom JAX-RS components](#17-advanced-custom-jax-rs-components)
18. [Comparison with alternatives](#18-comparison-with-alternatives)
19. [Complete examples](#19-complete-examples)

---

## 1. Why this library?

Most Java HTTP server options come bundled with an opinionated runtime: a DI framework,
auto-configuration, a metrics stack, a configuration file format, or GraalVM build-time
processing.  If you want an embedded server and nothing else, the overhead is significant.

`frisby-web:server` is different:

| Property | Detail |
|---|---|
| **Zero mandatory external dependencies** beyond Jersey and Jetty | No Spring, no Guice, no Micronaut, no CDI |
| **Fully explicit** | No classpath scanning, no annotations driven wiring, no magic defaults you have to override |
| **Pluggable serialization** | Bring your own `JsonSerializer` (Jackson, Gson, etc.) — the server has no hard serialization dependency |
| **Standard JAX-RS** | Resource classes are plain `@Path`-annotated POJOs; they work with any JAX-RS implementation |
| **First-class observability** | Structured per-request logging and a `ServerEventListener` callback interface — wire to any metrics backend |
| **Virtual-thread ready** | Pass a `newVirtualThreadPerTaskExecutor()` to handle thousands of concurrent connections without tuning thread pool sizes |

---

## 2. Maven dependency

> **Latest version:** check [Maven Central](https://central.sonatype.com/search?q=software.frisby.web)
> for the current release and replace `LATEST_VERSION` below.

Import the BOM in your project, then declare the dependency without a version:

```xml
<!-- In dependencyManagement -->
<dependency>
    <groupId>software.frisby.web</groupId>
    <artifactId>bom</artifactId>
    <version>LATEST_VERSION</version>
    <type>pom</type>
    <scope>import</scope>
</dependency>

<!-- In dependencies -->
<dependency>
    <groupId>software.frisby.web</groupId>
    <artifactId>server</artifactId>
</dependency>
```

To accept `multipart/form-data` request bodies in your resource methods, also add:

```xml
<dependency>
    <groupId>org.glassfish.jersey.media</groupId>
    <artifactId>jersey-media-multipart</artifactId>
</dependency>
```

For server-side HTTP Basic Auth / Bearer Token authentication, also add:

```xml
<dependency>
    <groupId>software.frisby.web</groupId>
    <artifactId>server-basic-security</artifactId>
</dependency>
```

For server-side OAuth2 Bearer Token authentication, also add:

```xml
<dependency>
    <groupId>software.frisby.web</groupId>
    <artifactId>server-oauth2-security</artifactId>
</dependency>
```

For a ready-made Jackson-backed `JsonSerializer`, also add:

```xml
<dependency>
    <groupId>software.frisby.web</groupId>
    <artifactId>jackson-serializer</artifactId>
</dependency>
```

`JacksonSerializer` (package `software.frisby.web.serial.jackson`) provides an opinionated
out-of-the-box serializer.  Use `JacksonSerializer.builder().build()` for the defaults, or
`JacksonSerializer.builder().mapper(customMapper).build()` to supply your own `ObjectMapper`.

---

## 3. Quick start

```java
// 1. Write a JAX-RS resource
@Path("/orders")
public class OrderResource {
    @GET
    @Produces("application/json")
    public List<Order> list() {
        return orderService.findAll();
    }

    @POST
    @Consumes("application/json")
    @Produces("application/json")
    public Response create(CreateOrderRequest request) {
        Order order = orderService.create(request);
        return Response.status(201).entity(order).build();
    }
}

// 2. Build and start the server
Server server = Server.builder()
        .configuration(c -> c
                .port(8080)
                .serializer(JacksonSerializer.builder().build()))
        .resources(new OrderResource(orderService))
        .healthCheck()
        .build();

server.start();
// server is now accepting connections on port 8080

// 3. Stop cleanly on shutdown
Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
```

The `configuration(UnaryOperator<ServerConfigurationBuilder>)` overload accepts a lambda
for inline configuration.  For shared or reusable configuration, use the object overload:

```java
ServerConfiguration config = ServerConfiguration.builder()
        .port(8080)
        .serializer(JacksonSerializer.builder().build())
        .build();

Server server = Server.builder()
        .configuration(config)
        .resources(new OrderResource(orderService))
        .build();
```

---

## 4. ServerBuilder reference

Obtain a builder via `Server.builder()`.

| Method | Required | Description |
|---|---|---|
| `configuration(ServerConfiguration)` | ✓ | Sets the server runtime configuration. |
| `configuration(UnaryOperator<ServerConfigurationBuilder>)` | ✓ | Inline lambda convenience overload. |
| `resources(Object...)` | ✓ | Registers one or more JAX-RS `@Path` resource instances. Cumulative. |
| `resources(List<Object>)` | ✓ | List overload. Cumulative. |
| `components(Object...)` | | Registers JAX-RS `@Provider` components (filters, exception mappers, etc.). Cumulative. |
| `components(List<Object>)` | | List overload. Cumulative. |
| `healthCheck()` | | Mounts a built-in `GET /health` → `{"status":"UP"}` endpoint. |
| `healthCheck(String path)` | | Same, but at a custom path (e.g. `/readyz`). |
| `eventListener(ServerEventListener)` | | Receives an `onRequestCompleted` callback after every request. |
| `build()` | | Returns a configured, not-yet-started `Server`. |

`build()` throws `IllegalStateException` if no configuration or no resources are provided.

---

## 5. ServerConfiguration reference

Obtain a builder via `ServerConfiguration.builder()`.

| Method | Default | Description |
|---|---|---|
| `port(int)` | — *required* | Port to bind to. `0` = OS-assigned ephemeral port (useful in tests). Retrieve the actual port via `Server.port()` after `start()`. |
| `host(String)` | `"0.0.0.0"` | IP address or hostname to bind to.  `"localhost"` restricts to loopback only. |
| `maxRequestSize(long)` | `4194304` (4 MB) | Maximum incoming request body in bytes. Requests exceeding this limit are rejected with HTTP 413. |
| `serializer(JsonSerializer)` | — *required* | JSON serializer used to (de)serialize entity bodies. |
| `ssl(SSLContext)` | none | Enables HTTPS with the provided context. |
| `ssl()` | none | Enables HTTPS using the JVM default `SSLContext` (driven by `javax.net.ssl.*` system properties). |
| `http2()` | off | Enables HTTP/2 over TLS (h2) via ALPN. Requires `ssl()` to also be set. |
| `gzip()` | off | Enables bidirectional gzip: decompresses `Content-Encoding: gzip` requests and compresses `application/json` responses when the client sends `Accept-Encoding: gzip`. |
| `cors(CorsConfiguration)` | none | Configures CORS headers and preflight handling. |
| `logging(LoggingConfiguration)` | 8 KB body, no redaction | Controls failure-log body size, header masking, and field redaction. |
| `logging(UnaryOperator<LoggingConfigurationBuilder>)` | | Inline lambda convenience overload. |
| `maxConcurrentRequests(int)` | `availableProcessors * 20` | Maximum number of requests processed concurrently. Excess requests receive `503` immediately. See [Concurrency configuration](#6-concurrency-configuration). |
| `executor(Executor)` | none | Custom executor for request threads. Pass `Executors.newVirtualThreadPerTaskExecutor()` for virtual threads (Java 21+). |
| `stopTimeout(Duration)` | none | Enables graceful shutdown — waits up to the given duration for in-flight requests before forcibly closing connections. |

---

## 6. Concurrency configuration

The server maintains a semaphore-based concurrency gate that is **always active**.  When
all permits are exhausted, new requests receive an immediate `503 Service Unavailable`
response — the request body is never read, so no memory is consumed for rejected requests.

The default limit is `Runtime.getRuntime().availableProcessors() * 20` (e.g. 80 on a
4-core machine, 320 on a 16-core machine).  Override it explicitly when you have measured
the right value for your workload:

```java
.configuration(c -> c
        .port(8080)
        .serializer(serializer)
        .maxConcurrentRequests(200))
```

### Virtual threads (Java 21+)

Pass any `Executor` via `executor(Executor)`. The primary use case is
`Executors.newVirtualThreadPerTaskExecutor()`, which routes each request onto its own
virtual thread.  Blocking I/O (database calls, downstream HTTP calls) suspends the carrier
thread while waiting, enabling high concurrency without a large platform-thread pool:

```java
.configuration(c -> c
        .port(8080)
        .serializer(serializer)
        .maxConcurrentRequests(200)
        .executor(Executors.newVirtualThreadPerTaskExecutor()))
```

`maxConcurrentRequests` acts as a back-pressure knob even with virtual threads — it limits
how many requests are actively processed simultaneously, guarding heap from memory pressure
when many large payloads arrive at once.

### 503 rejection behaviour

When the concurrency limit is reached, the server responds immediately with:

```
HTTP/1.1 503 Service Unavailable
Retry-After: 1
Content-Type: application/json

{"status":503,"message":"Service temporarily unavailable — concurrency limit reached."}
```

Capacity rejections are logged at `WARNING` with a `[capacity limit]` tag by
`software.frisby.web.server.RequestLogger`.  They are intentionally `WARNING`, not
`ERROR` — load shedding is correct operational behaviour, not a malfunction.

### Thread-pool sizing

The underlying `QueuedThreadPool` is sized at `maxConcurrentRequests + 4` to ensure
Jetty's acceptor and selector infrastructure threads are always schedulable.  The
semaphore is the real concurrency cap; the extra headroom is invisible to application code.

---

## 7. SSL / TLS

### Programmatic context (preferred)

```java
SSLContext sslContext = loadSslContext(); // your keystore loading logic

.configuration(c -> c
        .port(8443)
        .serializer(serializer)
        .ssl(sslContext))
```

### JVM default context (system properties)

```java
.configuration(c -> c
        .port(8443)
        .serializer(serializer)
        .ssl())   // delegates to SSLContext.getDefault()
```

The default context is driven by the standard JVM properties:

| Property | Purpose |
|---|---|
| `javax.net.ssl.keyStore` | Path to the keystore file |
| `javax.net.ssl.keyStorePassword` | Keystore password |
| `javax.net.ssl.trustStore` | Path to the truststore file |
| `javax.net.ssl.trustStorePassword` | Truststore password |

---

## 8. HTTP/2 over TLS

HTTP/2 (h2) is negotiated via ALPN during the TLS handshake.  Clients that advertise `h2`
receive an HTTP/2 connection; HTTP/1.1 clients continue to work unchanged.  h2c (cleartext
HTTP/2) is not supported.

```java
.configuration(c -> c
        .port(8443)
        .serializer(serializer)
        .ssl(sslContext)
        .http2())
```

`build()` throws `IllegalStateException` if `http2()` is called without a TLS context.

The ALPN protocol list presented to clients is `["h2", "http/1.1"]`, with `http/1.1` as the
fallback default.

---

## 9. GZIP compression

The `gzip()` single knob enables full bidirectional gzip support:

```java
.configuration(c -> c
        .port(8080)
        .serializer(serializer)
        .gzip())
```

When set:
- **Incoming requests** with `Content-Encoding: gzip` are transparently decompressed before
  the body reaches your resource method.
- **`application/json` responses** are compressed when the client sends `Accept-Encoding: gzip`.

Streaming responses (e.g. `InputStream` entities, file downloads) are never compressed —
their content type is not in the safe-to-compress set.

### Advanced compression

For custom compression behaviour — compressing additional media types, applying size
thresholds, or compressing non-JSON responses — omit `gzip()` and register a custom
`ContainerResponseFilter` (and Jersey's `GZipEncoder` if needed) via
`ServerBuilder.components(...)`.

---

## 10. CORS

```java
CorsConfiguration cors = CorsConfiguration.builder()
        .allowedOrigins("https://app.example.com")
        .allowedMethods("GET", "POST", "PUT", "DELETE")
        .allowedHeaders("Authorization", "Content-Type")
        .allowCredentials()
        .build();

.configuration(c -> c
        .port(8080)
        .serializer(serializer)
        .cors(cors))
```

### CorsConfigurationBuilder reference

| Method | Required | Description |
|---|---|---|
| `allowedOrigins(String...)` | ✓ | One or more allowed origins, or `"*"` for wildcard. |
| `allowedMethods(String...)` | ✓ | HTTP methods permitted in cross-origin requests. |
| `allowedHeaders(String...)` | | Allowed request headers.  When omitted, the filter echoes the value of the `Access-Control-Request-Headers` preflight header. |
| `allowCredentials()` | | Adds `Access-Control-Allow-Credentials: true`.  Incompatible with wildcard origin — `build()` throws if both are set. |

### How it works

The CORS filter runs at `@PreMatching` priority, before URI matching and authentication
filters.  For preflight `OPTIONS` requests, it responds immediately with the appropriate
`Access-Control-*` headers and short-circuits the pipeline.  For actual requests, it appends
`Access-Control-Allow-Origin` (and `Vary: Origin` when using specific origins) to the response.

---

## 11. Authentication

Register one or more `AuthenticationProvider` implementations via `ServerBuilder.authentication()`.
The server runs them as a first-accepts-wins chain inside a `@Priority(AUTHENTICATION)` filter —
the first provider whose `accepts()` method returns `true` handles the request.  If no provider
accepts, the request is rejected with `401 Unauthorized`.

The health check endpoint (if configured) always bypasses authentication — load balancer
liveness probes must not require credentials.

### HTTP Basic Auth (`server-basic-security`)

```java
import software.frisby.web.server.security.basic.BasicAuthAuthenticationProvider;

Server.builder()
        .configuration(c -> c.port(8080).serializer(serializer))
        .resources(new OrderResource(orderService))
        .authentication(
                BasicAuthAuthenticationProvider.of((username, password) ->
                        userService.authenticate(username, password)  // returns Principal or throws 401/403
                )
        )
        .build();
```

`CredentialsValidator` is a `@FunctionalInterface` — the lambda receives the decoded
`username` (`String`) and `password` (`char[]`).  Return a `Principal` for success;
throw `NotAuthorizedException` (401) or `ForbiddenException` (403) to reject.
The library handles all `Authorization: Basic` header parsing and Base64 decoding.

### Bearer Token (`server-oauth2-security`)

```java
import software.frisby.web.server.security.oauth2.BearerTokenAuthenticationProvider;

.authentication(
        BearerTokenAuthenticationProvider.of(token ->
                jwtService.validate(token)  // returns Principal or throws 401/403
        )
)
```

`BearerTokenValidator` is a `@FunctionalInterface` — the lambda receives the raw token
string extracted from `Authorization: Bearer <token>`.

### Multiple providers (first-accepts-wins chain)

```java
.authentication(
        BasicAuthAuthenticationProvider.of(credentialsValidator),
        BearerTokenAuthenticationProvider.of(tokenValidator)
)
```

### Role-based access control (`@RolesAllowed`)

Return a `ServerSecurityContext` carrying a role set from your validator, then register
Jersey's `RolesAllowedDynamicFeature` to enable the `@RolesAllowed` annotation on resource
methods:

```java
import software.frisby.web.server.ServerSecurityContext;
import org.glassfish.jersey.server.filter.RolesAllowedDynamicFeature;

Server.builder()
        .authentication(
                BasicAuthAuthenticationProvider.of((username, password) -> {
                    User user = userService.authenticate(username, password);
                    return ServerSecurityContext.of(user.principal(), Set.of(user.role().name()));
                })
        )
        .components(RolesAllowedDynamicFeature.class)
        .resources(new AdminResource())
        .build();

// In your resource class:
@GET
@Path("/report")
@RolesAllowed("ADMIN")
public Report adminReport() { ... }
```

`ServerSecurityContext` factory methods:

| Overload | Description |
|---|---|
| `of(Principal)` | No roles; `isUserInRole()` always returns `false`. |
| `of(Principal, Set<String> roles)` | Role set; `isUserInRole()` checks `roles.contains(role)`. |
| `of(Principal, Set<String> roles, boolean secure, String scheme)` | Full control — use for custom auth schemes. |

### Custom authentication scheme

For schemes not covered by the built-in providers (e.g. AWS ALB OIDC headers), implement
`AuthenticationProvider` directly:

```java
import software.frisby.web.server.AuthenticationProvider;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.SecurityContext;

public final class AlbOidcAuthenticationProvider implements AuthenticationProvider {

    @Override
    public boolean accepts(ContainerRequestContext ctx) {
        return null != ctx.getHeaderString("x-amzn-oidc-data");
    }

    @Override
    public SecurityContext authenticate(ContainerRequestContext ctx) {
        // validate JWT, extract principal and roles
        boolean isSecure = "https".equalsIgnoreCase(
                ctx.getUriInfo().getRequestUri().getScheme());
        return ServerSecurityContext.of(principal, Set.of(user.role().name()), isSecure, "BEARER");
    }
}
```

---

## 12. Health check endpoint

```java
Server.builder()
        ...
        .healthCheck()          // mounts at /health
        // or:
        .healthCheck("/readyz") // custom path — Kubernetes liveness probe style
```

The endpoint responds to `GET` with:

```
HTTP/1.1 200 OK
Content-Type: application/json

{"status":"UP"}
```

**Logging suppression** — health check requests are logged at `TRACE` (not `INFO`) by the
`software.frisby.web.server.RequestLogger` logger.  Load balancers polling every few seconds
would otherwise drown out meaningful application request logs at `INFO`.

**Event suppression** — `ServerEventListener` callbacks are not fired for health check
requests.  High-frequency polling must not inflate application metrics.

---

## 13. Graceful shutdown

Without `stopTimeout`, calling `server.stop()` terminates connections immediately.

```java
// Wait up to 30 seconds for in-flight requests to drain
.configuration(c -> c
        .port(8080)
        .serializer(serializer)
        .stopTimeout(Duration.ofSeconds(30)))
```

With a stop timeout configured:
1. The server stops accepting new connections.
2. It waits up to `timeout` for in-flight requests to produce a response.
3. Any remaining connections are forcibly closed once the timeout elapses.

This prevents requests from being abruptly terminated during rolling deployments.
`timeout` must be positive; `build()` throws `DurationOutsideRangeException` otherwise.

---

## 14. Logging

The server uses `System.Logger` throughout, routing through the standard JUL bridge
(or whichever logging backend is wired to `System.Logger` at runtime — SLF4J, Log4j 2,
etc., via a `System.LoggerFinder`).

### Logger names

| Logger | What it covers |
|---|---|
| `software.frisby.web.server.RequestLogger` | All request lifecycle events: server start/stop, per-request INFO lines, 4xx/5xx failure detail, health check traces, capacity rejection warnings. **This is the primary logger to configure.** |
| `software.frisby.web.server.DefaultServer` | Server wiring and lifecycle internals (connector setup, handler registration, component registration errors). |
| `software.frisby.web.server.JsonErrorHandler` | Pre-Jersey errors (primarily HTTP 413 from the request-size gate). |

### Log levels

| Level | Emitted by `RequestLogger` |
|---|---|
| `TRACE` | Health check requests (one line per poll — suppressed at `INFO` to avoid noise). |
| `INFO` | Server started / stopped.  Every 2xx and 3xx request: `METHOD path → STATUS (Nms)`. |
| `WARNING` | 4xx responses and unhandled failures: one-liner + full request context (headers + buffered body + response headers).  Capacity-limit 503 rejections tagged `[capacity limit]`. |
| `ERROR` | 5xx responses and server startup failures: same full context as WARNING, plus the originating exception attached to the log record for stack-trace visibility. |

### Silencing health check noise

Load balancers typically poll `/health` every 5–10 seconds.  If you want health check
requests completely invisible, set `RequestLogger` to `WARNING` or higher:

```properties
# logging.properties (JUL)
software.frisby.web.server.RequestLogger.level = WARNING
```

### Silencing the INFO request log when using a custom filter

If you register Jersey's `LoggingFeature` (or a custom `ContainerRequestFilter`) for
full-payload tracing, the built-in `INFO` line from `RequestLogger` is redundant.  Silence
it the same way:

```properties
software.frisby.web.server.RequestLogger.level = WARNING
```

---

## 15. Failure-detail logging and redaction

On 4xx and 5xx responses, the server automatically logs full request context — headers,
buffered request body, and response headers — at `WARNING` (4xx) or `ERROR` (5xx).  This
gives you the information needed to diagnose failures without enabling verbose tracing in
production.

### Default behaviour

- **Request body** — buffered up to **8 KB** from text-based entity types.  Binary content
  (`application/octet-stream`, `image/*`, `audio/*`, `video/*`) and multipart bodies are
  never buffered; a `[type/subtype — body not logged]` placeholder appears instead.
- **Hard-masked headers** (always, regardless of configuration):
    - `Authorization` — suppressed entirely.
    - `Cookie` — logged as `Cookie: name=[redacted]` (one line per cookie; names preserved).
    - `Set-Cookie` — logged as `name=[redacted]; Path=...; Secure; HttpOnly` (attributes preserved).
- **No field redaction** — all JSON body fields are logged as-is.

### Customising with LoggingConfiguration

```java
LoggingConfiguration logging = LoggingConfiguration.builder()
        .maxBodySize(4096)                        // buffer up to 4 KB (0 = disable body logging)
        .redactHeaders("X-Amzn-Oidc-Data")        // mask additional headers
        .redactFields("password", "token", "ssn") // redact JSON string fields by name
        .build();

.configuration(c -> c
        .port(8080)
        .serializer(serializer)
        .logging(logging))

// or inline:
.configuration(c -> c
        .port(8080)
        .serializer(serializer)
        .logging(l -> l
                .maxBodySize(4096)
                .redactHeaders("X-Amzn-Oidc-Data")
                .redactFields("password", "token")))
```

| Option | Default | Description |
|---|---|---|
| `maxBodySize(int)` | `8192` (8 KB) | Maximum bytes buffered from the request body and included in failure log entries.  Bodies larger than this are truncated with a note.  Pass `0` to disable body logging entirely. |
| `redactHeaders(String...)` | none | Additional header names whose values are replaced with `***` in failure log entries.  The three hard-coded headers (`Authorization`, `Cookie`, `Set-Cookie`) are always masked regardless of this setting. |
| `redactFields(String...)` | none | JSON body field names whose string values are replaced with `[redacted]`.  Matching is case-sensitive and exact.  Only string-typed values are affected; numbers, booleans, and nested objects are left unchanged. |

### Text-safe body types (buffered for logging)

Bodies are buffered for logging only when the `Content-Type` is one of:
`text/*`, `application/json`, `application/*+json`, `application/xml`, `application/*+xml`,
`application/x-www-form-urlencoded`, `application/graphql`.

All other types produce the `[type/subtype — body not logged]` placeholder.

---

## 16. Observability — ServerEventListener

Register a listener on `ServerBuilder` to receive a structured callback after each request:

```java
Server.builder()
        ...
        .eventListener(new MyMetricsListener())
```

```java
public class MyMetricsListener implements ServerEventListener {

    @Override
    public void onRequestCompleted(RequestCompletedEvent event) {
        metrics.recordRequest(
                event.method(),
                event.path(),
                event.statusCode(),
                event.latency(),
                event.requestBytes(),
                event.responseBytes()
        );
    }
}
```

`onRequestCompleted` is called after every request that produces an HTTP response,
regardless of status code — `4xx` and `5xx` responses are included.  The server guarantees
that every unhandled exception also produces a `500` response, so there is no separate
failure-only callback.

### RequestCompletedEvent fields

| Field | Type | Description |
|---|---|---|
| `method()` | `String` | HTTP method (`"GET"`, `"POST"`, etc.) |
| `path()` | `String` | Decoded request path, without query parameters |
| `statusCode()` | `int` | HTTP response status code |
| `latency()` | `Duration` | Time from request receipt to response written |
| `requestBytes()` | `long` | Request body size in bytes, or `0` if unknown |
| `responseBytes()` | `long` | Response body size in bytes, or `0` if unknown |
| `successful()` | `boolean` | `true` for 2xx status codes |

### Suppressed events

**Health check requests** — `ServerEventListener` callbacks are suppressed for requests
to the health check path.  High-frequency polling must not inflate application metrics.

### Exception safety

Exceptions thrown by the callback are caught, logged at `WARNING` via `DefaultServer`'s
logger, and suppressed — a misbehaving listener implementation can never cause a request
to fail.

---

## 17. Advanced: custom JAX-RS components

Register any JAX-RS provider component via `ServerBuilder.components(...)`:

```java
Server.builder()
        ...
        .components(
                new MyExceptionMapper(),
                new MyRequestFilter(),
                MultiPartFeature.class    // Class object also accepted
        )
```

Common use cases:

| Component | Purpose |
|---|---|
| `ExceptionMapper<T>` | Map application exceptions to HTTP responses |
| `ContainerRequestFilter` | Intercept requests (authentication, request ID injection, etc.) |
| `ContainerResponseFilter` | Intercept responses (custom headers, response transformation, etc.) |
| `WriterInterceptor` / `ReaderInterceptor` | Intercept entity serialization/deserialization |
| `MultiPartFeature` (`jersey-media-multipart`) | Enable `multipart/form-data` support |
| `LoggingFeature` (Jersey built-in) | Full-payload TRACE logging with configurable verbosity |

### Jersey's LoggingFeature

If you want to log full request and response bodies (beyond the failure-detail logging
described in [section 15](#15-failure-detail-logging-and-redaction)):

```java
import org.glassfish.jersey.logging.LoggingFeature;
import java.util.logging.Logger;
import java.util.logging.Level;

.components(new LoggingFeature(
        Logger.getLogger("software.frisby.web.server.request.trace"),
        Level.FINE,
        LoggingFeature.Verbosity.PAYLOAD_ANY,
        8192
))
```

When `LoggingFeature` is active, silence the built-in `RequestLogger` INFO line to avoid
duplication:

```properties
software.frisby.web.server.RequestLogger.level = WARNING
```

---

## 18. Comparison with alternatives

### vs. Spring Boot (embedded Tomcat / Netty)

Spring Boot is an application framework that happens to include an embedded server.  Starting
a Spring Boot application pulls in auto-configuration, a DI container, property resolution,
actuator endpoints, and a large transitive dependency graph — even for the simplest use cases.

`frisby-web:server` is just a server.  If you already have a service layer, you wire it
directly to JAX-RS resource classes.  No container, no auto-configuration, no classpath
scanning, no `application.properties`, no startup-time surprises.

**Choose Spring Boot** when you need the full ecosystem — Spring Security, Spring Data,
Spring Cloud, etc.

**Choose `frisby-web:server`** when you want an embedded HTTP server and nothing more.

### vs. Quarkus

Quarkus is excellent but is optimized for GraalVM native compilation.  Its extension model,
build-time processing, and ArC CDI container are central to its design.  Running on standard
HotSpot JVM is supported but is not where Quarkus shines.

`frisby-web:server` is pure HotSpot Java 17.  No build-time augmentation, no annotation
processors, no special Maven plugins required beyond the standard ones.

**Choose Quarkus** when native executable startup time and image size are the primary concern.

**Choose `frisby-web:server`** when you want a predictable, debuggable, standard JVM deployment.

### vs. Micronaut

Micronaut does DI at compile time (annotation processors), which removes reflection overhead.
Its HTTP server is tightly integrated with its DI and AOP layers.  Using the HTTP server
without the framework is awkward.

`frisby-web:server` has no DI layer and no annotation processing — resource instances are
constructed by your code and handed to the builder.

### vs. Dropwizard

Dropwizard is philosophically similar: embedded Jetty, JAX-RS (Jersey), and a production-ready
set of defaults.  The difference is that Dropwizard bundles Metrics, Hibernate Validator,
Liquibase, a YAML configuration file format, and a full `Application` lifecycle as mandatory
parts of its model.

`frisby-web:server` imposes none of that.  You get Jetty + Jersey and a clean API; bring the
rest only if you need it.

**Choose Dropwizard** when you want all of those batteries included and are comfortable with
its conventions.

### vs. Vert.x

Vert.x is a reactive, non-blocking, event-loop-based toolkit.  It uses a fundamentally
different programming model (Futures, reactive streams, verticles) that requires rethinking
how you structure your code.

`frisby-web:server` uses the standard blocking JAX-RS model.  With virtual threads
(`Executors.newVirtualThreadPerTaskExecutor()`), blocking I/O becomes cheap at scale
without changing the programming model at all.

**Choose Vert.x** when you are building an I/O-intensive service and your team is comfortable
with reactive programming.

**Choose `frisby-web:server`** when you want familiar, readable, easy-to-debug synchronous code
that scales well via virtual threads.

### Summary

| | Spring Boot | Quarkus | Dropwizard | Vert.x | **frisby-web:server** |
|---|---|---|---|---|---|
| Mandatory DI container | ✓ | ✓ | partial | | **✗** |
| Build-time processing | | ✓ | | | **✗** |
| Classpath scanning | ✓ | ✓ | ✓ | | **✗** |
| Bundled config format | ✓ | ✓ | ✓ (YAML) | | **✗** |
| Virtual thread support | ✓ (3.2+) | ✓ | partial | | **✓** |
| Standard JAX-RS API | ✓ | ✓ | ✓ | | **✓** |
| Pluggable serializer | | | | ✓ | **✓** |
| Zero mandatory extras | | | | | **✓** |

---

## 19. Complete examples

### Minimal HTTP server

```java
Server server = Server.builder()
        .configuration(c -> c
                .port(8080)
                .serializer(new JacksonSerializer()))
        .resources(new OrderResource(orderService))
        .healthCheck()
        .build();

server.start();
```

### HTTPS with graceful shutdown and virtual threads

```java
SSLContext sslContext = loadSslContext();

Server server = Server.builder()
        .configuration(c -> c
                .port(8443)
                .serializer(JacksonSerializer.builder().build())
                .ssl(sslContext)
                .stopTimeout(Duration.ofSeconds(30))
                .maxConcurrentRequests(200)
                .executor(Executors.newVirtualThreadPerTaskExecutor()))
        .resources(new OrderResource(orderService))
        .healthCheck("/readyz")
        .build();

server.start();

Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
```

### HTTPS + HTTP/2 + CORS

```java
Server server = Server.builder()
        .configuration(c -> c
                .port(8443)
                .serializer(new JacksonSerializer())
                .ssl(sslContext)
                .http2()
                .cors(CorsConfiguration.builder()
                        .allowedOrigins("https://app.example.com")
                        .allowedMethods("GET", "POST", "PUT", "DELETE")
                        .allowedHeaders("Authorization", "Content-Type")
                        .allowCredentials()
                        .build()))
        .resources(new OrderResource(orderService))
        .healthCheck()
        .build();
```

### Production server with metrics, failure-log redaction, and observability

```java
Server server = Server.builder()
        .configuration(c -> c
                .port(8443)
                .serializer(JacksonSerializer.builder().build())
                .ssl(sslContext)
                .stopTimeout(Duration.ofSeconds(30))
                .maxConcurrentRequests(500)
                .executor(Executors.newVirtualThreadPerTaskExecutor())
                .logging(l -> l
                        .maxBodySize(4096)
                        .redactHeaders("X-Amzn-Oidc-Data")
                        .redactFields("password", "token", "creditCardNumber"))
                .cors(CorsConfiguration.builder()
                        .allowedOrigins("https://app.example.com")
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH")
                        .allowedHeaders("Authorization", "Content-Type")
                        .allowCredentials()
                        .build()))
        .resources(
                new OrderResource(orderService),
                new CustomerResource(customerService),
                new ProductResource(productService))
        .components(new GlobalExceptionMapper())
        .healthCheck("/readyz")
        .eventListener(new DatadogMetricsListener(statsDClient))
        .build();
```

### Test server on ephemeral port

When `port(0)` is used, the OS assigns a free port.  Retrieve it after `start()` to
construct request URLs — this avoids port-conflict races when tests run in parallel.

```java
Server server = Server.builder()
        .configuration(c -> c
                .port(0)
                .serializer(new JacksonSerializer()))
        .resources(new OrderResource(orderService))
        .build();

server.start();

URI baseUri = URI.create("http://localhost:" + server.port());
// ... send requests to baseUri ...

server.stop();
```



