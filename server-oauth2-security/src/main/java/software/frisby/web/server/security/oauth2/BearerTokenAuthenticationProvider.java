package software.frisby.web.server.security.oauth2;

import software.frisby.core.validation.Values;
import software.frisby.web.server.AuthenticationProvider;

/**
 * An {@link AuthenticationProvider} that authenticates requests using the
 * {@code Authorization: Bearer} scheme (RFC 6750).
 * <p>
 * The library handles all header-parsing mechanics; callers supply only the
 * token-validation logic via a {@link BearerTokenValidator}.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * Server.builder()
 *     .authentication(
 *         BearerTokenAuthenticationProvider.of(token ->
 *             jwtService.validate(token)   // returns Principal or throws 401
 *         )
 *     )
 *     ...
 * }</pre>
 *
 * @see BearerTokenValidator
 * @see software.frisby.web.server.ServerBuilder#authentication(AuthenticationProvider...)
 */
public interface BearerTokenAuthenticationProvider extends AuthenticationProvider {
    /**
     * Creates a {@link BearerTokenAuthenticationProvider} backed by the given
     * {@link BearerTokenValidator}.
     *
     * @param validator The validator that performs token verification; must not be
     *                  {@code null}.
     * @return A new {@link BearerTokenAuthenticationProvider}; never {@code null}.
     * @throws software.frisby.core.validation.NullValueException if {@code validator} is
     *                                                            {@code null}.
     */
    static BearerTokenAuthenticationProvider of(BearerTokenValidator validator) {
        Values.notNull("validator", validator);

        return new DefaultBearerTokenAuthenticationProvider(validator);
    }
}

