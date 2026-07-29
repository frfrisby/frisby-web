package software.frisby.web.server;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.MessageBodyWriter;
import software.frisby.core.validation.Durations;
import software.frisby.core.validation.Sequences;
import software.frisby.web.serial.JsonSerializer;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Static factory for creating properly configured {@link WebApplicationException} instances.
 * <p>
 * Each factory method constructs a {@link WebApplicationException} with an embedded
 * {@link Response} carrying the correct HTTP status code, an optional body, and an optional
 * cause.  This eliminates the verbose {@link Response}-building boilerplate that Jersey's own
 * exception constructors require and avoids their common pitfalls — most notably, the
 * unwanted {@code WWW-Authenticate} challenge header that
 * {@link NotAuthorizedException} adds when constructed with anything other
 * than a pre-built {@link Response}.
 *
 * <h2>Body variants</h2>
 * <p>
 * Three body variants are available for each status code:
 * <ul>
 *   <li><strong>No body</strong> — the response is sent with an empty body.</li>
 *   <li><strong>{@link String} body</strong> — sent as {@code text/plain; charset=UTF-8}.</li>
 *   <li><strong>{@link Object} body</strong> — sent as {@code application/json}, serialized
 *       at response-write time by the server's configured
 *       {@link JsonSerializer} via an internal {@link MessageBodyWriter}.
 *       Do <em>not</em> pass a {@link String} here — use the {@link String} overload for
 *       plain-text bodies.</li>
 * </ul>
 *
 * <h2>Cause</h2>
 * <p>
 * All overloads accept an optional {@link Throwable} cause, which is attached to the
 * exception for logging and root-cause analysis without leaking internals to the caller.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Text body
 * throw HttpErrors.badRequest("'name' must not be blank.");
 *
 * // JSON body — serialized by the configured JsonSerializer
 * throw HttpErrors.unprocessableEntity(new ValidationErrorBody(violations));
 *
 * // No body exposed to caller; cause logged server-side
 * throw HttpErrors.serviceUnavailable("Key service unreachable.", upstreamException);
 *
 * // 401 without a WWW-Authenticate challenge header
 * throw HttpErrors.unauthorized("Token has expired.");
 * }</pre>
 */
public final class HttpErrors {
    private static final String ALLOWED_METHODS_ARGUMENT_NAME = "allowedMethods";
    private static final String RETRY_AFTER_ARGUMENT_NAME = "retryAfter";
    private static final String ALLOW_HEADER = "Allow";
    private static final String RETRY_AFTER_HEADER = "Retry-After";

    private HttpErrors() {
    }

    // -------------------------------------------------------------------------
    // Private response builders
    // -------------------------------------------------------------------------

    private static Response textResponse(int status, String message) {
        return Response.status(status).entity(message).type(MediaType.TEXT_PLAIN).build();
    }

    private static Response jsonResponse(int status, Object body) {
        return Response.status(status).entity(body).type(MediaType.APPLICATION_JSON).build();
    }

    private static Response emptyResponse(int status) {
        return Response.status(status).build();
    }

    private static String verbsToHeader(HttpVerb[] allowedMethods) {
        Sequences.notEmpty(ALLOWED_METHODS_ARGUMENT_NAME, allowedMethods);

        return new LinkedHashSet<>(List.of(allowedMethods))
                .stream()
                .map(HttpVerb::name)
                .collect(Collectors.joining(", "));
    }

    private static Response emptyResponseAllow(HttpVerb[] allowedMethods) {
        return Response
                .status(405)
                .header(ALLOW_HEADER, verbsToHeader(allowedMethods))
                .build();
    }

    private static Response textResponseAllow(String message, HttpVerb[] allowedMethods) {
        return Response
                .status(405)
                .entity(message)
                .type(MediaType.TEXT_PLAIN)
                .header(ALLOW_HEADER, verbsToHeader(allowedMethods))
                .build();
    }

    private static Response jsonResponseAllow(Object body, HttpVerb[] allowedMethods) {
        return Response
                .status(405)
                .entity(body)
                .type(MediaType.APPLICATION_JSON)
                .header(ALLOW_HEADER, verbsToHeader(allowedMethods))
                .build();
    }

    private static Response emptyResponseRetryAfter(int status, Duration retryAfter) {
        Durations.notNegative(RETRY_AFTER_ARGUMENT_NAME, retryAfter);

        return Response.status(status).header(RETRY_AFTER_HEADER, retryAfter.toSeconds()).build();
    }

    private static Response textResponseRetryAfter(int status, String message, Duration retryAfter) {
        Durations.notNegative(RETRY_AFTER_ARGUMENT_NAME, retryAfter);

        return Response.status(status).entity(message).type(MediaType.TEXT_PLAIN)
                .header(RETRY_AFTER_HEADER, retryAfter.toSeconds()).build();
    }

    private static Response jsonResponseRetryAfter(int status, Object body, Duration retryAfter) {
        Durations.notNegative(RETRY_AFTER_ARGUMENT_NAME, retryAfter);

        return Response.status(status).entity(body).type(MediaType.APPLICATION_JSON)
                .header(RETRY_AFTER_HEADER, retryAfter.toSeconds()).build();
    }

    // =========================================================================
    // 4xx Client Errors
    // =========================================================================

    // -------------------------------------------------------------------------
    // 400 Bad Request
    // -------------------------------------------------------------------------

    /**
     * Returns a {@code 400 Bad Request} exception.
     *
     * @return a {@link BadRequestException} with HTTP status {@code 400}; never {@code null}.
     */
    public static BadRequestException badRequest() {
        return new BadRequestException(emptyResponse(400));
    }

    /**
     * Returns a {@code 400 Bad Request} exception with a {@code text/plain} body.
     *
     * @param message the response body text; sent to the caller as {@code text/plain}.
     * @return a {@link BadRequestException} with HTTP status {@code 400}; never {@code null}.
     */
    public static BadRequestException badRequest(String message) {
        return new BadRequestException(message, textResponse(400, message));
    }

    /**
     * Returns a {@code 400 Bad Request} exception with an {@code application/json} body.
     *
     * @param body the response entity; serialized to {@code application/json}
     *             by the server's configured
     *             {@link JsonSerializer}.
     * @return a {@link BadRequestException} with HTTP status {@code 400}; never {@code null}.
     */
    public static BadRequestException badRequest(Object body) {
        return new BadRequestException(jsonResponse(400, body));
    }

    /**
     * Returns a {@code 400 Bad Request} exception with the given cause and no response body.
     *
     * @param cause the exception to attach as the cause; available for server-side
     *              logging but not exposed in the response body.
     * @return a {@link BadRequestException} with HTTP status {@code 400}; never {@code null}.
     */
    public static BadRequestException badRequest(Throwable cause) {
        return new BadRequestException(emptyResponse(400), cause);
    }

    /**
     * Returns a {@code 400 Bad Request} exception with a {@code text/plain} body and the given cause.
     *
     * @param message the response body text; sent to the caller as {@code text/plain}.
     * @param cause   the exception to attach as the cause; available for server-side
     *                logging but not exposed in the response body.
     * @return a {@link BadRequestException} with HTTP status {@code 400}; never {@code null}.
     */
    public static BadRequestException badRequest(String message, Throwable cause) {
        return new BadRequestException(message, textResponse(400, message), cause);
    }

    /**
     * Returns a {@code 400 Bad Request} exception with an {@code application/json} body and the given cause.
     *
     * @param body  the response entity; serialized to {@code application/json}
     *              by the server's configured
     *              {@link JsonSerializer}.
     * @param cause the exception to attach as the cause; available for server-side
     *              logging but not exposed in the response body.
     * @return a {@link BadRequestException} with HTTP status {@code 400}; never {@code null}.
     */
    public static BadRequestException badRequest(Object body, Throwable cause) {
        return new BadRequestException(jsonResponse(400, body), cause);
    }

    // -------------------------------------------------------------------------
    // 401 Unauthorized
    // -------------------------------------------------------------------------
    // All overloads use the Response-based constructor to suppress the
    // WWW-Authenticate challenge header that Jersey adds when using
    // NotAuthorizedException's challenge-based constructors directly.

    /**
     * Returns a {@code 401 Unauthorized} exception with no {@code WWW-Authenticate} challenge.
     *
     * @return a {@link NotAuthorizedException} with HTTP status {@code 401}; never {@code null}.
     */
    public static NotAuthorizedException unauthorized() {
        return new NotAuthorizedException(emptyResponse(401));
    }

