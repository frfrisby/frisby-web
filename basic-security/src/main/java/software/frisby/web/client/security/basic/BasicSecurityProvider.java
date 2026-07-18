package software.frisby.web.client.security.basic;

import software.frisby.web.client.security.SecurityProvider;

/**
 * A {@link SecurityProvider} that authenticates requests using HTTP Basic Authentication
 * (RFC 7617).
 * <p>
 * On each request, the provider encodes the configured credentials as
 * {@code Base64(username:password)} and sets the {@code Authorization: Basic <token>}
 * header.
 * <p>
 * Obtain an instance via {@link #builder()}:
 *
 * <pre>{@code
 * BasicSecurityProvider security = BasicSecurityProvider.builder()
 *         .credentials("alice", "s3cr3t")
 *         .build();
 *
 * Client client = Client.builder()
 *         .configuration(config)
 *         .security(security)
 *         .build();
 * }</pre>
 *
 * @see BasicSecurityProviderBuilder
 */
public interface BasicSecurityProvider extends SecurityProvider {
    /**
     * Returns a new builder for creating a {@link BasicSecurityProvider} instance.
     *
     * @return A new {@link BasicSecurityProviderBuilder}.
     */
    static BasicSecurityProviderBuilder builder() {
        return new DefaultBasicSecurityProviderBuilder();
    }
}

