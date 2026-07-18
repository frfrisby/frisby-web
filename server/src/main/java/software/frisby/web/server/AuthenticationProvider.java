package software.frisby.web.server;

import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.container.ContainerRequestContext;

/**
 * Pluggable server-side authentication provider.
 * <p>
 * Implementations are registered on {@link ServerBuilder#authentication(AuthenticationProvider...)} and
 * are invoked in insertion order by the internal {@code SecurityRequestFilter}.
 * The filter uses a <em>first-accepts-wins</em> strategy: the first provider whose
 * {@link #accepts} method returns {@code true} is asked to {@link #authenticate} the
 * request.  If no provider accepts the request a {@code 401 Unauthorized} response is
 * returned immediately.
 * <p>
 * The {@link #accepts} method is designed to be cheap — it should inspect only the
 * presence or scheme of the {@code Authorization} header (or whichever signal your
 * scheme uses) without performing any credential work.  Full credential validation
 * happens only in {@link #authenticate}, and only for the matching provider.
 *
 * <h2>Usage example — multi-scheme setup</h2>
 * <pre>{@code
 * Server.builder()
 *     .configuration(c -> c.port(8080).serializer(serializer))
 *     .resources(new OrderResource(orderService))
 *     .healthCheck()
 *     .authentication(
 *         BasicAuthAuthenticationProvider.of((username, password) ->
 *             userService.authenticate(username, password)
 *         ),
 *         BearerTokenAuthenticationProvider.of(token ->
 *             jwtService.validate(token)
 *         )
 *     )
 *     .build();
 * }</pre>
 *
 * <h2>Custom scheme example — AWS ALB OIDC header</h2>
 * <pre>{@code
 * public final class AlbOidcAuthenticationProvider implements AuthenticationProvider {
 *
 *     @Override
 *     public boolean accepts(ContainerRequestContext ctx) {
 *         return null != ctx.getHeaderString("x-amzn-oidc-data");
 *     }
 *
 *     @Override
 *     public SecurityContext authenticate(ContainerRequestContext ctx) {
 *         // ... validate JWT, load user ...
 *         boolean isSecure = "https".equalsIgnoreCase(
 *                 ctx.getUriInfo().getRequestUri().getScheme());
 *         return ServerSecurityContext.of(principal, Set.of(user.role().name()), isSecure, "BEARER");
 *     }
 * }
 * }</pre>
 *
 * <h2>Role-based access control</h2>
 * <p>
 * To use {@code @RolesAllowed} on resource methods, register Jersey's
 * {@code RolesAllowedDynamicFeature} as a component and return a
 * {@link ServerSecurityContext} with a roles set from {@link #authenticate}:
 * <pre>{@code
 * .components(RolesAllowedDynamicFeature.class)
 * }</pre>
 * Then in your validator:
 * <pre>{@code
 * return ServerSecurityContext.of(principal, Set.of("ADMIN", "USER"));
 * }</pre>
 *
 * @see ServerBuilder#authentication(AuthenticationProvider...)
 * @see ServerSecurityContext
 */
public interface AuthenticationProvider {
    /**
     * Returns {@code true} if this provider is capable of authenticating the given
     * request.
     * <p>
     * This method is invoked before {@link #authenticate} and should be fast.  A typical
     * implementation checks only the presence or prefix of the {@code Authorization} header
     * (e.g. {@code "Basic "}, {@code "Bearer "}) without decoding any credentials.
     * <p>
     * The method must not throw under any circumstances — a thrown exception here would
     * prevent subsequent providers in the chain from running.
     *
     * @param context The incoming request context; never {@code null}.
     * @return {@code true} if this provider should handle authentication for this request.
     */
    boolean accepts(ContainerRequestContext context);

    /**
     * Authenticates the request and returns a {@link SecurityContext} that reflects the
     * authenticated identity.
     * <p>
     * This method is only called when {@link #accepts} returned {@code true} for the same
     * request.  It should perform all credential validation and produce a {@link SecurityContext}
     * wrapping the authenticated {@link java.security.Principal}.
     * <p>
     * Use {@link ServerSecurityContext} to create a {@link SecurityContext} without boilerplate:
     * <pre>{@code
     * return ServerSecurityContext.of(principal);                         // no roles
     * return ServerSecurityContext.of(principal, Set.of("ADMIN"));        // with roles
     * }</pre>
     *
     * @param context The incoming request context; never {@code null}.
     * @return A {@link SecurityContext} for the authenticated request; never {@code null}.
     * @throws jakarta.ws.rs.NotAuthorizedException if authentication fails (wrong credentials,
     *                                              expired token, etc.).
     * @throws jakarta.ws.rs.ForbiddenException     if the caller is authenticated but not
     *                                              permitted to access this resource.
     */
    SecurityContext authenticate(ContainerRequestContext context);
}

