# Client Security Providers — `software.frisby.web`

This document describes all client-side authentication providers.
Attach this document when writing code that sends authenticated HTTP requests.

See `client.md` for general client usage.

---

## Overview

Authentication is handled by a `SecurityProvider` registered on the `ClientBuilder` or
on an individual request spec.  The provider adds the appropriate credentials to every
outbound request automatically — callers do not set `Authorization` headers by hand.

```java
// Default provider — applied to every request
Client client = Client.builder()
        .configuration(config)
        .security(mySecurityProvider)
        .build();

// Per-request override — overrides the client-level default
client.get()
        .path("/admin/users")
        .security(adminSecurityProvider)
        .send(List.class);
```

---

## `SecurityProvider` interface (`software.frisby.web.client.security`)

```java
public interface SecurityProvider {
    void secure(RequestContext request);
}
```

All built-in providers implement this interface.  Custom implementations follow the same
contract — call `request.addHeader(...)` or `request.addBearerToken(...)` inside
`secure()` to attach credentials.

---

## HTTP Basic Authentication — `BasicSecurityProvider`

**Module:** `basic-security`

```xml
<dependency>
    <groupId>software.frisby.web</groupId>
    <artifactId>basic-security</artifactId>
</dependency>
```

On every request, encodes `Base64(username:password)` and sets
`Authorization: Basic <token>`.

```java
// Convenience overload — username + password strings
BasicSecurityProvider security = BasicSecurityProvider.builder()
        .credentials("alice", "s3cr3t")
        .build();

// Explicit Credentials value object
BasicSecurityProvider security = BasicSecurityProvider.builder()
        .credentials(Credentials.of("alice", "s3cr3t"))
        .build();

Client client = Client.builder()
        .configuration(config)
        .security(security)
        .build();
```

### `BasicSecurityProviderBuilder`

| Method | Required | Notes |
|---|---|---|
| `credentials(Credentials)` | ✅ (one of the two) | Pre-constructed value object |
| `credentials(String username, String password)` | ✅ (one of the two) | Convenience overload |
| `build()` | — | Throws `IllegalStateException` if no credentials provided |

### `Credentials` value object

```java
Credentials.of("alice", "s3cr3t")
```

Both username and password must not be blank.

---

## Static / Dynamic Bearer Token — `BearerTokenSecurityProvider`

**Module:** `basic-security`

Sets `Authorization: Bearer <token>` on every request.

Use this provider when the token lifecycle is managed externally — for example, tokens
issued by an upstream gateway, obtained via OAuth 2 Authorization Code Flow in a separate
process, or rotated by an external secret manager.

For **automated client-credentials token acquisition and refresh**, use
`ClientCredentialsSecurityProvider` instead.

```java
// Static token — same value on every request
BearerTokenSecurityProvider security = BearerTokenSecurityProvider.builder()
        .token(myStaticJwt)
        .build();

// Dynamic token — supplier is called on every request
BearerTokenSecurityProvider security = BearerTokenSecurityProvider.builder()
        .token(() -> tokenCache.get())
        .build();
```

### `BearerTokenSecurityProviderBuilder`

| Method | Required | Notes |
|---|---|---|
| `token(String bearerToken)` | ✅ (one of the two) | Static token; convenience overload for `token(() -> bearerToken)` |
| `token(Supplier<String> tokenSupplier)` | ✅ (one of the two) | Evaluated on every request.  Must be thread-safe. |
| `build()` | — | Throws `IllegalStateException` if no token or supplier provided |

> The supplier is called directly on the request thread with no additional
> synchronization.  The implementation must handle concurrent access if it accesses
> shared state.

---

## OAuth 2.0 Client Credentials — `ClientCredentialsSecurityProvider`

**Module:** `oauth2-security`

```xml
<dependency>
    <groupId>software.frisby.web</groupId>
    <artifactId>oauth2-security</artifactId>
</dependency>
```

