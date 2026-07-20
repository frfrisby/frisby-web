package software.frisby.web.client.security.oauth2;

import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Formats and emits log messages for OAuth 2.0 token endpoint request and response events.
 * <p>
 * All token-endpoint logging is routed through this class, giving operators a single
 * logger name ({@code software.frisby.web.client.security.oauth2.TokenRequestLogger})
 * to configure independently of the main HTTP client logger.
 * <p>
 * Log level usage mirrors {@code RequestLogger} in the {@code client} module:
 * <ul>
 *   <li>{@code TRACE} — combined request + response with headers</li>
 *   <li>{@code INFO} — method, URI, status code, and latency for successful responses</li>
 *   <li>{@code WARNING} — method, URI, status code, and latency for error responses</li>
 *   <li>{@code ERROR} — method, URI, and exception detail for transport-level failures</li>
 * </ul>
 * <p>
 * <strong>Request and response bodies are never logged</strong> — the request body
 * contains the client credentials and the response body contains the access token.
 */
final class TokenRequestLogger {
    private static final System.Logger LOGGER = System.getLogger(TokenRequestLogger.class.getName());

    private static final String REQUEST_HEADERS = "Request Headers:";
    private static final String RESPONSE_HEADERS = "Response Headers:";
    private static final String AUTHORIZATION = "authorization";
    private static final String REDACTED = "[redacted]";
    private static final String ARROW_RIGHT = "→";
    private static final String ARROW_LEFT = "←";
    private static final String CROSS = "✕";
    private static final String INDENT_1 = "\n  ";
    private static final String INDENT_2 = "\n    ";

    private TokenRequestLogger() {
    }

    /**
     * Logs a successful token endpoint exchange.
     * Emits full request and response headers at {@code TRACE};
     * emits method, URI, status, and latency at {@code INFO}.
     *
     * @param request  The token endpoint request.
     * @param response The response received.
     * @param latency  The elapsed time from sending the request to receiving the response.
     */
    static void logSuccess(HttpRequest request, HttpResponse<String> response, Duration latency) {
        if (LOGGER.isLoggable(System.Logger.Level.TRACE)) {
            StringBuilder sb = new StringBuilder();

            appendRequestSection(sb, request);
            sb.append("\n").append(ARROW_LEFT).append(" ")
                    .append(response.statusCode())
                    .append(" (").append(latency.toMillis()).append("ms)");
            appendResponseHeaders(sb, response.headers());

            LOGGER.log(System.Logger.Level.TRACE, sb.toString());
        } else if (LOGGER.isLoggable(System.Logger.Level.INFO)) {
            LOGGER.log(
                    System.Logger.Level.INFO,
                    "{0} {1} " + ARROW_RIGHT + " {2} ({3}ms)",
                    request.method(),
                    request.uri(),
                    response.statusCode(),
                    latency.toMillis()
            );
        }
    }

    /**
     * Logs a token endpoint error response ({@code 4xx} / {@code 5xx}).
     * Emits full headers at {@code TRACE}; emits a status summary at {@code WARNING}.
     *
     * @param request  The token endpoint request.
     * @param response The error response received.
     * @param latency  The elapsed time from sending the request to receiving the response.
     */
    static void logTokenError(HttpRequest request, HttpResponse<String> response, Duration latency) {
        if (LOGGER.isLoggable(System.Logger.Level.TRACE)) {
            StringBuilder sb = new StringBuilder();

            appendRequestSection(sb, request);
            sb.append("\n").append(ARROW_LEFT).append(" ")
                    .append(response.statusCode())
                    .append(" (").append(latency.toMillis()).append("ms)");
            appendResponseHeaders(sb, response.headers());

            LOGGER.log(System.Logger.Level.TRACE, sb.toString());
        } else if (LOGGER.isLoggable(System.Logger.Level.WARNING)) {
            LOGGER.log(
                    System.Logger.Level.WARNING,
                    "{0} {1} " + ARROW_RIGHT + " {2} ({3}ms)",
                    request.method(),
                    request.uri(),
                    response.statusCode(),
                    latency.toMillis()
            );
        }
    }

    /**
     * Logs a transport-level failure (connect timeout, network error, etc.).
     * Emits full request headers at {@code TRACE}; emits a summary at {@code ERROR}.
     *
     * @param request The token endpoint request that failed.
     * @param cause   The exception that caused the failure.
     */
    static void logTransportError(HttpRequest request, Throwable cause) {
        if (LOGGER.isLoggable(System.Logger.Level.TRACE)) {
            StringBuilder sb = new StringBuilder();

            appendRequestSection(sb, request);
            sb.append("\n").append(CROSS).append(" ")
                    .append(cause.getClass().getSimpleName()).append(": ")
                    .append(cause.getMessage());

            LOGGER.log(System.Logger.Level.TRACE, sb.toString());
        } else if (LOGGER.isLoggable(System.Logger.Level.ERROR)) {
            LOGGER.log(
                    System.Logger.Level.ERROR,
                    "{0} {1} failed: {2}",
                    request.method(),
                    request.uri(),
                    cause.getMessage()
            );
        }
    }

    private static void appendRequestSection(StringBuilder sb, HttpRequest request) {
        sb.append(ARROW_RIGHT).append(" ").append(request.method()).append(" ").append(request.uri());
        sb.append(INDENT_1).append(REQUEST_HEADERS);

        for (Map.Entry<String, List<String>> entry : request.headers().map().entrySet()) {
            String name = entry.getKey();
            String lowerName = name.toLowerCase(Locale.ROOT);

            if (AUTHORIZATION.equals(lowerName)) {
                sb.append(INDENT_2).append(name).append(": ").append(REDACTED);
            } else {
                sb.append(INDENT_2).append(name).append(": ")
                        .append(String.join(", ", entry.getValue()));
            }
        }

        // Never append request body — contains client_id and client_secret.
    }

    private static void appendResponseHeaders(StringBuilder sb, HttpHeaders headers) {
        sb.append(INDENT_1).append(RESPONSE_HEADERS);

        for (Map.Entry<String, List<String>> entry : headers.map().entrySet()) {
            sb.append(INDENT_2).append(entry.getKey()).append(": ")
                    .append(String.join(", ", entry.getValue()));
        }

        // Never append response body — contains the access token.
    }
}