    /**
     * Returns a {@code 401 Unauthorized} exception with a {@code text/plain} body and no {@code WWW-Authenticate} challenge.
     *
     * @param message the response body text; sent to the caller as {@code text/plain}.
     * @return a {@link NotAuthorizedException} with HTTP status {@code 401}; never {@code null}.
     */
    public static NotAuthorizedException unauthorized(String message) {
        return new NotAuthorizedException(message, textResponse(401, message));
    }

    /**
     * Returns a {@code 401 Unauthorized} exception with an {@code application/json} body and no {@code WWW-Authenticate} challenge.
     *
     * @param body the response entity; serialized to {@code application/json}
     *             by the server's configured
     *             {@link JsonSerializer}.
     * @return a {@link NotAuthorizedException} with HTTP status {@code 401}; never {@code null}.
     */
    public static NotAuthorizedException unauthorized(Object body) {
        return new NotAuthorizedException(jsonResponse(401, body));
    }

    /**
     * Returns a {@code 401 Unauthorized} exception with the given cause, no response body, and no {@code WWW-Authenticate} challenge.
     *
     * @param cause the exception to attach as the cause; available for server-side
     *              logging but not exposed in the response body.
     * @return a {@link NotAuthorizedException} with HTTP status {@code 401}; never {@code null}.
     */
    public static NotAuthorizedException unauthorized(Throwable cause) {
        return new NotAuthorizedException(emptyResponse(401), cause);
    }

    /**
     * Returns a {@code 401 Unauthorized} exception with a {@code text/plain} body, the given cause, and no {@code WWW-Authenticate} challenge.
     *
     * @param message the response body text; sent to the caller as {@code text/plain}.
     * @param cause   the exception to attach as the cause; available for server-side
     *                logging but not exposed in the response body.
     * @return a {@link NotAuthorizedException} with HTTP status {@code 401}; never {@code null}.
     */
    public static NotAuthorizedException unauthorized(String message, Throwable cause) {
        return new NotAuthorizedException(message, textResponse(401, message), cause);
    }

    /**
     * Returns a {@code 401 Unauthorized} exception with an {@code application/json} body, the given cause, and no {@code WWW-Authenticate} challenge.
     *
     * @param body  the response entity; serialized to {@code application/json}
     *              by the server's configured
     *              {@link JsonSerializer}.
     * @param cause the exception to attach as the cause; available for server-side
     *              logging but not exposed in the response body.
     * @return a {@link NotAuthorizedException} with HTTP status {@code 401}; never {@code null}.
     */
    public static NotAuthorizedException unauthorized(Object body, Throwable cause) {
        return new NotAuthorizedException(jsonResponse(401, body), cause);
    }

    // -------------------------------------------------------------------------
    // 403 Forbidden
    // -------------------------------------------------------------------------

    /**
     * Returns a {@code 403 Forbidden} exception.
     *
     * @return a {@link ForbiddenException} with HTTP status {@code 403}; never {@code null}.
     */
    public static ForbiddenException forbidden() {
        return new ForbiddenException(emptyResponse(403));
    }

    /**
     * Returns a {@code 403 Forbidden} exception with a {@code text/plain} body.
     *
     * @param message the response body text; sent to the caller as {@code text/plain}.
     * @return a {@link ForbiddenException} with HTTP status {@code 403}; never {@code null}.
     */
    public static ForbiddenException forbidden(String message) {
        return new ForbiddenException(message, textResponse(403, message));
    }

    /**
     * Returns a {@code 403 Forbidden} exception with an {@code application/json} body.
     *
     * @param body the response entity; serialized to {@code application/json}
     *             by the server's configured
     *             {@link JsonSerializer}.
     * @return a {@link ForbiddenException} with HTTP status {@code 403}; never {@code null}.
     */
    public static ForbiddenException forbidden(Object body) {
        return new ForbiddenException(jsonResponse(403, body));
    }

    /**
     * Returns a {@code 403 Forbidden} exception with the given cause and no response body.
     *
     * @param cause the exception to attach as the cause; available for server-side
     *              logging but not exposed in the response body.
     * @return a {@link ForbiddenException} with HTTP status {@code 403}; never {@code null}.
     */
    public static ForbiddenException forbidden(Throwable cause) {
        return new ForbiddenException(emptyResponse(403), cause);
    }

    /**
     * Returns a {@code 403 Forbidden} exception with a {@code text/plain} body and the given cause.
     *
     * @param message the response body text; sent to the caller as {@code text/plain}.
     * @param cause   the exception to attach as the cause; available for server-side
     *                logging but not exposed in the response body.
     * @return a {@link ForbiddenException} with HTTP status {@code 403}; never {@code null}.
     */
    public static ForbiddenException forbidden(String message, Throwable cause) {
        return new ForbiddenException(message, textResponse(403, message), cause);
    }

    /**
     * Returns a {@code 403 Forbidden} exception with an {@code application/json} body and the given cause.
     *
     * @param body  the response entity; serialized to {@code application/json}
     *              by the server's configured
     *              {@link JsonSerializer}.
     * @param cause the exception to attach as the cause; available for server-side
     *              logging but not exposed in the response body.
     * @return a {@link ForbiddenException} with HTTP status {@code 403}; never {@code null}.
     */
    public static ForbiddenException forbidden(Object body, Throwable cause) {
        return new ForbiddenException(jsonResponse(403, body), cause);
    }

    // -------------------------------------------------------------------------
    // 404 Not Found
    // -------------------------------------------------------------------------

    /**
     * Returns a {@code 404 Not Found} exception.
     *
     * @return a {@link NotFoundException} with HTTP status {@code 404}; never {@code null}.
     */
    public static NotFoundException notFound() {
        return new NotFoundException(emptyResponse(404));
    }

    /**
     * Returns a {@code 404 Not Found} exception with a {@code text/plain} body.
     *
     * @param message the response body text; sent to the caller as {@code text/plain}.
     * @return a {@link NotFoundException} with HTTP status {@code 404}; never {@code null}.
     */
    public static NotFoundException notFound(String message) {
        return new NotFoundException(message, textResponse(404, message));
    }

    /**
     * Returns a {@code 404 Not Found} exception with an {@code application/json} body.
     *
     * @param body the response entity; serialized to {@code application/json}
     *             by the server's configured
     *             {@link JsonSerializer}.
     * @return a {@link NotFoundException} with HTTP status {@code 404}; never {@code null}.
     */
    public static NotFoundException notFound(Object body) {
        return new NotFoundException(jsonResponse(404, body));
    }

    /**
     * Returns a {@code 404 Not Found} exception with the given cause and no response body.
     *
     * @param cause the exception to attach as the cause; available for server-side
     *              logging but not exposed in the response body.
     * @return a {@link NotFoundException} with HTTP status {@code 404}; never {@code null}.
     */
    public static NotFoundException notFound(Throwable cause) {
        return new NotFoundException(emptyResponse(404), cause);
    }

    /**
     * Returns a {@code 404 Not Found} exception with a {@code text/plain} body and the given cause.
     *
     * @param message the response body text; sent to the caller as {@code text/plain}.
     * @param cause   the exception to attach as the cause; available for server-side
     *                logging but not exposed in the response body.
     * @return a {@link NotFoundException} with HTTP status {@code 404}; never {@code null}.
     */
    public static NotFoundException notFound(String message, Throwable cause) {
        return new NotFoundException(message, textResponse(404, message), cause);
    }

    /**
     * Returns a {@code 404 Not Found} exception with an {@code application/json} body and the given cause.
     *
     * @param body  the response entity; serialized to {@code application/json}
     *              by the server's configured
     *              {@link JsonSerializer}.
     * @param cause the exception to attach as the cause; available for server-side
     *              logging but not exposed in the response body.
     * @return a {@link NotFoundException} with HTTP status {@code 404}; never {@code null}.
     */
    public static NotFoundException notFound(Object body, Throwable cause) {
        return new NotFoundException(jsonResponse(404, body), cause);
    }

    // -------------------------------------------------------------------------
    // 405 Method Not Allowed
    // -------------------------------------------------------------------------

    /**
     * Returns a {@code 405 Method Not Allowed} exception.
     *
     * @param allowedMethods One or more {@link HttpVerb} values that are permitted for the
     *                       target resource; must not be {@code null} or empty.  Duplicate
     *                       values are silently deduplicated.  Sent as the {@code Allow}
     *                       response header.
     * @return a {@link NotAllowedException} with HTTP status {@code 405}; never {@code null}.
     * @throws software.frisby.core.validation.NullValueException       if {@code allowedMethods} is null.
     * @throws software.frisby.core.validation.MissingElementsException if {@code allowedMethods} is empty.
     */
    public static NotAllowedException methodNotAllowed(HttpVerb... allowedMethods) {
        return new NotAllowedException(emptyResponseAllow(allowedMethods));
    }

