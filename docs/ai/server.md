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
| `http2()` | disabled | Enables HTTP/2. Transport variant is auto-selected: **with `ssl()` configured** → h2 over TLS via ALPN (HTTP/1.1 clients fall back automatically); **without `ssl()`** → h2c (HTTP/2 cleartext upgrade, RFC 7540 §3.2). Use h2c behind a TLS-terminating ALB (e.g. AWS ALB with Protocol Version HTTP2) for internal service-to-service HTTP/2 without adding TLS to each backend service. |
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

## `HttpErrors` — static factory for HTTP error responses

`HttpErrors` removes the boilerplate and quirks of constructing Jersey exceptions directly.
Use it anywhere in a resource class, service layer, or `AuthenticationProvider`.

```java
import software.frisby.web.server.HttpErrors;

// Text body (Content-Type: text/plain)
throw HttpErrors.badRequest("'name' must not be blank.");
throw HttpErrors.unauthorized("Token has expired.");
throw HttpErrors.conflict("Record has changed — refresh and retry.");

// JSON body (Content-Type: application/json — serialized by the configured JsonSerializer)
throw HttpErrors.unprocessableEntity(new ValidationErrorBody(violations));

// Cause attached (for server-side logging; not exposed to the caller)
throw HttpErrors.serviceUnavailable("Key service unreachable.", upstreamException);

// Text body + cause
throw HttpErrors.internalServerError("Unexpected state.", unexpectedException);

// No body
throw HttpErrors.notFound();
```

### Six overloads per status code

| Signature | Body | Cause |
|---|---|---|
| `badRequest()` | none | — |
| `badRequest(String message)` | `text/plain` | — |
| `badRequest(Object body)` | `application/json` | — |
| `badRequest(Throwable cause)` | none | ✓ |
| `badRequest(String message, Throwable cause)` | `text/plain` | ✓ |
| `badRequest(Object body, Throwable cause)` | `application/json` | ✓ |

> **Important:** Pass `String` for plain-text bodies; pass non-`String` objects for JSON.
> Java picks the `String` overload for string literals automatically.

### Covered status codes

```
4xx: badRequest(400)  unauthorized(401)  forbidden(403)  notFound(404)
     methodNotAllowed(405)  notAcceptable(406)  requestTimeout(408)
     conflict(409)  gone(410)  payloadTooLarge(413)  unsupportedMediaType(415)
     unprocessableEntity(422)  tooManyRequests(429)

5xx: internalServerError(500)  notImplemented(501)  badGateway(502)
     serviceUnavailable(503)  gatewayTimeout(504)
```

### Key behaviors

- **`unauthorized()`** — all overloads suppress the `WWW-Authenticate` challenge header
  that Jersey's `NotAuthorizedException` constructors add by default.
- **JSON body** — the `Object` body is passed directly to the `Response` entity; it is
  serialized at write time by the server's registered `JsonMessageBodyProvider`, which
  delegates to the configured `JsonSerializer`.  No manual serialization required.
- **Return type** — all methods return `WebApplicationException`; callers just `throw` it.

---

## Static Assets

Register one or more static asset handlers via `ServerBuilder.staticAssets()`.  Static
handlers sit ahead of the JAX-RS servlet in Jetty's handler chain — JAX-RS endpoints
always take priority over static files.

### `ServerBuilder.staticAssets()`

| Method | Notes |
|---|---|
| `staticAssets(StaticAssetsConfiguration...)` | Registers one or more handlers. Calls are cumulative. Each configuration must have a unique, non-overlapping URL prefix — duplicates or overlap throw `IllegalStateException` at server startup. |

### `StaticAssetsConfiguration` — factory methods

| Factory | Validates at |
|---|---|
| `StaticAssetsConfiguration.classpath(String resourcePath)` — `resourcePath` must start with `/`; must not be null or blank | Builder time |
| `StaticAssetsConfiguration.filesystem(Path directory)` — `directory` must exist and be a readable directory | Builder time |

Classpath source existence is **not** checked at builder time (the builder's classloader
cannot see resources in the application JAR).  The server fails fast at startup with a
clear error if the path is missing.

### `StaticAssetsConfigurationBuilder` — all methods

