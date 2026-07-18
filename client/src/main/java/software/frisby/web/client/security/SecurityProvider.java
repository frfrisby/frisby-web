package software.frisby.web.client.security;

import software.frisby.web.client.GetSpec;

/**
 * Defines the contract for a security provider that adds authentication credentials
 * to outbound HTTP requests.
 * <p>
 * Implementations are provided by the {@code basic-security} and {@code oauth2-security}
 * modules.  Callers may also supply custom implementations for other authentication schemes.
 * <p>
 * A security provider is registered per-request via the verb spec (e.g.
 * {@link GetSpec#security(SecurityProvider)}), allowing different
 * credentials to be applied to different requests from the same client instance.
 *
 * @see RequestContext
 */
public interface SecurityProvider {
    /**
     * Adds the appropriate authentication credentials to the outbound request.
     *
     * @param request Provides the methods available for attaching credentials to the request.
     */
    void secure(RequestContext request);
}