    /**
     * Returns a {@code 405 Method Not Allowed} exception with a {@code text/plain} body.
     *
     * @param message        The response body text; sent to the caller as {@code text/plain}.
     * @param allowedMethods One or more {@link HttpVerb} values that are permitted for the
     *                       target resource; must not be {@code null} or empty.  Duplicate
     *                       values are silently deduplicated.  Sent as the {@code Allow}
     *                       response header.
     * @return a {@link NotAllowedException} with HTTP status {@code 405}; never {@code null}.
     * @throws software.frisby.core.validation.NullValueException       if {@code allowedMethods} is null.
     * @throws software.frisby.core.validation.MissingElementsException if {@code allowedMethods} is empty.
     */
    public static NotAllowedException methodNotAllowed(String message, HttpVerb... allowedMethods) {
        return new NotAllowedException(message, textResponseAllow(message, allowedMethods));
    }

    /**
     * Returns a {@code 405 Method Not Allowed} exception with an {@code application/json} body.
     *
     * @param body           The response entity; serialized to {@code application/json}
     *                       by the server's configured {@link JsonSerializer}.
     * @param allowedMethods One or more {@link HttpVerb} values that are permitted for the
     *                       target resource; must not be {@code null} or empty.  Duplicate
     *                       values are silently deduplicated.  Sent as the {@code Allow}
     *                       response header.
     * @return a {@link NotAllowedException} with HTTP status {@code 405}; never {@code null}.
     * @throws software.frisby.core.validation.NullValueException       if {@code allowedMethods} is null.
     * @throws software.frisby.core.validation.MissingElementsException if {@code allowedMethods} is empty.
     */
    public static NotAllowedException methodNotAllowed(Object body, HttpVerb... allowedMethods) {
        return new NotAllowedException(jsonResponseAllow(body, allowedMethods));
    }

    /**
     * Returns a {@code 405 Method Not Allowed} exception with the given cause and no response body.
     *
     * @param cause          The exception to attach as the cause; available for server-side
     *                       logging but not exposed in the response body.
     * @param allowedMethods One or more {@link HttpVerb} values that are permitted for the
     *                       target resource; must not be {@code null} or empty.  Duplicate
     *                       values are silently deduplicated.  Sent as the {@code Allow}
     *                       response header.
     * @return a {@link NotAllowedException} with HTTP status {@code 405}; never {@code null}.
     * @throws software.frisby.core.validation.NullValueException       if {@code allowedMethods} is null.
     * @throws software.frisby.core.validation.MissingElementsException if {@code allowedMethods} is empty.
     */
    public static NotAllowedException methodNotAllowed(Throwable cause, HttpVerb... allowedMethods) {
        return new NotAllowedException(emptyResponseAllow(allowedMethods), cause);
    }

    /**
     * Returns a {@code 405 Method Not Allowed} exception with a {@code text/plain} body and the given cause.
     *
     * @param message        The response body text; sent to the caller as {@code text/plain}.
     * @param cause          The exception to attach as the cause; available for server-side
     *                       logging but not exposed in the response body.
     * @param allowedMethods One or more {@link HttpVerb} values that are permitted for the
     *                       target resource; must not be {@code null} or empty.  Duplicate
     *                       values are silently deduplicated.  Sent as the {@code Allow}
     *                       response header.
     * @return a {@link NotAllowedException} with HTTP status {@code 405}; never {@code null}.
     * @throws software.frisby.core.validation.NullValueException       if {@code allowedMethods} is null.
     * @throws software.frisby.core.validation.MissingElementsException if {@code allowedMethods} is empty.
     */
    public static NotAllowedException methodNotAllowed(String message, Throwable cause, HttpVerb... allowedMethods) {
        return new NotAllowedException(message, textResponseAllow(message, allowedMethods), cause);
    }

    /**
     * Returns a {@code 405 Method Not Allowed} exception with an {@code application/json} body and the given cause.
     *
     * @param body           The response entity; serialized to {@code application/json}
     *                       by the server's configured {@link JsonSerializer}.
     * @param cause          The exception to attach as the cause; available for server-side
     *                       logging but not exposed in the response body.
     * @param allowedMethods One or more {@link HttpVerb} values that are permitted for the
     *                       target resource; must not be {@code null} or empty.  Duplicate
     *                       values are silently deduplicated.  Sent as the {@code Allow}
     *                       response header.
     * @return a {@link NotAllowedException} with HTTP status {@code 405}; never {@code null}.
     * @throws software.frisby.core.validation.NullValueException       if {@code allowedMethods} is null.
     * @throws software.frisby.core.validation.MissingElementsException if {@code allowedMethods} is empty.
     */
    public static NotAllowedException methodNotAllowed(Object body, Throwable cause, HttpVerb... allowedMethods) {
        return new NotAllowedException(jsonResponseAllow(body, allowedMethods), cause);
    }

    // -------------------------------------------------------------------------
    // 406 Not Acceptable
    // -------------------------------------------------------------------------

    /**
     * Returns a {@code 406 Not Acceptable} exception.
     *
     * @return a {@link NotAcceptableException} with HTTP status {@code 406}; never {@code null}.
     */
    public static NotAcceptableException notAcceptable() {
        return new NotAcceptableException(emptyResponse(406));
    }

    /**
     * Returns a {@code 406 Not Acceptable} exception with a {@code text/plain} body.
     *
     * @param message the response body text; sent to the caller as {@code text/plain}.
     * @return a {@link NotAcceptableException} with HTTP status {@code 406}; never {@code null}.
     */
    public static NotAcceptableException notAcceptable(String message) {
        return new NotAcceptableException(message, textResponse(406, message));
    }

    /**
     * Returns a {@code 406 Not Acceptable} exception with an {@code application/json} body.
     *
     * @param body the response entity; serialized to {@code application/json}
     *             by the server's configured
     *             {@link JsonSerializer}.
     * @return a {@link NotAcceptableException} with HTTP status {@code 406}; never {@code null}.
     */
    public static NotAcceptableException notAcceptable(Object body) {
        return new NotAcceptableException(jsonResponse(406, body));
    }

    /**
     * Returns a {@code 406 Not Acceptable} exception with the given cause and no response body.
     *
     * @param cause the exception to attach as the cause; available for server-side
     *              logging but not exposed in the response body.
     * @return a {@link NotAcceptableException} with HTTP status {@code 406}; never {@code null}.
     */
    public static NotAcceptableException notAcceptable(Throwable cause) {
        return new NotAcceptableException(emptyResponse(406), cause);
    }

    /**
     * Returns a {@code 406 Not Acceptable} exception with a {@code text/plain} body and the given cause.
     *
     * @param message the response body text; sent to the caller as {@code text/plain}.
     * @param cause   the exception to attach as the cause; available for server-side
     *                logging but not exposed in the response body.
     * @return a {@link NotAcceptableException} with HTTP status {@code 406}; never {@code null}.
     */
    public static NotAcceptableException notAcceptable(String message, Throwable cause) {
        return new NotAcceptableException(message, textResponse(406, message), cause);
    }

    /**
     * Returns a {@code 406 Not Acceptable} exception with an {@code application/json} body and the given cause.
     *
     * @param body  the response entity; serialized to {@code application/json}
     *              by the server's configured
     *              {@link JsonSerializer}.
     * @param cause the exception to attach as the cause; available for server-side
     *              logging but not exposed in the response body.
     * @return a {@link NotAcceptableException} with HTTP status {@code 406}; never {@code null}.
     */
    public static NotAcceptableException notAcceptable(Object body, Throwable cause) {
        return new NotAcceptableException(jsonResponse(406, body), cause);
    }

    // -------------------------------------------------------------------------
    // 408 Request Timeout
    // -------------------------------------------------------------------------

    /**
     * Returns a {@code 408 Request Timeout} exception.
     *
     * @return a {@link ClientErrorException} with HTTP status {@code 408}; never {@code null}.
     */
    public static ClientErrorException requestTimeout() {
        return new ClientErrorException(emptyResponse(408));
    }

    /**
     * Returns a {@code 408 Request Timeout} exception with a {@code text/plain} body.
     *
     * @param message the response body text; sent to the caller as {@code text/plain}.
     * @return a {@link ClientErrorException} with HTTP status {@code 408}; never {@code null}.
     */
    public static ClientErrorException requestTimeout(String message) {
        return new ClientErrorException(message, textResponse(408, message));
    }

