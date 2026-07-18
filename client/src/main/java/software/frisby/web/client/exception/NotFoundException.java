package software.frisby.web.client.exception;

import java.io.Serial;
import java.net.URI;
import java.net.http.HttpHeaders;

/**
 * Thrown when the server returns HTTP {@code 404 Not Found}.
 * <p>
 * The requested resource could not be found.  The resource may have been deleted
 * or the URI may be incorrect.
 */
public final class NotFoundException extends ClientException {
    @Serial
    private static final long serialVersionUID = 1L;
    private static final int STATUS_CODE = 404;

    /**
     * Creates a {@code 404} exception with the full request context.
     *
     * @param method  The HTTP method of the request (e.g. {@code GET}, {@code POST}).
     * @param uri     The URI of the request.
     * @param headers The response headers received from the server.
     * @param body    The response body, or {@code null} if none was received.
     */
    public NotFoundException(String method, URI uri, HttpHeaders headers, String body) {
        super(method, uri, STATUS_CODE, headers, body);
    }

    /**
     * Creates a {@code 404} exception with a response body.
     *
     * @param body The response body, or {@code null} if none was received.
     */
    public NotFoundException(String body) {
        super(STATUS_CODE, body);
    }

    /**
     * Creates a {@code 404} exception without request context or body.
     */
    public NotFoundException() {
        super(STATUS_CODE);
    }
}

