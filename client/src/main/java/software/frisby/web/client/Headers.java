package software.frisby.web.client;

import software.frisby.web.client.security.SecurityProvider;

/**
 * Provides constants for well-known HTTP header names.
 * <p>
 * Using these constants when calling
 * {@link GetSpec#header(String, String) spec.header()} prevents spelling errors
 * and makes the intent clear at the call site:
 *
 * <pre>{@code
 * client.get()
 *         .path("/reports")
 *         .header(Headers.ACCEPT_LANGUAGE, "en-US")
 *         .header(Headers.CACHE_CONTROL, "no-cache")
 *         .send(Report.class);
 * }</pre>
 *
 * <h2>Client-managed headers</h2>
 * <p>
 * The following headers are set automatically by the client based on the request
 * configuration and must not be passed to {@code header()}.  Attempting to do so
 * throws an {@link IllegalArgumentException}:
 * <ul>
 *   <li>{@link #ACCEPT} — set to {@code application/json} for JSON requests</li>
 *   <li>{@link #ACCEPT_ENCODING} — managed by the gzip configuration option</li>
 *   <li>{@link #CONTENT_TYPE} — set from the body type (JSON, multipart, form)</li>
 *   <li>{@link #CONTENT_LENGTH} — set from the serialized body size</li>
 *   <li>{@link #CONTENT_ENCODING} — set when request body compression is enabled</li>
 *   <li>{@link #TRANSFER_ENCODING} — managed by the JDK HTTP client</li>
 * </ul>
 */
public final class Headers {
    // -------------------------------------------------------------------------
    // Client-managed — do not pass to header()
    // -------------------------------------------------------------------------

    /**
     * {@code Accept} — client-managed; do not set manually.
     */
    public static final String ACCEPT = "Accept";

    /**
     * {@code Accept-Encoding} — client-managed; do not set manually.
     */
    public static final String ACCEPT_ENCODING = "Accept-Encoding";

    /**
     * {@code Content-Type} — client-managed; do not set manually.
     */
    public static final String CONTENT_TYPE = "Content-Type";

    /**
     * {@code Content-Length} — client-managed; do not set manually.
     */
    public static final String CONTENT_LENGTH = "Content-Length";

    /**
     * {@code Content-Encoding} — client-managed; do not set manually.
     */
    public static final String CONTENT_ENCODING = "Content-Encoding";

    /**
     * {@code Transfer-Encoding} — client-managed; do not set manually.
     */
    public static final String TRANSFER_ENCODING = "Transfer-Encoding";

    // -------------------------------------------------------------------------
    // Caller-settable
    // -------------------------------------------------------------------------

    /**
     * {@code Accept-Language} — indicates the preferred natural language for the response.
     */
    public static final String ACCEPT_LANGUAGE = "Accept-Language";

    /**
     * {@code Authorization} — carries credentials to authenticate the caller with
     * the server.  Prefer using a {@link SecurityProvider}
     * rather than setting this header directly.
     */
    public static final String AUTHORIZATION = "Authorization";

    /**
     * {@code Cache-Control} — directives for caching mechanisms in requests and responses.
     */
    public static final String CACHE_CONTROL = "Cache-Control";

    /**
     * {@code ETag} — identifier for a specific version of a resource.
     */
    public static final String ETAG = "ETag";

    /**
     * {@code If-Modified-Since} — makes the request conditional on the resource having been modified.
     */
    public static final String IF_MODIFIED_SINCE = "If-Modified-Since";

    /**
     * {@code If-None-Match} — makes the request conditional on the ETag not matching.
     */
    public static final String IF_NONE_MATCH = "If-None-Match";

    /**
     * {@code Last-Modified} — the date and time the resource was last modified.
     */
    public static final String LAST_MODIFIED = "Last-Modified";

    /**
     * {@code User-Agent} — identifies the client software making the request.
     */
    public static final String USER_AGENT = "User-Agent";

    /**
     * {@code X-Correlation-Id} — a caller-assigned identifier used to correlate a request
     * across multiple services in a distributed system.
     */
    public static final String X_CORRELATION_ID = "X-Correlation-Id";

    /**
     * {@code X-Request-Id} — a caller-assigned identifier that uniquely identifies
     * this individual request.
     */
    public static final String X_REQUEST_ID = "X-Request-Id";

    private Headers() {
    }
}

