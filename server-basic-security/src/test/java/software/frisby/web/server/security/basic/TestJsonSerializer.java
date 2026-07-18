package software.frisby.web.server.security.basic;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.frisby.web.serial.GenericType;
import software.frisby.web.serial.JsonSerializer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Type;

/**
 * Minimal Jackson-backed {@link JsonSerializer} for use in server-basic-security tests.
 * Package-private — not part of the public API.
 */
final class TestJsonSerializer implements JsonSerializer {
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public byte[] serialize(Object value) {
        try {
            return mapper.writeValueAsBytes(value);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    @Override
    public <T> T deserialize(byte[] json, Class<T> type) {
        try {
            return mapper.readValue(json, type);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    @Override
    public <T> T deserialize(byte[] json, GenericType<T> type) {
        try {
            Type rawType = type.type();

            return mapper.readValue(json, new TypeReference<T>() {
                @Override
                public Type getType() {
                    return rawType;
                }
            });
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}

