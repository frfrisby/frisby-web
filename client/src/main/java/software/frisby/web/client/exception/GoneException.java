package software.frisby.web.client.exception;

import java.io.Serial;
import java.net.URI;
import java.net.http.HttpHeaders;

/**
 * Thrown when the server returns HTTP {@code 410 Gone}.
 * <p>
 * The requested resource has been permanently removed and will not be available
 * again.  Unlike {@link NotFoundException} ({@code 404}), a {@code 410} signals
 * that the removal is intentional and permanent — clients and caches should
 * remove any references to this resource and not attempt to access it again.
 */
public final class GoneException extends ClientException {
    @Serial
    private static final long serialVersionUID = 1L;
    private static final int STATUS_CODE = 410;

    /**
     * Creates a {@code 410} exception with the full request context.
     *
     * @param method  The HTTP method of the request (e.g. {@code GET}, {@code POST}).
     * @param uri     The URI of the request.
     * @param headers The response headers received from the server.
     * @param body    The response body, or {@code null} if none was received.
     */
    public GoneException(String method, URI uri, HttpHeaders headers, String body) {
        super(method, uri, STATUS_CODE, headers, body);
    }

    /**
     * Creates a {@code 410} exception with a response body.
     *
     * @param body The response body, or {@code null} if none was received.
     */
    public GoneException(String body) {
        super(STATUS_CODE, body);
    }

    /**
     * Creates a {@code 410} exception without request context or body.
     */
    public GoneException() {
        super(STATUS_CODE);
    }
}

