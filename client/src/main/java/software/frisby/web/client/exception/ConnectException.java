package software.frisby.web.client.exception;

import software.frisby.web.client.ClientConfiguration;

import java.io.Serial;
import java.net.URI;

/**
 * Thrown when the TCP connection to the server is refused or the host is unreachable.
 * <p>
 * Common causes: the server is not running, the host is misspelled, a firewall is
 * blocking the connection, or the base URI in {@link ClientConfiguration}
 * is incorrect.
 */
public final class ConnectException extends HttpRequestException {
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
    public ConnectException(String message, Throwable cause, String method, URI uri) {
        super(message, cause, method, uri);
    }

    /**
     * Creates an exception wrapping the underlying cause, with request context.
     *
     * @param cause  The underlying cause.
     * @param method The HTTP method of the request (e.g. {@code GET}, {@code POST}).
     * @param uri    The URI of the request.
     */
    public ConnectException(Throwable cause, String method, URI uri) {
        super(cause, method, uri);
    }

    /**
     * Creates an exception without request context (for tests).
     *
     * @param message The detail message.
     * @param cause   The underlying cause.
     */
    public ConnectException(String message, Throwable cause) {
        super(message, cause);
    }
}

