package software.frisby.web.server.security.basic;

import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotAuthorizedException;
import software.frisby.web.server.AuthenticatedIdentity;

/**
 * Validates a username and password, returning an {@link AuthenticatedIdentity} that
 * carries the authenticated {@link java.security.Principal} and any assigned roles.
 * <p>
 * Implement this interface to supply your application's credential-validation logic.
 * The library handles all HTTP mechanics -- decoding the {@code Authorization: Basic}
 * header, splitting username and password -- and passes only the parsed credentials here.
 * <p>
 * The password is delivered as a {@code char[]} rather than a {@code String} so callers
 * can zero the array after use:
 *
 * <pre>{@code
 * (username, password) -> {
 *     try {
 *         User user = userService.authenticate(username, password);
 *         return AuthenticatedIdentity.of(user, Set.of(user.role().name()));
 *     } finally {
 *         Arrays.fill(password, '\0');  // optional but recommended
 *     }
 * }
 * }</pre>
 *
 * <h2>No roles</h2>
 * <pre>{@code
 * (username, password) ->
 *     AuthenticatedIdentity.of(userService.authenticate(username, password))
 * }</pre>
 *
 * <h2>Throwing on authentication failure</h2>
 * <p>
 * Throw {@link NotAuthorizedException} (mapped to {@code 401}) when the credentials are
 * wrong or the account is locked.  Throw {@link ForbiddenException} (mapped to
 * {@code 403}) when the caller is authenticated but not allowed to access this resource.
 * Both exceptions propagate unchanged through the library's authentication filter.
 *
 * @see BasicAuthAuthenticationProvider
 * @see AuthenticatedIdentity
 */
@FunctionalInterface
public interface CredentialsValidator {
    /**
     * Validates the supplied credentials and returns the authenticated identity.
     *
     * @param username The username extracted from the {@code Authorization: Basic} header;
     *                 never {@code null} or empty.
     * @param password The password extracted from the {@code Authorization: Basic} header,
     *                 as a {@code char[]} so callers may zero it after use; never
     *                 {@code null}.
     * @return The authenticated identity carrying the principal and any assigned roles;
     * never {@code null}.
     * @throws NotAuthorizedException if the credentials are invalid or the account is
     *                                not found.
     * @throws ForbiddenException     if the caller is authenticated but not permitted to
     *                                access this resource.
     */
    AuthenticatedIdentity validate(String username, char[] password);
}
