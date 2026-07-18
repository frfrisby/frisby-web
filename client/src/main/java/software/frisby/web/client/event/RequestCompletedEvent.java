package software.frisby.web.client.event;

import software.frisby.core.validation.Durations;
import software.frisby.core.validation.Numbers;
import software.frisby.core.validation.Strings;
import software.frisby.core.validation.Values;

import java.net.URI;
import java.time.Duration;

/**
 * Published by the client after a request completes successfully — that is, the server
 * responded and no exception was thrown to the caller.
 * <p>
 * {@code 4xx} and {@code 5xx} responses are reported via {@link RequestFailedEvent}
 * instead, because they result in an exception being thrown.  The two event types are
 * mutually exclusive: exactly one fires per request.
 *
 * @param method     The HTTP method of the request (e.g. {@code "GET"}, {@code "POST"}).
 * @param uri        The fully resolved URI of the request, including any query parameters.
 * @param statusCode The HTTP response status code (e.g. {@code 200}, {@code 201}).
 * @param latency    The elapsed time from the moment the request was sent until the
 *                   response headers were fully received.  For asynchronous requests,
 *                   this is the time until the {@link java.util.concurrent.CompletableFuture}
 *                   completed.
 * @see ClientEventListener#onRequestCompleted(RequestCompletedEvent)
 */
public record RequestCompletedEvent(String method,
                                    URI uri,
                                    int statusCode,
                                    Duration latency) {
    /**
     * Compact constructor — validates that all fields satisfy their documented constraints.
     *
     * @throws software.frisby.core.validation.BlankValueException               if {@code method} is blank.
     * @throws software.frisby.core.validation.NullValueException                if {@code uri} or
     *                                                                           {@code latency} is
     *                                                                           {@code null}.
     * @throws software.frisby.core.validation.NumericValueOutsideRangeException if {@code statusCode} is negative.
     * @throws software.frisby.core.validation.DurationOutsideRangeException     if {@code latency} is negative.
     */
    public RequestCompletedEvent {
        Strings.notBlank("method", method);
        Values.notNull("uri", uri);
        Numbers.notNegative("statusCode", statusCode);
        Durations.notNegative("latency", latency);
    }

    /**
     * Returns {@code true} if the request completed successfully (status code
     * {@code 2xx}).
     *
     * @return {@code true} for {@code 2xx} status codes.
     */
    public boolean successful() {
        return statusCode >= 200 && statusCode < 300;
    }

    @Override
    public String toString() {
        return method + " " + uri + " → " + statusCode + " (" + latency.toMillis() + "ms)";
    }
}
