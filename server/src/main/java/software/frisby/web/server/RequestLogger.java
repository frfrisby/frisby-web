package software.frisby.web.server;

import jakarta.ws.rs.WebApplicationException;

/**
 * Formats and emits log messages for server lifecycle and request events.
 * <p>
 * All server logging is routed through this class, giving operators a single
 * logger name ({@code software.frisby.web.server.RequestLogger}) to configure.
 * <p>
 * Log level usage:
 * <ul>
 *   <li>{@code TRACE} — full request + response for <em>all</em> completed requests:
 *       method, path, status, latency, request headers (masked), buffered request body
 *       (redacted), response headers, and response body (redacted)</li>
 *   <li>{@code INFO} — method, path, status code, and latency for each 2xx/3xx completed
 *       request (one-liner; only emitted when {@code TRACE} is not enabled)</li>
 *   <li>{@code WARNING} — 4xx responses, 5xx responses caused by a
 *       {@link WebApplicationException} (a deliberate, controlled failure), with full
 *       request context (headers + buffered body)</li>
 *   <li>{@code ERROR} — 5xx responses caused by an uncaught non-{@code WebApplicationException}
 *       (an unexpected application bug), and server startup failures, with full
 *       request context and attached stack trace</li>
 * </ul>
 */
final class RequestLogger {
    private static final System.Logger LOGGER = System.getLogger(RequestLogger.class.getName());

    private static final String ARROW_RIGHT = "→";
    private static final String CROSS = "✕";
    private static final String METHOD_PATH_PREFIX = "{0} {1} ";

    RequestLogger() {
    }

    /**
     * Returns the log level for a failure response.
     * <p>
     * 5xx responses caused by an uncaught non-{@link WebApplicationException} are genuine
     * bugs and warrant {@code ERROR}.  5xx responses caused by a
     * {@link WebApplicationException} are deliberate, controlled failures — the same class
     * of event as a 4xx — and are logged at {@code WARNING}.  When {@code cause} is
     * {@code null} the level defaults to {@code WARNING}.
     */
    private static System.Logger.Level determineFailureLevel(int statusCode, Throwable cause) {
        if (statusCode >= 500 && null != cause && !(cause instanceof WebApplicationException)) {
            return System.Logger.Level.ERROR;
        }

        return System.Logger.Level.WARNING;
    }

    /**
     * Returns {@code true} if the log level for a failure response with the given status
     * code and exception is currently enabled.
     * <p>
     * Callers should check this before invoking expensive detail-building operations
     * such as header iteration and body serialization.
     *
     * @param statusCode The HTTP response status code.
     * @param cause      The exception that triggered the failure, or {@code null} if none.
     */
    boolean isDetailLoggable(int statusCode, Throwable cause) {
        return LOGGER.isLoggable(determineFailureLevel(statusCode, cause));
    }

    /**
     * Returns {@code true} if {@code TRACE} level is currently enabled.
     * <p>
     * Callers should check this before invoking expensive detail-building operations
     * for successful responses — header iteration and response-entity serialization —
     * that are only needed when the full exchange is being logged.
     */
    boolean isTraceLoggable() {
        return LOGGER.isLoggable(System.Logger.Level.TRACE);
    }

    void logStarted(String uri, String configSummary) {
        if (LOGGER.isLoggable(System.Logger.Level.INFO)) {
            LOGGER.log(
                    System.Logger.Level.INFO,
                    "Server started at ''{0}''.{1}",
                    uri,
                    configSummary
            );
        }
    }

    void logStopped(String uri) {
        if (LOGGER.isLoggable(System.Logger.Level.INFO)) {
            LOGGER.log(
                    System.Logger.Level.INFO,
                    "Server stopped at ''{0}''.",
                    uri
            );
        }
    }

    void logStartFailed(Throwable cause) {
        LOGGER.log(
                System.Logger.Level.ERROR,
                "Server failed to start.",
                cause
        );
    }

