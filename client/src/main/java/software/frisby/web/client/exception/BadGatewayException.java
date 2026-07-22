package software.frisby.web.client.exception;

import java.io.Serial;
import java.net.URI;
import java.net.http.HttpHeaders;

/**
 * Thrown when the server returns HTTP {@code 502 Bad Gateway}.
 * <p>
 * An upstream server or proxy received an invalid response from an inbound
 * server while acting as a gateway or proxy.  This is a transient infrastructure
 * error — the backend service may be restarting, deploying, or temporarily
 * unreachable.  Retrying after a short delay is generally appropriate.
 */
public final class BadGatewayException extends ServerException {
    @Serial
    private static final long serialVersionUID = 1L;
    private static final int STATUS_CODE = 502;

    /**
     * Creates a {@code 502} exception with the full request context.
     *
     * @param method  The HTTP method of the request (e.g. {@code GET}, {@code POST}).
     * @param uri     The URI of the request.
     * @param headers The response headers received from the server.
     * @param body    The response body, or {@code null} if none was received.
     */
    public BadGatewayException(String method, URI uri, HttpHeaders headers, String body) {
        super(method, uri, STATUS_CODE, headers, body);
    }

    /**
     * Creates a {@code 502} exception with a response body.
     *
     * @param body The response body, or {@code null} if none was received.
     */
    public BadGatewayException(String body) {
        super(STATUS_CODE, body);
    }

    /**
     * Creates a {@code 502} exception without request context or body.
     */
    public BadGatewayException() {
        super(STATUS_CODE);
    }
}

