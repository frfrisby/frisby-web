package software.frisby.web.client.exception;

import java.io.Serial;
import java.net.URI;
import java.net.http.HttpHeaders;

/**
 * Thrown when the server returns HTTP {@code 408 Request Timeout}.
 * <p>
 * The server timed out waiting for the client to send the complete request.
 * This is a server-side timeout — the server gave up waiting for the request
 * to arrive.
 * <p>
 * Note: this is distinct from the client-side {@link ReadTimeoutException},
 * which is thrown when the <em>client</em> times out waiting for the server
 * to respond.  A {@code 408} is an HTTP response status code received from
 * the server; a {@link ReadTimeoutException} means no response was received
 * at all within the configured read timeout.
 * <p>
 * A {@code 408} is generally safe to retry — the server has explicitly closed
 * the connection and is ready to accept a new request.
 */
public final class RequestTimeoutException extends ClientException {
    @Serial
    private static final long serialVersionUID = 1L;
    private static final int STATUS_CODE = 408;

    /**
     * Creates a {@code 408} exception with the full request context.
     *
     * @param method  The HTTP method of the request (e.g. {@code GET}, {@code POST}).
     * @param uri     The URI of the request.
     * @param headers The response headers received from the server.
     * @param body    The response body, or {@code null} if none was received.
     */
    public RequestTimeoutException(String method, URI uri, HttpHeaders headers, String body) {
        super(method, uri, STATUS_CODE, headers, body);
    }

    /**
     * Creates a {@code 408} exception with a response body.
     *
     * @param body The response body, or {@code null} if none was received.
     */
    public RequestTimeoutException(String body) {
        super(STATUS_CODE, body);
    }

    /**
     * Creates a {@code 408} exception without request context or body.
     */
    public RequestTimeoutException() {
        super(STATUS_CODE);
    }
}