Implements the OAuth 2.0 client-credentials grant type (RFC 6749).  On every request,
ensures a valid bearer token is available — fetching a new one from the token endpoint
when none exists, or when the current token is within the expiry buffer of expiry.  Sets
`Authorization: Bearer <token>` automatically.

Token acquisition and refresh are thread-safe; concurrent requests never trigger
duplicate token fetches.

```java
// Minimal — no scope, default timeouts
ClientCredentialsSecurityProvider security = ClientCredentialsSecurityProvider.builder()
        .tokenEndpoint(URI.create("https://auth.example.com/oauth/token"))
        .credentials(ClientCredentials.of("my-client-id", "my-client-secret"))
        .serializer(myJsonSerializer)
        .build();

// Fully configured
ClientCredentialsSecurityProvider security = ClientCredentialsSecurityProvider.builder()
        .tokenEndpoint(URI.create("https://auth.example.com/oauth/token"))
        .credentials("my-client-id", "my-client-secret")
        .serializer(myJsonSerializer)
        .scope("read", "write")
        .connectTimeout(Duration.ofSeconds(5))
        .requestTimeout(Duration.ofSeconds(15))
        .sslContext(myCustomSslContext)
        .basicAuth()             // client_secret_basic instead of client_secret_post
        .expiryBuffer(Duration.ofSeconds(60))
        .eventListener(myTokenEventListener)
        .build();
```

### `ClientCredentialsSecurityProviderBuilder`

| Method | Required | Default | Notes |
|---|---|---|---|
| `tokenEndpoint(URI)` | ✅ | — | Fully qualified URI of the token endpoint |
| `credentials(ClientCredentials)` | ✅ (one of two) | — | Pre-constructed value object |
| `credentials(String clientId, String clientSecret)` | ✅ (one of two) | — | Convenience overload |
| `serializer(JsonSerializer)` | ✅ | — | Used to deserialize the token endpoint response |
| `scope(String... scopes)` | — | omitted | Space-joined and sent as `scope` parameter per RFC 6749 |
| `connectTimeout(Duration)` | — | 10 seconds | Max time to establish TCP connection to token endpoint |
| `requestTimeout(Duration)` | — | 30 seconds | Max time to wait for token endpoint response |
| `sslContext(SSLContext)` | — | JDK default | Custom TLS for token endpoint |
| `basicAuth()` | — | `client_secret_post` | Switches to `client_secret_basic` (RFC 6749 §2.3.1) — credentials in `Authorization` header rather than request body |
| `expiryBuffer(Duration)` | — | 30 seconds | Proactive refresh window: a token expiring in N seconds is treated as expired after N − buffer seconds |
| `eventListener(TokenEventListener)` | — | no-op | Receives notification after every token fetch attempt |
| `build()` | — | — | Throws `IllegalStateException` if `tokenEndpoint`, `credentials`, or `serializer` not provided |

### `ClientCredentials` value object

```java
ClientCredentials credentials = ClientCredentials.of("my-client-id", "my-client-secret");
// clientId()     — the client identifier
// clientSecret() — the client secret (redacted in toString())
```

Both `clientId` and `clientSecret` must not be blank.

### `TokenEventListener`

Notified after every token fetch attempt from the token endpoint.

```java
public interface TokenEventListener {
    void onTokenFetched(...);    // called on successful token acquisition
    void onTokenFetchFailed(...); // called when the token endpoint returns an error
}
```

Use `eventListener(listener)` on the builder to register.

---

## Credential security notes

- **`BasicSecurityProvider`** — transmits credentials on every request.  Use only over
  HTTPS.
- **`BearerTokenSecurityProvider`** — the static overload holds the token in memory for
  the life of the provider; rotate by constructing a new provider.  The supplier overload
  can fetch from a secret manager on demand.
- **`ClientCredentialsSecurityProvider`** — client secret is held in memory; use an
  `SSLContext` pointing at the correct trust store when calling private token endpoints.
  The `toString()` of `ClientCredentials` redacts the secret to prevent accidental log exposure.

