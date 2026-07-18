package software.frisby.web.client.exception;

import java.io.Serial;
import java.net.URI;
import java.net.http.HttpHeaders;

/**
 * Base class for HTTP {@code 5xx} server error responses.
 * <p>
 * A {@code 5xx} response indicates that the server encountered an error while
 * fulfilling an apparently valid request.  Catch this type to handle all server
 * errors in one place.
 * <p>
 * Use {@link #statusCode()} to distinguish between specific server error codes
 * when no concrete subclass exists for that code (e.g. {@code 502}, {@code 504}).
 *
 * <pre>{@code
 * try {
 *     client.get().path("/resource").send(Resource.class);
 * } catch (InternalServerErrorException e) {
 *     // 500 — unexpected server error; check server logs
 * } catch (ServiceUnavailableException e) {
 *     // 503 — overloaded; retry after a delay
 * } catch (ServerException e) {
 *     // catch-all for any other 5xx (502, 504, etc.)
 *     log.error("Server error {}: {}", e.statusCode(), e.uri());
 * }
 * }</pre>
 *
 * @see InternalServerErrorException
 * @see NotImplementedException
 * @see ServiceUnavailableException
 */
public class ServerException extends HttpResponseException {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Creates a {@code 5xx} exception with the full request context.
     *
     * @param method     The HTTP method of the request (e.g. {@code GET}, {@code POST}).
     * @param uri        The URI of the request.
     * @param statusCode The HTTP status code returned by the server.
     * @param headers    The response headers received from the server.
     * @param body       The response body, or {@code null} if none was received.
     */
    public ServerException(String method,
                           URI uri,
                           int statusCode,
                           HttpHeaders headers,
                           String body) {
        super(method, uri, statusCode, headers, body);
    }

    /**
     * Creates a {@code 5xx} exception with a status code and response body.
     *
     * @param statusCode The HTTP status code returned by the server.
     * @param body       The response body, or {@code null} if none was received.
     */
    public ServerException(int statusCode, String body) {
        super(statusCode, body);
    }

    /**
     * Creates a {@code 5xx} exception from a status code, without request context or body.
     *
     * @param statusCode The HTTP status code returned by the server.
     */
    public ServerException(int statusCode) {
        super(statusCode);
    }
}

