package software.frisby.web.client.security.basic;

import java.util.function.Supplier;

/**
 * A builder for creating a {@link BearerTokenSecurityProvider} instance.
 * <p>
 * Obtain an instance via {@link BearerTokenSecurityProvider#builder()}.
 *
 * <pre>{@code
 * // Static token
 * BearerTokenSecurityProvider security = BearerTokenSecurityProvider.builder()
 *         .token(myStaticJwt)
 *         .build();
 *
 * // Dynamic token — evaluated on every request
 * BearerTokenSecurityProvider security = BearerTokenSecurityProvider.builder()
 *         .token(() -> tokenCache.get())
 *         .build();
 * }</pre>
 */
public interface BearerTokenSecurityProviderBuilder {
    /**
     * Sets a static bearer token to be used on every request.
     * <p>
     * Convenience overload equivalent to {@code token(() -> bearerToken)}.
     *
     * @param bearerToken The token value; must not be blank.
     * @return This builder instance.
     * @throws software.frisby.core.validation.NullValueException  if the value is {@code null}.
     * @throws software.frisby.core.validation.BlankValueException if the value is blank.
     */
    BearerTokenSecurityProviderBuilder token(String bearerToken);

    /**
     * Sets a token supplier that is invoked on every request.
     * <p>
     * Use this overload when the token may rotate over time — for example, when
     * tokens are issued by an upstream gateway and periodically refreshed, or when
     * the caller manages an OAuth 2.0 Authorization Code Flow externally and stores
     * the current access token in a thread-safe holder.
     * <p>
     * The supplier is called directly on the request thread with no additional
     * synchronization; the implementation must handle concurrent access if the
     * supplier accesses shared state.
     *
     * @param tokenSupplier A supplier that returns the current bearer token; must
     *                      not be {@code null}.  The supplier itself should return a
     *                      non-blank token on every invocation.
     * @return This builder instance.
     * @throws software.frisby.core.validation.NullValueException if the supplier is {@code null}.
     */
    BearerTokenSecurityProviderBuilder token(Supplier<String> tokenSupplier);

    /**
     * Returns a new {@link BearerTokenSecurityProvider} configured with the supplied token.
     *
     * @return A new {@link BearerTokenSecurityProvider} instance.
     * @throws IllegalStateException if no token or token supplier has been provided.
     */
    BearerTokenSecurityProvider build();
}

