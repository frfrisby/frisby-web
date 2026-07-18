package software.frisby.web.client.exception;

import java.io.Serial;
import java.net.URI;
import java.net.http.HttpHeaders;

/**
 * Base class for HTTP {@code 4xx} client error responses.
 * <p>
 * A {@code 4xx} response indicates that the request was well-formed but the server
 * refused or could not process it.  Catch this type to handle all client errors in
 * one place, or catch a specific subclass for targeted handling.
 *
 * <pre>{@code
 * try {
 *     client.get().path("/resource").send(Resource.class);
 * } catch (NotFoundException e) {
 *     // 404 — resource does not exist
 * } catch (ClientException e) {
 *     // catch-all for any other 4xx
 *     log.warn("Client error: {}", e.statusCode());
 * }
 * }</pre>
 *
 * @see BadRequestException
 * @see UnauthorizedException
 * @see ForbiddenException
 * @see NotFoundException
 * @see ConflictException
 * @see PayloadTooLargeException
 * @see MethodNotAllowedException
 * @see UnprocessableEntityException
 * @see TooManyRequestsException
 */
public class ClientException extends HttpResponseException {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Creates an exception with the full request context.
     *
     * @param method     The HTTP method of the request.
     * @param uri        The URI of the request.
     * @param statusCode The HTTP response status code.
     * @param headers    The HTTP response headers.
     * @param body       The raw response body, or {@code null} if the response had no body.
     */
    public ClientException(String method,
                           URI uri,
                           int statusCode,
                           HttpHeaders headers,
                           String body) {
        super(method, uri, statusCode, headers, body);
    }

    /**
     * Creates an exception with a status code and response body.
     *
     * @param statusCode The HTTP response status code.
     * @param body       The raw response body, or {@code null} if the response had no body.
     */
    public ClientException(int statusCode, String body) {
        super(statusCode, body);
    }

    /**
     * Creates an exception from a status code, without request context or body.
     *
     * @param statusCode The HTTP response status code.
     */
    public ClientException(int statusCode) {
        super(statusCode);
    }
}

