package software.frisby.web.server;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.time.Duration;

import software.frisby.core.validation.Durations;

/**
 * Static factory for creating properly configured {@link WebApplicationException} instances.
 * <p>
 * Each factory method constructs a {@link WebApplicationException} with an embedded
 * {@link Response} carrying the correct HTTP status code, an optional body, and an optional
 * cause.  This eliminates the verbose {@link Response}-building boilerplate that Jersey's own
 * exception constructors require and avoids their common pitfalls — most notably, the
 * unwanted {@code WWW-Authenticate} challenge header that
 * {@link jakarta.ws.rs.NotAuthorizedException} adds when constructed with anything other
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
 *       {@link software.frisby.web.serial.JsonSerializer} via {@link JsonMessageBodyProvider}.
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

    private HttpErrors() {
    }

    // -------------------------------------------------------------------------
    // Private response builders
    // -------------------------------------------------------------------------

    private static final String RETRY_AFTER = "Retry-After";

    private static Response textResponse(int status, String message) {
        return Response.status(status).entity(message).type(MediaType.TEXT_PLAIN).build();
    }

    private static Response jsonResponse(int status, Object body) {
        return Response.status(status).entity(body).type(MediaType.APPLICATION_JSON).build();
    }

    private static Response emptyResponse(int status) {
        return Response.status(status).build();
    }

    private static Response emptyResponseRetryAfter(int status, Duration retryAfter) {
        Durations.notNegative("retryAfter", retryAfter);

        return Response.status(status).header(RETRY_AFTER, retryAfter.toSeconds()).build();
    }

    private static Response textResponseRetryAfter(int status, String message, Duration retryAfter) {
        Durations.notNegative("retryAfter", retryAfter);

        return Response.status(status).entity(message).type(MediaType.TEXT_PLAIN)
                .header(RETRY_AFTER, retryAfter.toSeconds()).build();
    }

    private static Response jsonResponseRetryAfter(int status, Object body, Duration retryAfter) {
        Durations.notNegative("retryAfter", retryAfter);

        return Response.status(status).entity(body).type(MediaType.APPLICATION_JSON)
                .header(RETRY_AFTER, retryAfter.toSeconds()).build();
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
     * @return a {@link WebApplicationException} with HTTP status {@code 400}; never {@code null}.
     */
    public static WebApplicationException badRequest() {
        return new WebApplicationException(emptyResponse(400));
    }

    /**
     * Returns a {@code 400 Bad Request} exception with a {@code text/plain} body.
     *
     * @param message the response body text; sent to the caller as {@code text/plain}.
     * @return a {@link WebApplicationException} with HTTP status {@code 400}; never {@code null}.
     */
    public static WebApplicationException badRequest(String message) {
        return new WebApplicationException(message, textResponse(400, message));
    }

    /**
     * Returns a {@code 400 Bad Request} exception with an {@code application/json} body.
     *
     * @param body the response entity; serialized to {@code application/json}
     *             by the server's configured
     *             {@link software.frisby.web.serial.JsonSerializer}.
     * @return a {@link WebApplicationException} with HTTP status {@code 400}; never {@code null}.
     */
    public static WebApplicationException badRequest(Object body) {
        return new WebApplicationException(jsonResponse(400, body));
    }

    /**
     * Returns a {@code 400 Bad Request} exception with the given cause and no response body.
     *
     * @param cause the exception to attach as the cause; available for server-side
     *              logging but not exposed in the response body.
     * @return a {@link WebApplicationException} with HTTP status {@code 400}; never {@code null}.
     */
    public static WebApplicationException badRequest(Throwable cause) {
        return new WebApplicationException(cause, emptyResponse(400));
    }

    /**
     * Returns a {@code 400 Bad Request} exception with a {@code text/plain} body and the given cause.
     *
     * @param message the response body text; sent to the caller as {@code text/plain}.
     * @param cause   the exception to attach as the cause; available for server-side
     *                logging but not exposed in the response body.
     * @return a {@link WebApplicationException} with HTTP status {@code 400}; never {@code null}.
     */
    public static WebApplicationException badRequest(String message, Throwable cause) {
        return new WebApplicationException(message, cause, textResponse(400, message));
    }

    /**
     * Returns a {@code 400 Bad Request} exception with an {@code application/json} body and the given cause.
     *
     * @param body  the response entity; serialized to {@code application/json}
     *              by the server's configured
     *              {@link software.frisby.web.serial.JsonSerializer}.
     * @param cause the exception to attach as the cause; available for server-side
     *              logging but not exposed in the response body.
     * @return a {@link WebApplicationException} with HTTP status {@code 400}; never {@code null}.
     */
    public static WebApplicationException badRequest(Object body, Throwable cause) {
        return new WebApplicationException(cause, jsonResponse(400, body));
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
     * @return a {@link WebApplicationException} with HTTP status {@code 401}; never {@code null}.
     */
    public static WebApplicationException unauthorized() {
        return new WebApplicationException(emptyResponse(401));
    }

    /**
     * Returns a {@code 401 Unauthorized} exception with a {@code text/plain} body and no {@code WWW-Authenticate} challenge.
     *
     * @param message the response body text; sent to the caller as {@code text/plain}.
     * @return a {@link WebApplicationException} with HTTP status {@code 401}; never {@code null}.
     */
    public static WebApplicationException unauthorized(String message) {
        return new WebApplicationException(message, textResponse(401, message));
    }

    /**
     * Returns a {@code 401 Unauthorized} exception with an {@code application/json} body and no {@code WWW-Authenticate} challenge.
     *
     * @param body the response entity; serialized to {@code application/json}
     *             by the server's configured
     *             {@link software.frisby.web.serial.JsonSerializer}.
     * @return a {@link WebApplicationException} with HTTP status {@code 401}; never {@code null}.
     */
    public static WebApplicationException unauthorized(Object body) {
        return new WebApplicationException(jsonResponse(401, body));
    }

    /**
     * Returns a {@code 401 Unauthorized} exception with the given cause, no response body, and no {@code WWW-Authenticate} challenge.
     *
     * @param cause the exception to attach as the cause; available for server-side
     *              logging but not exposed in the response body.
     * @return a {@link WebApplicationException} with HTTP status {@code 401}; never {@code null}.
     */
    public static WebApplicationException unauthorized(Throwable cause) {
        return new WebApplicationException(cause, emptyResponse(401));
    }

    /**
     * Returns a {@code 401 Unauthorized} exception with a {@code text/plain} body, the given cause, and no {@code WWW-Authenticate} challenge.
     *
     * @param message the response body text; sent to the caller as {@code text/plain}.
     * @param cause   the exception to attach as the cause; available for server-side
     *                logging but not exposed in the response body.
     * @return a {@link WebApplicationException} with HTTP status {@code 401}; never {@code null}.
     */
    public static WebApplicationException unauthorized(String message, Throwable cause) {
        return new WebApplicationException(message, cause, textResponse(401, message));
    }

    /**
     * Returns a {@code 401 Unauthorized} exception with an {@code application/json} body, the given cause, and no {@code WWW-Authenticate} challenge.
     *
     * @param body  the response entity; serialized to {@code application/json}
     *              by the server's configured
     *              {@link software.frisby.web.serial.JsonSerializer}.
     * @param cause the exception to attach as the cause; available for server-side
     *              logging but not exposed in the response body.
     * @return a {@link WebApplicationException} with HTTP status {@code 401}; never {@code null}.
     */
    public static WebApplicationException unauthorized(Object body, Throwable cause) {
        return new WebApplicationException(cause, jsonResponse(401, body));
    }

    // -------------------------------------------------------------------------
    // 403 Forbidden
    // -------------------------------------------------------------------------

    /**
     * Returns a {@code 403 Forbidden} exception.
     *
     * @return a {@link WebApplicationException} with HTTP status {@code 403}; never {@code null}.
     */
    public static WebApplicationException forbidden() {
        return new WebApplicationException(emptyResponse(403));
    }

    /**
     * Returns a {@code 403 Forbidden} exception with a {@code text/plain} body.
     *
     * @param message the response body text; sent to the caller as {@code text/plain}.
     * @return a {@link WebApplicationException} with HTTP status {@code 403}; never {@code null}.
     */
    public static WebApplicationException forbidden(String message) {
        return new WebApplicationException(message, textResponse(403, message));
    }

    /**
     * Returns a {@code 403 Forbidden} exception with an {@code application/json} body.
     *
     * @param body the response entity; serialized to {@code application/json}
     *             by the server's configured
     *             {@link software.frisby.web.serial.JsonSerializer}.
     * @return a {@link WebApplicationException} with HTTP status {@code 403}; never {@code null}.
     */
    public static WebApplicationException forbidden(Object body) {
        return new WebApplicationException(jsonResponse(403, body));
    }

    /**
     * Returns a {@code 403 Forbidden} exception with the given cause and no response body.
     *
     * @param cause the exception to attach as the cause; available for server-side
     *              logging but not exposed in the response body.
     * @return a {@link WebApplicationException} with HTTP status {@code 403}; never {@code null}.
     */
    public static WebApplicationException forbidden(Throwable cause) {
        return new WebApplicationException(cause, emptyResponse(403));
    }

    /**
     * Returns a {@code 403 Forbidden} exception with a {@code text/plain} body and the given cause.
     *
     * @param message the response body text; sent to the caller as {@code text/plain}.
     * @param cause   the exception to attach as the cause; available for server-side
     *                logging but not exposed in the response body.
     * @return a {@link WebApplicationException} with HTTP status {@code 403}; never {@code null}.
     */
    public static WebApplicationException forbidden(String message, Throwable cause) {
        return new WebApplicationException(message, cause, textResponse(403, message));
    }

    /**
     * Returns a {@code 403 Forbidden} exception with an {@code application/json} body and the given cause.
     *
     * @param body  the response entity; serialized to {@code application/json}
     *              by the server's configured
     *              {@link software.frisby.web.serial.JsonSerializer}.
     * @param cause the exception to attach as the cause; available for server-side
     *              logging but not exposed in the response body.
     * @return a {@link WebApplicationException} with HTTP status {@code 403}; never {@code null}.
     */
    public static WebApplicationException forbidden(Object body, Throwable cause) {
        return new WebApplicationException(cause, jsonResponse(403, body));
    }

    // -------------------------------------------------------------------------
    // 404 Not Found
    // -------------------------------------------------------------------------

    /**
     * Returns a {@code 404 Not Found} exception.
     *
     * @return a {@link WebApplicationException} with HTTP status {@code 404}; never {@code null}.
     */
    public static WebApplicationException notFound() {
        return new WebApplicationException(emptyResponse(404));
    }

    /**
     * Returns a {@code 404 Not Found} exception with a {@code text/plain} body.
     *
     * @param message the response body text; sent to the caller as {@code text/plain}.
     * @return a {@link WebApplicationException} with HTTP status {@code 404}; never {@code null}.
     */
    public static WebApplicationException notFound(String message) {
        return new WebApplicationException(message, textResponse(404, message));
    }

    /**
     * Returns a {@code 404 Not Found} exception with an {@code application/json} body.
     *
     * @param body the response entity; serialized to {@code application/json}
     *             by the server's configured
     *             {@link software.frisby.web.serial.JsonSerializer}.
     * @return a {@link WebApplicationException} with HTTP status {@code 404}; never {@code null}.
     */
    public static WebApplicationException notFound(Object body) {
        return new WebApplicationException(jsonResponse(404, body));
    }

    /**
     * Returns a {@code 404 Not Found} exception with the given cause and no response body.
     *
     * @param cause the exception to attach as the cause; available for server-side
     *              logging but not exposed in the response body.
     * @return a {@link WebApplicationException} with HTTP status {@code 404}; never {@code null}.
     */
    public static WebApplicationException notFound(Throwable cause) {
        return new WebApplicationException(cause, emptyResponse(404));
    }

    /**
     * Returns a {@code 404 Not Found} exception with a {@code text/plain} body and the given cause.
     *
     * @param message the response body text; sent to the caller as {@code text/plain}.
     * @param cause   the exception to attach as the cause; available for server-side
     *                logging but not exposed in the response body.
     * @return a {@link WebApplicationException} with HTTP status {@code 404}; never {@code null}.
     */
    public static WebApplicationException notFound(String message, Throwable cause) {
        return new WebApplicationException(message, cause, textResponse(404, message));
    }

    /**
     * Returns a {@code 404 Not Found} exception with an {@code application/json} body and the given cause.
     *
     * @param body  the response entity; serialized to {@code application/json}
     *              by the server's configured
     *              {@link software.frisby.web.serial.JsonSerializer}.
     * @param cause the exception to attach as the cause; available for server-side
     *              logging but not exposed in the response body.
     * @return a {@link WebApplicationException} with HTTP status {@code 404}; never {@code null}.
     */
    public static WebApplicationException notFound(Object body, Throwable cause) {
        return new WebApplicationException(cause, jsonResponse(404, body));
    }

    // -------------------------------------------------------------------------
    // 405 Method Not Allowed
    // -------------------------------------------------------------------------

    /**
     * Returns a {@code 405 Method Not Allowed} exception.
     *
     * @return a {@link WebApplicationException} with HTTP status {@code 405}; never {@code null}.
     */
    public static WebApplicationException methodNotAllowed() {
        return new WebApplicationException(emptyResponse(405));
    }

    /**
     * Returns a {@code 405 Method Not Allowed} exception with a {@code text/plain} body.
     *
     * @param message the response body text; sent to the caller as {@code text/plain}.
     * @return a {@link WebApplicationException} with HTTP status {@code 405}; never {@code null}.
     */
    public static WebApplicationException methodNotAllowed(String message) {
        return new WebApplicationException(message, textResponse(405, message));
    }

    /**
     * Returns a {@code 405 Method Not Allowed} exception with an {@code application/json} body.
     *
     * @param body the response entity; serialized to {@code application/json}
     *             by the server's configured
     *             {@link software.frisby.web.serial.JsonSerializer}.
     * @return a {@link WebApplicationException} with HTTP status {@code 405}; never {@code null}.
     */
    public static WebApplicationException methodNotAllowed(Object body) {
        return new WebApplicationException(jsonResponse(405, body));
    }

    /**
     * Returns a {@code 405 Method Not Allowed} exception with the given cause and no response body.
     *
     * @param cause the exception to attach as the cause; available for server-side
     *              logging but not exposed in the response body.
     * @return a {@link WebApplicationException} with HTTP status {@code 405}; never {@code null}.
     */
    public static WebApplicationException methodNotAllowed(Throwable cause) {
        return new WebApplicationException(cause, emptyResponse(405));
    }

    /**
     * Returns a {@code 405 Method Not Allowed} exception with a {@code text/plain} body and the given cause.
     *
     * @param message the response body text; sent to the caller as {@code text/plain}.
     * @param cause   the exception to attach as the cause; available for server-side
     *                logging but not exposed in the response body.
     * @return a {@link WebApplicationException} with HTTP status {@code 405}; never {@code null}.
     */
    public static WebApplicationException methodNotAllowed(String message, Throwable cause) {
        return new WebApplicationException(message, cause, textResponse(405, message));
    }

    /**
     * Returns a {@code 405 Method Not Allowed} exception with an {@code application/json} body and the given cause.
     *
     * @param body  the response entity; serialized to {@code application/json}
     *              by the server's configured
     *              {@link software.frisby.web.serial.JsonSerializer}.
     * @param cause the exception to attach as the cause; available for server-side
     *              logging but not exposed in the response body.
     * @return a {@link WebApplicationException} with HTTP status {@code 405}; never {@code null}.
     */
    public static WebApplicationException methodNotAllowed(Object body, Throwable cause) {
        return new WebApplicationException(cause, jsonResponse(405, body));
    }

    // -------------------------------------------------------------------------
    // 406 Not Acceptable
    // -------------------------------------------------------------------------

    /**
     * Returns a {@code 406 Not Acceptable} exception.
     *
     * @return a {@link WebApplicationException} with HTTP status {@code 406}; never {@code null}.
     */
    public static WebApplicationException notAcceptable() {
        return new WebApplicationException(emptyResponse(406));
    }

    /**
     * Returns a {@code 406 Not Acceptable} exception with a {@code text/plain} body.
     *
     * @param message the response body text; sent to the caller as {@code text/plain}.
     * @return a {@link WebApplicationException} with HTTP status {@code 406}; never {@code null}.
     */
    public static WebApplicationException notAcceptable(String message) {
        return new WebApplicationException(message, textResponse(406, message));
    }

    /**
     * Returns a {@code 406 Not Acceptable} exception with an {@code application/json} body.
     *
     * @param body the response entity; serialized to {@code application/json}
     *             by the server's configured
     *             {@link software.frisby.web.serial.JsonSerializer}.
     * @return a {@link WebApplicationException} with HTTP status {@code 406}; never {@code null}.
     */
    public static WebApplicationException notAcceptable(Object body) {
        return new WebApplicationException(jsonResponse(406, body));
    }

    /**
     * Returns a {@code 406 Not Acceptable} exception with the given cause and no response body.
     *
     * @param cause the exception to attach as the cause; available for server-side
     *              logging but not exposed in the response body.
     * @return a {@link WebApplicationException} with HTTP status {@code 406}; never {@code null}.
     */
    public static WebApplicationException notAcceptable(Throwable cause) {
        return new WebApplicationException(cause, emptyResponse(406));
    }

    /**
     * Returns a {@code 406 Not Acceptable} exception with a {@code text/plain} body and the given cause.
     *
     * @param message the response body text; sent to the caller as {@code text/plain}.
     * @param cause   the exception to attach as the cause; available for server-side
     *                logging but not exposed in the response body.
     * @return a {@link WebApplicationException} with HTTP status {@code 406}; never {@code null}.
     */
    public static WebApplicationException notAcceptable(String message, Throwable cause) {
        return new WebApplicationException(message, cause, textResponse(406, message));
    }

    /**
     * Returns a {@code 406 Not Acceptable} exception with an {@code application/json} body and the given cause.
     *
     * @param body  the response entity; serialized to {@code application/json}
     *              by the server's configured
     *              {@link software.frisby.web.serial.JsonSerializer}.
     * @param cause the exception to attach as the cause; available for server-side
     *              logging but not exposed in the response body.
     * @return a {@link WebApplicationException} with HTTP status {@code 406}; never {@code null}.
     */
    public static WebApplicationException notAcceptable(Object body, Throwable cause) {
        return new WebApplicationException(cause, jsonResponse(406, body));
    }

    // -------------------------------------------------------------------------
    // 408 Request Timeout
    // -------------------------------------------------------------------------

    /**
     * Returns a {@code 408 Request Timeout} exception.
     *
     * @return a {@link WebApplicationException} with HTTP status {@code 408}; never {@code null}.
     */
    public static WebApplicationException requestTimeout() {
        return new WebApplicationException(emptyResponse(408));
    }

    /**
     * Returns a {@code 408 Request Timeout} exception with a {@code text/plain} body.
     *
     * @param message the response body text; sent to the caller as {@code text/plain}.
     * @return a {@link WebApplicationException} with HTTP status {@code 408}; never {@code null}.
     */
    public static WebApplicationException requestTimeout(String message) {
        return new WebApplicationException(message, textResponse(408, message));
    }

    /**
     * Returns a {@code 408 Request Timeout} exception with an {@code application/json} body.
     *
     * @param body the response entity; serialized to {@code application/json}
     *             by the server's configured
     *             {@link software.frisby.web.serial.JsonSerializer}.
     * @return a {@link WebApplicationException} with HTTP status {@code 408}; never {@code null}.
     */
    public static WebApplicationException requestTimeout(Object body) {
        return new WebApplicationException(jsonResponse(408, body));
    }

    /**
     * Returns a {@code 408 Request Timeout} exception with the given cause and no response body.
     *
     * @param cause the exception to attach as the cause; available for server-side
     *              logging but not exposed in the response body.
     * @return a {@link WebApplicationException} with HTTP status {@code 408}; never {@code null}.
     */
    public static WebApplicationException requestTimeout(Throwable cause) {
        return new WebApplicationException(cause, emptyResponse(408));
    }

    /**
     * Returns a {@code 408 Request Timeout} exception with a {@code text/plain} body and the given cause.
     *
     * @param message the response body text; sent to the caller as {@code text/plain}.
     * @param cause   the exception to attach as the cause; available for server-side
     *                logging but not exposed in the response body.
     * @return a {@link WebApplicationException} with HTTP status {@code 408}; never {@code null}.
     */
    public static WebApplicationException requestTimeout(String message, Throwable cause) {
        return new WebApplicationException(message, cause, textResponse(408, message));
    }

    /**
     * Returns a {@code 408 Request Timeout} exception with an {@code application/json} body and the given cause.
     *
     * @param body  the response entity; serialized to {@code application/json}
     *              by the server's configured
     *              {@link software.frisby.web.serial.JsonSerializer}.
     * @param cause the exception to attach as the cause; available for server-side
     *              logging but not exposed in the response body.
     * @return a {@link WebApplicationException} with HTTP status {@code 408}; never {@code null}.
     */
    public static WebApplicationException requestTimeout(Object body, Throwable cause) {
        return new WebApplicationException(cause, jsonResponse(408, body));
    }

    // -------------------------------------------------------------------------
    // 409 Conflict
    // -------------------------------------------------------------------------

    /**
     * Returns a {@code 409 Conflict} exception.
     *
     * @return a {@link WebApplicationException} with HTTP status {@code 409}; never {@code null}.
     */
    public static WebApplicationException conflict() {
        return new WebApplicationException(emptyResponse(409));
    }

    /**
     * Returns a {@code 409 Conflict} exception with a {@code text/plain} body.
     *
     * @param message the response body text; sent to the caller as {@code text/plain}.
     * @return a {@link WebApplicationException} with HTTP status {@code 409}; never {@code null}.
     */
    public static WebApplicationException conflict(String message) {
        return new WebApplicationException(message, textResponse(409, message));
    }

    /**
     * Returns a {@code 409 Conflict} exception with an {@code application/json} body.
     *
     * @param body the response entity; serialized to {@code application/json}
     *             by the server's configured
     *             {@link software.frisby.web.serial.JsonSerializer}.
     * @return a {@link WebApplicationException} with HTTP status {@code 409}; never {@code null}.
     */
    public static WebApplicationException conflict(Object body) {
        return new WebApplicationException(jsonResponse(409, body));
    }

    /**
     * Returns a {@code 409 Conflict} exception with the given cause and no response body.
     *
     * @param cause the exception to attach as the cause; available for server-side
     *              logging but not exposed in the response body.
     * @return a {@link WebApplicationException} with HTTP status {@code 409}; never {@code null}.
     */
    public static WebApplicationException conflict(Throwable cause) {
        return new WebApplicationException(cause, emptyResponse(409));
    }

    /**
     * Returns a {@code 409 Conflict} exception with a {@code text/plain} body and the given cause.
     *
     * @param message the response body text; sent to the caller as {@code text/plain}.
     * @param cause   the exception to attach as the cause; available for server-side
     *                logging but not exposed in the response body.
     * @return a {@link WebApplicationException} with HTTP status {@code 409}; never {@code null}.
     */
    public static WebApplicationException conflict(String message, Throwable cause) {
        return new WebApplicationException(message, cause, textResponse(409, message));
    }

    /**
     * Returns a {@code 409 Conflict} exception with an {@code application/json} body and the given cause.
     *
     * @param body  the response entity; serialized to {@code application/json}
     *              by the server's configured
     *              {@link software.frisby.web.serial.JsonSerializer}.
     * @param cause the exception to attach as the cause; available for server-side
     *              logging but not exposed in the response body.
     * @return a {@link WebApplicationException} with HTTP status {@code 409}; never {@code null}.
     */
    public static WebApplicationException conflict(Object body, Throwable cause) {
        return new WebApplicationException(cause, jsonResponse(409, body));
    }

    // -------------------------------------------------------------------------
    // 410 Gone
    // -------------------------------------------------------------------------

    /**
     * Returns a {@code 410 Gone} exception.
     *
     * @return a {@link WebApplicationException} with HTTP status {@code 410}; never {@code null}.
     */
    public static WebApplicationException gone() {
        return new WebApplicationException(emptyResponse(410));
    }

    /**
     * Returns a {@code 410 Gone} exception with a {@code text/plain} body.
     *
     * @param message the response body text; sent to the caller as {@code text/plain}.
     * @return a {@link WebApplicationException} with HTTP status {@code 410}; never {@code null}.
     */
    public static WebApplicationException gone(String message) {
        return new WebApplicationException(message, textResponse(410, message));
    }

    /**
     * Returns a {@code 410 Gone} exception with an {@code application/json} body.
     *
     * @param body the response entity; serialized to {@code application/json}
     *             by the server's configured
     *             {@link software.frisby.web.serial.JsonSerializer}.
     * @return a {@link WebApplicationException} with HTTP status {@code 410}; never {@code null}.
     */
    public static WebApplicationException gone(Object body) {
        return new WebApplicationException(jsonResponse(410, body));
    }

    /**
     * Returns a {@code 410 Gone} exception with the given cause and no response body.
     *
     * @param cause the exception to attach as the cause; available for server-side
     *              logging but not exposed in the response body.
     * @return a {@link WebApplicationException} with HTTP status {@code 410}; never {@code null}.
     */
    public static WebApplicationException gone(Throwable cause) {
        return new WebApplicationException(cause, emptyResponse(410));
    }

    /**
     * Returns a {@code 410 Gone} exception with a {@code text/plain} body and the given cause.
     *
     * @param message the response body text; sent to the caller as {@code text/plain}.
     * @param cause   the exception to attach as the cause; available for server-side
     *                logging but not exposed in the response body.
     * @return a {@link WebApplicationException} with HTTP status {@code 410}; never {@code null}.
     */
    public static WebApplicationException gone(String message, Throwable cause) {
        return new WebApplicationException(message, cause, textResponse(410, message));
    }

    /**
     * Returns a {@code 410 Gone} exception with an {@code application/json} body and the given cause.
     *
     * @param body  the response entity; serialized to {@code application/json}
     *              by the server's configured
     *              {@link software.frisby.web.serial.JsonSerializer}.
     * @param cause the exception to attach as the cause; available for server-side
     *              logging but not exposed in the response body.
     * @return a {@link WebApplicationException} with HTTP status {@code 410}; never {@code null}.
     */
    public static WebApplicationException gone(Object body, Throwable cause) {
        return new WebApplicationException(cause, jsonResponse(410, body));
    }

    // -------------------------------------------------------------------------
    // 413 Payload Too Large
    // -------------------------------------------------------------------------

    /**
     * Returns a {@code 413 Payload Too Large} exception.
     *
     * @return a {@link WebApplicationException} with HTTP status {@code 413}; never {@code null}.
     */
    public static WebApplicationException payloadTooLarge() {
        return new WebApplicationException(emptyResponse(413));
    }

    /**
     * Returns a {@code 413 Payload Too Large} exception with a {@code text/plain} body.
     *
     * @param message the response body text; sent to the caller as {@code text/plain}.
     * @return a {@link WebApplicationException} with HTTP status {@code 413}; never {@code null}.
     */
    public static WebApplicationException payloadTooLarge(String message) {
        return new WebApplicationException(message, textResponse(413, message));
    }

    /**
     * Returns a {@code 413 Payload Too Large} exception with an {@code application/json} body.
     *
     * @param body the response entity; serialized to {@code application/json}
     *             by the server's configured
     *             {@link software.frisby.web.serial.JsonSerializer}.
     * @return a {@link WebApplicationException} with HTTP status {@code 413}; never {@code null}.
     */
    public static WebApplicationException payloadTooLarge(Object body) {
        return new WebApplicationException(jsonResponse(413, body));
    }

    /**
     * Returns a {@code 413 Payload Too Large} exception with the given cause and no response body.
     *
     * @param cause the exception to attach as the cause; available for server-side
     *              logging but not exposed in the response body.
     * @return a {@link WebApplicationException} with HTTP status {@code 413}; never {@code null}.
     */
    public static WebApplicationException payloadTooLarge(Throwable cause) {
        return new WebApplicationException(cause, emptyResponse(413));
    }

    /**
     * Returns a {@code 413 Payload Too Large} exception with a {@code text/plain} body and the given cause.
     *
     * @param message the response body text; sent to the caller as {@code text/plain}.
     * @param cause   the exception to attach as the cause; available for server-side
     *                logging but not exposed in the response body.
     * @return a {@link WebApplicationException} with HTTP status {@code 413}; never {@code null}.
     */
    public static WebApplicationException payloadTooLarge(String message, Throwable cause) {
        return new WebApplicationException(message, cause, textResponse(413, message));
    }

    /**
     * Returns a {@code 413 Payload Too Large} exception with an {@code application/json} body and the given cause.
     *
     * @param body  the response entity; serialized to {@code application/json}
     *              by the server's configured
     *              {@link software.frisby.web.serial.JsonSerializer}.
     * @param cause the exception to attach as the cause; available for server-side
     *              logging but not exposed in the response body.
     * @return a {@link WebApplicationException} with HTTP status {@code 413}; never {@code null}.
     */
    public static WebApplicationException payloadTooLarge(Object body, Throwable cause) {
        return new WebApplicationException(cause, jsonResponse(413, body));
    }

    // -------------------------------------------------------------------------
    // 415 Unsupported Media Type
    // -------------------------------------------------------------------------

    /**
     * Returns a {@code 415 Unsupported Media Type} exception.
     *
     * @return a {@link WebApplicationException} with HTTP status {@code 415}; never {@code null}.
     */
    public static WebApplicationException unsupportedMediaType() {
        return new WebApplicationException(emptyResponse(415));
    }

    /**
     * Returns a {@code 415 Unsupported Media Type} exception with a {@code text/plain} body.
     *
     * @param message the response body text; sent to the caller as {@code text/plain}.
     * @return a {@link WebApplicationException} with HTTP status {@code 415}; never {@code null}.
     */
    public static WebApplicationException unsupportedMediaType(String message) {
        return new WebApplicationException(message, textResponse(415, message));
    }

    /**
     * Returns a {@code 415 Unsupported Media Type} exception with an {@code application/json} body.
     *
     * @param body the response entity; serialized to {@code application/json}
     *             by the server's configured
     *             {@link software.frisby.web.serial.JsonSerializer}.
     * @return a {@link WebApplicationException} with HTTP status {@code 415}; never {@code null}.
     */
    public static WebApplicationException unsupportedMediaType(Object body) {
        return new WebApplicationException(jsonResponse(415, body));
    }

    /**
     * Returns a {@code 415 Unsupported Media Type} exception with the given cause and no response body.
     *
     * @param cause the exception to attach as the cause; available for server-side
     *              logging but not exposed in the response body.
     * @return a {@link WebApplicationException} with HTTP status {@code 415}; never {@code null}.
     */
    public static WebApplicationException unsupportedMediaType(Throwable cause) {
        return new WebApplicationException(cause, emptyResponse(415));
    }

    /**
     * Returns a {@code 415 Unsupported Media Type} exception with a {@code text/plain} body and the given cause.
     *
     * @param message the response body text; sent to the caller as {@code text/plain}.
     * @param cause   the exception to attach as the cause; available for server-side
     *                logging but not exposed in the response body.
     * @return a {@link WebApplicationException} with HTTP status {@code 415}; never {@code null}.
     */
    public static WebApplicationException unsupportedMediaType(String message, Throwable cause) {
        return new WebApplicationException(message, cause, textResponse(415, message));
    }

    /**
     * Returns a {@code 415 Unsupported Media Type} exception with an {@code application/json} body and the given cause.
     *
     * @param body  the response entity; serialized to {@code application/json}
     *              by the server's configured
     *              {@link software.frisby.web.serial.JsonSerializer}.
     * @param cause the exception to attach as the cause; available for server-side
     *              logging but not exposed in the response body.
     * @return a {@link WebApplicationException} with HTTP status {@code 415}; never {@code null}.
     */
    public static WebApplicationException unsupportedMediaType(Object body, Throwable cause) {
        return new WebApplicationException(cause, jsonResponse(415, body));
    }

    // -------------------------------------------------------------------------
    // 422 Unprocessable Entity
    // -------------------------------------------------------------------------

    /**
     * Returns a {@code 422 Unprocessable Entity} exception.
     *
     * @return a {@link WebApplicationException} with HTTP status {@code 422}; never {@code null}.
     */
    public static WebApplicationException unprocessableEntity() {
        return new WebApplicationException(emptyResponse(422));
    }

    /**
     * Returns a {@code 422 Unprocessable Entity} exception with a {@code text/plain} body.
     *
     * @param message the response body text; sent to the caller as {@code text/plain}.
     * @return a {@link WebApplicationException} with HTTP status {@code 422}; never {@code null}.
     */
    public static WebApplicationException unprocessableEntity(String message) {
        return new WebApplicationException(message, textResponse(422, message));
    }

    /**
     * Returns a {@code 422 Unprocessable Entity} exception with an {@code application/json} body.
     *
     * @param body the response entity; serialized to {@code application/json}
     *             by the server's configured
     *             {@link software.frisby.web.serial.JsonSerializer}.
     * @return a {@link WebApplicationException} with HTTP status {@code 422}; never {@code null}.
     */
    public static WebApplicationException unprocessableEntity(Object body) {
        return new WebApplicationException(jsonResponse(422, body));
    }

    /**
     * Returns a {@code 422 Unprocessable Entity} exception with the given cause and no response body.
     *
     * @param cause the exception to attach as the cause; available for server-side
     *              logging but not exposed in the response body.
     * @return a {@link WebApplicationException} with HTTP status {@code 422}; never {@code null}.
     */
    public static WebApplicationException unprocessableEntity(Throwable cause) {
        return new WebApplicationException(cause, emptyResponse(422));
    }

    /**
     * Returns a {@code 422 Unprocessable Entity} exception with a {@code text/plain} body and the given cause.
     *
     * @param message the response body text; sent to the caller as {@code text/plain}.
     * @param cause   the exception to attach as the cause; available for server-side
     *                logging but not exposed in the response body.
     * @return a {@link WebApplicationException} with HTTP status {@code 422}; never {@code null}.
     */
    public static WebApplicationException unprocessableEntity(String message, Throwable cause) {
        return new WebApplicationException(message, cause, textResponse(422, message));
    }

    /**
     * Returns a {@code 422 Unprocessable Entity} exception with an {@code application/json} body and the given cause.
     *
     * @param body  the response entity; serialized to {@code application/json}
     *              by the server's configured
     *              {@link software.frisby.web.serial.JsonSerializer}.
     * @param cause the exception to attach as the cause; available for server-side
     *              logging but not exposed in the response body.
     * @return a {@link WebApplicationException} with HTTP status {@code 422}; never {@code null}.
     */
    public static WebApplicationException unprocessableEntity(Object body, Throwable cause) {
        return new WebApplicationException(cause, jsonResponse(422, body));
    }

    // -------------------------------------------------------------------------
    // 429 Too Many Requests
    // -------------------------------------------------------------------------

    /**
     * Returns a {@code 429 Too Many Requests} exception.
     *
     * @return a {@link WebApplicationException} with HTTP status {@code 429}; never {@code null}.
     */
    public static WebApplicationException tooManyRequests() {
        return new WebApplicationException(emptyResponse(429));
    }

    /**
     * Returns a {@code 429 Too Many Requests} exception with a {@code text/plain} body.
     *
     * @param message the response body text; sent to the caller as {@code text/plain}.
     * @return a {@link WebApplicationException} with HTTP status {@code 429}; never {@code null}.
     */
    public static WebApplicationException tooManyRequests(String message) {
        return new WebApplicationException(message, textResponse(429, message));
    }

    /**
     * Returns a {@code 429 Too Many Requests} exception with an {@code application/json} body.
     *
     * @param body the response entity; serialized to {@code application/json}
     *             by the server's configured
     *             {@link software.frisby.web.serial.JsonSerializer}.
     * @return a {@link WebApplicationException} with HTTP status {@code 429}; never {@code null}.
     */
    public static WebApplicationException tooManyRequests(Object body) {
        return new WebApplicationException(jsonResponse(429, body));
    }

    /**
     * Returns a {@code 429 Too Many Requests} exception with the given cause and no response body.
     *
     * @param cause the exception to attach as the cause; available for server-side
     *              logging but not exposed in the response body.
     * @return a {@link WebApplicationException} with HTTP status {@code 429}; never {@code null}.
     */
    public static WebApplicationException tooManyRequests(Throwable cause) {
        return new WebApplicationException(cause, emptyResponse(429));
    }

    /**
     * Returns a {@code 429 Too Many Requests} exception with a {@code text/plain} body and the given cause.
     *
     * @param message the response body text; sent to the caller as {@code text/plain}.
     * @param cause   the exception to attach as the cause; available for server-side
     *                logging but not exposed in the response body.
     * @return a {@link WebApplicationException} with HTTP status {@code 429}; never {@code null}.
     */
    public static WebApplicationException tooManyRequests(String message, Throwable cause) {
        return new WebApplicationException(message, cause, textResponse(429, message));
    }

    /**
     * Returns a {@code 429 Too Many Requests} exception with an {@code application/json} body and the given cause.
     *
     * @param body  the response entity; serialized to {@code application/json}
     *              by the server's configured
     *              {@link software.frisby.web.serial.JsonSerializer}.
     * @param cause the exception to attach as the cause; available for server-side
     *              logging but not exposed in the response body.
     * @return a {@link WebApplicationException} with HTTP status {@code 429}; never {@code null}.
     */
    public static WebApplicationException tooManyRequests(Object body, Throwable cause) {
        return new WebApplicationException(cause, jsonResponse(429, body));
    }

    /**
     * Returns a {@code 429 Too Many Requests} exception with a {@code Retry-After} header
     * and no response body.
     *
     * @param retryAfter the duration after which the caller may retry; written as
     *                   {@code Retry-After: <seconds>} in the response.
     * @throws software.frisby.core.validation.NullValueException             if {@code retryAfter} is {@code null}.
     * @throws software.frisby.core.validation.DurationOutsideRangeException  if {@code retryAfter} is negative.
     * @return a {@link WebApplicationException} with HTTP status {@code 429}; never {@code null}.
     */
    public static WebApplicationException tooManyRequests(Duration retryAfter) {
        return new WebApplicationException(emptyResponseRetryAfter(429, retryAfter));
    }

    /**
     * Returns a {@code 429 Too Many Requests} exception with a {@code text/plain} body and a
     * {@code Retry-After} header.
     *
     * @param message    the response body text; sent to the caller as {@code text/plain}.
     * @param retryAfter the duration after which the caller may retry; written as
     *                   {@code Retry-After: <seconds>} in the response.
     * @throws software.frisby.core.validation.NullValueException             if {@code retryAfter} is {@code null}.
     * @throws software.frisby.core.validation.DurationOutsideRangeException  if {@code retryAfter} is negative.
     * @return a {@link WebApplicationException} with HTTP status {@code 429}; never {@code null}.
     */
    public static WebApplicationException tooManyRequests(String message, Duration retryAfter) {
        return new WebApplicationException(message, textResponseRetryAfter(429, message, retryAfter));
    }

    /**
     * Returns a {@code 429 Too Many Requests} exception with an {@code application/json} body and a
     * {@code Retry-After} header.
     *
     * @param body       the response entity; serialized to {@code application/json} by the
     *                   server's configured
     *                   {@link software.frisby.web.serial.JsonSerializer}.
     * @param retryAfter the duration after which the caller may retry; written as
     *                   {@code Retry-After: <seconds>} in the response.
     * @throws software.frisby.core.validation.NullValueException             if {@code retryAfter} is {@code null}.
     * @throws software.frisby.core.validation.DurationOutsideRangeException  if {@code retryAfter} is negative.
     * @return a {@link WebApplicationException} with HTTP status {@code 429}; never {@code null}.
     */
    public static WebApplicationException tooManyRequests(Object body, Duration retryAfter) {
        return new WebApplicationException(jsonResponseRetryAfter(429, body, retryAfter));
    }

    /**
     * Returns a {@code 429 Too Many Requests} exception with a {@code Retry-After} header,
     * no response body, and the given cause.
     *
     * @param retryAfter the duration after which the caller may retry; written as
     *                   {@code Retry-After: <seconds>} in the response.
     * @param cause      the exception to attach as the cause; available for server-side
     *                   logging but not exposed in the response body.
     * @throws software.frisby.core.validation.NullValueException             if {@code retryAfter} is {@code null}.
     * @throws software.frisby.core.validation.DurationOutsideRangeException  if {@code retryAfter} is negative.
     * @return a {@link WebApplicationException} with HTTP status {@code 429}; never {@code null}.
     */
    public static WebApplicationException tooManyRequests(Duration retryAfter, Throwable cause) {
        return new WebApplicationException(cause, emptyResponseRetryAfter(429, retryAfter));
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
     * @throws software.frisby.core.validation.NullValueException             if {@code retryAfter} is {@code null}.
     * @throws software.frisby.core.validation.DurationOutsideRangeException  if {@code retryAfter} is negative.
     * @return a {@link WebApplicationException} with HTTP status {@code 429}; never {@code null}.
     */
    public static WebApplicationException tooManyRequests(String message, Duration retryAfter, Throwable cause) {
        return new WebApplicationException(message, cause, textResponseRetryAfter(429, message, retryAfter));
    }

    /**
     * Returns a {@code 429 Too Many Requests} exception with an {@code application/json} body,
     * a {@code Retry-After} header, and the given cause.
     *
     * @param body       the response entity; serialized to {@code application/json} by the
     *                   server's configured
     *                   {@link software.frisby.web.serial.JsonSerializer}.
     * @param retryAfter the duration after which the caller may retry; written as
     *                   {@code Retry-After: <seconds>} in the response.
     * @param cause      the exception to attach as the cause; available for server-side
     *                   logging but not exposed in the response body.
     * @throws software.frisby.core.validation.NullValueException             if {@code retryAfter} is {@code null}.
     * @throws software.frisby.core.validation.DurationOutsideRangeException  if {@code retryAfter} is negative.
     * @return a {@link WebApplicationException} with HTTP status {@code 429}; never {@code null}.
     */
    public static WebApplicationException tooManyRequests(Object body, Duration retryAfter, Throwable cause) {
        return new WebApplicationException(cause, jsonResponseRetryAfter(429, body, retryAfter));
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
     * @return a {@link WebApplicationException} with HTTP status {@code 500}; never {@code null}.
     */
    public static WebApplicationException internalServerError() {
        return new WebApplicationException(emptyResponse(500));
    }

    /**
     * Returns a {@code 500 Internal Server Error} exception with a {@code text/plain} body.
     *
     * @param message the response body text; sent to the caller as {@code text/plain}.
     * @return a {@link WebApplicationException} with HTTP status {@code 500}; never {@code null}.
     */
    public static WebApplicationException internalServerError(String message) {
        return new WebApplicationException(message, textResponse(500, message));
    }

    /**
     * Returns a {@code 500 Internal Server Error} exception with an {@code application/json} body.
     *
     * @param body the response entity; serialized to {@code application/json}
     *             by the server's configured
     *             {@link software.frisby.web.serial.JsonSerializer}.
     * @return a {@link WebApplicationException} with HTTP status {@code 500}; never {@code null}.
     */
    public static WebApplicationException internalServerError(Object body) {
        return new WebApplicationException(jsonResponse(500, body));
    }

    /**
     * Returns a {@code 500 Internal Server Error} exception with the given cause and no response body.
     *
     * @param cause the exception to attach as the cause; available for server-side
     *              logging but not exposed in the response body.
     * @return a {@link WebApplicationException} with HTTP status {@code 500}; never {@code null}.
     */
    public static WebApplicationException internalServerError(Throwable cause) {
        return new WebApplicationException(cause, emptyResponse(500));
    }

    /**
     * Returns a {@code 500 Internal Server Error} exception with a {@code text/plain} body and the given cause.
     *
     * @param message the response body text; sent to the caller as {@code text/plain}.
     * @param cause   the exception to attach as the cause; available for server-side
     *                logging but not exposed in the response body.
     * @return a {@link WebApplicationException} with HTTP status {@code 500}; never {@code null}.
     */
    public static WebApplicationException internalServerError(String message, Throwable cause) {
        return new WebApplicationException(message, cause, textResponse(500, message));
    }

    /**
     * Returns a {@code 500 Internal Server Error} exception with an {@code application/json} body and the given cause.
     *
     * @param body  the response entity; serialized to {@code application/json}
     *              by the server's configured
     *              {@link software.frisby.web.serial.JsonSerializer}.
     * @param cause the exception to attach as the cause; available for server-side
     *              logging but not exposed in the response body.
     * @return a {@link WebApplicationException} with HTTP status {@code 500}; never {@code null}.
     */
    public static WebApplicationException internalServerError(Object body, Throwable cause) {
        return new WebApplicationException(cause, jsonResponse(500, body));
    }

    // -------------------------------------------------------------------------
    // 501 Not Implemented
    // -------------------------------------------------------------------------

    /**
     * Returns a {@code 501 Not Implemented} exception.
     *
     * @return a {@link WebApplicationException} with HTTP status {@code 501}; never {@code null}.
     */
    public static WebApplicationException notImplemented() {
        return new WebApplicationException(emptyResponse(501));
    }

    /**
     * Returns a {@code 501 Not Implemented} exception with a {@code text/plain} body.
     *
     * @param message the response body text; sent to the caller as {@code text/plain}.
     * @return a {@link WebApplicationException} with HTTP status {@code 501}; never {@code null}.
     */
    public static WebApplicationException notImplemented(String message) {
        return new WebApplicationException(message, textResponse(501, message));
    }

    /**
     * Returns a {@code 501 Not Implemented} exception with an {@code application/json} body.
     *
     * @param body the response entity; serialized to {@code application/json}
     *             by the server's configured
     *             {@link software.frisby.web.serial.JsonSerializer}.
     * @return a {@link WebApplicationException} with HTTP status {@code 501}; never {@code null}.
     */
    public static WebApplicationException notImplemented(Object body) {
        return new WebApplicationException(jsonResponse(501, body));
    }

    /**
     * Returns a {@code 501 Not Implemented} exception with the given cause and no response body.
     *
     * @param cause the exception to attach as the cause; available for server-side
     *              logging but not exposed in the response body.
     * @return a {@link WebApplicationException} with HTTP status {@code 501}; never {@code null}.
     */
    public static WebApplicationException notImplemented(Throwable cause) {
        return new WebApplicationException(cause, emptyResponse(501));
    }

    /**
     * Returns a {@code 501 Not Implemented} exception with a {@code text/plain} body and the given cause.
     *
     * @param message the response body text; sent to the caller as {@code text/plain}.
     * @param cause   the exception to attach as the cause; available for server-side
     *                logging but not exposed in the response body.
     * @return a {@link WebApplicationException} with HTTP status {@code 501}; never {@code null}.
     */
    public static WebApplicationException notImplemented(String message, Throwable cause) {
        return new WebApplicationException(message, cause, textResponse(501, message));
    }

    /**
     * Returns a {@code 501 Not Implemented} exception with an {@code application/json} body and the given cause.
     *
     * @param body  the response entity; serialized to {@code application/json}
     *              by the server's configured
     *              {@link software.frisby.web.serial.JsonSerializer}.
     * @param cause the exception to attach as the cause; available for server-side
     *              logging but not exposed in the response body.
     * @return a {@link WebApplicationException} with HTTP status {@code 501}; never {@code null}.
     */
    public static WebApplicationException notImplemented(Object body, Throwable cause) {
        return new WebApplicationException(cause, jsonResponse(501, body));
    }

    // -------------------------------------------------------------------------
    // 502 Bad Gateway
    // -------------------------------------------------------------------------

    /**
     * Returns a {@code 502 Bad Gateway} exception.
     *
     * @return a {@link WebApplicationException} with HTTP status {@code 502}; never {@code null}.
     */
    public static WebApplicationException badGateway() {
        return new WebApplicationException(emptyResponse(502));
    }

    /**
     * Returns a {@code 502 Bad Gateway} exception with a {@code text/plain} body.
     *
     * @param message the response body text; sent to the caller as {@code text/plain}.
     * @return a {@link WebApplicationException} with HTTP status {@code 502}; never {@code null}.
     */
    public static WebApplicationException badGateway(String message) {
        return new WebApplicationException(message, textResponse(502, message));
    }

    /**
     * Returns a {@code 502 Bad Gateway} exception with an {@code application/json} body.
     *
     * @param body the response entity; serialized to {@code application/json}
     *             by the server's configured
     *             {@link software.frisby.web.serial.JsonSerializer}.
     * @return a {@link WebApplicationException} with HTTP status {@code 502}; never {@code null}.
     */
    public static WebApplicationException badGateway(Object body) {
        return new WebApplicationException(jsonResponse(502, body));
    }

    /**
     * Returns a {@code 502 Bad Gateway} exception with the given cause and no response body.
     *
     * @param cause the exception to attach as the cause; available for server-side
     *              logging but not exposed in the response body.
     * @return a {@link WebApplicationException} with HTTP status {@code 502}; never {@code null}.
     */
    public static WebApplicationException badGateway(Throwable cause) {
        return new WebApplicationException(cause, emptyResponse(502));
    }

    /**
     * Returns a {@code 502 Bad Gateway} exception with a {@code text/plain} body and the given cause.
     *
     * @param message the response body text; sent to the caller as {@code text/plain}.
     * @param cause   the exception to attach as the cause; available for server-side
     *                logging but not exposed in the response body.
     * @return a {@link WebApplicationException} with HTTP status {@code 502}; never {@code null}.
     */
    public static WebApplicationException badGateway(String message, Throwable cause) {
        return new WebApplicationException(message, cause, textResponse(502, message));
    }

    /**
     * Returns a {@code 502 Bad Gateway} exception with an {@code application/json} body and the given cause.
     *
     * @param body  the response entity; serialized to {@code application/json}
     *              by the server's configured
     *              {@link software.frisby.web.serial.JsonSerializer}.
     * @param cause the exception to attach as the cause; available for server-side
     *              logging but not exposed in the response body.
     * @return a {@link WebApplicationException} with HTTP status {@code 502}; never {@code null}.
     */
    public static WebApplicationException badGateway(Object body, Throwable cause) {
        return new WebApplicationException(cause, jsonResponse(502, body));
    }

    // -------------------------------------------------------------------------
    // 503 Service Unavailable
    // -------------------------------------------------------------------------

    /**
     * Returns a {@code 503 Service Unavailable} exception.
     *
     * @return a {@link WebApplicationException} with HTTP status {@code 503}; never {@code null}.
     */
    public static WebApplicationException serviceUnavailable() {
        return new WebApplicationException(emptyResponse(503));
    }

    /**
     * Returns a {@code 503 Service Unavailable} exception with a {@code text/plain} body.
     *
     * @param message the response body text; sent to the caller as {@code text/plain}.
     * @return a {@link WebApplicationException} with HTTP status {@code 503}; never {@code null}.
     */
    public static WebApplicationException serviceUnavailable(String message) {
        return new WebApplicationException(message, textResponse(503, message));
    }

    /**
     * Returns a {@code 503 Service Unavailable} exception with an {@code application/json} body.
     *
     * @param body the response entity; serialized to {@code application/json}
     *             by the server's configured
     *             {@link software.frisby.web.serial.JsonSerializer}.
     * @return a {@link WebApplicationException} with HTTP status {@code 503}; never {@code null}.
     */
    public static WebApplicationException serviceUnavailable(Object body) {
        return new WebApplicationException(jsonResponse(503, body));
    }

    /**
     * Returns a {@code 503 Service Unavailable} exception with the given cause and no response body.
     *
     * @param cause the exception to attach as the cause; available for server-side
     *              logging but not exposed in the response body.
     * @return a {@link WebApplicationException} with HTTP status {@code 503}; never {@code null}.
     */
    public static WebApplicationException serviceUnavailable(Throwable cause) {
        return new WebApplicationException(cause, emptyResponse(503));
    }

    /**
     * Returns a {@code 503 Service Unavailable} exception with a {@code text/plain} body and the given cause.
     *
     * @param message the response body text; sent to the caller as {@code text/plain}.
     * @param cause   the exception to attach as the cause; available for server-side
     *                logging but not exposed in the response body.
     * @return a {@link WebApplicationException} with HTTP status {@code 503}; never {@code null}.
     */
    public static WebApplicationException serviceUnavailable(String message, Throwable cause) {
        return new WebApplicationException(message, cause, textResponse(503, message));
    }

    /**
     * Returns a {@code 503 Service Unavailable} exception with an {@code application/json} body and the given cause.
     *
     * @param body  the response entity; serialized to {@code application/json}
     *              by the server's configured
     *              {@link software.frisby.web.serial.JsonSerializer}.
     * @param cause the exception to attach as the cause; available for server-side
     *              logging but not exposed in the response body.
     * @return a {@link WebApplicationException} with HTTP status {@code 503}; never {@code null}.
     */
    public static WebApplicationException serviceUnavailable(Object body, Throwable cause) {
        return new WebApplicationException(cause, jsonResponse(503, body));
    }

    /**
     * Returns a {@code 503 Service Unavailable} exception with a {@code Retry-After} header
     * and no response body.
     *
     * @param retryAfter the duration after which the caller may retry; written as
     *                   {@code Retry-After: <seconds>} in the response.
     * @throws software.frisby.core.validation.NullValueException             if {@code retryAfter} is {@code null}.
     * @throws software.frisby.core.validation.DurationOutsideRangeException  if {@code retryAfter} is negative.
     * @return a {@link WebApplicationException} with HTTP status {@code 503}; never {@code null}.
     */
    public static WebApplicationException serviceUnavailable(Duration retryAfter) {
        return new WebApplicationException(emptyResponseRetryAfter(503, retryAfter));
    }

    /**
     * Returns a {@code 503 Service Unavailable} exception with a {@code text/plain} body and a
     * {@code Retry-After} header.
     *
     * @param message    the response body text; sent to the caller as {@code text/plain}.
     * @param retryAfter the duration after which the caller may retry; written as
     *                   {@code Retry-After: <seconds>} in the response.
     * @throws software.frisby.core.validation.NullValueException             if {@code retryAfter} is {@code null}.
     * @throws software.frisby.core.validation.DurationOutsideRangeException  if {@code retryAfter} is negative.
     * @return a {@link WebApplicationException} with HTTP status {@code 503}; never {@code null}.
     */
    public static WebApplicationException serviceUnavailable(String message, Duration retryAfter) {
        return new WebApplicationException(message, textResponseRetryAfter(503, message, retryAfter));
    }

    /**
     * Returns a {@code 503 Service Unavailable} exception with an {@code application/json} body and a
     * {@code Retry-After} header.
     *
     * @param body       the response entity; serialized to {@code application/json} by the
     *                   server's configured
     *                   {@link software.frisby.web.serial.JsonSerializer}.
     * @param retryAfter the duration after which the caller may retry; written as
     *                   {@code Retry-After: <seconds>} in the response.
     * @throws software.frisby.core.validation.NullValueException             if {@code retryAfter} is {@code null}.
     * @throws software.frisby.core.validation.DurationOutsideRangeException  if {@code retryAfter} is negative.
     * @return a {@link WebApplicationException} with HTTP status {@code 503}; never {@code null}.
     */
    public static WebApplicationException serviceUnavailable(Object body, Duration retryAfter) {
        return new WebApplicationException(jsonResponseRetryAfter(503, body, retryAfter));
    }

    /**
     * Returns a {@code 503 Service Unavailable} exception with a {@code Retry-After} header,
     * no response body, and the given cause.
     *
     * @param retryAfter the duration after which the caller may retry; written as
     *                   {@code Retry-After: <seconds>} in the response.
     * @param cause      the exception to attach as the cause; available for server-side
     *                   logging but not exposed in the response body.
     * @throws software.frisby.core.validation.NullValueException             if {@code retryAfter} is {@code null}.
     * @throws software.frisby.core.validation.DurationOutsideRangeException  if {@code retryAfter} is negative.
     * @return a {@link WebApplicationException} with HTTP status {@code 503}; never {@code null}.
     */
    public static WebApplicationException serviceUnavailable(Duration retryAfter, Throwable cause) {
        return new WebApplicationException(cause, emptyResponseRetryAfter(503, retryAfter));
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
     * @throws software.frisby.core.validation.NullValueException             if {@code retryAfter} is {@code null}.
     * @throws software.frisby.core.validation.DurationOutsideRangeException  if {@code retryAfter} is negative.
     * @return a {@link WebApplicationException} with HTTP status {@code 503}; never {@code null}.
     */
    public static WebApplicationException serviceUnavailable(String message, Duration retryAfter, Throwable cause) {
        return new WebApplicationException(message, cause, textResponseRetryAfter(503, message, retryAfter));
    }

    /**
     * Returns a {@code 503 Service Unavailable} exception with an {@code application/json} body,
     * a {@code Retry-After} header, and the given cause.
     *
     * @param body       the response entity; serialized to {@code application/json} by the
     *                   server's configured
     *                   {@link software.frisby.web.serial.JsonSerializer}.
     * @param retryAfter the duration after which the caller may retry; written as
     *                   {@code Retry-After: <seconds>} in the response.
     * @param cause      the exception to attach as the cause; available for server-side
     *                   logging but not exposed in the response body.
     * @throws software.frisby.core.validation.NullValueException             if {@code retryAfter} is {@code null}.
     * @throws software.frisby.core.validation.DurationOutsideRangeException  if {@code retryAfter} is negative.
     * @return a {@link WebApplicationException} with HTTP status {@code 503}; never {@code null}.
     */
    public static WebApplicationException serviceUnavailable(Object body, Duration retryAfter, Throwable cause) {
        return new WebApplicationException(cause, jsonResponseRetryAfter(503, body, retryAfter));
    }

    // -------------------------------------------------------------------------
    // 504 Gateway Timeout
    // -------------------------------------------------------------------------

    /**
     * Returns a {@code 504 Gateway Timeout} exception.
     *
     * @return a {@link WebApplicationException} with HTTP status {@code 504}; never {@code null}.
     */
    public static WebApplicationException gatewayTimeout() {
        return new WebApplicationException(emptyResponse(504));
    }

    /**
     * Returns a {@code 504 Gateway Timeout} exception with a {@code text/plain} body.
     *
     * @param message the response body text; sent to the caller as {@code text/plain}.
     * @return a {@link WebApplicationException} with HTTP status {@code 504}; never {@code null}.
     */
    public static WebApplicationException gatewayTimeout(String message) {
        return new WebApplicationException(message, textResponse(504, message));
    }

    /**
     * Returns a {@code 504 Gateway Timeout} exception with an {@code application/json} body.
     *
     * @param body the response entity; serialized to {@code application/json}
     *             by the server's configured
     *             {@link software.frisby.web.serial.JsonSerializer}.
     * @return a {@link WebApplicationException} with HTTP status {@code 504}; never {@code null}.
     */
    public static WebApplicationException gatewayTimeout(Object body) {
        return new WebApplicationException(jsonResponse(504, body));
    }

    /**
     * Returns a {@code 504 Gateway Timeout} exception with the given cause and no response body.
     *
     * @param cause the exception to attach as the cause; available for server-side
     *              logging but not exposed in the response body.
     * @return a {@link WebApplicationException} with HTTP status {@code 504}; never {@code null}.
     */
    public static WebApplicationException gatewayTimeout(Throwable cause) {
        return new WebApplicationException(cause, emptyResponse(504));
    }

    /**
     * Returns a {@code 504 Gateway Timeout} exception with a {@code text/plain} body and the given cause.
     *
     * @param message the response body text; sent to the caller as {@code text/plain}.
     * @param cause   the exception to attach as the cause; available for server-side
     *                logging but not exposed in the response body.
     * @return a {@link WebApplicationException} with HTTP status {@code 504}; never {@code null}.
     */
    public static WebApplicationException gatewayTimeout(String message, Throwable cause) {
        return new WebApplicationException(message, cause, textResponse(504, message));
    }

    /**
     * Returns a {@code 504 Gateway Timeout} exception with an {@code application/json} body and the given cause.
     *
     * @param body  the response entity; serialized to {@code application/json}
     *              by the server's configured
     *              {@link software.frisby.web.serial.JsonSerializer}.
     * @param cause the exception to attach as the cause; available for server-side
     *              logging but not exposed in the response body.
     * @return a {@link WebApplicationException} with HTTP status {@code 504}; never {@code null}.
     */
    public static WebApplicationException gatewayTimeout(Object body, Throwable cause) {
        return new WebApplicationException(cause, jsonResponse(504, body));
    }
}