    /**
     * Returns a {@code 408 Request Timeout} exception with an {@code application/json} body.
     *
     * @param body the response entity; serialized to {@code application/json}
     *             by the server's configured
     *             {@link JsonSerializer}.
     * @return a {@link ClientErrorException} with HTTP status {@code 408}; never {@code null}.
     */
    public static ClientErrorException requestTimeout(Object body) {
        return new ClientErrorException(jsonResponse(408, body));
    }

    /**
     * Returns a {@code 408 Request Timeout} exception with the given cause and no response body.
     *
     * @param cause the exception to attach as the cause; available for server-side
     *              logging but not exposed in the response body.
     * @return a {@link ClientErrorException} with HTTP status {@code 408}; never {@code null}.
     */
    public static ClientErrorException requestTimeout(Throwable cause) {
        return new ClientErrorException(emptyResponse(408), cause);
    }

    /**
     * Returns a {@code 408 Request Timeout} exception with a {@code text/plain} body and the given cause.
     *
     * @param message the response body text; sent to the caller as {@code text/plain}.
     * @param cause   the exception to attach as the cause; available for server-side
     *                logging but not exposed in the response body.
     * @return a {@link ClientErrorException} with HTTP status {@code 408}; never {@code null}.
     */
    public static ClientErrorException requestTimeout(String message, Throwable cause) {
        return new ClientErrorException(message, textResponse(408, message), cause);
    }

    /**
     * Returns a {@code 408 Request Timeout} exception with an {@code application/json} body and the given cause.
     *
     * @param body  the response entity; serialized to {@code application/json}
     *              by the server's configured
     *              {@link JsonSerializer}.
     * @param cause the exception to attach as the cause; available for server-side
     *              logging but not exposed in the response body.
     * @return a {@link ClientErrorException} with HTTP status {@code 408}; never {@code null}.
     */
    public static ClientErrorException requestTimeout(Object body, Throwable cause) {
        return new ClientErrorException(jsonResponse(408, body), cause);
    }

    // -------------------------------------------------------------------------
    // 409 Conflict
    // -------------------------------------------------------------------------

    /**
     * Returns a {@code 409 Conflict} exception.
     *
     * @return a {@link ClientErrorException} with HTTP status {@code 409}; never {@code null}.
     */
    public static ClientErrorException conflict() {
        return new ClientErrorException(emptyResponse(409));
    }

    /**
     * Returns a {@code 409 Conflict} exception with a {@code text/plain} body.
     *
     * @param message the response body text; sent to the caller as {@code text/plain}.
     * @return a {@link ClientErrorException} with HTTP status {@code 409}; never {@code null}.
     */
    public static ClientErrorException conflict(String message) {
        return new ClientErrorException(message, textResponse(409, message));
    }

    /**
     * Returns a {@code 409 Conflict} exception with an {@code application/json} body.
     *
     * @param body the response entity; serialized to {@code application/json}
     *             by the server's configured
     *             {@link JsonSerializer}.
     * @return a {@link ClientErrorException} with HTTP status {@code 409}; never {@code null}.
     */
    public static ClientErrorException conflict(Object body) {
        return new ClientErrorException(jsonResponse(409, body));
    }

    /**
     * Returns a {@code 409 Conflict} exception with the given cause and no response body.
     *
     * @param cause the exception to attach as the cause; available for server-side
     *              logging but not exposed in the response body.
     * @return a {@link ClientErrorException} with HTTP status {@code 409}; never {@code null}.
     */
    public static ClientErrorException conflict(Throwable cause) {
        return new ClientErrorException(emptyResponse(409), cause);
    }

    /**
     * Returns a {@code 409 Conflict} exception with a {@code text/plain} body and the given cause.
     *
     * @param message the response body text; sent to the caller as {@code text/plain}.
     * @param cause   the exception to attach as the cause; available for server-side
     *                logging but not exposed in the response body.
     * @return a {@link ClientErrorException} with HTTP status {@code 409}; never {@code null}.
     */
    public static ClientErrorException conflict(String message, Throwable cause) {
        return new ClientErrorException(message, textResponse(409, message), cause);
    }

    /**
     * Returns a {@code 409 Conflict} exception with an {@code application/json} body and the given cause.
     *
     * @param body  the response entity; serialized to {@code application/json}
     *              by the server's configured
     *              {@link JsonSerializer}.
     * @param cause the exception to attach as the cause; available for server-side
     *              logging but not exposed in the response body.
     * @return a {@link ClientErrorException} with HTTP status {@code 409}; never {@code null}.
     */
    public static ClientErrorException conflict(Object body, Throwable cause) {
        return new ClientErrorException(jsonResponse(409, body), cause);
    }

    // -------------------------------------------------------------------------
    // 410 Gone
    // -------------------------------------------------------------------------

    /**
     * Returns a {@code 410 Gone} exception.
     *
     * @return a {@link ClientErrorException} with HTTP status {@code 410}; never {@code null}.
     */
    public static ClientErrorException gone() {
        return new ClientErrorException(emptyResponse(410));
    }

    /**
     * Returns a {@code 410 Gone} exception with a {@code text/plain} body.
     *
     * @param message the response body text; sent to the caller as {@code text/plain}.
     * @return a {@link ClientErrorException} with HTTP status {@code 410}; never {@code null}.
     */
    public static ClientErrorException gone(String message) {
        return new ClientErrorException(message, textResponse(410, message));
    }

    /**
     * Returns a {@code 410 Gone} exception with an {@code application/json} body.
     *
     * @param body the response entity; serialized to {@code application/json}
     *             by the server's configured
     *             {@link JsonSerializer}.
     * @return a {@link ClientErrorException} with HTTP status {@code 410}; never {@code null}.
     */
    public static ClientErrorException gone(Object body) {
        return new ClientErrorException(jsonResponse(410, body));
    }

    /**
     * Returns a {@code 410 Gone} exception with the given cause and no response body.
     *
     * @param cause the exception to attach as the cause; available for server-side
     *              logging but not exposed in the response body.
     * @return a {@link ClientErrorException} with HTTP status {@code 410}; never {@code null}.
     */
    public static ClientErrorException gone(Throwable cause) {
        return new ClientErrorException(emptyResponse(410), cause);
    }

    /**
     * Returns a {@code 410 Gone} exception with a {@code text/plain} body and the given cause.
     *
     * @param message the response body text; sent to the caller as {@code text/plain}.
     * @param cause   the exception to attach as the cause; available for server-side
     *                logging but not exposed in the response body.
     * @return a {@link ClientErrorException} with HTTP status {@code 410}; never {@code null}.
     */
    public static ClientErrorException gone(String message, Throwable cause) {
        return new ClientErrorException(message, textResponse(410, message), cause);
    }

    /**
     * Returns a {@code 410 Gone} exception with an {@code application/json} body and the given cause.
     *
     * @param body  the response entity; serialized to {@code application/json}
     *              by the server's configured
     *              {@link JsonSerializer}.
     * @param cause the exception to attach as the cause; available for server-side
     *              logging but not exposed in the response body.
     * @return a {@link ClientErrorException} with HTTP status {@code 410}; never {@code null}.
     */
    public static ClientErrorException gone(Object body, Throwable cause) {
        return new ClientErrorException(jsonResponse(410, body), cause);
    }

    // -------------------------------------------------------------------------
    // 413 Payload Too Large
    // -------------------------------------------------------------------------

    /**
     * Returns a {@code 413 Payload Too Large} exception.
     *
     * @return a {@link ClientErrorException} with HTTP status {@code 413}; never {@code null}.
     */
    public static ClientErrorException payloadTooLarge() {
        return new ClientErrorException(emptyResponse(413));
    }

    /**
     * Returns a {@code 413 Payload Too Large} exception with a {@code text/plain} body.
     *
     * @param message the response body text; sent to the caller as {@code text/plain}.
     * @return a {@link ClientErrorException} with HTTP status {@code 413}; never {@code null}.
     */
    public static ClientErrorException payloadTooLarge(String message) {
        return new ClientErrorException(message, textResponse(413, message));
    }

    /**
     * Returns a {@code 413 Payload Too Large} exception with an {@code application/json} body.
     *
     * @param body the response entity; serialized to {@code application/json}
     *             by the server's configured
     *             {@link JsonSerializer}.
     * @return a {@link ClientErrorException} with HTTP status {@code 413}; never {@code null}.
     */
    public static ClientErrorException payloadTooLarge(Object body) {
        return new ClientErrorException(jsonResponse(413, body));
    }