| Method | Default | Constraints |
|---|---|---|
| `urlPrefix(String)` | `"/"` | Must start with `/`; not null or blank. Serves all unmatched paths when `"/"`. |
| `cacheMaxAge(Duration)` | none | Emits `Cache-Control: max-age=<s>, public`. `Duration.ZERO` emits `max-age=0, no-cache`. Omitting emits no header. Not null, not negative. |
| `responseHeaders(Map<String,String>)` | empty | Headers added to every asset response. Cumulative — later calls merge into earlier; duplicate keys take the later value. Map and all keys/values must not be null. |
| `spaFallback(boolean)` | `false` | When `true`, extensionless paths that resolve to a 404 are re-served as `index.html` with `200`. Paths with a file extension that are missing still return 404. |
| `preCompressed()` | `false` | Enables serving of pre-compressed sibling files. When a client sends `Accept-Encoding: gzip` or `br`, Jetty looks for a `.gz` or `.br` sibling file and serves it directly with the appropriate `Content-Encoding` header. Brotli is preferred over gzip when both siblings exist. No-op when no siblings are present. Use with Vite/webpack pre-compression output. |
| `errorPage(int statusCode, String path)` | none | Maps an HTTP error status (400–599) to a file in the asset root. Status code preserved; only body and `Content-Type` come from the file. Multiple calls allowed (different codes); duplicate codes are last-write-wins. Each path is validated for readability at server startup. |
| `authFilter(StaticAssetsAuthFilter)` | none | Invoked before every asset request. See below. |
| `build()` | — | Returns a `StaticAssetsConfiguration`. |

### `StaticAssetsAuthFilter` — `@FunctionalInterface`

```java
boolean authorize(Request request, Response response) throws Exception
```

- `true` — allow; asset is served normally.
- `false` — block.  If the response is not yet committed, the handler checks
  `errorPages()` for the response status and serves a configured page automatically.
  If no page is configured for that status, the response completes as-is (with whatever
  status the filter set).
- Throw — signals a backend failure (e.g. `IOException`).  The handler catches it, logs
  at `ERROR`, and serves the configured `errorPage(500, ...)` if present, or returns a
  plain `500`.

**Critical:** JAX-RS `AuthenticationProvider` implementations do **not** apply to static
assets — they run inside Jersey, which static handlers bypass entirely.  Use `authFilter()`
to gate static asset access.

### Built-in behaviours

| Behaviour | Detail |
|---|---|
| **Dotfile protection** | Final path segment starting with `.` (e.g. `/.env`) → unconditional `404`. Cannot be disabled. |
| **Directory index** | `GET /` and `GET /subdir/` serve the `index.html` within that directory. |
| **Pre-compressed serving** | When `preCompressed()` is set, `.br` and `.gz` siblings are served in preference to the original when the client advertises the matching `Accept-Encoding`. Brotli preferred when both exist. `Vary: Accept-Encoding` added automatically. |
| **ETags / Last-Modified** | Emitted automatically; `If-None-Match` returns `304 Not Modified`. |
| **JAX-RS priority** | Static handlers only receive requests that no JAX-RS endpoint matched. |
| **Startup validation** | Asset source and all `errorPage` paths validated when the server starts; missing resources → `IllegalStateException` with a clear message. |
| **URL prefix conflict detection** | Exact duplicates or proper-prefix overlaps (e.g. `/admin` + `/admin/reports`) → `IllegalStateException` at startup. |

### Usage examples

**Minimal SPA from classpath:**
```java
Server.builder()
        ...
        .staticAssets(
                StaticAssetsConfiguration.classpath("/web")
                        .spaFallback(true)
                        .errorPage(404, "404.html")
                        .responseHeaders(Map.of(
                                "Content-Security-Policy", "default-src 'self'",
                                "X-Frame-Options", "DENY",
                                "X-Content-Type-Options", "nosniff"
                        ))
                        .build()
        )
```

**Multiple roots — auth-gated admin SPA + public docs:**
```java
Server.builder()
        ...
        .staticAssets(
                StaticAssetsConfiguration.classpath("/admin-web")
                        .urlPrefix("/admin")
                        .spaFallback(true)
                        .errorPage(401, "401.html")
                        .errorPage(500, "500.html")
                        .authFilter((req, res) -> {
                            if (!tokenStore.isValid(extractToken(req))) {
                                res.setStatus(401);
                                return false;
                            }
                            return true;
                        })
                        .build(),
                StaticAssetsConfiguration.filesystem(Path.of("/var/docs"))
                        .urlPrefix("/docs")
                        .cacheMaxAge(Duration.ofHours(1))
                        .build()
        )
```

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
                        .http2()                              // h2c — no ssl() = cleartext HTTP/2
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

