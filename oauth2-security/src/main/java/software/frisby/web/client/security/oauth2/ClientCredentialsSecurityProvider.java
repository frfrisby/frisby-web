package software.frisby.web.client.security.oauth2;

import software.frisby.web.client.security.SecurityProvider;

/**
 * A {@link SecurityProvider} that authenticates outbound requests using the
 * OAuth 2.0 client-credentials grant type.
 * <p>
 * On every request, the provider ensures a valid bearer token is available —
 * fetching a new one from the token endpoint when none exists or when the current
 * token is within the configured expiry buffer of expiry (default 30 seconds) — and
 * sets the {@code Authorization: Bearer <token>} header automatically.
 * <p>
 * Token acquisition and refresh are thread-safe; concurrent requests will never
 * trigger duplicate token fetches.
 * <p>
 * Obtain an instance via {@link #builder()}:
 *
 * <pre>{@code
 * ClientCredentialsSecurityProvider security = ClientCredentialsSecurityProvider.builder()
 *         .tokenEndpoint(URI.create("https://auth.example.com/oauth/token"))
 *         .credentials(ClientCredentials.of("my-client-id", "my-client-secret"))
 *         .serializer(myJsonSerializer)
 *         .scope("read", "write")   // optional
 *         .build();
 *
 * Client client = Client.builder()
 *         .configuration(
 *                 ClientConfiguration.builder()
 *                         .uri(URI.create("https://api.example.com"))
 *                         .serializer(myJsonSerializer)
 *                         .build()
 *         )
 *         .security(security)
 *         .build();
 * }</pre>
 *
 * @see ClientCredentialsSecurityProviderBuilder
 * @see ClientCredentials
 */
public interface ClientCredentialsSecurityProvider extends SecurityProvider {
    /**
     * Returns a new builder for constructing a {@link ClientCredentialsSecurityProvider}.
     *
     * @return A new {@link ClientCredentialsSecurityProviderBuilder} instance.
     */
    static ClientCredentialsSecurityProviderBuilder builder() {
        return new DefaultClientCredentialsSecurityProviderBuilder();
    }
}

