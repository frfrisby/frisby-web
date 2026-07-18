package software.frisby.web.client.exception;

import software.frisby.web.client.PostSpec;

import java.io.Serial;
import java.net.URI;
import java.net.http.HttpHeaders;

/**
 * Thrown when the server returns HTTP {@code 413 Content Too Large}.
 * <p>
 * The request body exceeds the size limit the server is willing to process.
 * Consider enabling {@link PostSpec#compress() compress()}
 * to reduce payload size.
 */
public final class PayloadTooLargeException extends ClientException {
    @Serial
    private static final long serialVersionUID = 1L;
    private static final int STATUS_CODE = 413;

    /**
     * Creates a {@code 413} exception with the full request context.
     *
     * @param method  The HTTP method of the request (e.g. {@code GET}, {@code POST}).
     * @param uri     The URI of the request.
     * @param headers The response headers received from the server.
     * @param body    The response body, or {@code null} if none was received.
     */
    public PayloadTooLargeException(String method, URI uri, HttpHeaders headers, String body) {
        super(method, uri, STATUS_CODE, headers, body);
    }

    /**
     * Creates a {@code 413} exception with a response body.
     *
     * @param body The response body, or {@code null} if none was received.
     */
    public PayloadTooLargeException(String body) {
        super(STATUS_CODE, body);
    }

    /**
     * Creates a {@code 413} exception without request context or body.
     */
    public PayloadTooLargeException() {
        super(STATUS_CODE);
    }
}

