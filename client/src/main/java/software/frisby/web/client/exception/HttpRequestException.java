package software.frisby.web.client.exception;

import java.net.URI;
import java.util.Optional;

/**
 * Base class for transport-level failures that occur before or during an HTTP request,
 * without necessarily receiving an HTTP response.
 * <p>
 * This hierarchy is the counterpart to {@link HttpResponseException}:
 * <ul>
 *   <li>{@link HttpResponseException} — the server responded with an error status code</li>
 *   <li>{@link HttpRequestException} — the request could not be delivered to the server</li>
 * </ul>
 * <p>
 * Concrete subclasses cover specific transport failure modes:
 * <ul>
 *   <li>{@link ConnectException} — the TCP connection was refused or the host was unreachable</li>
 *   <li>{@link ConnectTimeoutException} — no connection could be established within the
 *       configured connect timeout</li>
 *   <li>{@link ReadTimeoutException} — a connection was established but the server did not
 *       respond within the configured read timeout</li>
 *   <li>{@link TransportException} — an IO-level failure not covered by a more specific
 *       subclass (e.g. TLS handshake failure, connection reset, protocol error)</li>
 *   <li>{@link AbortedException} — the request was interrupted (thread interruption or
 *       response subscriber cancellation)</li>
 *   <li>{@link TooManyRedirectsException} — the redirect limit was exceeded, indicating
 *       a redirect loop or an unusually deep redirect chain</li>
 * </ul>
 *
 * <pre>{@code
 * try {
 *     client.get().path("/resource").send(Resource.class);
 * } catch (ConnectTimeoutException e) {
 *     // could not establish a connection in time
 * } catch (ReadTimeoutException e) {
 *     // connected but server did not respond in time
 * } catch (HttpRequestException e) {
 *     // catch-all for any other transport failure
 * }
 * }</pre>
 */
public class HttpRequestException extends RuntimeException {
    /**
     * The HTTP method of the request that caused this failure, or {@code null} if unknown.
     */
    private final String method;

    /**
     * The URI of the request that caused this failure, or {@code null} if unknown.
     */
    private final URI uri;

    /**
     * Creates an exception with the full request context.
     *
     * @param message The detail message.
     * @param cause   The underlying cause.
     * @param method  The HTTP method of the request.
     * @param uri     The URI of the request.
     */
    public HttpRequestException(String message,
                                Throwable cause,
                                String method,
                                URI uri) {
        super(message, cause);

        this.method = method;
        this.uri = uri;
    }

    /**
     * Creates an exception with the full request context, using the cause's message.
     *
     * @param cause  The underlying cause.
     * @param method The HTTP method of the request.
     * @param uri    The URI of the request.
     */
    public HttpRequestException(Throwable cause, String method, URI uri) {
        this(null != cause ? cause.toString() : null, cause, method, uri);
    }

    /**
     * Creates an exception without request context.
     * <p>
     * Used in situations where the method and URI are not yet known, or in tests.
     *
     * @param message The detail message.
     * @param cause   The underlying cause.
     */
    public HttpRequestException(String message, Throwable cause) {
        super(message, cause);

        this.method = null;
        this.uri = null;
    }

    /**
     * Returns the HTTP method of the request that caused this failure, when available.
     *
     * @return The method (e.g. {@code "GET"}), or empty if not set.
     */
    public Optional<String> method() {
        return Optional.ofNullable(method);
    }

    /**
     * Returns the URI of the request that caused this failure, when available.
     *
     * @return The request URI, or empty if not set.
     */
    public Optional<URI> uri() {
        return Optional.ofNullable(uri);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        sb.append(getClass().getSimpleName());

        if (null != method) {
            sb.append(" — ").append(method).append(" ").append(uri);
        }

        String message = getMessage();

        if (null != message && !message.isBlank()) {
            sb.append(": ").append(message);
        }

        return sb.toString();
    }
}

