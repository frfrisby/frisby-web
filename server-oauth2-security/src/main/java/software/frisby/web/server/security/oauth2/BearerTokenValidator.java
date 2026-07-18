package software.frisby.web.server.security.oauth2;

import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotAuthorizedException;
import software.frisby.web.server.AuthenticatedIdentity;

/**
 * Validates a Bearer token, returning an {@link AuthenticatedIdentity} that carries
 * the authenticated {@link java.security.Principal} and any assigned roles.
 * <p>
 * The library handles all HTTP mechanics; callers supply only the token-validation
 * logic via a lambda or named class.
 *
 * @see BearerTokenAuthenticationProvider
 * @see AuthenticatedIdentity
 */
@FunctionalInterface
public interface BearerTokenValidator {
    /**
     * Validates the supplied Bearer token and returns the authenticated identity.
     *
     * @param token The raw token string; never {@code null} or empty.
     * @return The authenticated identity; never {@code null}.
     * @throws NotAuthorizedException if the token is invalid, expired, or not found.
     * @throws ForbiddenException     if the caller is not permitted to access this resource.
     */
    AuthenticatedIdentity validate(String token);
}
