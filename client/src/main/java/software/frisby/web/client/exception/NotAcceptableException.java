package software.frisby.web.client.exception;

import java.io.Serial;
import java.net.URI;
import java.net.http.HttpHeaders;

/**
 * Thrown when the server returns HTTP {@code 406 Not Acceptable}.
 * <p>
 * The server cannot produce a response matching the media types, encodings, or
 * languages listed in the request's {@code Accept} headers.  Verify that the
 * client's {@code Accept} header is compatible with the media types the target
 * resource can produce.
 */
public final class NotAcceptableException extends ClientException {
    @Serial
    private static final long serialVersionUID = 1L;
    private static final int STATUS_CODE = 406;

    /**
     * Creates a {@code 406} exception with the full request context.
     *
     * @param method  The HTTP method of the request (e.g. {@code GET}, {@code POST}).
     * @param uri     The URI of the request.
     * @param headers The response headers received from the server.
     * @param body    The response body, or {@code null} if none was received.
     */
    public NotAcceptableException(String method, URI uri, HttpHeaders headers, String body) {
        super(method, uri, STATUS_CODE, headers, body);
    }

    /**
     * Creates a {@code 406} exception with a response body.
     *
     * @param body The response body, or {@code null} if none was received.
     */
    public NotAcceptableException(String body) {
        super(STATUS_CODE, body);
    }

    /**
     * Creates a {@code 406} exception without request context or body.
     */
    public NotAcceptableException() {
        super(STATUS_CODE);
    }
}

