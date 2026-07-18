package software.frisby.web.client.exception;

/**
 * Thrown when a URI path template is invalid or cannot be assembled into a
 * syntactically valid URI.
 * <p>
 * Common causes:
 * <ul>
 *   <li>A path parameter name supplied to a {@code path(String, String, String)} or
 *       {@code path(String, PathParameter...)} overload does not correspond to a
 *       {@code {name}} placeholder in the path template — detected immediately at the
 *       {@code path()} call site.</li>
 *   <li>After placeholder substitution the assembled URI string is not a valid RFC 3986
 *       URI — for example because a placeholder was left unreplaced.</li>
 * </ul>
 */
public final class UriSyntaxException extends RuntimeException {
    /**
     * Constructs a {@code UriSyntaxException} with the given detail message.
     *
     * @param message The detail message.
     */
    public UriSyntaxException(String message) {
        super(message);
    }

    /**
     * Constructs a {@code UriSyntaxException} with the given detail message and cause.
     *
     * @param message The detail message.
     * @param cause   The underlying {@link java.net.URISyntaxException}.
     */
    public UriSyntaxException(String message, Throwable cause) {
        super(message, cause);
    }
}

