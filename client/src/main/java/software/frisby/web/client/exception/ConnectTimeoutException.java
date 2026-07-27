package software.frisby.web.client.exception;

import software.frisby.web.client.ClientConfigurationBuilder;

import java.io.Serial;
import java.net.URI;

/**
 * Thrown when no TCP connection can be established within the configured connect timeout.
 * <p>
 * The server may be overloaded, the network may be congested, or the connect timeout in
 * {@link ClientConfigurationBuilder#connectTimeout} may be too low
 * for the network conditions.
 *
 * @see ClientConfigurationBuilder#connectTimeout
 */
public final class ConnectTimeoutException extends HttpRequestException {
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
    public ConnectTimeoutException(String message, Throwable cause, String method, URI uri) {
        super(message, cause, method, uri);
    }

    /**
     * Creates an exception wrapping the underlying cause, with request context.
     *
     * @param cause  The underlying cause.
     * @param method The HTTP method of the request (e.g. {@code GET}, {@code POST}).
     * @param uri    The URI of the request.
     */
    public ConnectTimeoutException(Throwable cause, String method, URI uri) {
        super(cause, method, uri);
    }

    /**
     * Creates an exception without request context (for tests).
     *
     * @param message The detail message.
     * @param cause   The underlying cause.
     */
    public ConnectTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
    /**
     * Creates an exception with a detail message and no request context.
     *
     * @param message The detail message.
     */
    public ConnectTimeoutException(String message) {
        super(message);
    }

    /**
     * Creates an exception wrapping a cause, without request context.
     *
     * @param cause The underlying cause.
     */
    public ConnectTimeoutException(Throwable cause) {
        super(cause);
    }

    /**
     * Creates an exception without a message, cause, or request context.
     * <p>
     * Useful in tests where only the exception type matters.
     */
    public ConnectTimeoutException() {
        super();
    }
}


