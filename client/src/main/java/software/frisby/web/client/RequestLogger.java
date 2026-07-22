package software.frisby.web.client;

import software.frisby.core.validation.Values;
import software.frisby.web.client.exception.HttpResponseException;

import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Formats and emits log messages for HTTP request and response events.
 * <p>
 * All HTTP client logging is routed through this class, giving operators a single
 * logger name ({@code software.frisby.web.client.RequestLogger}) to configure.
 * <p>
 * Request and response are always emitted as a single combined log entry —
 * this keeps the full exchange correlated in one message regardless of concurrency.
 * <p>
 * Log level usage:
 * <ul>
 *   <li>{@code TRACE} — full request + response: method, URI, request headers
 *       (masked per {@link ClientLoggingConfiguration#redactedHeaders()}), optional
 *       request body (redacted), status, response headers, optional response body
 *       (redacted)</li>
 *   <li>{@code INFO} — method, URI, status code, and latency for successful responses</li>
 *   <li>{@code WARNING} — same full block as {@code TRACE} for HTTP error responses
 *       ({@code 4xx} / {@code 5xx})</li>
 *   <li>{@code ERROR} — same full block as {@code TRACE} for transport-level failures
 *       ({@code ConnectTimeoutException}, {@code ReadTimeoutException}, etc.)</li>
 * </ul>
 * <p>
 * Bodies are truncated to {@link ClientLoggingConfiguration#maxBodySize()} characters
 * and sensitive JSON / form-encoded field values are replaced with {@code [redacted]}
 * per {@link ClientLoggingConfiguration#redactedBodyFields()}.
 */
final class RequestLogger {
    private static final System.Logger LOGGER = System.getLogger(RequestLogger.class.getName());

    private static final String REQUEST_HEADERS = "Request Headers:";
    private static final String RESPONSE_HEADERS = "Response Headers:";
    private static final String REQUEST_BODY = "Request Body:";
    private static final String RESPONSE_BODY = "Response Body:";
    private static final String COOKIE = "cookie";
    private static final String SET_COOKIE = "set-cookie";
    private static final String CONTENT_TYPE = "content-type";
    private static final String REDACTED = "[redacted]";
    private static final String ARROW_RIGHT = "→";
    private static final String ARROW_LEFT = "←";
    private static final String CROSS = "✕";
    private static final String HORIZONTAL_ELLIPSIS = "…";
    private static final String INDENT_1 = "\n  ";
    private static final String INDENT_2 = "\n    ";

    private final ClientLoggingConfiguration config;

    RequestLogger(ClientLoggingConfiguration config) {
        this.config = Values.notNull("config", config);
    }

    /**
     * Redacts the cookie value in a {@code Set-Cookie} response header while
     * preserving the cookie name and all security attributes.
     * <p>
     * Input:  {@code session=abc123; Path=/api; Secure; HttpOnly}<br>
     * Output: {@code session=[redacted]; Path=/api; Secure; HttpOnly}
     */
    private static String redactSetCookieValue(String value) {
        if (value.isBlank()) {
            return REDACTED;
        }

        int firstSemicolon = value.indexOf(';');
        String cookiePair = firstSemicolon >= 0 ? value.substring(0, firstSemicolon) : value;
        String attributes = firstSemicolon >= 0 ? value.substring(firstSemicolon) : "";

        int eq = cookiePair.indexOf('=');

        if (eq > 0) {
            return cookiePair.substring(0, eq + 1) + REDACTED + attributes;
        }

        return REDACTED + attributes;
    }

    private static String redactFieldValues(String json, Collection<String> fields) {
        if (fields.isEmpty()) {
            return json;
        }

        String result = json;

        for (String field : fields) {
            // "fieldname"\s*:\s*"string-value"  — only string values are redacted
            String regex = "\"" + Pattern.quote(field) + "\"\\s*:\\s*\"(?:[^\"\\\\]|\\\\.)*\"";
            result = result.replaceAll(regex, "\"" + field + "\": \"[redacted]\"");
        }

        return result;
    }

    // Reserved for request body logging — used when Content-Type is
    // application/x-www-form-urlencoded (e.g. OAuth2 token requests, login forms).
    private static String redactFormValues(String form, Collection<String> fields) {
        if (fields.isEmpty()) {
            return form;
        }

        String result = form;

        for (String field : fields) {
            // Match the field name preceded by start-of-string or '&', followed by '='
            // and any value up to the next '&' or end-of-string.
            String regex = "(?:^|(?<=&))" + Pattern.quote(field) + "=[^&]*";
            result = result.replaceAll(regex, field + "=[redacted]");
        }

        return result;
    }

    /**
     * Logs a successful exchange that completed on the given 1-based retry attempt.
     * When {@code retryAttempt > 1} the attempt number is included in the log line.
     */
    <T> void logSuccess(OutboundRequest outbound,
                        HttpResponse<T> response,
                        Duration latency,
                        byte[] responseBodySnapshot,
                        int retryAttempt) {
        if (LOGGER.isLoggable(System.Logger.Level.TRACE)) {
            StringBuilder sb = new StringBuilder();

            appendRequestSection(sb, outbound);
            sb.append("\n").append(ARROW_LEFT).append(" ")
                    .append(response.statusCode())
                    .append(" (").append(latency.toMillis()).append("ms");

            if (retryAttempt > 1) {
                sb.append(", attempt ").append(retryAttempt);
            }

            sb.append(")");
            appendResponseHeaders(sb, response.headers());

            if (null != responseBodySnapshot) {
                String bodyStr = new String(responseBodySnapshot, java.nio.charset.StandardCharsets.UTF_8);
                appendTruncatedBody(sb, bodyStr, false, RESPONSE_BODY);
            }

            LOGGER.log(System.Logger.Level.TRACE, sb.toString());
        } else if (LOGGER.isLoggable(System.Logger.Level.INFO)) {
            if (retryAttempt > 1) {
                LOGGER.log(
                        System.Logger.Level.INFO,
                        "{0} {1} " + ARROW_RIGHT + " {2} ({3}ms, attempt {4})",
                        outbound.request().method(),
                        outbound.request().uri(),
                        response.statusCode(),
                        latency.toMillis(),
                        retryAttempt
                );
            } else {
                LOGGER.log(
                        System.Logger.Level.INFO,
                        "{0} {1} " + ARROW_RIGHT + " {2} ({3}ms)",
                        outbound.request().method(),
                        outbound.request().uri(),
                        response.statusCode(),
                        latency.toMillis()
                );
            }
        }
    }

    /**
     * Logs an HTTP error response ({@code 4xx} / {@code 5xx}) as a combined exchange entry.
     * Emits the full request + response block (headers and body) at both {@code TRACE}
     * and {@code WARNING}.
     */
    void logError(OutboundRequest outbound,
                  HttpResponseException exception,
                  Duration latency,
                  int retryAttempt) {
        System.Logger.Level level = effectiveLevel(System.Logger.Level.TRACE, System.Logger.Level.WARNING);

        if (null == level) {
            return;
        }

        StringBuilder sb = new StringBuilder();

        appendRequestSection(sb, outbound);
        sb.append("\n").append(ARROW_LEFT).append(" ")
                .append(exception.statusCode())
                .append(" (").append(latency.toMillis()).append("ms");

        if (retryAttempt > 1) {
            sb.append(", attempt ").append(retryAttempt);
        }

        sb.append(")");
        appendResponseHeaders(sb, exception.headers());
        exception.body().ifPresent(body -> appendTruncatedBody(sb, body, false, RESPONSE_BODY));

        LOGGER.log(level, sb.toString());
    }

    /**
     * Logs a transport-level failure (connect timeout, read timeout, etc.).
     * Emits the full request block (headers and body) at both {@code TRACE}
     * and {@code ERROR}.
     */
    void logTransportError(OutboundRequest outbound, Throwable cause, int retryAttempt) {
        System.Logger.Level level = effectiveLevel(System.Logger.Level.TRACE, System.Logger.Level.ERROR);

        if (null == level) {
            return;
        }

        StringBuilder sb = new StringBuilder();

        appendRequestSection(sb, outbound);
        sb.append("\n").append(CROSS).append(" ");

        if (retryAttempt > 1) {
            sb.append("[attempt ").append(retryAttempt).append("] ");
        }

        sb.append(cause.getClass().getSimpleName()).append(": ").append(cause.getMessage());

        LOGGER.log(level, sb.toString());
    }

    /**
     * Returns the highest-priority loggable level between {@code preferred} and
     * {@code fallback}, or {@code null} if neither is loggable.
     */
    private System.Logger.Level effectiveLevel(System.Logger.Level preferred,
                                               System.Logger.Level fallback) {
        if (LOGGER.isLoggable(preferred)) {
            return preferred;
        }

        if (LOGGER.isLoggable(fallback)) {
            return fallback;
        }

        return null;
    }

    private void appendRequestSection(StringBuilder sb, OutboundRequest outbound) {
        HttpRequest request = outbound.request();

        sb.append(ARROW_RIGHT).append(" ").append(request.method()).append(" ").append(request.uri());
        sb.append(INDENT_1).append(REQUEST_HEADERS);

        for (Map.Entry<String, List<String>> entry : request.headers().map().entrySet()) {
            String name = entry.getKey();
            String lowerName = name.toLowerCase(Locale.ROOT);

            if (COOKIE.equals(lowerName)) {
                appendCookieHeader(sb, name, entry.getValue());
            } else if (config.redactedHeaders().contains(lowerName)) {
                sb.append(INDENT_2).append(name).append(": ").append(REDACTED);
            } else {
                sb.append(INDENT_2).append(name).append(": ")
                        .append(String.join(", ", entry.getValue()));
            }
        }

        appendRequestBody(sb, outbound);
    }

    /**
     * Appends one {@code Cookie: name=[redacted]} line per cookie to {@code sb}.
     * <p>
     * A single {@code Cookie} header can carry multiple cookies separated by {@code ;}.
     * Each pair is formatted individually so cookie names remain visible in the log
     * while their values are replaced with {@code [redacted]}.
     */
    private void appendCookieHeader(StringBuilder sb, String headerName, List<String> headerValues) {
        for (String headerValue : headerValues) {
            for (String pair : headerValue.split(";")) {
                String trimmed = pair.trim();

                if (trimmed.isEmpty()) {
                    continue;
                }

                int eq = trimmed.indexOf('=');
                String formatted = eq > 0
                        ? trimmed.substring(0, eq + 1) + REDACTED
                        : trimmed;

                sb.append(INDENT_2).append(headerName).append(": ").append(formatted);
            }
        }
    }

    private void appendRequestBody(StringBuilder sb, OutboundRequest outbound) {
        byte[] snapshotBytes = outbound.bodySnapshot();

        if (null == snapshotBytes || config.maxBodySize() == 0) {
            return;
        }

        String lowerContentType = outbound.request().headers()
                .firstValue(CONTENT_TYPE)
                .orElse("")
                .toLowerCase(Locale.ROOT);

        // Decode to String here — deferred until we know logging is active.
        String snapshot = new String(snapshotBytes, java.nio.charset.StandardCharsets.UTF_8);

        if (lowerContentType.startsWith("multipart/")) {
            sb.append(INDENT_1).append(REQUEST_BODY).append(INDENT_2).append(snapshot);
        } else {
            boolean formEncoded = lowerContentType.contains("application/x-www-form-urlencoded");
            appendTruncatedBody(sb, snapshot, formEncoded, REQUEST_BODY);
        }
    }

    private void appendResponseHeaders(StringBuilder sb, HttpHeaders headers) {
        sb.append(INDENT_1).append(RESPONSE_HEADERS);

        for (Map.Entry<String, List<String>> entry : headers.map().entrySet()) {
            String name = entry.getKey();
            String lowerName = name.toLowerCase(Locale.ROOT);

            if (SET_COOKIE.equals(lowerName)) {
                // Redact the cookie value, preserve the name and all attributes
                // (Path, Domain, Secure, HttpOnly, SameSite, Max-Age).
                for (String value : entry.getValue()) {
                    sb.append(INDENT_2).append(name).append(": ")
                            .append(redactSetCookieValue(value));
                }
            } else if (config.redactedHeaders().contains(lowerName)) {
                sb.append(INDENT_2).append(name).append(": ").append(REDACTED);
            } else {
                sb.append(INDENT_2).append(name).append(": ")
                        .append(String.join(", ", entry.getValue()));
            }
        }
    }

    private void appendTruncatedBody(StringBuilder sb, String body, boolean formEncoded, String sectionLabel) {
        int limit = config.maxBodySize();

        if (0 == limit) {
            return;
        }

        String redacted = formEncoded
                ? redactFormValues(body, config.redactedBodyFields())
                : redactFieldValues(body, config.redactedBodyFields());

        sb.append(INDENT_1).append(sectionLabel);
        sb.append(INDENT_2);

        if (redacted.length() > limit) {
            sb.append(redacted, 0, limit).append(HORIZONTAL_ELLIPSIS).append("(truncated)");
        } else {
            sb.append(redacted);
        }
    }
}