    /**
     * Logs a completed 2xx/3xx request.
     * <p>
     * When {@code TRACE} is enabled, emits a full multi-line entry (headers + bodies)
     * using the pre-formatted {@code detail} string provided by the caller.
     * When only {@code INFO} is enabled, emits a compact one-liner and ignores
     * {@code detail}.
     *
     * @param method     The HTTP method.
     * @param path       The request path.
     * @param statusCode The HTTP response status code.
     * @param latencyMs  The request latency in milliseconds.
     * @param detail     A pre-formatted multi-line detail string built by the caller
     *                   when {@link #isTraceLoggable()} returned {@code true};
     *                   empty string otherwise.
     */
    void logRequest(String method,
                    String path,
                    int statusCode,
                    long latencyMs,
                    String detail) {
        if (LOGGER.isLoggable(System.Logger.Level.TRACE)) {
            String message = method + " " + path + " " + ARROW_RIGHT + " " + statusCode
                    + " (" + latencyMs + "ms)" + detail;

            LOGGER.log(System.Logger.Level.TRACE, message);
        } else if (LOGGER.isLoggable(System.Logger.Level.INFO)) {
            LOGGER.log(
                    System.Logger.Level.INFO,
                    METHOD_PATH_PREFIX + ARROW_RIGHT + " {2} ({3}ms)",
                    method,
                    path,
                    statusCode,
                    latencyMs
            );
        }
    }

    /**
     * Logs a 4xx or 5xx response with full request context.
     * <p>
     * 4xx responses and 5xx responses caused by a {@link WebApplicationException} are
     * logged at {@code WARNING}; 5xx responses caused by an uncaught non-{@code WebApplicationException}
     * are logged at {@code ERROR}.
     * The {@code detail} string is pre-formatted by the caller and may span multiple
     * lines (request headers, buffered body, response headers).
     * <p>
     * When the resolved level is {@code ERROR} and {@code cause} is non-null, the exception
     * is attached to the log record so that the full stack trace appears in the output.
     * {@code WARNING}-level entries omit the stack trace; the detail string is sufficient.
     *
     * @param method     The HTTP method.
     * @param path       The request path.
     * @param statusCode The HTTP response status code.
     * @param latencyMs  The request latency in milliseconds.
     * @param detail     A pre-formatted multi-line detail string; may be empty.
     * @param cause      The exception that triggered the failure, or {@code null} if none.
     */
    void logFailureDetail(String method,
                          String path,
                          int statusCode,
                          long latencyMs,
                          String detail,
                          Throwable cause) {
        System.Logger.Level level = determineFailureLevel(statusCode, cause);

        if (!LOGGER.isLoggable(level)) {
            return;
        }

        String message = method + " " + path + " " + CROSS + " " + statusCode
                + " (" + latencyMs + "ms)" + detail;

        // Attach the original exception only for ERROR-level entries so the full stack
        // trace appears in the log record — essential for root-cause analysis of
        // unexpected server failures.  WARNING-level entries are deliberate, controlled
        // failures; the detail string is sufficient.
        if (level == System.Logger.Level.ERROR) {
            LOGGER.log(level, message, cause);
        } else {
            LOGGER.log(level, message);
        }
    }

    void logHealthCheck(String method,
                        String path,
                        int statusCode,
                        long latencyMs) {
        if (LOGGER.isLoggable(System.Logger.Level.TRACE)) {
            LOGGER.log(
                    System.Logger.Level.TRACE,
                    METHOD_PATH_PREFIX + ARROW_RIGHT + " {2} ({3}ms)",
                    method,
                    path,
                    statusCode,
                    latencyMs
            );
        }
    }

    /**
     * Logs a {@code 503 Service Unavailable} response produced by the concurrency-limit
     * gate at {@code WARNING} level.
     * <p>
     * Load shedding is intentional, expected operational behavior — the server is
     * working correctly by rejecting excess requests — so {@code WARNING} is the
     * appropriate level, not {@code ERROR}.  {@code ERROR} is reserved for responses
     * that indicate a genuine malfunction inside the server.
     *
     * @param method    The HTTP method.
     * @param path      The request path.
     * @param latencyMs The latency from request receipt to response write, in milliseconds.
     */
    void logCapacityRejection(String method,
                              String path,
                              long latencyMs) {
        if (LOGGER.isLoggable(System.Logger.Level.WARNING)) {
            LOGGER.log(
                    System.Logger.Level.WARNING,
                    METHOD_PATH_PREFIX + CROSS + " 503 ({2}ms)  [capacity limit]",
                    method,
                    path,
                    latencyMs
            );
        }
    }
}
