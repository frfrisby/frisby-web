package software.frisby.web.client.event;

import software.frisby.core.validation.Durations;
import software.frisby.core.validation.Strings;
import software.frisby.core.validation.Values;

import java.net.URI;
import java.time.Duration;
import java.util.Optional;

/**
 * Published by the client when a request fails with an exception before or after
 * a response is received.
 * <p>
 * Failure scenarios include:
 * <ul>
 *   <li>Connection refused or TCP timeout (no status code available)</li>
 *   <li>Read timeout — server accepted the connection but did not respond in time</li>
 *   <li>SSL handshake failure</li>
 *   <li>An HTTP-status exception thrown by the client after receiving a
 *       {@code 4xx} or {@code 5xx} response (status code available)</li>
 * </ul>
 *
 * @param method     The HTTP method of the request (e.g. {@code "GET"}, {@code "POST"}).
 * @param uri        The fully resolved URI of the request, including any query parameters.
 * @param statusCode The HTTP response status code, if a response was received.
 *                   Present for {@code 4xx} / {@code 5xx} failures; empty for
 *                   transport-level failures (connect timeout, read timeout, etc.).
 * @param latency    The elapsed time from the moment the request was sent until the
 *                   failure occurred.
 * @param cause      The exception that caused the failure.
 * @see ClientEventListener#onRequestFailed(RequestFailedEvent)
 */
public record RequestFailedEvent(String method,
                                 URI uri,
                                 Optional<Integer> statusCode,
                                 Duration latency,
                                 Throwable cause) {
    /**
     * Compact constructor — validates that all fields satisfy their documented constraints.
     *
     * @param method     the HTTP method; must not be blank
     * @param uri        the request URI; must not be {@code null}
     * @param statusCode the optional status code wrapper; must not be {@code null}
     * @param latency    the request latency; must not be {@code null} or negative
     * @param cause      the failure cause; must not be {@code null}
     * @throws software.frisby.core.validation.BlankValueException           if {@code method} is blank.
     * @throws software.frisby.core.validation.NullValueException            if {@code uri},
     *                                                                        {@code statusCode},
     *                                                                        {@code latency}, or
     *                                                                        {@code cause} is
     *                                                                        {@code null}.
     * @throws software.frisby.core.validation.DurationOutsideRangeException if {@code latency} is negative.
     */
    public RequestFailedEvent {
        Strings.notBlank("method", method);
        Values.notNull("uri", uri);
        Values.notNull("statusCode", statusCode);
        Durations.notNegative("latency", latency);
        Values.notNull("cause", cause);
    }

    /**
     * Creates a {@link RequestFailedEvent} for a transport-level failure where no
     * HTTP response was received (connect timeout, read timeout, SSL error, etc.).
     *
     * @param method  The HTTP method.
     * @param uri     The request URI.
     * @param latency The elapsed time before the failure occurred.
     * @param cause   The exception that caused the failure.
     * @return A new {@link RequestFailedEvent} with an empty {@code statusCode}.
     */
    public static RequestFailedEvent transportFailure(String method,
                                                      URI uri,
                                                      Duration latency,
                                                      Throwable cause) {
        return new RequestFailedEvent(
                method,
                uri,
                Optional.empty(),
                latency,
                cause
        );
    }

    /**
     * Creates a {@link RequestFailedEvent} for an HTTP-status failure where a
     * {@code 4xx} or {@code 5xx} response was received.
     *
     * @param method     The HTTP method.
     * @param uri        The request URI.
     * @param statusCode The HTTP response status code.
     * @param latency    The elapsed time until the failure occurred.
     * @param cause      The exception that caused the failure.
     * @return A new {@link RequestFailedEvent} with the given {@code statusCode}.
     */
    public static RequestFailedEvent httpFailure(String method,
                                                 URI uri,
                                                 int statusCode,
                                                 Duration latency,
                                                 Throwable cause) {
        return new RequestFailedEvent(
                method,
                uri,
                Optional.of(statusCode),
                latency,
                cause
        );
    }

    @Override
    public String toString() {
        String status = statusCode.map(integer -> " → " + integer).orElse("");

        return method + " " + uri + status +
                " failed after " + latency.toMillis() + "ms" +
                " (" + cause.getClass().getSimpleName() + ")";
    }
}
