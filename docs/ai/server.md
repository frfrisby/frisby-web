# HTTP Server — `software.frisby.web`

This document describes the complete public API for the `server` module.
Attach this document when writing code that hosts JAX-RS resources using `frisby-web`.

---

## Maven coordinates

```xml
<dependency>
    <groupId>software.frisby.web</groupId>
    <artifactId>server</artifactId>
</dependency>
```

---

## Quick start

```java
// 1. Build a serializer (see serialization.md)
JacksonSerializer serializer = JacksonSerializer.builder().build();

// 2. Build and start the server
Server server = Server.builder()
        .configuration(
                ServerConfiguration.builder()
                        .port(8080)
                        .serializer(serializer)
                        .build()
        )
        .resources(new OrderResource(orderService))
        .healthCheck()
        .build();

server.start();
// ... serve requests ...
server.stop();
```

---

## `Server`

```java
static ServerBuilder builder()

int     port()           // bound port; 0 before start() if port(0) was configured
URI     uri()            // base URI — scheme, host, port; no trailing slash
ServerConfiguration configuration()
boolean isRunning()
void    start()          // throws UncheckedIOException if port cannot be bound
void    stop()
```

`start()` and `stop()` are thread-safe; only the first caller in each case has any effect.

`uri()` is suitable for direct use as a client base URI:
```java
Client client = Client.builder()
        .configuration(c -> c
                .uri(server.uri())
                .serializer(serializer))
        .build();
```

---

## `ServerBuilder`

Obtain via `Server.builder()`.

| Method | Required | Notes |
|---|---|---|
| `configuration(ServerConfiguration)` | ✅ | Runtime settings |
| `configuration(UnaryOperator<ServerConfigurationBuilder>)` | — | Lambda convenience overload |
| `resources(Object...)` / `resources(List<Object>)` | ✅ | JAX-RS `@Path`-annotated instances; calls are cumulative |
| `components(Object...)` / `components(List<Object>)` | — | JAX-RS `@Provider` instances or classes; calls are cumulative |
| `healthCheck()` | — | Mounts liveness probe at `/health` |
| `healthCheck(String path)` | — | Mounts liveness probe at custom path |
| `authentication(AuthenticationProvider...)` / `authentication(List<AuthenticationProvider>)` | — | Auth chain; calls are cumulative; see `server-security.md` |
| `eventListener(ServerEventListener)` | — | Metrics / tracing hook; defaults to no-op |
| `build()` | — | Throws `IllegalStateException` if no configuration or no resources provided |

---

## `ServerConfiguration` / `ServerConfigurationBuilder`

Obtain via `ServerConfiguration.builder()`.

### Required options

| Method | Description |
|---|---|
| `port(int)` | Network port.  Pass `0` for OS-assigned ephemeral port (useful in tests). |
| `serializer(JsonSerializer)` | JSON serializer for request/response bodies. |

### Optional options

| Method | Default | Description |
|---|---|---|
| `host(String)` | `"0.0.0.0"` | Bind address.  Use `"localhost"` to restrict to loopback. |
| `maxRequestSize(long)` | 4 MB | Request bodies larger than this return HTTP 413. |
| `gzip()` | disabled | Transparently decompresses `Content-Encoding: gzip` requests and compresses `application/json` responses when `Accept-Encoding: gzip` is present. |
| `http2()` | disabled | Enables HTTP/2 over TLS (h2) via ALPN.  Requires `ssl()`.  `build()` throws if called without a TLS context. |
| `ssl()` | plain HTTP | Enables HTTPS using the JDK default `SSLContext`. |
| `ssl(SSLContext)` | plain HTTP | Enables HTTPS using a custom `SSLContext`. |
| `cors(CorsConfiguration)` | disabled | CORS filter; see below. |
| `logging(ServerLoggingConfiguration)` | See below | Header masking, field redaction, body size cap. |
| `logging(UnaryOperator<ServerLoggingConfigurationBuilder>)` | — | Lambda convenience overload. |
| `maxConcurrentRequests(int)` | `availableProcessors * 20` | In-flight request cap.  Excess requests receive HTTP 503 with `Retry-After: 1`. |
| `executor(Executor)` | Platform threads | Pass `Executors.newVirtualThreadPerTaskExecutor()` (Java 21+) for virtual threads. |
| `stopTimeout(Duration)` | immediate | Graceful shutdown: server waits up to this duration for in-flight requests to drain before closing connections. |

---

## Server logging — `ServerLoggingConfiguration` / `ServerLoggingConfigurationBuilder`

Controls what appears in server-side failure log entries (4xx, 5xx, unhandled exceptions).

```java
ServerLoggingConfiguration logging = ServerLoggingConfiguration.builder()
        .maxBodySize(4096)                               // default: 8192 (8 KB); 0 = disable body logging
        .redactHeaders("X-Amzn-Oidc-Data", "X-Api-Key")  // always masked: Authorization, Cookie, Set-Cookie
        .redactFields("password", "token")               // JSON / form-encoded field values → [redacted]
        .build();
```

- `redactHeaders` matching is case-insensitive; always includes `authorization`, `cookie`, `set-cookie`.
- `redactFields` matching is case-sensitive; affects JSON string fields and form-encoded fields only.
- Calls to `redactHeaders` and `redactFields` are cumulative.
- `maxBodySize` clamps logged body length; truncated bodies are marked in the log.
- Maximum `maxBodySize` is 100 MB; minimum is 0.

Pass to `ServerConfigurationBuilder.logging(logging)`.

---

## Health check

