# HTTP Client — `software.frisby.web`

This document describes the complete public API for the `client` module.
Attach this document when writing code that uses `frisby-web` to call remote HTTP services.

---

## Maven coordinates

```xml
<dependency>
    <groupId>software.frisby.web</groupId>
    <artifactId>client</artifactId>
</dependency>
```

---

## Quick start

```java
// 1. Build a serializer (see serialization.md)
JacksonSerializer serializer = JacksonSerializer.builder().build();

// 2. Build a client
Client client = Client.builder()
        .configuration(
                ClientConfiguration.builder()
                        .uri(URI.create("https://api.example.com"))
                        .connectTimeout(Duration.ofSeconds(5))
                        .readTimeout(Duration.ofSeconds(30))
                        .serializer(serializer)
                        .build()
        )
        .build();

// 3. Make requests
User user = client.get()
        .path("/users/{id}", "id", userId)
        .send(User.class)
        .body();
```

---

## `Client`

The entry point.  One instance per target service, reused for the application lifetime.

```java
static ClientBuilder builder()   // factory — returns ClientBuilder
GetSpec    get()
PostSpec   post()
PutSpec    put()
PatchSpec  patch()
DeleteSpec delete()
HeadSpec   head()
ClientConfiguration configuration()
```

---

## `ClientBuilder`

| Method | Required | Notes |
|---|---|---|
| `configuration(ClientConfiguration)` | ✅ | Base URI, timeouts, serializer |
| `configuration(UnaryOperator<ClientConfigurationBuilder>)` | — | Lambda convenience overload |
| `security(SecurityProvider)` | — | Default auth for all requests; per-request override available |
| `eventListener(ClientEventListener)` | — | Metrics / tracing hook; defaults to no-op |
| `build()` | — | Throws `IllegalStateException` if no configuration provided |

---

## `ClientConfiguration` / `ClientConfigurationBuilder`

Obtain via `ClientConfiguration.builder()`.

### Required options

| Method | Description |
|---|---|
| `uri(URI)` | Base URI.  All request paths are resolved against this. |
| `connectTimeout(Duration)` | Max time to establish a TCP connection. |
| `readTimeout(Duration)` | Max time to wait for a response after sending. |
| `serializer(JsonSerializer)` | JSON serializer for request/response bodies. |

### Optional options

| Method | Default | Description |
|---|---|---|
| `sslContext(SSLContext)` | JDK default | Custom TLS — private CA, mTLS. |
| `redirectPolicy(HttpClient.Redirect)` | `NORMAL` | `NORMAL` follows HTTP→HTTP and HTTPS→HTTPS redirects.  `NEVER` returns 3xx directly; `response.body()` will be `null` for 3xx. |
| `httpVersion(HttpClient.Version)` | `HTTP_1_1` | Set to `HTTP_2` for HTTP/2. |
| `decompress()` | — | Registers built-in gzip decompressor; adds `Accept-Encoding: gzip`. |
| `decompress(ContentDecompressor)` | — | Custom decompressor (e.g. brotli).  Calls are additive. |
| `executor(Executor)` | Shared default | Custom thread pool or virtual threads (Java 21+). |
| `logging(ClientLoggingConfiguration)` | See below | Header/field redaction and body size cap for log entries. |

`build()` throws `DuplicateElementsException` if two registered decompressors share the
same `encoding()` token.

---

## Logging configuration — `ClientLoggingConfiguration` / `ClientLoggingConfigurationBuilder`

Controls what appears in client-side log entries.

```java
ClientLoggingConfiguration logging = ClientLoggingConfiguration.builder()
        .maxBodySize(4096)                          // default: 8192 (8 KB); 0 = disable body logging
        .redactHeaders("X-Api-Key", "X-Amzn-Oidc-Data")  // always masked: Authorization, Cookie, Set-Cookie
        .redactFields("password", "token")          // JSON field values replaced with [redacted]
        .build();
```

- `redactHeaders` matching is case-insensitive.
- `redactFields` matching is case-sensitive; only `String`-typed JSON fields are affected.
- Calls to `redactHeaders` and `redactFields` are cumulative.

Pass to `ClientConfigurationBuilder.logging(logging)`.

---

## HTTP verb specs — common methods

All six specs (`GetSpec`, `PostSpec`, `PutSpec`, `PatchSpec`, `DeleteSpec`, `HeadSpec`)
share the following builder methods:

### `path(String path)`
Sets the URI path, resolved against the base URI.  Leading/trailing slashes normalized.

### `path(String path, String parameterId, String parameterValue)`
Single named placeholder substitution:
```java
client.get().path("/users/{id}", "id", userId)
```

### `path(String path, PathParameter... parameters)`
Multiple named placeholder substitutions:
```java
client.get().path(
        "/teams/{teamId}/members/{memberId}",
        PathParameter.of("teamId", teamId),
        PathParameter.of("memberId", memberId)
)
```

