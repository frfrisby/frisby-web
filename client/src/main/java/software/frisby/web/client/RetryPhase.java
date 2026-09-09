package software.frisby.web.client;

/**
 * Identifies the phase of the request lifecycle in which a failure occurred.
 * <p>
 * This signal is available to {@link RetryPolicy} implementations to make phase-aware
 * decisions. For example, a custom policy might retry differently for pre-flight failures
 * (serialization, URI resolution, security-provider errors) than for transport or
 * HTTP-response failures.
 *
 * @see RetryContext#phase()
 * @see RetryPolicy
 */
public enum RetryPhase {
    /**
     * Request construction phase — the request body could not be serialized, the URI could
     * not be resolved, or the security provider threw an exception. No HTTP request was
     * sent to the server.
     *
     * <p>Though these errors have nothing to do with authentication per se, this phase
     * covers all failures that occur before the JDK {@link java.net.http.HttpClient}
     * invokes {@code send()} or {@code sendAsync()}.
     */
    PRE_FLIGHT,

    /**
     * Transport phase — the HTTP client encountered a network-level failure before
     * receiving an HTTP response. Examples: connection refused, connect timeout, read
     * timeout, SSL/TLS handshake failure, unexpected connection reset.
     *
     * <p>{@link java.net.http.HttpClient} does not differentiate between failures before
     * vs after the server reads the request; only whether an HTTP response was received.
     */
    TRANSPORT,

    /**
     * HTTP response phase — the server responded with an HTTP error status code
     * ({@code 4xx} or {@code 5xx}), which the client mapped to an
     * {@link software.frisby.web.client.exception.HttpResponseException}.
     */
    HTTP_RESPONSE
}

