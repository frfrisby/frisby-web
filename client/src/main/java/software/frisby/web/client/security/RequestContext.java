package software.frisby.web.client.security;

import java.net.HttpCookie;

/**
 * Provides the methods available to a {@link SecurityProvider} for attaching
 * authentication credentials to an outbound HTTP request before it is sent.
 *
 * @see SecurityProvider
 */
public interface RequestContext {
    /**
     * Adds a header to the outbound request.
     *
     * @param name  The header name.
     * @param value The header value.
     */
    void addHeader(String name, String value);

    /**
     * Adds a cookie to the outbound request.
     *
     * @param cookie The cookie to add.
     */
    void addCookie(HttpCookie cookie);
}

