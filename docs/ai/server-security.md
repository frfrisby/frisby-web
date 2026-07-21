# Server Security — `software.frisby.web`

This document describes the server-side authentication API.
Attach this document when writing code that protects JAX-RS resources with
HTTP Basic Auth or Bearer Token (JWT) authentication.

See `server.md` for general server usage.

---

## How authentication works

Authentication providers are registered on `ServerBuilder.authentication(...)` and form
a **first-accepts-wins** chain.  For every incoming request (except the health check
endpoint):

1. Providers are evaluated in insertion order.
2. The first provider whose `accepts(ContainerRequestContext)` returns `true` calls
   `authenticate(ContainerRequestContext)`.
3. `authenticate` returns a `SecurityContext` for the request, or throws
   `NotAuthorizedException` (→ 401) / `ForbiddenException` (→ 403).
4. If no provider accepts the request, the server returns `401 Unauthorized` immediately.

The `accepts` method is designed to be fast — it should only inspect the presence or
prefix of the `Authorization` header, never perform credential work.

---

## HTTP Basic Authentication — `BasicAuthAuthenticationProvider`

**Module:** `server-basic-security`

```xml
<dependency>
    <groupId>software.frisby.web</groupId>
    <artifactId>server-basic-security</artifactId>
</dependency>
```

The library decodes the `Authorization: Basic` header and extracts the username and
password.  The caller supplies only the credential-validation logic via a
`CredentialsValidator` lambda.

```java
Server.builder()
        .authentication(
                BasicAuthAuthenticationProvider.of((username, password) ->
                        userService.authenticate(username, password)
                )
        )
        ...
```

### `CredentialsValidator` — functional interface

```java
AuthenticatedIdentity validate(String username, char[] password)
```

- `username` — extracted username; never null or empty.
- `password` — extracted password as `char[]` so callers can zero it after use.
- Returns `AuthenticatedIdentity` on success.
- Throws `NotAuthorizedException` (→ 401) on bad credentials or locked account.
- Throws `ForbiddenException` (→ 403) on authenticated but not permitted.

**With roles (enables `@RolesAllowed`):**
```java
BasicAuthAuthenticationProvider.of((username, password) -> {
    try {
        User user = userService.authenticate(username, password);
        return AuthenticatedIdentity.of(user, Set.of(user.role().name()));
    } finally {
        Arrays.fill(password, '\0');  // optional but recommended
    }
})
```

**Without roles:**
```java
BasicAuthAuthenticationProvider.of((username, password) ->
        AuthenticatedIdentity.of(userService.authenticate(username, password))
)
```

---

## Bearer Token Authentication — `BearerTokenAuthenticationProvider`

**Module:** `server-oauth2-security`

```xml
<dependency>
    <groupId>software.frisby.web</groupId>
    <artifactId>server-oauth2-security</artifactId>
</dependency>
```

The library extracts the token from the `Authorization: Bearer` header.  The caller
supplies only the token-validation logic via a `BearerTokenValidator` lambda.

```java
Server.builder()
        .authentication(
                BearerTokenAuthenticationProvider.of(token ->
                        jwtService.validate(token)
                )
        )
        ...
```

### `BearerTokenValidator` — functional interface

```java
AuthenticatedIdentity validate(String token)
```

- `token` — raw token string; never null or empty.
- Returns `AuthenticatedIdentity` on success.
- Throws `NotAuthorizedException` (→ 401) on invalid/expired token.
- Throws `ForbiddenException` (→ 403) on authenticated but not permitted.

**With roles:**
```java
BearerTokenAuthenticationProvider.of(token -> {
    Claims claims = jwtService.validate(token);
    return AuthenticatedIdentity.of(
            () -> claims.getSubject(),
            Set.of(claims.get("role", String.class))
    );
})
```

---

## `AuthenticatedIdentity`

The return value from `CredentialsValidator.validate` and `BearerTokenValidator.validate`.
Carries the authenticated `Principal` and an optional set of role names.

```java
AuthenticatedIdentity.of(principal)               // no roles
AuthenticatedIdentity.of(principal, Set.of("ADMIN", "USER"))  // with roles
```

The `Principal` is any `java.security.Principal` implementation — a lambda works:
```java
AuthenticatedIdentity.of(() -> "alice")
AuthenticatedIdentity.of(() -> userId.toString())
```

Fields:
- `principal()` — the `java.security.Principal`; never null.
- `roles()` — unmodifiable `Set<String>`; never null; may be empty.

