package software.frisby.web.client.exception;

import software.frisby.core.validation.Strings;

import java.io.Serial;
import java.util.Optional;

/**
 * Thrown when a successful HTTP response body cannot be deserialized to the
 * requested type.
 * <p>
 * This exception is distinct from {@link HttpResponseException} (the server
 * returned an error status) and {@link HttpRequestException} (a transport-level
 * failure).  It represents a client-side processing failure that occurs after a
 * valid HTTP response has been received but cannot be parsed into the caller's
 * target type.
 * <p>
 * The raw response body is preserved in {@link #rawBody()} to aid diagnosis —
 * it can be logged, passed to a JSON validator, or deserialized to an alternative
 * type.
 *
 * <pre>{@code
 * try {
 *     MyDto dto = client.get()
 *             .path("/items/{id}", "id", itemId)
 *             .send(MyDto.class)
 *             .body();
 * } catch (ResponseDeserializationException e) {
 *     log.error("Cannot parse response as {}: {}", e.targetType(), e.rawBody().orElse("<empty>"));
 * }
 * }</pre>
 */
public final class ResponseDeserializationException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * The fully-qualified name of the type the client attempted to deserialize into.
     */
    private final String targetType;

    /**
     * The raw response body that could not be deserialized, or {@code null} if not available.
     */
    private final String rawBody;

    /**
     * Creates an exception with full context about the failed deserialization.
     *
     * @param message    A description of the failure.
     * @param cause      The underlying serializer exception.
     * @param targetType The fully-qualified name of the type that could not be populated.
     * @param rawBody    The raw response body string; may be {@code null}.
     */
    public ResponseDeserializationException(String message,
                                            Throwable cause,
                                            String targetType,
                                            String rawBody) {
        super(message, cause);

        this.targetType = Strings.notBlank("targetType", targetType);
        this.rawBody = rawBody;
    }

    /**
     * Returns the fully-qualified name of the type the client attempted to deserialize into.
     *
     * @return The target type name (e.g. {@code "com.example.MyDto"} or
     * {@code "java.util.List<com.example.MyDto>"}).
     */
    public String targetType() {
        return targetType;
    }

    /**
     * Returns the raw response body that could not be deserialized, when available.
     * <p>
     * May be empty if the body was null or blank.
     *
     * @return The raw response body, or empty if not available.
     */
    public Optional<String> rawBody() {
        return Optional.ofNullable(rawBody);
    }
}

