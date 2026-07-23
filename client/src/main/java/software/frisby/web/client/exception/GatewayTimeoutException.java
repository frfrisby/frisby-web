package software.frisby.web.client.exception;

import java.io.Serial;
import java.net.URI;
import java.net.http.HttpHeaders;

/**
 * Thrown when the server returns HTTP {@code 504 Gateway Timeout}.
 * <p>
 * A gateway or proxy did not receive a timely response from an upstream server.
 * The backend service accepted the connection but did not respond within the
 * gateway's timeout window.  This is a transient infrastructure error — retrying
 * after a short delay is generally appropriate.
 */
public final class GatewayTimeoutException extends ServerException {
    @Serial
    private static final long serialVersionUID = 1L;
    private static final int STATUS_CODE = 504;

    /**
     * Creates a {@code 504} exception with the full request context.
     *
     * @param method  The HTTP method of the request (e.g. {@code GET}, {@code POST}).
     * @param uri     The URI of the request.
     * @param headers The response headers received from the server.
     * @param body    The response body, or {@code null} if none was received.
     */
    public GatewayTimeoutException(String method, URI uri, HttpHeaders headers, String body) {
        super(method, uri, STATUS_CODE, headers, body);
    }

    /**
     * Creates a {@code 504} exception with a response body.
     *
     * @param body The response body, or {@code null} if none was received.
     */
    public GatewayTimeoutException(String body) {
        super(STATUS_CODE, body);
    }

    /**
     * Creates a {@code 504} exception without request context or body.
     */
    public GatewayTimeoutException() {
        super(STATUS_CODE);
    }
}