    /**
     * Returns a {@code 413 Payload Too Large} exception with the given cause and no response body.
     *
     * @param cause the exception to attach as the cause; available for server-side
     *              logging but not exposed in the response body.
     * @return a {@link ClientErrorException} with HTTP status {@code 413}; never {@code null}.
     */
    public static ClientErrorException payloadTooLarge(Throwable cause) {
        return new ClientErrorException(emptyResponse(413), cause);
    }

    /**
     * Returns a {@code 413 Payload Too Large} exception with a {@code text/plain} body and the given cause.
     *
     * @param message the response body text; sent to the caller as {@code text/plain}.
     * @param cause   the exception to attach as the cause; available for server-side
     *                logging but not exposed in the response body.
     * @return a {@link ClientErrorException} with HTTP status {@code 413}; never {@code null}.
     */
    public static ClientErrorException payloadTooLarge(String message, Throwable cause) {
        return new ClientErrorException(message, textResponse(413, message), cause);
    }

    /**
     * Returns a {@code 413 Payload Too Large} exception with an {@code application/json} body and the given cause.
     *
     * @param body  the response entity; serialized to {@code application/json}
     *              by the server's configured
     *              {@link JsonSerializer}.
     * @param cause the exception to attach as the cause; available for server-side
     *              logging but not exposed in the response body.
     * @return a {@link ClientErrorException} with HTTP status {@code 413}; never {@code null}.
     */
    public static ClientErrorException payloadTooLarge(Object body, Throwable cause) {
        return new ClientErrorException(jsonResponse(413, body), cause);
    }

    // -------------------------------------------------------------------------
    // 415 Unsupported Media Type
    // -------------------------------------------------------------------------

    /**
     * Returns a {@code 415 Unsupported Media Type} exception.
     *
     * @return a {@link NotSupportedException} with HTTP status {@code 415}; never {@code null}.
     */
    public static NotSupportedException unsupportedMediaType() {
        return new NotSupportedException(emptyResponse(415));
    }

    /**
     * Returns a {@code 415 Unsupported Media Type} exception with a {@code text/plain} body.
     *
     * @param message the response body text; sent to the caller as {@code text/plain}.
     * @return a {@link NotSupportedException} with HTTP status {@code 415}; never {@code null}.
     */
    public static NotSupportedException unsupportedMediaType(String message) {
        return new NotSupportedException(message, textResponse(415, message));
    }

    /**
     * Returns a {@code 415 Unsupported Media Type} exception with an {@code application/json} body.
     *
     * @param body the response entity; serialized to {@code application/json}
     *             by the server's configured
     *             {@link JsonSerializer}.
     * @return a {@link NotSupportedException} with HTTP status {@code 415}; never {@code null}.
     */
    public static NotSupportedException unsupportedMediaType(Object body) {
        return new NotSupportedException(jsonResponse(415, body));
    }

    /**
     * Returns a {@code 415 Unsupported Media Type} exception with the given cause and no response body.
     *
     * @param cause the exception to attach as the cause; available for server-side
     *              logging but not exposed in the response body.
     * @return a {@link NotSupportedException} with HTTP status {@code 415}; never {@code null}.
     */
    public static NotSupportedException unsupportedMediaType(Throwable cause) {
        return new NotSupportedException(emptyResponse(415), cause);
    }

    /**
     * Returns a {@code 415 Unsupported Media Type} exception with a {@code text/plain} body and the given cause.
     *
     * @param message the response body text; sent to the caller as {@code text/plain}.
     * @param cause   the exception to attach as the cause; available for server-side
     *                logging but not exposed in the response body.
     * @return a {@link NotSupportedException} with HTTP status {@code 415}; never {@code null}.
     */
    public static NotSupportedException unsupportedMediaType(String message, Throwable cause) {
        return new NotSupportedException(message, textResponse(415, message), cause);
    }

    /**
     * Returns a {@code 415 Unsupported Media Type} exception with an {@code application/json} body and the given cause.
     *
     * @param body  the response entity; serialized to {@code application/json}
     *              by the server's configured
     *              {@link JsonSerializer}.
     * @param cause the exception to attach as the cause; available for server-side
     *              logging but not exposed in the response body.
     * @return a {@link NotSupportedException} with HTTP status {@code 415}; never {@code null}.
     */
    public static NotSupportedException unsupportedMediaType(Object body, Throwable cause) {
        return new NotSupportedException(jsonResponse(415, body), cause);
    }

    // -------------------------------------------------------------------------
    // 422 Unprocessable Entity
    // -------------------------------------------------------------------------

    /**
     * Returns a {@code 422 Unprocessable Entity} exception.
     *
     * @return a {@link ClientErrorException} with HTTP status {@code 422}; never {@code null}.
     */
    public static ClientErrorException unprocessableEntity() {
        return new ClientErrorException(emptyResponse(422));
    }

    /**
     * Returns a {@code 422 Unprocessable Entity} exception with a {@code text/plain} body.
     *
     * @param message the response body text; sent to the caller as {@code text/plain}.
     * @return a {@link ClientErrorException} with HTTP status {@code 422}; never {@code null}.
     */
    public static ClientErrorException unprocessableEntity(String message) {
        return new ClientErrorException(message, textResponse(422, message));
    }

    /**
     * Returns a {@code 422 Unprocessable Entity} exception with an {@code application/json} body.
     *
     * @param body the response entity; serialized to {@code application/json}
     *             by the server's configured
     *             {@link JsonSerializer}.
     * @return a {@link ClientErrorException} with HTTP status {@code 422}; never {@code null}.
     */
    public static ClientErrorException unprocessableEntity(Object body) {
        return new ClientErrorException(jsonResponse(422, body));
    }

    /**
     * Returns a {@code 422 Unprocessable Entity} exception with the given cause and no response body.
     *
     * @param cause the exception to attach as the cause; available for server-side
     *              logging but not exposed in the response body.
     * @return a {@link ClientErrorException} with HTTP status {@code 422}; never {@code null}.
     */
    public static ClientErrorException unprocessableEntity(Throwable cause) {
        return new ClientErrorException(emptyResponse(422), cause);
    }

    /**
     * Returns a {@code 422 Unprocessable Entity} exception with a {@code text/plain} body and the given cause.
     *
     * @param message the response body text; sent to the caller as {@code text/plain}.
     * @param cause   the exception to attach as the cause; available for server-side
     *                logging but not exposed in the response body.
     * @return a {@link ClientErrorException} with HTTP status {@code 422}; never {@code null}.
     */
    public static ClientErrorException unprocessableEntity(String message, Throwable cause) {
        return new ClientErrorException(message, textResponse(422, message), cause);
    }

    /**
     * Returns a {@code 422 Unprocessable Entity} exception with an {@code application/json} body and the given cause.
     *
     * @param body  the response entity; serialized to {@code application/json}
     *              by the server's configured
     *              {@link JsonSerializer}.
     * @param cause the exception to attach as the cause; available for server-side
     *              logging but not exposed in the response body.
     * @return a {@link ClientErrorException} with HTTP status {@code 422}; never {@code null}.
     */
    public static ClientErrorException unprocessableEntity(Object body, Throwable cause) {
        return new ClientErrorException(jsonResponse(422, body), cause);
    }

    // -------------------------------------------------------------------------
    // 429 Too Many Requests
    // -------------------------------------------------------------------------

    /**
     * Returns a {@code 429 Too Many Requests} exception.
     *
     * @return a {@link ClientErrorException} with HTTP status {@code 429}; never {@code null}.
     */
    public static ClientErrorException tooManyRequests() {
        return new ClientErrorException(emptyResponse(429));
    }

    /**
     * Returns a {@code 429 Too Many Requests} exception with a {@code text/plain} body.
     *
     * @param message the response body text; sent to the caller as {@code text/plain}.
     * @return a {@link ClientErrorException} with HTTP status {@code 429}; never {@code null}.
     */
    public static ClientErrorException tooManyRequests(String message) {
        return new ClientErrorException(message, textResponse(429, message));
    }

    /**
     * Returns a {@code 429 Too Many Requests} exception with an {@code application/json} body.
     *
     * @param body the response entity; serialized to {@code application/json}
     *             by the server's configured
     *             {@link JsonSerializer}.
     * @return a {@link ClientErrorException} with HTTP status {@code 429}; never {@code null}.
     */
    public static ClientErrorException tooManyRequests(Object body) {
        return new ClientErrorException(jsonResponse(429, body));
    }

    /**
     * Returns a {@code 429 Too Many Requests} exception with the given cause and no response body.
     *
     * @param cause the exception to attach as the cause; available for server-side
     *              logging but not exposed in the response body.
     * @return a {@link ClientErrorException} with HTTP status {@code 429}; never {@code null}.
     */
    public static ClientErrorException tooManyRequests(Throwable cause) {
        return new ClientErrorException(emptyResponse(429), cause);
    }

