package software.frisby.web.server.event;

import software.frisby.core.validation.Durations;
import software.frisby.core.validation.Numbers;
import software.frisby.core.validation.Strings;
import software.frisby.core.validation.Values;

import java.time.Duration;
import java.util.Optional;

/**
 * Published by the server after every request that completes with an HTTP response,
 * regardless of the response status code.
 * <p>
 * {@code 4xx} and {@code 5xx} responses are considered <em>completed</em> requests
 * from the server's perspective — the resource or an exception mapper produced a
 * response.
 * <p>
 * The {@code path} field contains only the request path — query parameters are
 * intentionally excluded to avoid logging sensitive values that callers may pass
 * as query parameters (API keys, tokens, PII, etc.).
 * <p>
 * The {@code endpoint} field identifies the matched JAX-RS resource class and method
 * when available.  It is empty for requests that did not match any resource (e.g.
 * {@code 404} responses to unregistered paths).  Use {@code endpoint} rather than
 * {@code path} for metrics labels — path-based labels cause a cardinality explosion
 * when paths contain variable segments (e.g. {@code /orders/42}, {@code /orders/43}).
 *
 * @param method        The HTTP method of the request (e.g. {@code "GET"}, {@code "POST"}).
 * @param path          The decoded request path (e.g. {@code "/orders/123"}), without
 *                      query parameters.
 * @param statusCode    The HTTP response status code (e.g. {@code 200}, {@code 404}).
 * @param latency       The elapsed time from the moment the request was received to the
 *                      moment the response was fully written.
 * @param requestBytes  The number of bytes in the request body, or {@code 0} if the
 *                      request carried no body or the size could not be determined.
 * @param responseBytes The number of bytes in the response body, or {@code 0} if the
 *                      response carried no body or the size could not be determined.
 * @param endpoint      The matched JAX-RS resource class and method, or empty if the
 *                      request did not match any resource.
 * @see ServerEventListener#onRequestCompleted(RequestCompletedEvent)
 * @see Endpoint
 */
public record RequestCompletedEvent(String method,
                                    String path,
                                    int statusCode,
                                    Duration latency,
                                    long requestBytes,
                                    long responseBytes,
                                    Optional<Endpoint> endpoint) {
    /**
     * Compact constructor — validates that all fields satisfy their documented constraints.
     *
     * @param method        the HTTP method; must not be blank
     * @param path          the request path; must not be blank
     * @param statusCode    the HTTP status code; must not be negative
     * @param latency       the request latency; must not be negative
     * @param requestBytes  the request body byte count; must not be negative
     * @param responseBytes the response body byte count; must not be negative
     * @param endpoint      the matched endpoint; must not be {@code null} (may be empty)
     * @throws software.frisby.core.validation.BlankValueException               if {@code method}
     *                                                                           or {@code path} is
     *                                                                           blank.
     * @throws software.frisby.core.validation.NumericValueOutsideRangeException if
     *                                                                           {@code statusCode},
     *                                                                           {@code requestBytes},
     *                                                                           or
     *                                                                           {@code responseBytes}
     *                                                                           is negative.
     * @throws software.frisby.core.validation.DurationOutsideRangeException     if {@code latency}
     *                                                                           is negative.
     * @throws software.frisby.core.validation.NullValueException                if {@code endpoint}
     *                                                                           is {@code null}.
     */
    public RequestCompletedEvent {
        Strings.notBlank("method", method);
        Strings.notBlank("path", path);
        Numbers.notNegative("statusCode", statusCode);
        Durations.notNegative("latency", latency);
        Numbers.notNegative("requestBytes", requestBytes);
        Numbers.notNegative("responseBytes", responseBytes);
        Values.notNull("endpoint", endpoint);
    }

    /**
     * Returns {@code true} if the request completed successfully (status code {@code 2xx}).
     *
     * @return {@code true} for {@code 2xx} status codes.
     */
    public boolean successful() {
        return statusCode >= 200 && statusCode < 300;
    }

    @Override
    public String toString() {
        return method + " " + path + " → " + statusCode + " (" + latency.toMillis() + "ms)";
    }
}
