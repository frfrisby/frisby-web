package software.frisby.web.server;

import software.frisby.core.validation.Values;

import java.security.Principal;
import java.util.Set;

/**
 * The result of a successful authentication — carries the authenticated
 * {@link Principal} and an optional set of role names.
 * <p>
 * The library converts this record into a {@link jakarta.ws.rs.core.SecurityContext}
 * automatically.  Roles are propagated to
 * {@link jakarta.ws.rs.core.SecurityContext#isUserInRole(String)} via a flat
 * {@link Set#contains(Object)} check, which enables {@code @RolesAllowed} enforcement
 * when Jersey's {@code RolesAllowedDynamicFeature} is registered.
 *
 * <h2>Usage — no roles</h2>
 * <pre>{@code
 * BasicAuthAuthenticationProvider.of((username, password) ->
 *     AuthenticatedIdentity.of(userService.authenticate(username, password))
 * )
 * }</pre>
 *
 * <h2>Usage — with roles</h2>
 * <pre>{@code
 * BasicAuthAuthenticationProvider.of((username, password) -> {
 *     User user = userService.authenticate(username, password);
 *     return AuthenticatedIdentity.of(user, Set.of(user.role().name()));
 * })
 * }</pre>
 *
 * @param principal The authenticated principal; must not be {@code null}.
 * @param roles     The role names assigned to this principal; may be empty, must not
 *                  be {@code null}.
 * @see AuthenticationProvider
 * @see ServerSecurityContext
 */
public record AuthenticatedIdentity(Principal principal, Set<String> roles) {
    /**
     * Compact constructor — validates that neither {@code principal} nor {@code roles}
     * is {@code null}, and defensively copies {@code roles} to an unmodifiable set.
     *
     * @throws software.frisby.core.validation.NullValueException if {@code principal}
     *                                                            or {@code roles} is
     *                                                            {@code null}.
     */
    public AuthenticatedIdentity {
        Values.notNull("principal", principal);
        Values.notNull("roles", roles);

        roles = Set.copyOf(roles);
    }

    /**
     * Creates an {@link AuthenticatedIdentity} for {@code principal} with no roles.
     *
     * @param principal The authenticated principal; must not be {@code null}.
     * @return A new {@link AuthenticatedIdentity}; never {@code null}.
     * @throws software.frisby.core.validation.NullValueException if {@code principal}
     *                                                            is {@code null}.
     */
    public static AuthenticatedIdentity of(Principal principal) {
        return new AuthenticatedIdentity(principal, Set.of());
    }

    /**
     * Creates an {@link AuthenticatedIdentity} for {@code principal} with the given
     * {@code roles}.
     *
     * @param principal The authenticated principal; must not be {@code null}.
     * @param roles     The role names assigned to this principal; may be empty, must not
     *                  be {@code null}.
     * @return A new {@link AuthenticatedIdentity}; never {@code null}.
     * @throws software.frisby.core.validation.NullValueException if {@code principal}
     *                                                            or {@code roles} is
     *                                                            {@code null}.
     */
    public static AuthenticatedIdentity of(Principal principal, Set<String> roles) {
        return new AuthenticatedIdentity(principal, roles);
    }
}

