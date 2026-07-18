package software.frisby.web.client.exception;

import java.io.Serial;
import java.net.URI;

/**
 * Thrown when the request fails due to an IO-level error that is not covered by a more
 * specific transport exception.
 * <p>
 * Common causes:
 * <ul>
 *   <li>A TLS/SSL handshake failure (e.g. untrusted certificate, protocol mismatch)</li>
 *   <li>A connection reset by the server mid-stream</li>
 *   <li>A protocol-level error in the HTTP response</li>
 *   <li>A network-level IO failure not classified as a timeout or connection refusal</li>
 * </ul>
 * <p>
 * The underlying {@link java.io.IOException} is always available via {@link #getCause()}.
 */
public final class TransportException extends HttpRequestException {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Creates an exception with the full request context.
     *
     * @param message The detail message.
     * @param cause   The underlying cause.
     * @param method  The HTTP method of the request (e.g. {@code GET}, {@code POST}).
     * @param uri     The URI of the request.
     */
    public TransportException(String message, Throwable cause, String method, URI uri) {
        super(message, cause, method, uri);
    }

    /**
     * Creates an exception wrapping the underlying cause, with request context.
     *
     * @param cause  The underlying cause.
     * @param method The HTTP method of the request (e.g. {@code GET}, {@code POST}).
     * @param uri    The URI of the request.
     */
    public TransportException(Throwable cause, String method, URI uri) {
        super(cause, method, uri);
    }

    /**
     * Creates an exception without request context (for tests).
     *
     * @param message The detail message.
     * @param cause   The underlying cause.
     */
    public TransportException(String message, Throwable cause) {
        super(message, cause);
    }
}

