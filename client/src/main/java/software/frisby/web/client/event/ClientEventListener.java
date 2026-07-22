package software.frisby.web.client.event;

import software.frisby.web.client.ClientBuilder;

/**
 * An observer interface for receiving notifications about HTTP request outcomes.
 * <p>
 * Register an implementation via
 * {@link ClientBuilder#eventListener(ClientEventListener)}.
 * The two callbacks are <em>mutually exclusive</em>: exactly one fires per request.
 * {@link #onRequestCompleted} fires when the request succeeds (no exception thrown);
 * {@link #onRequestFailed} fires when any exception is thrown — whether from an HTTP
 * error response ({@code 4xx}/{@code 5xx}) or a transport-level failure.
 * <p>
 * Implementations can forward events to any metrics or tracing backend:
 * OpenTelemetry, Micrometer, Datadog, or a custom store.  The interface intentionally
 * carries no dependency on any specific metrics library.
 * <p>
 * Callback methods are called on the thread that completed the request.  For
 * asynchronous requests this may be a thread-pool thread, not the caller thread.
 * Implementations must be thread-safe.
 * <p>
 * When no listener is registered the client uses a no-op implementation and incurs
 * no observable overhead.
 *
 * <pre>{@code
 * // Example: forward to Micrometer
 * // Total requests  = onRequestCompleted count + onRequestFailed count
 * // Success latency = onRequestCompleted latency
 * // Error rate      = onRequestFailed count / total
 * Client.builder()
 *         .configuration(config)
 *         .eventListener(new ClientEventListener() {
 *             @Override
 *             public void onRequestCompleted(RequestCompletedEvent event) {
 *                 Timer.builder("http.client.requests")
 *                         .tag("method", event.method())
 *                         .tag("status", String.valueOf(event.statusCode()))
 *                         .tag("outcome", "success")
 *                         .register(registry)
 *                         .record(event.latency());
 *             }
 *
 *             @Override
 *             public void onRequestFailed(RequestFailedEvent event) {
 *                 Timer.builder("http.client.requests")
 *                         .tag("method", event.method())
 *                         .tag("status", event.statusCode().map(String::valueOf).orElse("none"))
 *                         .tag("outcome", "error")
 *                         .tag("exception", event.cause().getClass().getSimpleName())
 *                         .register(registry)
 *                         .record(event.latency());
 *             }
 *         })
 *         .build();
 * }</pre>
 *
 * @see RequestCompletedEvent
 * @see RequestFailedEvent
 */
public interface ClientEventListener {
    /**
     * Called when a request completes successfully — the server responded and no
     * exception was thrown to the caller.
     * <p>
     * {@code 4xx} and {@code 5xx} responses are <em>not</em> reported here; they are
     * reported via {@link #onRequestFailed} because they cause an exception to be thrown.
     *
     * @param event The event containing the method, URI, status code, and latency.
     */
    void onRequestCompleted(RequestCompletedEvent event);

    /**
     * Called when a request fails with an exception.
     * <p>
     * This covers both HTTP error responses ({@code 4xx}/{@code 5xx}) and transport-level
     * failures (connect timeout, read timeout, TLS error, etc.).
     * {@link RequestFailedEvent#statusCode()} is present for HTTP-status failures and
     * empty for transport failures.
     * <p>
     * <strong>Retry context:</strong> when a {@link software.frisby.web.client.RetryPolicy}
     * is configured and the request is eligible for retry, this callback fires
     * <em>once per failed attempt</em> — not just for the final failure.
     * {@link RequestFailedEvent#retryAttempt()} identifies the attempt number so that
     * listeners can distinguish intermediate failures from terminal ones, or suppress
     * per-attempt noise in metrics:
     * <pre>{@code
     * void onRequestFailed(RequestFailedEvent event) {
     *     // Count every attempt for raw error-rate monitoring
     *     errorCounter.increment();
     *
     *     // Only alert on the final failure (no retry will follow this one)
     *     if (event.retryAttempt().isEmpty()) {
     *         // No retry context — this is the only and final attempt
     *         alert(event);
     *     }
     *     // Note: retryAttempt present does NOT mean the request will be retried;
     *     // a retry only follows if the policy returns a delay.  A successful
     *     // outcome after retries produces onRequestCompleted, not onRequestFailed.
     * }
     * }</pre>
     *
     * @param event The event containing the method, URI, optional status code, latency,
     *              causing exception, and optional retry attempt number.
     */
    void onRequestFailed(RequestFailedEvent event);
}

