package software.frisby.web.server.security.basic;

import software.frisby.core.validation.Values;
import software.frisby.web.server.AuthenticationProvider;

/**
 * An {@link AuthenticationProvider} that authenticates requests using the
 * {@code Authorization: Basic} scheme (RFC 7617).
 * <p>
 * The library handles all header-parsing mechanics; callers supply only the
 * credential-validation logic via a {@link CredentialsValidator}.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * Server.builder()
 *     .authentication(
 *         BasicAuthAuthenticationProvider.of((username, password) ->
 *             userService.authenticate(username, password)  // returns Principal or throws 401
 *         )
 *     )
 *     ...
 * }</pre>
 *
 * @see CredentialsValidator
 * @see software.frisby.web.server.ServerBuilder#authentication(AuthenticationProvider...)
 */
public interface BasicAuthAuthenticationProvider extends AuthenticationProvider {

    /**
     * Creates a {@link BasicAuthAuthenticationProvider} backed by the given
     * {@link CredentialsValidator}.
     *
     * @param validator The validator that performs credential verification; must not be
     *                  {@code null}.
     * @return A new {@link BasicAuthAuthenticationProvider}; never {@code null}.
     * @throws software.frisby.core.validation.NullValueException if {@code validator} is
     *                                                            {@code null}.
     */
    static BasicAuthAuthenticationProvider of(CredentialsValidator validator) {
        Values.notNull("validator", validator);

        return new DefaultBasicAuthAuthenticationProvider(validator);
    }
}