### `parameter(String name, String value)`
Appends a query parameter.

### `parameter(String name, String... values)`
Appends a multivalued query parameter — one `name=value` pair per value.

### `header(String name, String value)` / `header(String name, String... values)`
Adds a request header.  The following headers are client-managed and **must not** be set
manually: `Accept`, `Accept-Encoding`, `Content-Type`, `Content-Length`,
`Content-Encoding`, `Transfer-Encoding`.  Attempting to set any of these throws
`IllegalArgumentException`.

### `cookie(HttpCookie cookie)`
Adds a cookie to the request.

### `security(SecurityProvider provider)`
Overrides the default security provider for this individual request.

---

## `GetSpec`

```java
// Typed JSON deserialization
<T> HttpResponse<T>                    send(Class<T> responseType)
<T> HttpResponse<T>                    send(GenericType<T> responseType)

// Binary / streaming download
HttpResponse<InputStream>              download()

// Async variants
<T> CompletableFuture<HttpResponse<T>> sendAsync(Class<T> responseType)
<T> CompletableFuture<HttpResponse<T>> sendAsync(GenericType<T> responseType)
CompletableFuture<HttpResponse<InputStream>> downloadAsync()
```

Deserialization is performed only for `2xx` responses.  With `NEVER` redirect policy,
a `3xx` returns a response with a `null` body — always check `statusCode()` first.

---

## `PostSpec` / `PutSpec`

Both support JSON, multipart, and form-encoded bodies.

### Body methods

```java
PostSpec body(Object body)             // JSON — serialized via configured JsonSerializer
PostSpec body(FormData formData)       // multipart/form-data
PostSpec body(FormUrlEncoded form)     // application/x-www-form-urlencoded
```

### Compression (JSON bodies only)
```java
PostSpec compress()                            // gzip (built-in)
PostSpec compress(ContentCompressor compressor) // custom algorithm
```

Calling `compress()` with a `FormData` or `FormUrlEncoded` body throws
`IllegalStateException` at send time.

### Send methods
```java
<T> HttpResponse<T>                    send(Class<T> responseType)
<T> HttpResponse<T>                    send(GenericType<T> responseType)
HttpResponse<Void>                     send()
<T> CompletableFuture<HttpResponse<T>> sendAsync(Class<T> responseType)
<T> CompletableFuture<HttpResponse<T>> sendAsync(GenericType<T> responseType)
CompletableFuture<HttpResponse<Void>>  sendAsync()
```

---

## `PatchSpec`

Identical to `PostSpec` / `PutSpec` except:
- **No `FormData` body support** — `PATCH` carries a change description; file upload
  belongs on `POST` or `PUT`.
- Supports `body(Object)` and `body(FormUrlEncoded)` only.
- For formal patch formats (RFC 7396 merge-patch, RFC 6902 json-patch), set the
  `Content-Type` header explicitly before calling `body()`:
  ```java
  client.patch()
          .path("/users/{id}", "id", userId)
          .header(Headers.CONTENT_TYPE, "application/merge-patch+json")
          .body(partialUser)
          .send();
  ```

---

## `DeleteSpec`

No body.

```java
HttpResponse<Void>                    send()
CompletableFuture<HttpResponse<Void>> sendAsync()
```

---

## `HeadSpec`

No body; response body is always empty.  Use the response headers.

```java
HttpResponse<Void>                    send()
CompletableFuture<HttpResponse<Void>> sendAsync()
```

Common uses: existence checks, `Content-Length` pre-flight, cache validation via `ETag`
/ `Last-Modified`, lightweight liveness probes.

---

## `FormData` — multipart/form-data

```java
FormData formData = FormData.of(
        FormPart.file("file", inputStream, "report.pdf"),         // binary file part
        FormPart.file("file", inputStream, "report.pdf", size),   // with known size
        FormPart.json("metadata", metadataObject),                // JSON entity part
        FormPart.text("category", "invoices")                     // plain-text scalar
);
```

- `FormData.of(FormPart...)` and `FormData.of(List<FormPart>)` — at least one part required.
- Parts are transmitted in the order they were provided.
- Supported by `PostSpec` and `PutSpec` only.

---

## `FormUrlEncoded` — application/x-www-form-urlencoded

```java
FormUrlEncoded form = FormUrlEncoded.builder()
        .field("grant_type", "client_credentials")
        .field("client_id", clientId)
        .field("client_secret", clientSecret)
        .build();
```

- Field names must not be blank; values may be blank (represents an empty field).
- Insertion order is preserved.
- Duplicate field names overwrite each other.
- `build()` throws if no fields have been added.
- Supported by `PostSpec`, `PutSpec`, and `PatchSpec`.

---

## `PathParameter`

Used when substituting multiple named placeholders in a single call:

