package software.frisby.web.client.exception;

import java.io.Serial;
import java.net.URI;
import java.net.http.HttpHeaders;

/**
 * Thrown when the server returns HTTP {@code 403 Forbidden}.
 * <p>
 * The server understood the request but refuses to authorize it.  Unlike
 * {@code 401}, re-authenticating will not change the outcome — the caller
 * does not have the necessary permissions.
 */
public final class ForbiddenException extends ClientException {
    @Serial
    private static final long serialVersionUID = 1L;
    private static final int STATUS_CODE = 403;

    /**
     * Creates a {@code 403} exception with the full request context.
     *
     * @param method  The HTTP method of the request (e.g. {@code GET}, {@code POST}).
     * @param uri     The URI of the request.
     * @param headers The response headers received from the server.
     * @param body    The response body, or {@code null} if none was received.
     */
    public ForbiddenException(String method, URI uri, HttpHeaders headers, String body) {
        super(method, uri, STATUS_CODE, headers, body);
    }

    /**
     * Creates a {@code 403} exception with a response body.
     *
     * @param body The response body, or {@code null} if none was received.
     */
    public ForbiddenException(String body) {
        super(STATUS_CODE, body);
    }

    /**
     * Creates a {@code 403} exception without request context or body.
     */
    public ForbiddenException() {
        super(STATUS_CODE);
    }
}

