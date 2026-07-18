package software.frisby.web.client.exception;

import java.io.Serial;
import java.net.URI;
import java.net.http.HttpHeaders;

/**
 * Thrown when the server returns HTTP {@code 405 Method Not Allowed}.
 * <p>
 * The HTTP method used in the request is not supported for the target resource.
 * The server's response will include an {@code Allow} header listing the permitted
 * methods.  Common causes include calling {@code POST} on a read-only endpoint or
 * using {@code GET} on an endpoint that only accepts {@code PUT}.
 *
 * <pre>{@code
 * try {
 *     // ...
 * } catch (MethodNotAllowedException e) {
 *     String allowed = e.headers()
 *             .firstValue("Allow")
 *             .orElse("(none listed)");
 *     log.error("Method not allowed. Permitted methods: {}", allowed);
 * }
 * }</pre>
 */
public final class MethodNotAllowedException extends ClientException {
    @Serial
    private static final long serialVersionUID = 1L;
    private static final int STATUS_CODE = 405;

    /**
     * Creates a {@code 405} exception with the full request context.
     *
     * @param method  The HTTP method of the request (e.g. {@code GET}, {@code POST}).
     * @param uri     The URI of the request.
     * @param headers The response headers received from the server.
     * @param body    The response body, or {@code null} if none was received.
     */
    public MethodNotAllowedException(String method, URI uri, HttpHeaders headers, String body) {
        super(method, uri, STATUS_CODE, headers, body);
    }

    /**
     * Creates a {@code 405} exception with a response body.
     *
     * @param body The response body, or {@code null} if none was received.
     */
    public MethodNotAllowedException(String body) {
        super(STATUS_CODE, body);
    }

    /**
     * Creates a {@code 405} exception without request context or body.
     */
    public MethodNotAllowedException() {
        super(STATUS_CODE);
    }
}

