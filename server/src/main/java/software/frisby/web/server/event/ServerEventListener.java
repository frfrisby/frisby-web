package software.frisby.web.server.event;

/**
 * An observer interface for receiving notifications about HTTP request outcomes
 * on the server side.
 * <p>
 * Register an implementation via
 * {@link software.frisby.web.server.ServerBuilder#eventListener(ServerEventListener)}.
 * The server invokes the appropriate method after every completed or failed request.
 * <p>
 * Implementations can forward events to any metrics or tracing backend:
 * OpenTelemetry, Micrometer, Datadog, or a custom store.  The interface intentionally
 * carries no dependency on any specific metrics library.
 * <p>
 * Callback methods are invoked on the thread that finishes processing the request.
 * Implementations must be thread-safe.
 * <p>
 * Exceptions thrown by a callback are caught, logged at WARNING level, and suppressed —
 * a buggy listener implementation will never affect request processing.
 * <p>
 * When no listener is registered the server uses a no-op implementation and incurs
 * no observable overhead.
 *
 * <pre>{@code
 * // Example: forward to Micrometer
 * Server.builder()
 *         .configuration(config)
 *         .resources(new OrderResource(service))
 *         .eventListener(new ServerEventListener() {
 *             @Override
 *             public void onRequestCompleted(RequestCompletedEvent event) {
 *                 Timer.builder("http.server.requests")
 *                         .tag("method", event.method())
 *                         .tag("path", event.path())
 *                         .tag("status", String.valueOf(event.statusCode()))
 *                         .register(registry)
 *                         .record(event.latency());
 *             }
 *         })
 *         .build();
 * }</pre>
 *
 * @see RequestCompletedEvent
 */
public interface ServerEventListener {
    /**
     * Called after every request that completes with an HTTP response, regardless
     * of the response status code.
     * <p>
     * All responses are reported here — {@code 4xx} and {@code 5xx} included.
     * {@link software.frisby.web.server.Server} guarantees that
     * every unhandled exception also produces a {@code 500} response, so there is
     * no separate "failed without response" callback.
     *
     * @param event The event containing the method, path, status code, latency, and
     *              request/response byte counts.
     */
    void onRequestCompleted(RequestCompletedEvent event);
}