    /**
     * Returns a {@code 429 Too Many Requests} exception with a {@code text/plain} body and the given cause.
     *
     * @param message the response body text; sent to the caller as {@code text/plain}.
     * @param cause   the exception to attach as the cause; available for server-side
     *                logging but not exposed in the response body.
     * @return a {@link ClientErrorException} with HTTP status {@code 429}; never {@code null}.
     */
    public static ClientErrorException tooManyRequests(String message, Throwable cause) {
        return new ClientErrorException(message, textResponse(429, message), cause);
    }

    /**
     * Returns a {@code 429 Too Many Requests} exception with an {@code application/json} body and the given cause.
     *
     * @param body  the response entity; serialized to {@code application/json}
     *              by the server's configured
     *              {@link JsonSerializer}.
     * @param cause the exception to attach as the cause; available for server-side
     *              logging but not exposed in the response body.
     * @return a {@link ClientErrorException} with HTTP status {@code 429}; never {@code null}.
     */
    public static ClientErrorException tooManyRequests(Object body, Throwable cause) {
        return new ClientErrorException(jsonResponse(429, body), cause);
    }

    /**
     * Returns a {@code 429 Too Many Requests} exception with a {@code Retry-After} header
     * and no response body.
     *
     * @param retryAfter the duration after which the caller may retry; written as
     *                   {@code Retry-After: <seconds>} in the response.
     * @return a {@link ClientErrorException} with HTTP status {@code 429}; never {@code null}.
     * @throws software.frisby.core.validation.NullValueException            if {@code retryAfter} is {@code null}.
     * @throws software.frisby.core.validation.DurationOutsideRangeException if {@code retryAfter} is negative.
     */
    public static ClientErrorException tooManyRequests(Duration retryAfter) {
        return new ClientErrorException(emptyResponseRetryAfter(429, retryAfter));
    }

    /**
     * Returns a {@code 429 Too Many Requests} exception with a {@code text/plain} body and a
     * {@code Retry-After} header.
     *
     * @param message    the response body text; sent to the caller as {@code text/plain}.
     * @param retryAfter the duration after which the caller may retry; written as
     *                   {@code Retry-After: <seconds>} in the response.
     * @return a {@link ClientErrorException} with HTTP status {@code 429}; never {@code null}.
     * @throws software.frisby.core.validation.NullValueException            if {@code retryAfter} is {@code null}.
     * @throws software.frisby.core.validation.DurationOutsideRangeException if {@code retryAfter} is negative.
     */
    public static ClientErrorException tooManyRequests(String message, Duration retryAfter) {
        return new ClientErrorException(message, textResponseRetryAfter(429, message, retryAfter));
    }

    /**
     * Returns a {@code 429 Too Many Requests} exception with an {@code application/json} body and a
     * {@code Retry-After} header.
     *
     * @param body       the response entity; serialized to {@code application/json} by the
     *                   server's configured
     *                   {@link JsonSerializer}.
     * @param retryAfter the duration after which the caller may retry; written as
     *                   {@code Retry-After: <seconds>} in the response.
     * @return a {@link ClientErrorException} with HTTP status {@code 429}; never {@code null}.
     * @throws software.frisby.core.validation.NullValueException            if {@code retryAfter} is {@code null}.
     * @throws software.frisby.core.validation.DurationOutsideRangeException if {@code retryAfter} is negative.
     */
    public static ClientErrorException tooManyRequests(Object body, Duration retryAfter) {
        return new ClientErrorException(jsonResponseRetryAfter(429, body, retryAfter));
    }

    /**
     * Returns a {@code 429 Too Many Requests} exception with a {@code Retry-After} header,
     * no response body, and the given cause.
     *
     * @param retryAfter the duration after which the caller may retry; written as
     *                   {@code Retry-After: <seconds>} in the response.
     * @param cause      the exception to attach as the cause; available for server-side
     *                   logging but not exposed in the response body.
     * @return a {@link ClientErrorException} with HTTP status {@code 429}; never {@code null}.
     * @throws software.frisby.core.validation.NullValueException            if {@code retryAfter} is {@code null}.
     * @throws software.frisby.core.validation.DurationOutsideRangeException if {@code retryAfter} is negative.
     */
    public static ClientErrorException tooManyRequests(Duration retryAfter, Throwable cause) {
        return new ClientErrorException(emptyResponseRetryAfter(429, retryAfter), cause);
    }

    /**
     * Returns a {@code 429 Too Many Requests} exception with a {@code text/plain} body,
     * a {@code Retry-After} header, and the given cause.
     *
     * @param message    the response body text; sent to the caller as {@code text/plain}.
     * @param retryAfter the duration after which the caller may retry; written as
     *                   {@code Retry-After: <seconds>} in the response.
     * @param cause      the exception to attach as the cause; available for server-side
     *                   logging but not exposed in the response body.
     * @return a {@link ClientErrorException} with HTTP status {@code 429}; never {@code null}.
     * @throws software.frisby.core.validation.NullValueException            if {@code retryAfter} is {@code null}.
     * @throws software.frisby.core.validation.DurationOutsideRangeException if {@code retryAfter} is negative.
     */
    public static ClientErrorException tooManyRequests(String message, Duration retryAfter, Throwable cause) {
        return new ClientErrorException(message, textResponseRetryAfter(429, message, retryAfter), cause);
    }

    /**
     * Returns a {@code 429 Too Many Requests} exception with an {@code application/json} body,
     * a {@code Retry-After} header, and the given cause.
     *
     * @param body       the response entity; serialized to {@code application/json} by the
     *                   server's configured
     *                   {@link JsonSerializer}.
     * @param retryAfter the duration after which the caller may retry; written as
     *                   {@code Retry-After: <seconds>} in the response.
     * @param cause      the exception to attach as the cause; available for server-side
     *                   logging but not exposed in the response body.
     * @return a {@link ClientErrorException} with HTTP status {@code 429}; never {@code null}.
     * @throws software.frisby.core.validation.NullValueException            if {@code retryAfter} is {@code null}.
     * @throws software.frisby.core.validation.DurationOutsideRangeException if {@code retryAfter} is negative.
     */
    public static ClientErrorException tooManyRequests(Object body, Duration retryAfter, Throwable cause) {
        return new ClientErrorException(jsonResponseRetryAfter(429, body, retryAfter), cause);
    }

    // =========================================================================
    // 5xx Server Errors
    // =========================================================================

    // -------------------------------------------------------------------------
    // 500 Internal Server Error
    // -------------------------------------------------------------------------

    /**
     * Returns a {@code 500 Internal Server Error} exception.
     *
     * @return a {@link InternalServerErrorException} with HTTP status {@code 500}; never {@code null}.
     */
    public static InternalServerErrorException internalServerError() {
        return new InternalServerErrorException(emptyResponse(500));
    }

    /**
     * Returns a {@code 500 Internal Server Error} exception with a {@code text/plain} body.
     *
     * @param message the response body text; sent to the caller as {@code text/plain}.
     * @return a {@link InternalServerErrorException} with HTTP status {@code 500}; never {@code null}.
     */
    public static InternalServerErrorException internalServerError(String message) {
        return new InternalServerErrorException(message, textResponse(500, message));
    }

    /**
     * Returns a {@code 500 Internal Server Error} exception with an {@code application/json} body.
     *
     * @param body the response entity; serialized to {@code application/json}
     *             by the server's configured
     *             {@link JsonSerializer}.
     * @return a {@link InternalServerErrorException} with HTTP status {@code 500}; never {@code null}.
     */
    public static InternalServerErrorException internalServerError(Object body) {
        return new InternalServerErrorException(jsonResponse(500, body));
    }

    /**
     * Returns a {@code 500 Internal Server Error} exception with the given cause and no response body.
     *
     * @param cause the exception to attach as the cause; available for server-side
     *              logging but not exposed in the response body.
     * @return a {@link InternalServerErrorException} with HTTP status {@code 500}; never {@code null}.
     */
    public static InternalServerErrorException internalServerError(Throwable cause) {
        return new InternalServerErrorException(emptyResponse(500), cause);
    }

    /**
     * Returns a {@code 500 Internal Server Error} exception with a {@code text/plain} body and the given cause.
     *
     * @param message the response body text; sent to the caller as {@code text/plain}.
     * @param cause   the exception to attach as the cause; available for server-side
     *                logging but not exposed in the response body.
     * @return a {@link InternalServerErrorException} with HTTP status {@code 500}; never {@code null}.
     */
    public static InternalServerErrorException internalServerError(String message, Throwable cause) {
        return new InternalServerErrorException(message, textResponse(500, message), cause);
    }

