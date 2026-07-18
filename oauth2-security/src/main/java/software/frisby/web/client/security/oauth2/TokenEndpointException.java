package software.frisby.web.client.security.oauth2;

import java.net.URI;
import java.util.Optional;

/**
 * Thrown when the token endpoint returns an HTTP error response ({@code 4xx} / {@code 5xx}),
 * or when a success response does not contain a valid {@code access_token}.
 * <p>
 * This exception is intentionally separate from the HTTP exception types in the
 * {@code client} module ({@link software.frisby.web.client.exception.HttpResponseException}
 * and its subtypes) so that callers can distinguish between a failure on the resource API
 * and a failure on the token endpoint at a glance.
 * <p>
 * The full response body from the token server is preserved when present.  Authorization
 * servers typically include an {@code error} and {@code error_description} field that
 * precisely identify the cause of the failure.
 *
 * <pre>{@code
 * try {
 *     Order order = client.post()
 *             .path("/orders")
 *             .body(newOrder)
 *             .send(Order.class)
 *             .body();
 * } catch (TokenEndpointException ex) {
 *     if (ex.statusCode() == 429) {
 *         // token server is rate-limiting; back off and retry
 *     } else if (ex.statusCode() == 503) {
 *         // token server temporarily unavailable; retry later
 *     } else {
 *         // configuration or protocol error
 *         ex.body().ifPresent(body -> log.error("Token error: {}", body));
 *     }
 * }
 * }</pre>
 *
 * @see ClientCredentialsSecurityProvider
 */
public final class TokenEndpointException extends RuntimeException {
    private static final int BODY_PREVIEW_LENGTH = 200;

    /** The URI of the token endpoint that produced this error. */
    private final URI tokenEndpoint;
    /** The HTTP status code returned by the token endpoint, or {@code 0} for protocol/parsing errors. */
    private final int statusCode;
    /** The raw response body, or {@code null} if none was received. */
    private final String body;

    /**
     * Creates an exception for an HTTP error response from the token endpoint.
     *
     * @param tokenEndpoint The URI of the token endpoint.
     * @param statusCode    The HTTP status code returned by the token endpoint, or
     *                      {@code 0} when the failure is a protocol or parsing error
     *                      rather than an HTTP-level error.
     * @param body          The response body, or {@code null} if none was received.
     */
    public TokenEndpointException(URI tokenEndpoint, int statusCode, String body) {
        super((String) null);

        this.tokenEndpoint = tokenEndpoint;
        this.statusCode = statusCode;
        this.body = body;
    }

    /**
     * Returns the URI of the token endpoint that produced this error.
     *
     * @return The token endpoint URI.
     */
    public URI tokenEndpoint() {
        return tokenEndpoint;
    }

    /**
     * Returns the HTTP status code returned by the token endpoint.
     * <p>
     * Returns {@code 0} when the failure is a protocol or parsing error (e.g. the
     * server responded with {@code 200} but the body did not contain an
     * {@code access_token} field).
     *
     * @return The HTTP status code, or {@code 0} for non-HTTP failures.
     */
    public int statusCode() {
        return statusCode;
    }

    /**
     * Returns the raw response body received from the token endpoint, when present.
     *
     * @return The response body, or empty if none was received.
     */
    public Optional<String> body() {
        return Optional.ofNullable(body);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        sb.append("Token endpoint POST ").append(tokenEndpoint);

        if (statusCode > 0) {
            sb.append(" → ").append(statusCode);
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