---

## `ServerSecurityContext`

A factory that creates `jakarta.ws.rs.core.SecurityContext` instances without boilerplate.
Used when implementing a custom `AuthenticationProvider` directly.

```java
// Simplest — no roles; isSecure() = false, getAuthenticationScheme() = null
return ServerSecurityContext.of(principal);

// With roles — enables @RolesAllowed
return ServerSecurityContext.of(principal, Set.of("ADMIN", "USER"));

// Fully specified
return ServerSecurityContext.of(principal, Set.of("ADMIN"), isSecure, "BEARER");
```

`isUserInRole(role)` performs a flat `Set.contains(role)` check.  Hierarchical role
logic is domain-specific and requires a custom `SecurityContext` implementation.

---

## `AuthenticationProvider` interface

Implement this directly for custom authentication schemes (API key, OIDC header,
certificate, etc.).

```java
public interface AuthenticationProvider {
    boolean accepts(ContainerRequestContext context);
    SecurityContext authenticate(ContainerRequestContext context);
}
```

**Custom scheme example — AWS ALB OIDC header:**
```java
public final class AlbOidcAuthenticationProvider implements AuthenticationProvider {

    @Override
    public boolean accepts(ContainerRequestContext ctx) {
        return null != ctx.getHeaderString("x-amzn-oidc-data");
    }

    @Override
    public SecurityContext authenticate(ContainerRequestContext ctx) {
        String jwtData = ctx.getHeaderString("x-amzn-oidc-data");
        Claims claims = oidcService.validate(jwtData);
        boolean isSecure = "https".equalsIgnoreCase(
                ctx.getUriInfo().getRequestUri().getScheme());
        return ServerSecurityContext.of(
                () -> claims.getSubject(),
                Set.of(claims.get("role", String.class)),
                isSecure,
                "BEARER"
        );
    }
}
```

---

## Role-based access control

To use `@RolesAllowed` on resource methods:

1. Register Jersey's `RolesAllowedDynamicFeature` as a server component.
2. Return an `AuthenticatedIdentity` (or `ServerSecurityContext`) that includes the
   caller's roles.

```java
Server.builder()
        .authentication(
                BasicAuthAuthenticationProvider.of((username, password) -> {
                    User user = userService.authenticate(username, password);
                    return AuthenticatedIdentity.of(user, Set.of(user.role().name()));
                })
        )
        .components(RolesAllowedDynamicFeature.class)
        ...
```

Then on the resource:
```java
@Path("/admin")
public class AdminResource {
    @GET
    @RolesAllowed("ADMIN")
    public Response getAdminData() { ... }
}
```

---

## Mixed public and secured endpoints

Authentication is applied globally to all non-health-check requests.  To expose some
endpoints without credentials while protecting others, add an anonymous catch-all
provider as the **last** entry in the chain:

```java
Server.builder()
        .authentication(
                // Protected endpoints — try JWT first
                BearerTokenAuthenticationProvider.of(jwtService::validate),
                // Fallback — anonymous access; endpoint-level control via @RolesAllowed
                new AuthenticationProvider() {
                    @Override
                    public boolean accepts(ContainerRequestContext ctx) {
                        return true;  // accept everything not handled above
                    }
                    @Override
                    public SecurityContext authenticate(ContainerRequestContext ctx) {
                        return ServerSecurityContext.of(() -> "anonymous");
                    }
                }
        )
        .components(RolesAllowedDynamicFeature.class)
        ...
```

Then on resources that must reject anonymous callers:
```java
@GET
@RolesAllowed("USER")  // anonymous principal has no roles → 403
public Response securedMethod() { ... }
```

---

## Multiple schemes on a single server

Providers are tried in order.  The first one to `accept` wins.

```java
Server.builder()
        .authentication(
                BasicAuthAuthenticationProvider.of((u, p) ->
                        userService.authenticate(u, p)
                ),
                BearerTokenAuthenticationProvider.of(token ->
                        jwtService.validate(token)
                )
        )
        ...
```

---

## Security context in resource methods

The authenticated `SecurityContext` is available via standard JAX-RS injection:

```java
@GET
@Path("/profile")
public Response getProfile(@Context SecurityContext securityContext) {
    String username = securityContext.getUserPrincipal().getName();
    boolean isAdmin = securityContext.isUserInRole("ADMIN");
    ...
}
```