    /**
     * Returns a {@code 500 Internal Server Error} exception with an {@code application/json} body and the given cause.
     *
     * @param body  the response entity; serialized to {@code application/json}
     *              by the server's configured
     *              {@link JsonSerializer}.
     * @param cause the exception to attach as the cause; available for server-side
     *              logging but not exposed in the response body.
     * @return a {@link InternalServerErrorException} with HTTP status {@code 500}; never {@code null}.
     */
    public static InternalServerErrorException internalServerError(Object body, Throwable cause) {
        return new InternalServerErrorException(jsonResponse(500, body), cause);
    }

    // -------------------------------------------------------------------------
    // 501 Not Implemented
    // -------------------------------------------------------------------------

    /**
     * Returns a {@code 501 Not Implemented} exception.
     *
     * @return a {@link ServerErrorException} with HTTP status {@code 501}; never {@code null}.
     */
    public static ServerErrorException notImplemented() {
        return new ServerErrorException(emptyResponse(501));
    }

    /**
     * Returns a {@code 501 Not Implemented} exception with a {@code text/plain} body.
     *
     * @param message the response body text; sent to the caller as {@code text/plain}.
     * @return a {@link ServerErrorException} with HTTP status {@code 501}; never {@code null}.
     */
    public static ServerErrorException notImplemented(String message) {
        return new ServerErrorException(message, textResponse(501, message));
    }

    /**
     * Returns a {@code 501 Not Implemented} exception with an {@code application/json} body.
     *
     * @param body the response entity; serialized to {@code application/json}
     *             by the server's configured
     *             {@link JsonSerializer}.
     * @return a {@link ServerErrorException} with HTTP status {@code 501}; never {@code null}.
     */
    public static ServerErrorException notImplemented(Object body) {
        return new ServerErrorException(jsonResponse(501, body));
    }

    /**
     * Returns a {@code 501 Not Implemented} exception with the given cause and no response body.
     *
     * @param cause the exception to attach as the cause; available for server-side
     *              logging but not exposed in the response body.
     * @return a {@link ServerErrorException} with HTTP status {@code 501}; never {@code null}.
     */
    public static ServerErrorException notImplemented(Throwable cause) {
        return new ServerErrorException(emptyResponse(501), cause);
    }

    /**
     * Returns a {@code 501 Not Implemented} exception with a {@code text/plain} body and the given cause.
     *
     * @param message the response body text; sent to the caller as {@code text/plain}.
     * @param cause   the exception to attach as the cause; available for server-side
     *                logging but not exposed in the response body.
     * @return a {@link ServerErrorException} with HTTP status {@code 501}; never {@code null}.
     */
    public static ServerErrorException notImplemented(String message, Throwable cause) {
        return new ServerErrorException(message, textResponse(501, message), cause);
    }

    /**
     * Returns a {@code 501 Not Implemented} exception with an {@code application/json} body and the given cause.
     *
     * @param body  the response entity; serialized to {@code application/json}
     *              by the server's configured
     *              {@link JsonSerializer}.
     * @param cause the exception to attach as the cause; available for server-side
     *              logging but not exposed in the response body.
     * @return a {@link ServerErrorException} with HTTP status {@code 501}; never {@code null}.
     */
    public static ServerErrorException notImplemented(Object body, Throwable cause) {
        return new ServerErrorException(jsonResponse(501, body), cause);
    }

    // -------------------------------------------------------------------------
    // 502 Bad Gateway
    // -------------------------------------------------------------------------

    /**
     * Returns a {@code 502 Bad Gateway} exception.
     *
     * @return a {@link ServerErrorException} with HTTP status {@code 502}; never {@code null}.
     */
    public static ServerErrorException badGateway() {
        return new ServerErrorException(emptyResponse(502));
    }

    /**
     * Returns a {@code 502 Bad Gateway} exception with a {@code text/plain} body.
     *
     * @param message the response body text; sent to the caller as {@code text/plain}.
     * @return a {@link ServerErrorException} with HTTP status {@code 502}; never {@code null}.
     */
    public static ServerErrorException badGateway(String message) {
        return new ServerErrorException(message, textResponse(502, message));
    }

    /**
     * Returns a {@code 502 Bad Gateway} exception with an {@code application/json} body.
     *
     * @param body the response entity; serialized to {@code application/json}
     *             by the server's configured
     *             {@link JsonSerializer}.
     * @return a {@link ServerErrorException} with HTTP status {@code 502}; never {@code null}.
     */
    public static ServerErrorException badGateway(Object body) {
        return new ServerErrorException(jsonResponse(502, body));
    }

    /**
     * Returns a {@code 502 Bad Gateway} exception with the given cause and no response body.
     *
     * @param cause the exception to attach as the cause; available for server-side
     *              logging but not exposed in the response body.
     * @return a {@link ServerErrorException} with HTTP status {@code 502}; never {@code null}.
     */
    public static ServerErrorException badGateway(Throwable cause) {
        return new ServerErrorException(emptyResponse(502), cause);
    }

    /**
     * Returns a {@code 502 Bad Gateway} exception with a {@code text/plain} body and the given cause.
     *
     * @param message the response body text; sent to the caller as {@code text/plain}.
     * @param cause   the exception to attach as the cause; available for server-side
     *                logging but not exposed in the response body.
     * @return a {@link ServerErrorException} with HTTP status {@code 502}; never {@code null}.
     */
    public static ServerErrorException badGateway(String message, Throwable cause) {
        return new ServerErrorException(message, textResponse(502, message), cause);
    }

    /**
     * Returns a {@code 502 Bad Gateway} exception with an {@code application/json} body and the given cause.
     *
     * @param body  the response entity; serialized to {@code application/json}
     *              by the server's configured
     *              {@link JsonSerializer}.
     * @param cause the exception to attach as the cause; available for server-side
     *              logging but not exposed in the response body.
     * @return a {@link ServerErrorException} with HTTP status {@code 502}; never {@code null}.
     */
    public static ServerErrorException badGateway(Object body, Throwable cause) {
        return new ServerErrorException(jsonResponse(502, body), cause);
    }

    // -------------------------------------------------------------------------
    // 503 Service Unavailable
    // -------------------------------------------------------------------------

    /**
     * Returns a {@code 503 Service Unavailable} exception.
     *
     * @return a {@link ServiceUnavailableException} with HTTP status {@code 503}; never {@code null}.
     */
    public static ServiceUnavailableException serviceUnavailable() {
        return new ServiceUnavailableException(emptyResponse(503));
    }

    /**
     * Returns a {@code 503 Service Unavailable} exception with a {@code text/plain} body.
     *
     * @param message the response body text; sent to the caller as {@code text/plain}.
     * @return a {@link ServiceUnavailableException} with HTTP status {@code 503}; never {@code null}.
     */
    public static ServiceUnavailableException serviceUnavailable(String message) {
        return new ServiceUnavailableException(message, textResponse(503, message));
    }

    /**
     * Returns a {@code 503 Service Unavailable} exception with an {@code application/json} body.
     *
     * @param body the response entity; serialized to {@code application/json}
     *             by the server's configured
     *             {@link JsonSerializer}.
     * @return a {@link ServiceUnavailableException} with HTTP status {@code 503}; never {@code null}.
     */
    public static ServiceUnavailableException serviceUnavailable(Object body) {
        return new ServiceUnavailableException(jsonResponse(503, body));
    }

    /**
     * Returns a {@code 503 Service Unavailable} exception with the given cause and no response body.
     *
     * @param cause the exception to attach as the cause; available for server-side
     *              logging but not exposed in the response body.
     * @return a {@link ServiceUnavailableException} with HTTP status {@code 503}; never {@code null}.
     */
    public static ServiceUnavailableException serviceUnavailable(Throwable cause) {
        return new ServiceUnavailableException(emptyResponse(503), cause);
    }

    /**
     * Returns a {@code 503 Service Unavailable} exception with a {@code text/plain} body and the given cause.
     *
     * @param message the response body text; sent to the caller as {@code text/plain}.
     * @param cause   the exception to attach as the cause; available for server-side
     *                logging but not exposed in the response body.
     * @return a {@link ServiceUnavailableException} with HTTP status {@code 503}; never {@code null}.
     */
    public static ServiceUnavailableException serviceUnavailable(String message, Throwable cause) {
        return new ServiceUnavailableException(message, textResponse(503, message), cause);
    }

