package software.frisby.web.client.exception;

import java.io.Serial;
import java.net.URI;
import java.net.http.HttpHeaders;

/**
 * Thrown when the server returns HTTP {@code 409 Conflict}.
 * <p>
 * The request conflicts with the current state of the server.  Common causes
 * include attempting to create a resource that already exists, or a concurrent
 * modification conflict.
 */
public final class ConflictException extends ClientException {
    @Serial
    private static final long serialVersionUID = 1L;
    private static final int STATUS_CODE = 409;

    /**
     * Creates a {@code 409} exception with the full request context.
     *
     * @param method  The HTTP method of the request (e.g. {@code GET}, {@code POST}).
     * @param uri     The URI of the request.
     * @param headers The response headers received from the server.
     * @param body    The response body, or {@code null} if none was received.
     */
    public ConflictException(String method, URI uri, HttpHeaders headers, String body) {
        super(method, uri, STATUS_CODE, headers, body);
    }

    /**
     * Creates a {@code 409} exception with a response body.
     *
     * @param body The response body, or {@code null} if none was received.
     */
    public ConflictException(String body) {
        super(STATUS_CODE, body);
    }

    /**
     * Creates a {@code 409} exception without request context or body.
     */
    public ConflictException() {
        super(STATUS_CODE);
    }
}