```java
PathParameter.of("teamId", teamId)       // id: placeholder name (without braces)
                                          // value: substitution value
```

Both `id` and `value` must not be blank.

---

## `ContentCompressor` — custom request body compression

```java
ContentCompressor brotli = ContentCompressor.of("br", bytes -> myBrotliLib.compress(bytes));
```

- `encoding()` — returns the `Content-Encoding` token (e.g. `"br"`).
- `compress(byte[])` — compresses and returns the bytes.
- `ContentCompressor.of(String encoding, BodyCompressor compressor)` — factory method.
- Use the no-arg `compress()` on the spec for the built-in `gzip` algorithm.

---

## Events — `ClientEventListener`

Register via `ClientBuilder.eventListener(listener)`.  Exactly one callback fires per
request — they are mutually exclusive.

```java
void onRequestCompleted(RequestCompletedEvent event)  // 2xx response; no exception thrown
void onRequestFailed(RequestFailedEvent event)         // any exception thrown (4xx/5xx or transport)
```

### `RequestCompletedEvent` (record)
```
method()      String    — "GET", "POST", etc.
uri()         URI       — full resolved URI including query parameters
statusCode()  int       — HTTP response status code
latency()     Duration  — time from send to response headers fully received
successful()  boolean   — true for 2xx
```

### `RequestFailedEvent` (record)
```
method()     String             — "GET", "POST", etc.
uri()        URI                — full resolved URI
statusCode() Optional<Integer>  — present for 4xx/5xx; empty for transport failures
latency()    Duration           — time from send to failure
cause()      Throwable          — the exception
```

Factory methods:
```java
RequestFailedEvent.transportFailure(method, uri, latency, cause)  // connect/read timeout, SSL
RequestFailedEvent.httpFailure(method, uri, statusCode, latency, cause)  // 4xx/5xx
```

---

## `ResponseStatus`

An enum covering standard HTTP status codes with reason phrases.

```java
ResponseStatus status = ResponseStatus.fromCode(404);   // NOT_FOUND
status.code()           // 404
status.reason()         // "Not Found"
status.isSuccess()      // false (2xx)
status.isClientError()  // true  (4xx)
status.isServerError()  // false (5xx)
```

Unknown codes return `ResponseStatus.UNKNOWN`.

---

## Exception hierarchy

All HTTP error exceptions extend `HttpResponseException`.  The client throws on `4xx` / `5xx`
responses and on transport failures.

| Exception | Trigger |
|---|---|
| `BadRequestException` | 400 |
| `UnauthorizedException` | 401 |
| `ForbiddenException` | 403 |
| `NotFoundException` | 404 |
| `MethodNotAllowedException` | 405 |
| `NotAcceptableException` | 406 |
| `RequestTimeoutException` | 408 |
| `ConflictException` | 409 |
| `GoneException` | 410 |
| `PayloadTooLargeException` | 413 |
| `UnsupportedMediaTypeException` | 415 |
| `UnprocessableEntityException` | 422 |
| `TooManyRequestsException` | 429 |
| `InternalServerErrorException` | 500 |
| `NotImplementedException` | 501 |
| `BadGatewayException` | 502 |
| `ServiceUnavailableException` | 503 |
| `GatewayTimeoutException` | 504 |
| `HttpResponseException` | Any other `4xx` / `5xx` |
| `ConnectException` | TCP connection refused or reset |
| `ConnectTimeoutException` | `connectTimeout` exceeded |
| `ReadTimeoutException` | `readTimeout` exceeded |
| `TooManyRedirectsException` | Redirect loop detected |
| `AbortedException` | Request aborted by the JDK HTTP client |
| `TransportException` | Other transport-layer failure |
| `UriSyntaxException` | Malformed URI or unresolved path parameter |
| `UnsupportedContentEncodingException` | Server returned a `Content-Encoding` with no registered decompressor |
| `ResponseDeserializationException` | Deserialization of a response body failed |

---

## Complete example — production-grade client

```java
JacksonSerializer serializer = JacksonSerializer.builder().build();

Client client = Client.builder()
        .configuration(
                ClientConfiguration.builder()
                        .uri(URI.create("https://api.example.com"))
                        .connectTimeout(Duration.ofSeconds(5))
                        .readTimeout(Duration.ofSeconds(30))
                        .serializer(serializer)
                        .decompress()                             // gzip response decompression
                        .logging(l -> l
                                .maxBodySize(4096)
                                .redactHeaders("X-Api-Key")
                                .redactFields("password", "secret"))
                        .build()
        )
        .eventListener(new ClientEventListener() {
            @Override
            public void onRequestCompleted(RequestCompletedEvent event) {
                // record latency metric
            }
            @Override
            public void onRequestFailed(RequestFailedEvent event) {
                // increment error counter
            }
        })
        .build();
```

