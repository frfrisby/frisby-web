package software.frisby.web.client.event;

import software.frisby.core.validation.Durations;
import software.frisby.core.validation.Numbers;
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
 * <h2>Retry context</h2>
 * <p>
 * When a {@link software.frisby.web.client.RetryPolicy} is configured and the request
 * is eligible for retry (idempotent method, non-multipart body), this callback fires
 * <em>for every failed attempt</em> — not just the final one.  The {@link #retryAttempt()}
 * field identifies which attempt failed:
 * <ul>
 *   <li>{@link Optional#empty()} — no retry context (no policy configured, or the
 *       request was ineligible — multipart body or non-idempotent method without
 *       {@code allowNonIdempotent()}).</li>
 *   <li>{@code Optional.of(1)} — first attempt failed; the policy may or may not retry.</li>
 *   <li>{@code Optional.of(2)} or higher — a retry attempt that also failed.</li>
 * </ul>
 * <p>
 * A successful outcome after one or more retries produces a final
 * {@link ClientEventListener#onRequestCompleted} callback (not a failed one), so the
 * last {@code onRequestFailed} call with a given {@code retryAttempt} value is not
 * necessarily the terminal event for that logical request.
 *
 * @param method       The HTTP method of the request (e.g. {@code "GET"}, {@code "POST"}).
 * @param uri          The fully resolved URI of the request, including any query parameters.
 * @param statusCode   The HTTP response status code, if a response was received.
 *                     Present for {@code 4xx} / {@code 5xx} failures; empty for
 *                     transport-level failures (connect timeout, read timeout, etc.).
 * @param latency      The elapsed time from the moment the request was sent until the
 *                     failure occurred.
 * @param cause        The exception that caused the failure.
 * @param retryAttempt The 1-based attempt number, present when the request was processed
 *                     through the retry path.  Empty when no retry context applies.
 * @see ClientEventListener#onRequestFailed(RequestFailedEvent)
 */
public record RequestFailedEvent(String method,
                                 URI uri,
                                 Optional<Integer> statusCode,
                                 Duration latency,
                                 Throwable cause,
                                 Optional<Integer> retryAttempt) {
    /**
     * Compact constructor — validates that all fields satisfy their documented constraints.
     *
     * @throws software.frisby.core.validation.BlankValueException               if {@code method} is blank.
     * @throws software.frisby.core.validation.NullValueException                if {@code uri},
     *                                                                           {@code statusCode},
     *                                                                           {@code latency},
     *                                                                           {@code cause}, or
     *                                                                           {@code retryAttempt}
     *                                                                           is {@code null}.
     * @throws software.frisby.core.validation.DurationOutsideRangeException     if {@code latency} is negative.
     * @throws software.frisby.core.validation.NumericValueOutsideRangeException if {@code retryAttempt}
     *                                                                           is present but less than 1.
     */
    public RequestFailedEvent {
        Strings.notBlank("method", method);
        Values.notNull("uri", uri);
        Values.notNull("statusCode", statusCode);
        Durations.notNegative("latency", latency);
        Values.notNull("cause", cause);
        Values.notNull("retryAttempt", retryAttempt);

        retryAttempt.ifPresent(integer -> Numbers.positive("retryAttempt", integer));
    }

    /**
     * Creates a {@link RequestFailedEvent} for a transport-level failure where no
     * HTTP response was received (connect timeout, read timeout, SSL error, etc.).
     *
     * @param method  The HTTP method.
     * @param uri     The request URI.
     * @param latency The elapsed time before the failure occurred.
     * @param cause   The exception that caused the failure.
     * @return A new {@link RequestFailedEvent} with an empty {@code statusCode} and
     * empty {@code retryAttempt}.
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
                cause,
                Optional.empty()
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
     * @return A new {@link RequestFailedEvent} with the given {@code statusCode} and
     * empty {@code retryAttempt}.
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
                cause,
                Optional.empty()
        );
    }

    /**
     * Returns a copy of this event with the given 1-based retry attempt number set.
     * <p>
     * Used by the client engine to annotate events that occur within a retry sequence.
     *
     * @param attempt The 1-based attempt number; must be {@code >= 1}.
     * @return A new {@link RequestFailedEvent} identical to this one except for the
     * {@code retryAttempt} field.
     * @throws software.frisby.core.validation.NumericValueOutsideRangeException if {@code attempt < 1}.
     */
    public RequestFailedEvent withRetryAttempt(int attempt) {
        return new RequestFailedEvent(
                method,
                uri,
                statusCode,
                latency,
                cause,
                Optional.of(Numbers.positive("attempt", attempt))
        );
    }

    @Override
    public String toString() {
        String status = statusCode.map(integer -> " → " + integer).orElse("");
        String attempt = retryAttempt.map(n -> ", attempt " + n).orElse("");

        return method + " " + uri + status +
                " failed after " + latency.toMillis() + "ms" +
                " (" + cause.getClass().getSimpleName() + attempt + ")";
    }
}
