package software.frisby.web.client.exception;

import java.io.Serial;
import java.net.URI;
import java.net.http.HttpHeaders;

/**
 * Thrown when the server returns HTTP {@code 415 Unsupported Media Type}.
 * <p>
 * The server refuses to accept the request because the payload format is not
 * supported.  This typically means the {@code Content-Type} header sent with
 * the request does not match any media type the target resource accepts.
 * Verify that the serializer and the {@code Content-Type} header are consistent
 * with what the server expects.
 */
public final class UnsupportedMediaTypeException extends ClientException {
    @Serial
    private static final long serialVersionUID = 1L;
    private static final int STATUS_CODE = 415;

    /**
     * Creates a {@code 415} exception with the full request context.
     *
     * @param method  The HTTP method of the request (e.g. {@code GET}, {@code POST}).
     * @param uri     The URI of the request.
     * @param headers The response headers received from the server.
     * @param body    The response body, or {@code null} if none was received.
     */
    public UnsupportedMediaTypeException(String method, URI uri, HttpHeaders headers, String body) {
        super(method, uri, STATUS_CODE, headers, body);
    }

    /**
     * Creates a {@code 415} exception with a response body.
     *
     * @param body The response body, or {@code null} if none was received.
     */
    public UnsupportedMediaTypeException(String body) {
        super(STATUS_CODE, body);
    }

    /**
     * Creates a {@code 415} exception without request context or body.
     */
    public UnsupportedMediaTypeException() {
        super(STATUS_CODE);
    }
}

