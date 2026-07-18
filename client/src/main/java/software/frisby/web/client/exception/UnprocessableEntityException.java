package software.frisby.web.client.exception;

import java.io.Serial;
import java.net.URI;
import java.net.http.HttpHeaders;

/**
 * Thrown when the server returns HTTP {@code 422 Unprocessable Content}.
 * <p>
 * The request was well-formed and understood, but the server cannot process it
 * because the content fails semantic validation.  This is the standard response
 * for domain-level validation errors in modern REST APIs — for example, a required
 * field is missing, a value is out of range, or a business rule is violated.
 * <p>
 * The response body typically contains a structured description of the validation
 * errors.  Retrieve it via {@link #body()} and deserialize with the caller's
 * {@link software.frisby.web.serial.JsonSerializer}.
 *
 * <pre>{@code
 * try {
 *     // ...
 * } catch (UnprocessableEntityException e) {
 *     e.body().ifPresent(raw -> {
 *         ValidationErrors errors = serializer.deserialize(raw, ValidationErrors.class);
 *         // handle field-level validation errors
 *     });
 * }
 * }</pre>
 */
public final class UnprocessableEntityException extends ClientException {
    @Serial
    private static final long serialVersionUID = 1L;
    private static final int STATUS_CODE = 422;

    /**
     * Creates a {@code 422} exception with the full request context.
     *
     * @param method  The HTTP method of the request (e.g. {@code GET}, {@code POST}).
     * @param uri     The URI of the request.
     * @param headers The response headers received from the server.
     * @param body    The response body, or {@code null} if none was received.
     */
    public UnprocessableEntityException(String method, URI uri, HttpHeaders headers, String body) {
        super(method, uri, STATUS_CODE, headers, body);
    }

    /**
     * Creates a {@code 422} exception with a response body.
     *
     * @param body The response body, or {@code null} if none was received.
     */
    public UnprocessableEntityException(String body) {
        super(STATUS_CODE, body);
    }

    /**
     * Creates a {@code 422} exception without request context or body.
     */
    public UnprocessableEntityException() {
        super(STATUS_CODE);
    }
}

