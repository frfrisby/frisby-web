# frisby-web — HTTP Client

`software.frisby.web:client` provides a lightweight, type-safe HTTP client built on the
JDK 11+ `HttpClient`.  It handles JSON serialization, response deserialization, pluggable
authentication, request/response compression, structured logging with sensitive-value
redaction, and an observability callback interface — with a minimal, fluent API and zero
mandatory external dependencies beyond the JDK.

---

## Contents

1. [Why this library?](#1-why-this-library)
2. [Maven dependency](#2-maven-dependency)
3. [Quick start](#3-quick-start)
4. [ClientBuilder reference](#4-clientbuilder-reference)
5. [ConfigurationBuilder reference](#5-configurationbuilder-reference)
6. [Making requests — verb specs](#6-making-requests--verb-specs)
7. [Path parameters and query parameters](#7-path-parameters-and-query-parameters)
8. [Request headers and cookies](#8-request-headers-and-cookies)
9. [Request bodies](#9-request-bodies)
10. [Response handling](#10-response-handling)
11. [Asynchronous requests](#11-asynchronous-requests)
12. [Compression](#12-compression)
13. [Authentication — basic-security](#13-authentication--basic-security)
14. [Authentication — oauth2-security](#14-authentication--oauth2-security)
15. [Logging and redaction](#15-logging-and-redaction)
16. [Observability — ClientEventListener](#16-observability--clienteventlistener)
17. [TLS / custom SSLContext](#17-tls--custom-sslcontext)
18. [Exception hierarchy](#18-exception-hierarchy)
19. [Complete examples](#19-complete-examples)

---

## 1. Why this library?

| Property                                 | Detail                                                                                                                                                                |
|------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Zero mandatory external dependencies** | Backed by `java.net.http.HttpClient` — no OkHttp, no Apache HttpClient, no Netty                                                                                      |
| **Fully explicit**                       | No classpath scanning, no auto-configuration, no magic defaults                                                                                                       |
| **Pluggable serialization**              | Bring your own `JsonSerializer` (Jackson, Gson, etc.) — no hard serialization dependency                                                                              |
| **Pluggable authentication**             | `SecurityProvider` interface covers Basic, Bearer, OAuth 2.0, or any custom scheme                                                                                    |
| **Pluggable compression**                | `ContentCompressor` / `ContentDecompressor` interfaces allow gzip out of the box and brotli/zstd via caller-supplied implementations — no JNI transitive dependencies |
| **First-class observability**            | Structured per-request logging and a `ClientEventListener` callback interface — wire to any metrics backend                                                           |
| **Per-request security override**        | Every verb spec has a `.security()` method to override the client default for one request                                                                             |

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
    <artifactId>client</artifactId>
</dependency>
```

For HTTP Basic / Bearer token authentication, also add:

```xml
<dependency>
    <groupId>software.frisby.web</groupId>
    <artifactId>basic-security</artifactId>
</dependency>
```

For OAuth 2.0 client-credentials authentication, also add:

```xml
<dependency>
    <groupId>software.frisby.web</groupId>
    <artifactId>oauth2-security</artifactId>
</dependency>
```

For a ready-made Jackson-backed `JsonSerializer`, also add:

```xml
<dependency>
    <groupId>software.frisby.web</groupId>
    <artifactId>jackson-serializer</artifactId>
</dependency>
```

`JacksonSerializer` (package `software.frisby.web.serial.jackson`) is an opinionated
out-of-the-box serializer: ISO-8601 dates, plain `BigDecimal`, `Optional` support via
`Jdk8Module`, `JavaTimeModule` registered, and `FAIL_ON_UNKNOWN_PROPERTIES = false`.
Use `JacksonSerializer.builder().mapper(customMapper).build()` to supply your own
`ObjectMapper` if the defaults do not suit your needs.

---

## 3. Quick start

```java
// Build a reusable client — one instance per target service, for the lifetime of the app
Client client = Client.builder()
        .configuration(c -> c
                .uri(URI.create("https://api.example.com"))
                .connectTimeout(Duration.ofSeconds(5))
                .readTimeout(Duration.ofSeconds(30))
                .serializer(JacksonSerializer.builder().build()))
        .build();

// GET — typed response
User user = client.get()
        .path("/users/{id}", "id", userId)
        .send(User.class)
        .body();

// POST — JSON body, typed response
Order order = client.post()
        .path("/orders")
        .body(new CreateOrderRequest(userId, items))
        .send(Order.class)
        .body();

// DELETE — no response body
client.delete()
        .path("/sessions/{id}", "id", sessionId)
        .send();
```

The `configuration(UnaryOperator<ConfigurationBuilder>)` overload accepts a lambda for
inline configuration.  For shared or reusable configuration, use the object overload:

```java
Configuration config = Configuration.builder()
        .uri(URI.create("https://api.example.com"))
        .connectTimeout(Duration.ofSeconds(5))
        .readTimeout(Duration.ofSeconds(30))
        .serializer(JacksonSerializer.builder().build())
        .build();

Client client = Client.builder()
        .configuration(config)
        .build();
```

---

## 4. ClientBuilder reference

Obtain a builder via `Client.builder()`.

| Method                                               | Required | Description                                                                  |
|------------------------------------------------------|----------|------------------------------------------------------------------------------|
| `configuration(Configuration)`                       | ✓        | Sets the client runtime configuration.                                       |
| `configuration(UnaryOperator<ConfigurationBuilder>)` | ✓        | Inline lambda convenience overload.                                          |
| `security(SecurityProvider)`                         |          | Default security provider applied to every request. Overridable per request. |
| `eventListener(ClientEventListener)`                 |          | Receives a callback after every completed or failed request.                 |
| `build()`                                            |          | Returns a configured `Client` instance.                                      |

`build()` throws `IllegalStateException` if no configuration is provided.

---

## 5. ConfigurationBuilder reference

Obtain a builder via `Configuration.builder()`.

| Method                                | Default                            | Description                                                                                                                                     |
|---------------------------------------|------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------|
| `uri(URI)`                            | — *required*                       | Base URI of the target service.  All request paths are resolved against this.                                                                   |
| `connectTimeout(Duration)`            | — *required*                       | Maximum time to wait for a TCP connection.                                                                                                      |
| `readTimeout(Duration)`               | — *required*                       | Maximum time to wait for a response after a request is sent.                                                                                    |
| `serializer(JsonSerializer)`          | — *required*                       | JSON serializer used to serialize request bodies and deserialize response bodies.                                                               |
| `sslContext(SSLContext)`              | JDK default                        | Custom TLS context — private CA trust store, mutual TLS client certificate, etc.                                                                |
| `redirectPolicy(HttpClient.Redirect)` | `NORMAL`                           | Whether redirects are followed automatically.  See [Response handling](#10-response-handling).                                                  |
| `httpVersion(HttpClient.Version)`     | `HTTP_1_1`                         | HTTP protocol version preference.                                                                                                               |
| `decompress()`                        | off                                | Registers the built-in gzip response decompressor.  Additive — call multiple times for multiple encodings.  See [Compression](#12-compression). |
| `decompress(ContentDecompressor)`     | off                                | Registers a custom response decompressor.  Additive.                                                                                            |
| `executor(Executor)`                  | shared default                     | Custom `Executor` for the underlying `HttpClient`.  Virtual threads: `Executors.newVirtualThreadPerTaskExecutor()`.                             |
| `logging(ClientLoggingConfiguration)` | 8 KB body, built-in headers masked | Controls header masking, body field redaction, and body size limits for log entries.                                                            |
| `build()`                             |                                    | Returns a `Configuration` instance.                                                                                                             |

`build()` throws `IllegalStateException` if any required option is absent, and
`DuplicateElementsException` if two registered decompressors share the same encoding token.

---

## 6. Making requests — verb specs

Every `Client` method returns a fluent spec object.  Configure the request by chaining
calls in any order, then call `send()` to execute.

```java
// GET
client.get().path("/resource").send(MyType.class);

// POST / PUT / PATCH — require a body
client.post().path("/resource").body(payload).send(MyType.class);
client.put().path("/resource/{id}", "id", id).body(payload).send(MyType.class);
client.patch().path("/resource/{id}", "id", id).body(patch).send(MyType.class);

// DELETE
client.delete().path("/resource/{id}", "id", id).send();

// HEAD — response headers only, no body
client.head().path("/resource").send();
```

All specs support:
- `path(String)` — path relative to the base URI
- `path(String, String, String)` — path with a single named placeholder
- `path(String, PathParameter...)` — path with multiple named placeholders
- `parameter(String, String)` — query parameter
- `parameter(String, String...)` — multivalued query parameter
- `header(String, String)` — request header
- `header(String, String...)` — multivalued request header
- `cookie(HttpCookie)` — adds a `Cookie` header entry
- `security(SecurityProvider)` — per-request security override

`GET`, `HEAD`, and `DELETE` additionally support `send(GenericType<T>)` for generic
response types.  `POST`, `PUT`, and `PATCH` also support `compress()` /
`compress(ContentCompressor)`.

---

## 7. Path parameters and query parameters

### Named path placeholders

```java
// Single placeholder
client.get().path("/users/{id}", "id", userId).send(User.class);

// Multiple placeholders
client.get()
        .path("/teams/{team}/members/{member}",
                PathParameter.of("team", teamId),
                PathParameter.of("member", memberId))
        .send(Member.class);
```

The placeholder name must appear in the path template surrounded by braces.
`UriSyntaxException` is thrown if the name does not match any placeholder in the path.

### Query parameters

```java
// Single value
client.get()
        .path("/products")
        .parameter("category", "electronics")
        .parameter("page", "1")
        .send(new GenericType<List<Product>>() {});

// Multi-value (repeats the key)
client.get()
        .path("/products")
        .parameter("tag", "sale", "featured", "new")
        .send(new GenericType<List<Product>>() {});
// → /products?tag=sale&tag=featured&tag=new
```

---

## 8. Request headers and cookies

```java
client.get()
        .path("/reports")
        .header("X-Tenant-Id", tenantId)
        .header("Accept-Language", "en-US", "fr")   // multi-value
        .cookie(new HttpCookie("session", sessionToken))
        .send(Report.class);
```

The following headers are managed by the client and may not be set manually:
`Accept`, `Accept-Encoding`, `Content-Type`, `Content-Length`, `Content-Encoding`,
`Transfer-Encoding`.  Attempting to set any of these throws `IllegalArgumentException`.

---

## 9. Request bodies

### JSON body

Any object passed to `body(Object)` is serialized to JSON using the configured
`JsonSerializer`.  If the object is already a `String`, it is sent as-is.

```java
client.post()
        .path("/users")
        .body(new CreateUserRequest("alice", "alice@example.com"))
        .send(User.class);
```

### Multipart form-data

```java
client.post()
        .path("/documents")
        .body(FormData.of(
                FormPart.json("metadata", metadataObject),
                FormPart.file("file", fileStream, "report.pdf", MediaType.of("application/pdf"))
        ))
        .send(Document.class);
```

The `FormPart` factory methods are:

| Method                                             | Part type                                                    |
|----------------------------------------------------|--------------------------------------------------------------|
| `FormPart.file(name, stream, fileName)`            | File stream; `Content-Type` guessed from extension           |
| `FormPart.file(name, stream, fileName, mediaType)` | File stream; explicit `Content-Type`                         |
| `FormPart.json(name, body)`                        | Object serialized to JSON by the configured `JsonSerializer` |
| `FormPart.text(name, value)`                       | Plain string; `Content-Type: text/plain`                     |
| `FormPart.entity(name, content, mediaType)`        | Pre-serialized string with explicit `Content-Type`           |

### URL-encoded form

```java
client.post()
        .path("/session")
        .body(FormUrlEncoded.builder()
                .field("username", "alice")
                .field("password", "s3cr3t")
                .build())
        .send(SessionToken.class);
```

---

## 10. Response handling

Every `send()` call returns `HttpResponse<T>`.  For `2xx` responses the body is
deserialized; for `4xx` / `5xx` responses an exception is thrown automatically (see
[Exception hierarchy](#18-exception-hierarchy)).

```java
HttpResponse<User> response = client.get()
        .path("/users/{id}", "id", userId)
        .send(User.class);

int status = response.statusCode();   // 200
User user  = response.body();         // deserialized
```

### Generic response types

```java
List<Order> orders = client.get()
        .path("/orders")
        .send(new GenericType<List<Order>>() {})
        .body();
```

### No response body

```java
// Returns HttpResponse<Void>
HttpResponse<Void> response = client.post()
        .path("/events")
        .body(event)
        .send();
```

### Redirect policy

The default `NORMAL` policy follows HTTP → HTTP and HTTPS → HTTPS redirects
transparently.  Set `redirectPolicy(HttpClient.Redirect.NEVER)` to receive `3xx`
responses directly — `response.body()` will be `null` for those; the redirect target
is in `response.headers().firstValue("Location")`.

---

## 11. Asynchronous requests

Every `send()` variant has a corresponding `sendAsync()` that returns
`CompletableFuture<HttpResponse<T>>`.

```java
// Fire and chain
client.get()
        .path("/users/{id}", "id", userId)
        .sendAsync(User.class)
        .thenAccept(response -> renderProfile(response.body()));

// Collect multiple results in parallel
CompletableFuture<HttpResponse<Product>> f1 =
        client.get().path("/products/1").sendAsync(Product.class);
CompletableFuture<HttpResponse<Product>> f2 =
        client.get().path("/products/2").sendAsync(Product.class);

CompletableFuture.allOf(f1, f2).join();
```

---

## 12. Compression

### Response decompression

Register one or more decompressors on `ConfigurationBuilder`.  The client derives the
`Accept-Encoding` header automatically from the registered encodings and decompresses
matching responses before deserialization.

```java
// Built-in gzip
Configuration.builder()
        .decompress()
        // ...
        .build();

// Custom algorithm (caller supplies the library)
Configuration.builder()
        .decompress()                                                       // gzip
        .decompress(ContentDecompressor.of("br", BrotliInputStream::new))  // brotli
        .build();
```

If the server responds with a `Content-Encoding` value that has no registered
decompressor, `UnsupportedContentEncodingException` is thrown immediately — the
client never attempts to deserialize compressed bytes as JSON.

### Request body compression

Call `compress()` on any `POST`, `PUT`, or `PATCH` spec to gzip-compress the JSON
request body.  Use `compress(ContentCompressor)` for a custom algorithm.

```java
// Built-in gzip
client.post()
        .path("/readings/ingest")
        .compress()
        .body(deviceReadingsBatch)
        .send(IngestResponse.class);

// Custom algorithm
client.post()
        .path("/ingest")
        .compress(ContentCompressor.of("br", bytes -> myBrotliLib.compress(bytes)))
        .body(payload)
        .send(IngestResponse.class);
```

`Content-Encoding` is set automatically.  Compression applies only to JSON bodies
(via `body(Object)`); using it with `FormData` or `FormUrlEncoded` throws
`IllegalStateException` at send time.

---

## 13. Authentication — basic-security

The `basic-security` module provides `BasicSecurityProvider` (HTTP Basic Auth) and
`BearerTokenSecurityProvider` (Bearer token).

### HTTP Basic Auth

```java
BasicSecurityProvider basic = BasicSecurityProvider.builder()
        .credentials(Credentials.of("alice", "s3cr3t"))
        .build();

Client client = Client.builder()
        .configuration(config)
        .security(basic)
        .build();
```

The provider encodes the credentials as `Base64(username:password)` and sets the
`Authorization: Basic <encoded>` header on every request, following RFC 7617.

`Credentials` redacts the password in `toString()` — it is safe to log.

### Bearer token — static

```java
BearerTokenSecurityProvider bearer = BearerTokenSecurityProvider.builder()
        .token("eyJhbGciOiJSUzI1NiJ9...")
        .build();
```

Sets `Authorization: Bearer <token>` on every request.

### Bearer token — dynamic (token supplier)

```java
BearerTokenSecurityProvider bearer = BearerTokenSecurityProvider.builder()
        .token(() -> tokenStore.currentToken())
        .build();
```

The supplier is called on every request, enabling tokens that rotate or expire to be
refreshed externally.

### Per-request override

```java
// Most requests use the client-level security provider
Client client = Client.builder()
        .configuration(config)
        .security(defaultBearer)
        .build();

// One privileged request uses a different token
client.get()
        .path("/admin/report")
        .security(adminBearer)
        .send(Report.class);
```

---

## 14. Authentication — oauth2-security

The `oauth2-security` module provides `ClientCredentialsSecurityProvider`, which
implements the OAuth 2.0 client-credentials flow:

- Fetches a token from the configured token endpoint before the first request.
- Caches the token and reuses it until it expires (with a configurable expiry buffer).
- Automatically refreshes the token when it expires.
- Thread-safe — concurrent requests will not trigger multiple simultaneous token fetches.

```java
ClientCredentialsSecurityProvider oauth2 =
        ClientCredentialsSecurityProvider.builder()
                .tokenEndpoint(URI.create("https://auth.example.com/oauth2/token"))
                .credentials(ClientCredentials.of("my-client-id", "my-client-secret"))
                .serializer(myJsonSerializer)                // required
                .scope("read:orders", "write:orders")        // optional
                .connectTimeout(Duration.ofSeconds(10))      // optional; default 10 s
                .requestTimeout(Duration.ofSeconds(30))      // optional; default 30 s
                .expiryBuffer(Duration.ofSeconds(30))        // optional; default 30 s
                .basicAuth()                                 // optional; default is body params
                .sslContext(myCustomSslContext)              // optional
                .eventListener(myTokenMetricsListener)       // optional
                .build();

Client client = Client.builder()
        .configuration(config)
        .security(oauth2)
        .build();
```

`ClientCredentials` redacts the client secret in `toString()` — it is safe to log.

### Client auth method

By default, credentials are sent as `client_id` / `client_secret` form body parameters
(`client_secret_post`).  Call `basicAuth()` to send them as an `Authorization: Basic`
header instead (`client_secret_basic`), which many identity providers prefer or require:

```java
ClientCredentialsSecurityProvider.builder()
        .tokenEndpoint(...)
        .credentials(...)
        .serializer(...)
        .basicAuth()   // Authorization: Basic base64(clientId:clientSecret)
        .build();
```

### Expiry buffer

The `expiryBuffer` (default 30 seconds) causes the provider to treat a token as expired
slightly before its actual `expires_in` time, guarding against clock skew and tokens
that expire mid-flight.  Tune it for your environment:

```java
.expiryBuffer(Duration.ofSeconds(60))   // more conservative for high-latency networks
```

### Token event listener

Implement `TokenEventListener` to receive callbacks for metrics or alerting:

```java
public class MyTokenMetrics implements TokenEventListener {
    @Override
    public void onTokenFetched(Duration latency) {
        metrics.record("token.fetch.latency", latency);
    }

    @Override
    public void onTokenFetchFailed(Duration latency, Throwable cause) {
        metrics.increment("token.fetch.failures");
        alerts.notify("Token fetch failed: " + cause.getMessage());
    }
}
```

### Token endpoint logging

Token endpoint activity is logged under a separate logger name:
`software.frisby.web.client.security.oauth2.TokenRequestLogger`.

This lets you control token-fetch log verbosity independently of the main request
logger — for example, silence the frequent token refreshes in production while keeping
the main client at `INFO`:

```
software.frisby.web.client.security.oauth2.TokenRequestLogger = WARNING
```

Log level usage follows the same conventions as the main client logger:

| Level     | Content                                                                 |
|-----------|-------------------------------------------------------------------------|
| `TRACE`   | Full request and response headers for the token endpoint exchange       |
| `INFO`    | Method, URI, status code, and latency — successful token fetches only   |
| `WARNING` | Method, URI, status code, and latency — error responses (`4xx` / `5xx`) |
| `ERROR`   | Method, URI, and exception message — transport-level failures           |

**Request and response bodies are never logged**, regardless of log level.  The request
body contains the client credentials and the response body contains the access token —
both are sensitive and suppressed unconditionally.  The `Authorization` header is masked
as `[redacted]` when `basicAuth()` mode is active.

### Token endpoint errors

If the token endpoint returns a non-`2xx` response, `TokenEndpointException` is thrown.
It carries the HTTP status code, response headers, and a preview of the response body.

---

## 15. Logging and redaction

All HTTP client logging is emitted via `System.Logger` under the logger name
`software.frisby.web.client.RequestLogger`.  Configure it with any `java.util.logging`
(or SLF4J-JUL bridge) configuration.

OAuth 2.0 token endpoint activity uses a separate logger —
see [Token endpoint logging](#token-endpoint-logging) in section 14.

### Log levels

| Level     | Content                                                                                                                                                                    |
|-----------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `TRACE`   | Full request + response: method, URI, all request headers (masked), optional request body (redacted), status, response headers, optional response body (redacted), latency |
| `INFO`    | Method, URI, status code, and latency — successful responses only                                                                                                          |
| `WARNING` | Full request + response block — HTTP error responses (`4xx` / `5xx`)                                                                                                       |
| `ERROR`   | Full request block — transport-level failures (connect timeout, read timeout, etc.)                                                                                        |

### Built-in redaction

The following are always masked regardless of configuration:
- `Authorization` header → `[redacted]`
- `Cookie` header → cookie names preserved, values replaced with `[redacted]`
- `Set-Cookie` header → cookie name and attributes preserved, value replaced with `[redacted]`

### Custom header and field redaction

```java
Configuration.builder()
        .logging(ClientLoggingConfiguration.builder()
                .redactHeaders("x-api-key", "x-internal-token")
                .redactFields("password", "ssn", "cardNumber")
                .maxBodySize(4096)   // truncate logged bodies to 4 KB; 0 = suppress bodies
                .build())
        // ...
        .build();
```

`redactFields` matches JSON string field values by name in both request and response
bodies — the value is replaced with `"[redacted]"` in the log output but the actual
HTTP body is never modified.

---

## 16. Observability — ClientEventListener

Implement `ClientEventListener` to receive a `RequestCompletedEvent` after every request
(successful or failed) — useful for recording latency histograms, request counts, and
error rates without coupling the client to a specific metrics library.

```java
public class MyMetrics implements ClientEventListener {
    @Override
    public void onRequestCompleted(RequestCompletedEvent event) {
        metrics.record(
                event.method(),
                event.uri().getPath(),
                event.statusCode().orElse(0),
                event.latency()
        );

        event.exception().ifPresent(ex ->
                errors.increment(ex.getClass().getSimpleName())
        );
    }
}

Client client = Client.builder()
        .configuration(config)
        .eventListener(new MyMetrics())
        .build();
```

`RequestCompletedEvent` fields:

| Field          | Type                  | Description                                                              |
|----------------|-----------------------|--------------------------------------------------------------------------|
| `method()`     | `String`              | HTTP method (`"GET"`, `"POST"`, etc.)                                    |
| `uri()`        | `URI`                 | The full request URI                                                     |
| `statusCode()` | `OptionalInt`         | HTTP status code; empty for transport failures                           |
| `latency()`    | `Duration`            | Wall-clock time from send to response (or failure)                       |
| `exception()`  | `Optional<Throwable>` | The exception for transport or HTTP error responses; empty for successes |

---

## 17. TLS / custom SSLContext

For HTTPS with the default JDK trust store, no configuration is needed — the JDK
`HttpClient` uses TLS automatically for `https://` URIs.

For custom trust stores or mutual TLS:

```java
SSLContext ctx = SSLContext.getInstance("TLS");
ctx.init(keyManagers, trustManagers, null);

Configuration.builder()
        .uri(URI.create("https://internal-service.example.com"))
        .sslContext(ctx)
        // ...
        .build();
```

---

## 18. Exception hierarchy

All exceptions are subclasses of `WebServiceException`.

### HTTP error responses

| Exception                      | Status                  |
|--------------------------------|-------------------------|
| `BadRequestException`          | 400                     |
| `UnauthorizedException`        | 401                     |
| `ForbiddenException`           | 403                     |
| `NotFoundException`            | 404                     |
| `MethodNotAllowedException`    | 405                     |
| `ConflictException`            | 409                     |
| `GoneException`                | 410                     |
| `UnprocessableEntityException` | 422                     |
| `TooManyRequestsException`     | 429                     |
| `InternalServerErrorException` | 500                     |
| `ServiceUnavailableException`  | 503                     |
| `HttpResponseException`        | any other `4xx` / `5xx` |

All HTTP error exceptions extend `HttpResponseException` which provides:
- `statusCode()` — the HTTP status code
- `body()` — `Optional<String>` response body (empty if the error response had no body)
- `headers()` — the response headers

### Transport failures

| Exception                 | Cause                                     |
|---------------------------|-------------------------------------------|
| `ConnectException`        | TCP connection refused                    |
| `ConnectTimeoutException` | TCP connection timed out                  |
| `ReadTimeoutException`    | No response received within `readTimeout` |
| `TransportException`      | Any other low-level I/O failure           |

### Other

| Exception                             | Cause                                                                          |
|---------------------------------------|--------------------------------------------------------------------------------|
| `UnsupportedContentEncodingException` | Server responded with a `Content-Encoding` that has no registered decompressor |
| `ResponseDeserializationException`    | The response body could not be deserialized to the requested type              |
| `UriSyntaxException`                  | A path parameter name did not match any placeholder in the path template       |

---

## 19. Complete examples

### Typed GET with error handling

```java
try {
    User user = client.get()
            .path("/users/{id}", "id", userId)
            .send(User.class)
            .body();
} catch (NotFoundException ex) {
    // 404 — user does not exist
} catch (UnauthorizedException ex) {
    // 401 — token expired or invalid
} catch (ReadTimeoutException ex) {
    // Service too slow
}
```

### POST with compression and OAuth 2.0

```java
ClientCredentialsSecurityProvider oauth2 =
        ClientCredentialsSecurityProvider.builder()
                .tokenEndpoint(URI.create("https://auth.example.com/oauth2/token"))
                .credentials(ClientCredentials.of(clientId, clientSecret))
                .serializer(JacksonSerializer.builder().build())
                .scope("ingest:readings")
                .build();

Client client = Client.builder()
        .configuration(c -> c
                .uri(URI.create("https://api.example.com"))
                .connectTimeout(Duration.ofSeconds(5))
                .readTimeout(Duration.ofSeconds(30))
                .serializer(JacksonSerializer.builder().build())
                .decompress())
        .security(oauth2)
        .build();

IngestResponse result = client.post()
        .path("/readings/ingest")
        .compress()
        .body(deviceReadingsBatch)
        .send(IngestResponse.class)
        .body();
```

### Multipart upload

```java
HttpResponse<Document> response = client.post()
        .path("/documents")
        .body(FormData.of(
                FormPart.entity("metadata",
                        "{\"title\":\"Q3 Report\"}",
                        MediaType.of("application/xml")),
                FormPart.file("file",
                        Files.newInputStream(reportPath),
                        "q3-report.pdf",
                        MediaType.of("application/pdf"))
        ))
        .send(Document.class);
```

### Async parallel fetch

```java
List<String> ids = List.of("order-1", "order-2", "order-3");

List<CompletableFuture<HttpResponse<Order>>> futures = ids.stream()
        .map(id -> client.get()
                .path("/orders/{id}", "id", id)
                .sendAsync(Order.class))
        .toList();

List<Order> orders = futures.stream()
        .map(f -> f.join().body())
        .toList();
```

### Client with full configuration

```java
Client client = Client.builder()
        .configuration(
                Configuration.builder()
                        .uri(URI.create("https://api.example.com"))
                        .connectTimeout(Duration.ofSeconds(5))
                        .readTimeout(Duration.ofSeconds(30))
                        .serializer(JacksonSerializer.builder().build())
                        .decompress()                          // Accept-Encoding: gzip
                        .redirectPolicy(HttpClient.Redirect.NORMAL)
                        .httpVersion(HttpClient.Version.HTTP_1_1)
                        .logging(ClientLoggingConfiguration.builder()
                                .redactHeaders("x-api-key")
                                .redactFields("password", "token")
                                .maxBodySize(8192)
                                .build())
                        .build()
        )
        .security(
                ClientCredentialsSecurityProvider.builder()
                        .tokenEndpoint(URI.create("https://auth.example.com/token"))
                        .credentials(ClientCredentials.of(clientId, clientSecret))
                        .serializer(JacksonSerializer.builder().build())
                        .build()
        )
        .eventListener(new MyMetricsListener())
        .build();
```




