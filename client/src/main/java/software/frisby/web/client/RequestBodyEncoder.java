package software.frisby.web.client;

import software.frisby.web.serial.JsonSerializer;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Shared body-encoding utilities used by {@link PostRequest}, {@link PutRequest},
 * {@link PatchRequest}, and {@link MultipartBodyBuilder}.
 */
final class RequestBodyEncoder {
    private RequestBodyEncoder() {
    }

    /**
     * URL-encodes a map of field name/value pairs into an
     * {@code application/x-www-form-urlencoded} string.
     *
     * @param fields The form fields to encode.
     * @return A percent-encoded {@code name=value} string with {@code &} as the delimiter.
     */
    static String encodeFormFields(Map<String, String> fields) {
        StringJoiner joiner = new StringJoiner("&");

        for (Map.Entry<String, String> entry : fields.entrySet()) {
            String encodedName = URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8);
            String encodedValue = URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8);

            joiner.add(encodedName + "=" + encodedValue);
        }

        return joiner.toString();
    }

    /**
     * Serializes a request body object to a {@code byte[]}.
     * <p>
     * {@link String} bodies are encoded as UTF-8 directly; all other types are
     * serialized via the supplied {@link JsonSerializer}.
     *
     * @param body       The body to serialize.
     * @param serializer The serializer to use for non-{@link String} bodies.
     * @return The serialized body bytes.
     */
    static byte[] serializeBody(Object body, JsonSerializer serializer) {
        if (body instanceof String s) {
            return s.getBytes(StandardCharsets.UTF_8);
        }

        return serializer.serialize(body);
    }
}

