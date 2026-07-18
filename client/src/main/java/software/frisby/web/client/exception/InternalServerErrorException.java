package software.frisby.web.client.exception;

import java.io.Serial;
import java.net.URI;
import java.net.http.HttpHeaders;

/**
 * Thrown when the server returns HTTP {@code 500 Internal Server Error}.
 * <p>
 * The server encountered an unexpected condition that prevented it from
 * fulfilling the request.  This is a general-purpose error indicating a
 * problem on the server side with no specific actionable guidance for the
 * caller.  Check server logs for root-cause details.
 * <p>
 * For other {@code 5xx} errors without a specific exception type, the base
 * {@link ServerException} is thrown.
 */
public final class InternalServerErrorException extends ServerException {
    @Serial
    private static final long serialVersionUID = 1L;
    private static final int STATUS_CODE = 500;

    /**
     * Creates a {@code 500} exception with the full request context.
     *
     * @param method  The HTTP method of the request (e.g. {@code GET}, {@code POST}).
     * @param uri     The URI of the request.
     * @param headers The response headers received from the server.
     * @param body    The response body, or {@code null} if none was received.
     */
    public InternalServerErrorException(String method, URI uri, HttpHeaders headers, String body) {
        super(method, uri, STATUS_CODE, headers, body);
    }

    /**
     * Creates a {@code 500} exception with a response body.
     *
     * @param body The response body, or {@code null} if none was received.
     */
    public InternalServerErrorException(String body) {
        super(STATUS_CODE, body);
    }

    /**
     * Creates a {@code 500} exception without request context or body.
     */
    public InternalServerErrorException() {
        super(STATUS_CODE);
    }
}

