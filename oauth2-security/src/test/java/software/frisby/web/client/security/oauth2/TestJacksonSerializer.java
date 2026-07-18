package software.frisby.web.client.security.oauth2;

import com.fasterxml.jackson.databind.ObjectMapper;
import software.frisby.web.serial.GenericType;
import software.frisby.web.serial.JsonSerializer;

/**
 * Minimal {@link JsonSerializer} backed by Jackson for use in oauth2-security tests.
 */
final class TestJacksonSerializer implements JsonSerializer {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public byte[] serialize(Object value) {
        try {
            return MAPPER.writeValueAsBytes(value);
        } catch (Exception e) {
            throw new IllegalArgumentException("Serialization failed.", e);
        }
    }

    @Override
    public <T> T deserialize(byte[] content, Class<T> type) {
        try {
            return MAPPER.readValue(content, type);
        } catch (Exception e) {
            throw new IllegalArgumentException("Deserialization failed.", e);
        }
    }

    @Override
    public <T> T deserialize(byte[] content, GenericType<T> genericType) {
        try {
            return MAPPER.readValue(
                    content,
                    MAPPER.getTypeFactory().constructType(genericType.type())
            );
        } catch (Exception e) {
            throw new IllegalArgumentException("Deserialization failed.", e);
        }
    }
}

