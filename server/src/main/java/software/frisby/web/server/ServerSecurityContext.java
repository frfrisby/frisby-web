package software.frisby.web.server;

import jakarta.ws.rs.core.SecurityContext;

import java.security.Principal;
import java.util.Set;

/**
 * Factory for creating {@link SecurityContext} instances for use in custom
 * {@link AuthenticationProvider} implementations.
 * <p>
 * Eliminates the boilerplate of implementing {@link SecurityContext} directly.
 * Three overloads are provided, from the simplest common case to the fully-specified form:
 *
 * <pre>{@code
 * // Simplest — no roles; isSecure and scheme derived automatically.
 * return ServerSecurityContext.of(principal);
 *
 * // With roles — enables @RolesAllowed on resource methods.
 * return ServerSecurityContext.of(principal, Set.of("ADMIN", "USER"));
 *
 * // Fully specified — custom secure flag and authentication scheme string.
 * return ServerSecurityContext.of(principal, Set.of("ADMIN"), isSecure, "BEARER");
 * }</pre>
 *
 * <h2>Role-based access control</h2>
 * <p>
 * When roles are supplied, {@link SecurityContext#isUserInRole(String)} performs a flat
 * {@code Set.contains(role)} check.  Hierarchical role logic (e.g. {@code ADMIN} implies
 * {@code USER}) is domain-specific and requires a custom {@link SecurityContext} implementation.
 * <p>
 * To activate {@code @RolesAllowed} enforcement, register Jersey's
 * {@code RolesAllowedDynamicFeature} as a component on the server:
 *
 * <pre>{@code
 * Server.builder()
 *     .components(RolesAllowedDynamicFeature.class)
 *     ...
 * }</pre>
 *
 * @see AuthenticationProvider
 */
public final class ServerSecurityContext {
    private ServerSecurityContext() {
    }

    /**
     * Creates a {@link SecurityContext} for {@code principal} with no roles.
     * <p>
     * {@code isSecure()} returns {@code false}; {@code getAuthenticationScheme()} returns
     * {@code null}.  Use {@link #of(Principal, Set, boolean, String)} when either matters.
     *
     * @param principal The authenticated principal; must not be {@code null}.
     * @return A new {@link SecurityContext}; never {@code null}.
     * @throws software.frisby.core.validation.NullValueException if {@code principal} is {@code null}.
     */
    public static SecurityContext of(Principal principal) {
        return of(principal, Set.of(), false, null);
    }

    /**
     * Creates a {@link SecurityContext} for {@code principal} with the given {@code roles}.
     * <p>
     * {@code isSecure()} returns {@code false}; {@code getAuthenticationScheme()} returns
     * {@code null}.  Use {@link #of(Principal, Set, boolean, String)} when either matters.
     *
     * @param principal The authenticated principal; must not be {@code null}.
     * @param roles     The set of role names the principal holds; may be empty, must not be
     *                  {@code null}.  Passed to {@link SecurityContext#isUserInRole(String)} via
     *                  a flat {@code Set.contains(role)} check.
     * @return A new {@link SecurityContext}; never {@code null}.
     * @throws software.frisby.core.validation.NullValueException if {@code principal} or
     *                                                            {@code roles} is {@code null}.
     */
    public static SecurityContext of(Principal principal, Set<String> roles) {
        return of(principal, roles, false, null);
    }

    /**
     * Creates a fully-specified {@link SecurityContext}.
     *
     * @param principal The authenticated principal; must not be {@code null}.
     * @param roles     The set of role names the principal holds; may be empty, must not be
     *                  {@code null}.
     * @param secure    Whether the request arrived over a secure channel (e.g. HTTPS).
     * @param scheme    The authentication scheme string to return from
     *                  {@link SecurityContext#getAuthenticationScheme()}; may be {@code null}.
     *                  Common values: {@link SecurityContext#BASIC_AUTH},
     *                  {@code "BEARER"}, {@link SecurityContext#DIGEST_AUTH},
     *                  {@link SecurityContext#FORM_AUTH}.
     * @return A new {@link SecurityContext}; never {@code null}.
     * @throws software.frisby.core.validation.NullValueException if {@code principal} or
     *                                                            {@code roles} is {@code null}.
     */
    public static SecurityContext of(Principal principal, Set<String> roles, boolean secure, String scheme) {
        software.frisby.core.validation.Values.notNull("principal", principal);
        software.frisby.core.validation.Values.notNull("roles", roles);

        Set<String> immutableRoles = Set.copyOf(roles);

        return new SecurityContext() {
            @Override
            public Principal getUserPrincipal() {
                return principal;
            }

            @Override
            public boolean isUserInRole(String role) {
                return immutableRoles.contains(role);
            }

            @Override
            public boolean isSecure() {
                return secure;
            }

            @Override
            public String getAuthenticationScheme() {
                return scheme;
            }
        };
    }
}


