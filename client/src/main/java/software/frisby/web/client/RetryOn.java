package software.frisby.web.client;

/**
 * Identifies a category of failure that a {@link RetryPolicy} should treat as
 * retryable.
 * <p>
 * Used with {@link RetryPolicyBuilder#on(RetryOn...)} to declare which exceptions
 * trigger a retry attempt.  Convenience constants for common groups are available
 * on {@link RetryPolicy}:
 *
 * <pre>{@code
 * RetryPolicy.builder()
 *         .maxAttempts(3)
 *         .on(RetryPolicy.GATEWAY_ERRORS)   // BAD_GATEWAY, SERVICE_UNAVAILABLE, GATEWAY_TIMEOUT
 *         .on(RetryPolicy.TRANSPORT_ERRORS) // CONNECT_FAILURE, CONNECT_TIMEOUT, READ_TIMEOUT
 *         .delay(RetryDelay.exponential(Duration.ofSeconds(1)))
 *         .build();
 * }</pre>
 *
 * @see RetryPolicy
 * @see RetryPolicyBuilder
 */
public enum RetryOn {

    // -------------------------------------------------------------------------
    // HTTP response errors
    // -------------------------------------------------------------------------

    /**
     * HTTP {@code 408 Request Timeout} — the server timed out waiting for the request
     * to arrive.  A new request is safe to attempt.
     */
    REQUEST_TIMEOUT,

    /**
     * HTTP {@code 429 Too Many Requests} — the client has exceeded a rate limit.
     * Consider pairing with {@link RetryPolicyBuilder#honorRetryAfterHeader(java.time.Duration)}
     * to respect the server's requested back-off window.
     */
    TOO_MANY_REQUESTS,

    /**
     * HTTP {@code 502 Bad Gateway} — a gateway or proxy received an invalid response
     * from an upstream server.  Typically, a transient infrastructure condition.
     */
    BAD_GATEWAY,

    /**
     * HTTP {@code 503 Service Unavailable} — the server is temporarily unable to
     * handle requests, usually due to overload or maintenance.
     */
    SERVICE_UNAVAILABLE,

    /**
     * HTTP {@code 504 Gateway Timeout} — a gateway or proxy did not receive a timely
     * response from an upstream server.  Typically, a transient infrastructure condition.
     */
    GATEWAY_TIMEOUT,

    // -------------------------------------------------------------------------
    // Transport errors
    // -------------------------------------------------------------------------

    /**
     * TCP connection refused or host unreachable ({@code ConnectException}).
     * The target service may be restarting or temporarily unavailable.
     */
    CONNECT_FAILURE,

    /**
     * TCP connection timed out ({@code ConnectTimeoutException}).
     * The target service may be under load or temporarily unreachable.
     */
    CONNECT_TIMEOUT,

    /**
     * The server accepted the connection but did not respond within the configured
     * read timeout ({@code ReadTimeoutException}).  Safe to retry for idempotent
     * methods; use {@link RetryPolicyBuilder#allowNonIdempotent()} with caution
     * as the server may have already processed the request.
     */
    READ_TIMEOUT,

    /**
     * Any other transport-layer failure — SSL/TLS handshake errors, unexpected
     * connection resets, and similar low-level I/O failures ({@code TransportException}).
     * <p>
     * SSL failures are often configuration issues rather than transient conditions.
     * Include this value only when you have confirmed that your target environment
     * may produce transient SSL errors (e.g. certificate rotation events).
     */
    TRANSPORT_FAILURE
}

