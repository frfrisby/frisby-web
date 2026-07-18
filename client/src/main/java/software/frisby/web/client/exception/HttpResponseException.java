package software.frisby.web.client.exception;

import java.io.Serial;
import java.net.URI;
import java.net.http.HttpHeaders;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Base class for all exceptions thrown when the server returns an HTTP error response.
 * <p>
 * The two main branches are:
 * <ul>
 *   <li>{@link ClientException} — {@code 4xx} responses; the request was well-formed
 *       but the server refused or could not process it</li>
 *   <li>{@link ServerException} — {@code 5xx} responses; the server encountered an
 *       error fulfilling an apparently valid request</li>
 * </ul>
 * <p>
 * Transport-level failures (connect timeout, read timeout, etc.) are represented by
 * {@link HttpRequestException} and its subclasses, which are separate from this hierarchy.
 *
 * <pre>{@code
 * try {
 *     User user = client.get()
 *             .path("/users/{id}", "id", userId)
 *             .send(User.class)
 *             .body();
 * } catch (NotFoundException e) {
 *     // 404 — resource does not exist
 * } catch (UnprocessableEntityException e) {
 *     // 422 — request body failed server-side validation
 * } catch (TooManyRequestsException e) {
 *     // 429 — rate limited; check e.headers() for Retry-After
 * } catch (ClientException e) {
 *     // catch-all for any other 4xx
 * } catch (ServiceUnavailableException e) {
 *     // 503 — server overloaded; candidate for retry
 * } catch (ServerException e) {
 *     // catch-all for any other 5xx
 * } catch (HttpRequestException e) {
 *     // connect timeout, read timeout, etc.
 * }
 * }</pre>
 */
public class HttpResponseException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

    private static final int BODY_PREVIEW_LENGTH = 200;

    // Headers stored as a map so instances remain serializable.
    /**
     * Serializable copy of the response headers.
     */
    private final Map<String, List<String>> headerMap;

    /**
     * The HTTP method of the request, or {@code null} if not set.
     */
    private final String method;

    /**
     * The URI of the request, or {@code null} if not set.
     */
    private final URI uri;

    /**
     * The raw numeric HTTP status code.
     */
    private final int statusCode;

    /**
     * The resolved {@link ResponseStatus} for this status code.
     */
    private final ResponseStatus status;

    /**
     * The raw response body, or {@code null} if the response had no body.
     */
    private final String body;

    /**
     * Creates an exception with the full request context.
     * <p>
     * This is the constructor used by the client implementation.
     *
     * @param method     The HTTP method of the request.
     * @param uri        The URI of the request.
     * @param statusCode The HTTP response status code.
     * @param headers    The HTTP response headers.
     * @param body       The raw response body, or {@code null} if the response had no body.
     */
    public HttpResponseException(String method,
                                 URI uri,
                                 int statusCode,
                                 HttpHeaders headers,
                                 String body) {
        super((String) null);

        this.method = method;
        this.uri = uri;
        this.statusCode = statusCode;
        this.status = ResponseStatus.fromCode(statusCode);
        this.headerMap = Map.copyOf(headers.map());
        this.body = body;
    }

    /**
     * Creates an exception with a status code and response body, without request context.
     * <p>
     * Useful in tests and custom error-handling code where the full request context
     * is not available.
     *
     * @param statusCode The HTTP response status code.
     * @param body       The raw response body, or {@code null} if the response had no body.
     */
    public HttpResponseException(int statusCode, String body) {
        super((String) null);

        this.method = null;
        this.uri = null;
        this.statusCode = statusCode;
        this.status = ResponseStatus.fromCode(statusCode);
        this.headerMap = Map.of();
        this.body = body;
    }

    /**
     * Creates an exception from a status code, without request context or body.
     *
     * @param statusCode The HTTP response status code.
     */
    public HttpResponseException(int statusCode) {
        this(statusCode, null);
    }

    /**
     * Returns the HTTP response status code.
     *
     * @return The status code (e.g. {@code 404}).
     */
    public int statusCode() {
        return statusCode;
    }

    /**
     * Returns the resolved {@link ResponseStatus} for this exception's status code.
     *
     * @return The response status; {@link ResponseStatus#UNKNOWN} for unlisted codes.
     */
    public ResponseStatus status() {
        return status;
    }

    /**
     * Returns the HTTP response headers.
     *
     * @return The response headers.
     */
    public HttpHeaders headers() {
        return HttpHeaders.of(headerMap, (name, value) -> true);
    }

    /**
     * Returns the HTTP method of the request that produced this error, when available.
     *
     * @return The method (e.g. {@code "GET"}), or empty if not set.
     */
    public Optional<String> method() {
        return Optional.ofNullable(method);
    }

    /**
     * Returns the URI of the request that produced this error, when available.
     *
     * @return The request URI, or empty if not set.
     */
    public Optional<URI> uri() {
        return Optional.ofNullable(uri);
    }

    /**
     * Returns the raw response body exactly as received from the server, when present.
     * <p>
     * The content may be plain text, a JSON structure, HTML, or any other format
     * depending on what the server returned.  Use the caller's {@code JsonSerializer}
     * to deserialize a structured body.
     *
     * @return The raw response body, or empty if the response had no body.
     */
    public Optional<String> body() {
        return Optional.ofNullable(body);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        sb.append("HTTP ").append(statusCode).append(" ").append(status.reason());

        if (null != method) {
            sb.append(" — ").append(method).append(" ").append(uri);
        }

        if (null != body && !body.isBlank()) {
            sb.append(": ");

            if (body.length() > BODY_PREVIEW_LENGTH) {
                sb.append(body, 0, BODY_PREVIEW_LENGTH).append("…");
            } else {
                sb.append(body);
            }
        }

        return sb.toString();
    }
}

