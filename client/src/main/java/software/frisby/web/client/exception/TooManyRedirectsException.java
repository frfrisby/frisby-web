package software.frisby.web.client.exception;

import software.frisby.web.client.ClientConfigurationBuilder;

import java.io.Serial;
import java.net.URI;
import java.net.http.HttpClient;

/**
 * Thrown when the client cannot complete a request because it followed too many
 * redirects in a row.
 * <p>
 * This typically indicates a redirect loop (A→B→A) or an unusually deep redirect
 * chain that exceeds the JDK's internal limit.  Check the server-side redirect
 * configuration or consider using
 * {@link ClientConfigurationBuilder#redirectPolicy} with
 * {@link HttpClient.Redirect#NEVER} to diagnose the redirect path.
 */
public final class TooManyRedirectsException extends HttpRequestException {
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
    public TooManyRedirectsException(String message, Throwable cause, String method, URI uri) {
        super(message, cause, method, uri);
    }

    /**
     * Creates an exception wrapping the underlying cause, with request context.
     *
     * @param cause  The underlying cause.
     * @param method The HTTP method of the request (e.g. {@code GET}, {@code POST}).
     * @param uri    The URI of the request.
     */
    public TooManyRedirectsException(Throwable cause, String method, URI uri) {
        super(cause, method, uri);
    }

    /**
     * Creates an exception without request context (for tests).
     *
     * @param message The detail message.
     * @param cause   The underlying cause.
     */
    public TooManyRedirectsException(String message, Throwable cause) {
        super(message, cause);
    }
}

