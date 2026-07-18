package software.frisby.web.client;

import software.frisby.web.client.exception.*;

import java.net.URI;
import java.net.http.HttpHeaders;

/**
 * Maps HTTP response status codes to the appropriate {@link HttpResponseException} subclass.
 */
final class ExceptionFactory {
    private ExceptionFactory() {
    }

    /**
     * Creates the most specific {@link HttpResponseException} subclass for the given
     * status code.  Falls back to {@link ClientException} for unrecognized {@code 4xx}
     * codes and {@link ServerException} for unrecognized {@code 5xx} codes.
     *
     * @param body       The raw response body, or {@code null} if the response had no body.
     * @param method     The HTTP method of the request.
     * @param uri        The URI of the request.
     * @param statusCode The HTTP response status code.
     * @param headers    The HTTP response headers.
     * @return The appropriate exception instance; never {@code null}.
     */
    static HttpResponseException create(String body,
                                        String method,
                                        URI uri,
                                        int statusCode,
                                        HttpHeaders headers) {
        return switch (statusCode) {
            case 400 -> new BadRequestException(method, uri, headers, body);
            case 401 -> new UnauthorizedException(method, uri, headers, body);
            case 403 -> new ForbiddenException(method, uri, headers, body);
            case 404 -> new NotFoundException(method, uri, headers, body);
            case 405 -> new MethodNotAllowedException(method, uri, headers, body);
            case 409 -> new ConflictException(method, uri, headers, body);
            case 413 -> new PayloadTooLargeException(method, uri, headers, body);
            case 422 -> new UnprocessableEntityException(method, uri, headers, body);
            case 429 -> new TooManyRequestsException(method, uri, headers, body);
            case 500 -> new InternalServerErrorException(method, uri, headers, body);
            case 501 -> new NotImplementedException(method, uri, headers, body);
            case 503 -> new ServiceUnavailableException(method, uri, headers, body);
            default -> {
                if (statusCode >= 400 && statusCode < 500) {
                    yield new ClientException(method, uri, statusCode, headers, body);
                } else {
                    yield new ServerException(method, uri, statusCode, headers, body);
                }
            }
        };
    }

    /**
     * Returns {@code true} if the status code represents an error response
     * ({@code 4xx} or {@code 5xx}).
     *
     * @param statusCode The HTTP status code.
     * @return {@code true} for {@code 4xx} and {@code 5xx} status codes.
     */
    static boolean isError(int statusCode) {
        return statusCode >= 400;
    }
}

