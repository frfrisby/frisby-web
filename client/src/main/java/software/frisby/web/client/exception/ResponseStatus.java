package software.frisby.web.client.exception;

import software.frisby.core.validation.Numbers;
import software.frisby.core.validation.Strings;

/**
 * HTTP status codes and their standard reason phrases.
 * <p>
 * Covers the codes defined in RFC 7231 and several widely-used extensions.
 * Use {@link #fromCode(int)} to look up a status by its integer value.
 *
 * <pre>{@code
 * ResponseStatus status = ResponseStatus.fromCode(404);
 * System.out.println(status.reason()); // "Not Found"
 * }</pre>
 */
public enum ResponseStatus {
    // 2xx — Success

    /**
     * 200 OK
     */
    OK(200, "OK"),
    /**
     * 201 Created
     */
    CREATED(201, "Created"),
    /**
     * 202 Accepted
     */
    ACCEPTED(202, "Accepted"),
    /**
     * 204 No Content
     */
    NO_CONTENT(204, "No Content"),
    /**
     * 206 Partial Content
     */
    PARTIAL_CONTENT(206, "Partial Content"),

    // 3xx — Redirection

    /**
     * 301 Moved Permanently
     */
    MOVED_PERMANENTLY(301, "Moved Permanently"),
    /**
     * 302 Found
     */
    FOUND(302, "Found"),
    /**
     * 303 See Other
     */
    SEE_OTHER(303, "See Other"),
    /**
     * 304 Not Modified
     */
    NOT_MODIFIED(304, "Not Modified"),
    /**
     * 307 Temporary Redirect
     */
    TEMPORARY_REDIRECT(307, "Temporary Redirect"),
    /**
     * 308 Permanent Redirect
     */
    PERMANENT_REDIRECT(308, "Permanent Redirect"),

    // 4xx — Client Error

    /**
     * 400 Bad Request
     */
    BAD_REQUEST(400, "Bad Request"),
    /**
     * 401 Unauthorized
     */
    UNAUTHORIZED(401, "Unauthorized"),
    /**
     * 403 Forbidden
     */
    FORBIDDEN(403, "Forbidden"),
    /**
     * 404 Not Found
     */
    NOT_FOUND(404, "Not Found"),
    /**
     * 405 Method Not Allowed
     */
    METHOD_NOT_ALLOWED(405, "Method Not Allowed"),
    /**
     * 406 Not Acceptable
     */
    NOT_ACCEPTABLE(406, "Not Acceptable"),
    /**
     * 408 Request Timeout
     */
    REQUEST_TIMEOUT(408, "Request Timeout"),
    /**
     * 409 Conflict
     */
    CONFLICT(409, "Conflict"),
    /**
     * 410 Gone
     */
    GONE(410, "Gone"),
    /**
     * 413 Content Too Large
     */
    CONTENT_TOO_LARGE(413, "Content Too Large"),
    /**
     * 415 Unsupported Media Type
     */
    UNSUPPORTED_MEDIA_TYPE(415, "Unsupported Media Type"),
    /**
     * 422 Unprocessable Content
     */
    UNPROCESSABLE_CONTENT(422, "Unprocessable Content"),
    /**
     * 429 Too Many Requests
     */
    TOO_MANY_REQUESTS(429, "Too Many Requests"),

    // 5xx — Server Error

    /**
     * 500 Internal Server Error
     */
    INTERNAL_SERVER_ERROR(500, "Internal Server Error"),
    /**
     * 501 Not Implemented
     */
    NOT_IMPLEMENTED(501, "Not Implemented"),
    /**
     * 502 Bad Gateway
     */
    BAD_GATEWAY(502, "Bad Gateway"),
    /**
     * 503 Service Unavailable
     */
    SERVICE_UNAVAILABLE(503, "Service Unavailable"),
    /**
     * 504 Gateway Timeout
     */
    GATEWAY_TIMEOUT(504, "Gateway Timeout"),

    /**
     * Sentinel for status codes not listed above.
     */
    UNKNOWN(-1, "Unknown");

    private final int code;
    private final String reason;

    ResponseStatus(int code, String reason) {
        this.code = Numbers.rangeExclusiveMax("code", code, -1, 600);
        this.reason = Strings.notBlank("reason", reason);
    }

    /**
     * Returns the {@link ResponseStatus} for the provided integer code, or
     * {@link #UNKNOWN} if the code is not listed.
     *
     * @param code The HTTP status code.
     * @return The matching {@link ResponseStatus}.
     */
    public static ResponseStatus fromCode(int code) {
        for (ResponseStatus value : values()) {
            if (value.code == code) {
                return value;
            }
        }

        return UNKNOWN;
    }

    /**
     * Returns the integer status code (e.g. {@code 404}).
     *
     * @return The status code.
     */
    public int code() {
        return code;
    }

    /**
     * Returns the standard reason phrase (e.g. {@code "Not Found"}).
     *
     * @return The reason phrase.
     */
    public String reason() {
        return reason;
    }

    /**
     * Returns {@code true} if this status represents a successful response (2xx).
     *
     * @return {@code true} for {@code 2xx} status codes.
     */
    public boolean isSuccess() {
        return code >= 200 && code < 300;
    }

    /**
     * Returns {@code true} if this status represents a client error (4xx).
     *
     * @return {@code true} for {@code 4xx} status codes.
     */
    public boolean isClientError() {
        return code >= 400 && code < 500;
    }

    /**
     * Returns {@code true} if this status represents a server error (5xx).
     *
     * @return {@code true} for {@code 5xx} status codes.
     */
    public boolean isServerError() {
        return code >= 500;
    }

    @Override
    public String toString() {
        return code + " " + reason;
    }
}

