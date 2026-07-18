package software.frisby.web.client.exception;

import java.io.Serial;
import java.net.URI;

/**
 * Thrown when a connection was established but the server did not respond within the
 * configured read timeout.
 * <p>
 * The server may be overloaded or performing a slow operation.  Consider increasing the
 * read timeout for operations known to be slow, or investigate server-side performance.
 *
 * @see software.frisby.web.client.ClientConfigurationBuilder#readTimeout
 */
public final class ReadTimeoutException extends HttpRequestException {
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
    public ReadTimeoutException(String message, Throwable cause, String method, URI uri) {
        super(message, cause, method, uri);
    }

    /**
     * Creates an exception wrapping the underlying cause, with request context.
     *
     * @param cause  The underlying cause.
     * @param method The HTTP method of the request (e.g. {@code GET}, {@code POST}).
     * @param uri    The URI of the request.
     */
    public ReadTimeoutException(Throwable cause, String method, URI uri) {
        super(cause, method, uri);
    }

    /**
     * Creates an exception without request context (for tests).
     *
     * @param message The detail message.
     * @param cause   The underlying cause.
     */
    public ReadTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}

