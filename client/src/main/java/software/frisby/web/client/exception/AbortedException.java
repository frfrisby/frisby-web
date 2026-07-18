package software.frisby.web.client.exception;

import java.io.Serial;
import java.net.URI;

/**
 * Thrown when a request is interrupted or canceled before a response is received.
 * <p>
 * Common causes:
 * <ul>
 *   <li>The calling thread was interrupted ({@link Thread#interrupt()}) while the
 *       request was in flight</li>
 *   <li>The client was shut down while a request was pending</li>
 *   <li>The response subscriber explicitly canceled the response stream</li>
 * </ul>
 * <p>
 * For IO-level failures such as TLS errors or connection resets, see
 * {@link TransportException}.
 */
public final class AbortedException extends HttpRequestException {
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
    public AbortedException(String message, Throwable cause, String method, URI uri) {
        super(message, cause, method, uri);
    }

    /**
     * Creates an exception wrapping the underlying cause, with request context.
     *
     * @param cause  The underlying cause.
     * @param method The HTTP method of the request (e.g. {@code GET}, {@code POST}).
     * @param uri    The URI of the request.
     */
    public AbortedException(Throwable cause, String method, URI uri) {
        super(cause, method, uri);
    }

    /**
     * Creates an exception without request context (for tests).
     *
     * @param message The detail message.
     * @param cause   The underlying cause.
     */
    public AbortedException(String message, Throwable cause) {
        super(message, cause);
    }
}

