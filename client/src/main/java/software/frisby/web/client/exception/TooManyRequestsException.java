package software.frisby.web.client.exception;

import java.io.Serial;
import java.net.URI;
import java.net.http.HttpHeaders;

/**
 * Thrown when the server returns HTTP {@code 429 Too Many Requests}.
 * <p>
 * The caller has sent too many requests in a given time window and has been
 * rate-limited.  The response may include a {@code Retry-After} header indicating
 * how long to wait before retrying, either as a delay in seconds or as an
 * HTTP-date after which the request may be retried.
 *
 * <pre>{@code
 * try {
 *     // ...
 * } catch (TooManyRequestsException e) {
 *     long retryAfterSeconds = e.headers()
 *             .firstValueAsLong("Retry-After")
 *             .orElse(60L);
 *     scheduleRetry(Duration.ofSeconds(retryAfterSeconds));
 * }
 * }</pre>
 */
public final class TooManyRequestsException extends ClientException {
    @Serial
    private static final long serialVersionUID = 1L;
    private static final int STATUS_CODE = 429;

    /**
     * Creates a {@code 429} exception with the full request context.
     *
     * @param method  The HTTP method of the request (e.g. {@code GET}, {@code POST}).
     * @param uri     The URI of the request.
     * @param headers The response headers received from the server.
     * @param body    The response body, or {@code null} if none was received.
     */
    public TooManyRequestsException(String method, URI uri, HttpHeaders headers, String body) {
        super(method, uri, STATUS_CODE, headers, body);
    }

    /**
     * Creates a {@code 429} exception with a response body.
     *
     * @param body The response body, or {@code null} if none was received.
     */
    public TooManyRequestsException(String body) {
        super(STATUS_CODE, body);
    }

    /**
     * Creates a {@code 429} exception without request context or body.
     */
    public TooManyRequestsException() {
        super(STATUS_CODE);
    }
}

