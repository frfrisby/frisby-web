package software.frisby.web.client.security.oauth2;

import java.time.Duration;

/**
 * An observer interface for receiving notifications about OAuth 2.0 token fetch outcomes.
 * <p>
 * Register an implementation via
 * {@link ClientCredentialsSecurityProviderBuilder#eventListener(TokenEventListener)}.
 * The provider invokes the appropriate method after every token fetch —
 * both successful acquisitions and failures.
 * <p>
 * Implementations can forward events to any metrics or tracing backend:
 * OpenTelemetry, Micrometer, Datadog, or a custom store.  The interface intentionally
 * carries no dependency on any specific metrics library.
 * <p>
 * Callbacks are invoked on the thread that performed the token request.
 * Because the provider serializes token fetches, only one fetch is in flight at
 * a time; implementations do not need to be internally synchronized, but they
 * <em>must not</em> block or perform any action that would re-enter the provider
 * (such as initiating a new secured request on the same client).
 * <p>
 * When no listener is registered the provider uses a no-op implementation and incurs
 * no observable overhead.
 *
 * <pre>{@code
 * // Example: forward to Micrometer
 * ClientCredentialsSecurityProvider security = ClientCredentialsSecurityProvider.builder()
 *         .tokenEndpoint(URI.create("https://auth.example.com/oauth/token"))
 *         .credentials("my-client-id", "my-client-secret")
 *         .serializer(myJsonSerializer)
 *         .eventListener(new TokenEventListener() {
 *             @Override
 *             public void onTokenFetched(Duration latency) {
 *                 Timer.builder("oauth2.token.fetch")
 *                         .tag("outcome", "success")
 *                         .register(registry)
 *                         .record(latency);
 *             }
 *
 *             @Override
 *             public void onTokenFetchFailed(Duration latency, Throwable cause) {
 *                 Timer.builder("oauth2.token.fetch")
 *                         .tag("outcome", "failure")
 *                         .tag("exception", cause.getClass().getSimpleName())
 *                         .register(registry)
 *                         .record(latency);
 *             }
 *         })
 *         .build();
 * }</pre>
 *
 * @see ClientCredentialsSecurityProvider
 * @see ClientCredentialsSecurityProviderBuilder
 */
public interface TokenEventListener {
    /**
     * Called after a token is successfully obtained from the token endpoint —
     * both on the initial fetch and on every subsequent refresh.
     *
     * @param latency The elapsed time from sending the token request to receiving
     *                the response.
     */
    void onTokenFetched(Duration latency);

    /**
     * Called when a token fetch fails, whether due to a transport-level error
     * (connect timeout, network failure) or an HTTP error response from the
     * token endpoint.
     *
     * @param latency The elapsed time from sending the token request to the
     *                point of failure.
     * @param cause   The exception that caused the failure.
     */
    void onTokenFetchFailed(Duration latency, Throwable cause);
}

