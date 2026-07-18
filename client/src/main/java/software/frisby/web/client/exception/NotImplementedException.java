package software.frisby.web.client.exception;

import java.io.Serial;
import java.net.URI;
import java.net.http.HttpHeaders;

/**
 * Thrown when the server returns HTTP {@code 501 Not Implemented}.
 * <p>
 * The server does not support the functionality required to fulfill the request.
 * This is typically returned when the server does not recognize the request method
 * or lacks support for a specific operation at the requested endpoint.
 */
public final class NotImplementedException extends ServerException {
    @Serial
    private static final long serialVersionUID = 1L;
    private static final int STATUS_CODE = 501;

    /**
     * Creates a {@code 501} exception with the full request context.
     *
     * @param method  The HTTP method of the request (e.g. {@code GET}, {@code POST}).
     * @param uri     The URI of the request.
     * @param headers The response headers received from the server.
     * @param body    The response body, or {@code null} if none was received.
     */
    public NotImplementedException(String method, URI uri, HttpHeaders headers, String body) {
        super(method, uri, STATUS_CODE, headers, body);
    }

    /**
     * Creates a {@code 501} exception with a response body.
     *
     * @param body The response body, or {@code null} if none was received.
     */
    public NotImplementedException(String body) {
        super(STATUS_CODE, body);
    }

    /**
     * Creates a {@code 501} exception without request context or body.
     */
    public NotImplementedException() {
        super(STATUS_CODE);
    }
}

