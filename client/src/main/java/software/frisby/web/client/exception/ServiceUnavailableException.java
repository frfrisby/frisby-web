package software.frisby.web.client.exception;

import java.io.Serial;
import java.net.URI;
import java.net.http.HttpHeaders;

/**
 * Thrown when the server returns HTTP {@code 503 Service Unavailable}.
 * <p>
 * The server is temporarily unable to handle requests — typically due to being
 * overloaded or undergoing maintenance.  Unlike a {@link ServerException} for
 * an unrecoverable error, a {@code 503} is a strong signal that retrying after
 * a delay is appropriate.
 * <p>
 * The response may include a {@code Retry-After} header suggesting how long to
 * wait before retrying.
 *
 * <pre>{@code
 * try {
 *     // ...
 * } catch (ServiceUnavailableException e) {
 *     long retryAfterSeconds = e.headers()
 *             .firstValueAsLong("Retry-After")
 *             .orElse(30L);
 *     scheduleRetry(Duration.ofSeconds(retryAfterSeconds));
 * }
 * }</pre>
 */
public final class ServiceUnavailableException extends ServerException {
    @Serial
    private static final long serialVersionUID = 1L;
    private static final int STATUS_CODE = 503;

    /**
     * Creates a {@code 503} exception with the full request context.
     *
     * @param method  The HTTP method of the request (e.g. {@code GET}, {@code POST}).
     * @param uri     The URI of the request.
     * @param headers The response headers received from the server.
     * @param body    The response body, or {@code null} if none was received.
     */
    public ServiceUnavailableException(String method, URI uri, HttpHeaders headers, String body) {
        super(method, uri, STATUS_CODE, headers, body);
    }

    /**
     * Creates a {@code 503} exception with a response body.
     *
     * @param body The response body, or {@code null} if none was received.
     */
    public ServiceUnavailableException(String body) {
        super(STATUS_CODE, body);
    }

    /**
     * Creates a {@code 503} exception without request context or body.
     */
    public ServiceUnavailableException() {
        super(STATUS_CODE);
    }
}

