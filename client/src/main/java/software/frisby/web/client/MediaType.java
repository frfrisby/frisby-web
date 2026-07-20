package software.frisby.web.client;

import software.frisby.core.validation.Strings;

import java.util.Objects;

/**
 * Represents an HTTP media type (also called a MIME type or content type).
 * <p>
 * Well-known types are available as constants.  Custom types can be created via
 * {@link #of(String)}.
 *
 * <pre>{@code
 * // Using a well-known constant
 * FormPart.entity("payload", json, MediaType.APPLICATION_JSON);
 *
 * // Using a custom type
 * FormPart.entity("payload", xmlString, MediaType.of("application/xml"));
 * }</pre>
 *
 * @see FormPart
 */
public final class MediaType {
    /**
     * {@code application/json}
     */
    public static final MediaType APPLICATION_JSON = new MediaType("application/json");
    /**
     * {@code application/octet-stream}
     */
    public static final MediaType APPLICATION_OCTET_STREAM = new MediaType("application/octet-stream");
    /**
     * {@code multipart/form-data}
     */
    public static final MediaType MULTIPART_FORM_DATA = new MediaType("multipart/form-data");
    /**
     * {@code text/plain}
     */
    public static final MediaType TEXT_PLAIN = new MediaType("text/plain");
    /**
     * {@code application/x-www-form-urlencoded}
     */
    public static final MediaType FORM_URL_ENCODED = new MediaType("application/x-www-form-urlencoded");

    private static final String VALUE_ARGUMENT_NAME = "value";
    private final String value;

    private MediaType(String value) {
        this.value = value;
    }

    /**
     * Creates a {@link MediaType} from the provided string value.
     *
     * @param value The media type string; e.g. {@code "application/xml"}.
     * @return A new {@link MediaType} instance.
     * @throws IllegalArgumentException if {@code value} is null or blank.
     */
    public static MediaType of(String value) {
        return new MediaType(Strings.notBlank(VALUE_ARGUMENT_NAME, value));
    }

    /**
     * Returns the raw media type string value.
     *
     * @return The media type string; e.g. {@code "application/json"}.
     */
    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof MediaType mediaType)) return false;
        return Objects.equals(value, mediaType.value);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(value);
    }

    @Override
    public String toString() {
        return value;
    }
}