    /**
     * Returns a {@code 503 Service Unavailable} exception with an {@code application/json} body and the given cause.
     *
     * @param body  the response entity; serialized to {@code application/json}
     *              by the server's configured
     *              {@link JsonSerializer}.
     * @param cause the exception to attach as the cause; available for server-side
     *              logging but not exposed in the response body.
     * @return a {@link ServiceUnavailableException} with HTTP status {@code 503}; never {@code null}.
     */
    public static ServiceUnavailableException serviceUnavailable(Object body, Throwable cause) {
        return new ServiceUnavailableException(jsonResponse(503, body), cause);
    }

    /**
     * Returns a {@code 503 Service Unavailable} exception with a {@code Retry-After} header
     * and no response body.
     *
     * @param retryAfter the duration after which the caller may retry; written as
     *                   {@code Retry-After: <seconds>} in the response.
     * @return a {@link ServiceUnavailableException} with HTTP status {@code 503}; never {@code null}.
     * @throws software.frisby.core.validation.NullValueException            if {@code retryAfter} is {@code null}.
     * @throws software.frisby.core.validation.DurationOutsideRangeException if {@code retryAfter} is negative.
     */
    public static ServiceUnavailableException serviceUnavailable(Duration retryAfter) {
        return new ServiceUnavailableException(emptyResponseRetryAfter(503, retryAfter));
    }

    /**
     * Returns a {@code 503 Service Unavailable} exception with a {@code text/plain} body and a
     * {@code Retry-After} header.
     *
     * @param message    the response body text; sent to the caller as {@code text/plain}.
     * @param retryAfter the duration after which the caller may retry; written as
     *                   {@code Retry-After: <seconds>} in the response.
     * @return a {@link ServiceUnavailableException} with HTTP status {@code 503}; never {@code null}.
     * @throws software.frisby.core.validation.NullValueException            if {@code retryAfter} is {@code null}.
     * @throws software.frisby.core.validation.DurationOutsideRangeException if {@code retryAfter} is negative.
     */
    public static ServiceUnavailableException serviceUnavailable(String message, Duration retryAfter) {
        return new ServiceUnavailableException(message, textResponseRetryAfter(503, message, retryAfter));
    }

    /**
     * Returns a {@code 503 Service Unavailable} exception with an {@code application/json} body and a
     * {@code Retry-After} header.
     *
     * @param body       the response entity; serialized to {@code application/json} by the
     *                   server's configured
     *                   {@link JsonSerializer}.
     * @param retryAfter the duration after which the caller may retry; written as
     *                   {@code Retry-After: <seconds>} in the response.
     * @return a {@link ServiceUnavailableException} with HTTP status {@code 503}; never {@code null}.
     * @throws software.frisby.core.validation.NullValueException            if {@code retryAfter} is {@code null}.
     * @throws software.frisby.core.validation.DurationOutsideRangeException if {@code retryAfter} is negative.
     */
    public static ServiceUnavailableException serviceUnavailable(Object body, Duration retryAfter) {
        return new ServiceUnavailableException(jsonResponseRetryAfter(503, body, retryAfter));
    }

    /**
     * Returns a {@code 503 Service Unavailable} exception with a {@code Retry-After} header,
     * no response body, and the given cause.
     *
     * @param retryAfter the duration after which the caller may retry; written as
     *                   {@code Retry-After: <seconds>} in the response.
     * @param cause      the exception to attach as the cause; available for server-side
     *                   logging but not exposed in the response body.
     * @return a {@link ServiceUnavailableException} with HTTP status {@code 503}; never {@code null}.
     * @throws software.frisby.core.validation.NullValueException            if {@code retryAfter} is {@code null}.
     * @throws software.frisby.core.validation.DurationOutsideRangeException if {@code retryAfter} is negative.
     */
    public static ServiceUnavailableException serviceUnavailable(Duration retryAfter, Throwable cause) {
        return new ServiceUnavailableException(emptyResponseRetryAfter(503, retryAfter), cause);
    }

    /**
     * Returns a {@code 503 Service Unavailable} exception with a {@code text/plain} body,
     * a {@code Retry-After} header, and the given cause.
     *
     * @param message    the response body text; sent to the caller as {@code text/plain}.
     * @param retryAfter the duration after which the caller may retry; written as
     *                   {@code Retry-After: <seconds>} in the response.
     * @param cause      the exception to attach as the cause; available for server-side
     *                   logging but not exposed in the response body.
     * @return a {@link ServiceUnavailableException} with HTTP status {@code 503}; never {@code null}.
     * @throws software.frisby.core.validation.NullValueException            if {@code retryAfter} is {@code null}.
     * @throws software.frisby.core.validation.DurationOutsideRangeException if {@code retryAfter} is negative.
     */
    public static ServiceUnavailableException serviceUnavailable(String message, Duration retryAfter, Throwable cause) {
        return new ServiceUnavailableException(message, textResponseRetryAfter(503, message, retryAfter), cause);
    }

    /**
     * Returns a {@code 503 Service Unavailable} exception with an {@code application/json} body,
     * a {@code Retry-After} header, and the given cause.
     *
     * @param body       the response entity; serialized to {@code application/json} by the
     *                   server's configured
     *                   {@link JsonSerializer}.
     * @param retryAfter the duration after which the caller may retry; written as
     *                   {@code Retry-After: <seconds>} in the response.
     * @param cause      the exception to attach as the cause; available for server-side
     *                   logging but not exposed in the response body.
     * @return a {@link ServiceUnavailableException} with HTTP status {@code 503}; never {@code null}.
     * @throws software.frisby.core.validation.NullValueException            if {@code retryAfter} is {@code null}.
     * @throws software.frisby.core.validation.DurationOutsideRangeException if {@code retryAfter} is negative.
     */
    public static ServiceUnavailableException serviceUnavailable(Object body, Duration retryAfter, Throwable cause) {
        return new ServiceUnavailableException(jsonResponseRetryAfter(503, body, retryAfter), cause);
    }

    // -------------------------------------------------------------------------
    // 504 Gateway Timeout
    // -------------------------------------------------------------------------

    /**
     * Returns a {@code 504 Gateway Timeout} exception.
     *
     * @return a {@link ServerErrorException} with HTTP status {@code 504}; never {@code null}.
     */
    public static ServerErrorException gatewayTimeout() {
        return new ServerErrorException(emptyResponse(504));
    }

    /**
     * Returns a {@code 504 Gateway Timeout} exception with a {@code text/plain} body.
     *
     * @param message the response body text; sent to the caller as {@code text/plain}.
     * @return a {@link ServerErrorException} with HTTP status {@code 504}; never {@code null}.
     */
    public static ServerErrorException gatewayTimeout(String message) {
        return new ServerErrorException(message, textResponse(504, message));
    }

    /**
     * Returns a {@code 504 Gateway Timeout} exception with an {@code application/json} body.
     *
     * @param body the response entity; serialized to {@code application/json}
     *             by the server's configured
     *             {@link JsonSerializer}.
     * @return a {@link ServerErrorException} with HTTP status {@code 504}; never {@code null}.
     */
    public static ServerErrorException gatewayTimeout(Object body) {
        return new ServerErrorException(jsonResponse(504, body));
    }

    /**
     * Returns a {@code 504 Gateway Timeout} exception with the given cause and no response body.
     *
     * @param cause the exception to attach as the cause; available for server-side
     *              logging but not exposed in the response body.
     * @return a {@link ServerErrorException} with HTTP status {@code 504}; never {@code null}.
     */
    public static ServerErrorException gatewayTimeout(Throwable cause) {
        return new ServerErrorException(emptyResponse(504), cause);
    }

    /**
     * Returns a {@code 504 Gateway Timeout} exception with a {@code text/plain} body and the given cause.
     *
     * @param message the response body text; sent to the caller as {@code text/plain}.
     * @param cause   the exception to attach as the cause; available for server-side
     *                logging but not exposed in the response body.
     * @return a {@link ServerErrorException} with HTTP status {@code 504}; never {@code null}.
     */
    public static ServerErrorException gatewayTimeout(String message, Throwable cause) {
        return new ServerErrorException(message, textResponse(504, message), cause);
    }

    /**
     * Returns a {@code 504 Gateway Timeout} exception with an {@code application/json} body and the given cause.
     *
     * @param body  the response entity; serialized to {@code application/json}
     *              by the server's configured
     *              {@link JsonSerializer}.
     * @param cause the exception to attach as the cause; available for server-side
     *              logging but not exposed in the response body.
     * @return a {@link ServerErrorException} with HTTP status {@code 504}; never {@code null}.
     */
    public static ServerErrorException gatewayTimeout(Object body, Throwable cause) {
        return new ServerErrorException(jsonResponse(504, body), cause);
    }
}