```java
.healthCheck()              // GET /health → 200 {"status":"UP"}
.healthCheck("/readyz")     // custom path — Kubernetes liveness probe convention
```

**Behavior:**
- Always returns `200 {"status":"UP"}` while the server is running.
- Bypasses the `maxConcurrentRequests` semaphore when the server is healthy at capacity —
  prevents the load balancer from recycling a live, healthy instance under heavy load.
- During graceful shutdown (`stopTimeout` configured), returns `503` — the correct drain
  signal to the load balancer.
- Logged at `TRACE` rather than `INFO` to avoid drowning meaningful request logs.
- `ServerEventListener` callbacks are suppressed for health check requests.
- Path rules: must start with `/`; must not end with `/`; no consecutive slashes;
  max 256 characters; max 64 path segments; alphanumeric, hyphens, underscores, dots only.

---

## JAX-RS components

Register `@Provider` classes or instances via `components()`.  Common uses:

**Multipart support** — add the Jersey multipart dependency and register:
```java
.components(org.glassfish.jersey.media.multipart.MultiPartFeature.class)
```

**Role-based access control** — register `RolesAllowedDynamicFeature` to activate
`@RolesAllowed` on resource methods (see `server-security.md`):
```java
.components(org.glassfish.jersey.server.filter.RolesAllowedDynamicFeature.class)
```

**Custom compression** — register `GZipEncoder` and a custom `ContainerResponseFilter`
for advanced compression control (compress additional media types, apply size thresholds)
beyond what `ServerConfigurationBuilder.gzip()` provides.

---

## CORS — `CorsConfiguration` / `CorsConfigurationBuilder`

```java
// Specific origins with credentials
CorsConfiguration cors = CorsConfiguration.builder()
        .allowedOrigins("https://app.example.com", "https://admin.example.com")
        .allowedMethods("GET", "POST", "PUT", "DELETE")
        .allowedHeaders("Authorization", "Content-Type")   // optional; defaults to echo
        .allowCredentials()                                // optional; default false
        .build();

// Wildcard origin — any browser origin (incompatible with allowCredentials())
CorsConfiguration cors = CorsConfiguration.builder()
        .allowedOrigins("*")
        .allowedMethods("GET", "POST")
        .build();
```

Pass to `ServerConfigurationBuilder.cors(cors)`.

### Builder methods

| Method | Required | Notes |
|---|---|---|
| `allowedOrigins(String... origins)` | ✅ | Cumulative.  Use `"*"` for wildcard.  Cannot combine with `allowCredentials()`. |
| `allowedMethods(String... methods)` | ✅ | Cumulative.  Sent as `Access-Control-Allow-Methods` in preflight. |
| `allowedHeaders(String... headers)` | — | Cumulative.  When not called, the server **echoes** the client's `Access-Control-Request-Headers` value (permissive default). |
| `allowCredentials()` | — | Adds `Access-Control-Allow-Credentials: true`.  Cannot combine with wildcard origin. |
| `build()` | — | Throws if no origins or no methods.  Throws `IllegalStateException` if wildcard + credentials. |

### `AllowedHeaders` variants
- `AllowedHeaders.Echo` — server echoes whatever headers the browser requests (default when `allowedHeaders` never called).
- `AllowedHeaders.Explicit` — server advertises only the declared header names.

---

## Events — `ServerEventListener`

Register via `ServerBuilder.eventListener(listener)`.

```java
void onRequestCompleted(RequestCompletedEvent event)
```

All responses are reported here — `4xx` and `5xx` included.  Every unhandled exception
produces a `500` response, so there is no separate "failed without response" callback.

Callbacks are on the thread that finishes processing the request.  Implementations must
be thread-safe.  Exceptions thrown by a callback are caught, logged at `WARNING`, and
suppressed — a buggy listener never affects request processing.

### `RequestCompletedEvent` (server, record)

```
method()         String   — "GET", "POST", etc.
path()           String   — decoded request path without query parameters
statusCode()     int      — HTTP response status code
latency()        Duration — time from request received to response fully written
requestBytes()   long     — request body size in bytes (0 if no body or unknown)
responseBytes()  long     — response body size in bytes (0 if no body or unknown)
successful()     boolean  — true for 2xx
```

> The `path` field excludes query parameters intentionally — query parameters may contain
> sensitive values (API keys, tokens, PII) that must not appear in metrics tags.

---

## Complete example — production-grade server

```java
JacksonSerializer serializer = JacksonSerializer.builder().build();

// Graceful-shutdown executor
Executor executor = Executors.newVirtualThreadPerTaskExecutor();

Server server = Server.builder()
        .configuration(
                ServerConfiguration.builder()
                        .port(8080)
                        .serializer(serializer)
                        .gzip()
                        .maxRequestSize(10 * 1024 * 1024)   // 10 MB
                        .maxConcurrentRequests(500)
                        .executor(executor)
                        .stopTimeout(Duration.ofSeconds(30))
                        .cors(CorsConfiguration.builder()
                                .allowedOrigins("https://app.example.com")
                                .allowedMethods("GET", "POST", "PUT", "DELETE")
                                .allowCredentials()
                                .build())
                        .logging(l -> l
                                .maxBodySize(4096)
                                .redactFields("password", "token"))
                        .build()
        )
        .resources(
                new OrderResource(orderService),
                new UserResource(userService)
        )
        .healthCheck()
        .eventListener(event -> metrics.record(event))
        .build();

server.start();

// Shutdown hook
Runtime.getRuntime().addShutdownHook(new Thread(() -> {
    server.stop();
    executor.close();
}));
```

