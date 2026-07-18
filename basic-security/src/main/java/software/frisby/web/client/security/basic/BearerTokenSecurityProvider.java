package software.frisby.web.client.security.basic;

import software.frisby.web.client.security.SecurityProvider;

/**
 * A {@link SecurityProvider} that authenticates requests by setting an
 * {@code Authorization: Bearer <token>} header.
 * <p>
 * Use this provider when the caller manages the token lifecycle externally —
 * for example, when tokens are issued by an upstream gateway, obtained via the
 * OAuth 2.0 Authorization Code Flow in a separate process, or rotated by an
 * external secret manager.
 * <p>
 * For automated client-credentials token acquisition and refresh, use
 * {@code ClientCredentialsSecurityProvider} (from the {@code oauth2-security} module)
 * instead.
 * <p>
 * Obtain an instance via {@link #builder()}:
 *
 * <pre>{@code
 * // Static token
 * BearerTokenSecurityProvider security = BearerTokenSecurityProvider.builder()
 *         .token(myStaticToken)
 *         .build();
 *
 * // Dynamic token — supplier is called on every request
 * BearerTokenSecurityProvider security = BearerTokenSecurityProvider.builder()
 *         .token(() -> tokenStore.currentToken())
 *         .build();
 * }</pre>
 *
 * @see BearerTokenSecurityProviderBuilder
 */
public interface BearerTokenSecurityProvider extends SecurityProvider {
    /**
     * Returns a new builder for creating a {@link BearerTokenSecurityProvider} instance.
     *
     * @return A new {@link BearerTokenSecurityProviderBuilder}.
     */
    static BearerTokenSecurityProviderBuilder builder() {
        return new DefaultBearerTokenSecurityProviderBuilder();
    }
}

