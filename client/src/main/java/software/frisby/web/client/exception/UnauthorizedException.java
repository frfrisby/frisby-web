package software.frisby.web.client.exception;

import java.io.Serial;
import java.net.URI;
import java.net.http.HttpHeaders;

/**
 * Thrown when the server returns HTTP {@code 401 Unauthorized}.
 * <p>
 * The request requires authentication.  The response should include a
 * {@code WWW-Authenticate} header describing the authentication scheme.
 * Verify that the security provider is correctly configured.
 */
public final class UnauthorizedException extends ClientException {
    @Serial
    private static final long serialVersionUID = 1L;
    private static final int STATUS_CODE = 401;

    /**
     * Creates a {@code 401} exception with the full request context.
     *
     * @param method  The HTTP method of the request (e.g. {@code GET}, {@code POST}).
     * @param uri     The URI of the request.
     * @param headers The response headers received from the server.
     * @param body    The response body, or {@code null} if none was received.
     */
    public UnauthorizedException(String method, URI uri, HttpHeaders headers, String body) {
        super(method, uri, STATUS_CODE, headers, body);
    }

    /**
     * Creates a {@code 401} exception with a response body.
     *
     * @param body The response body, or {@code null} if none was received.
     */
    public UnauthorizedException(String body) {
        super(STATUS_CODE, body);
    }

    /**
     * Creates a {@code 401} exception without request context or body.
     */
    public UnauthorizedException() {
        super(STATUS_CODE);
    }
}

