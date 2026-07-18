package software.frisby.web.serial.jackson;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.frisby.core.validation.Values;
import software.frisby.web.serial.GenericType;

import java.io.IOException;
import java.io.UncheckedIOException;

final class DefaultJacksonSerializer implements JacksonSerializer {
    private final ObjectMapper mapper;

    DefaultJacksonSerializer(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public byte[] serialize(Object value) {
        Values.notNull("value", value);

        try {
            return mapper.writeValueAsBytes(value);
        } catch (IOException ex) {
            throw new UncheckedIOException("Serialization failed.", ex);
        }
    }

    @Override
    public <T> T deserialize(byte[] content, Class<T> type) {
        Values.notNull("content", content);
        Values.notNull("type", type);

        try {
            return mapper.readValue(content, type);
        } catch (IOException ex) {
            throw new UncheckedIOException("Deserialization failed.", ex);
        }
    }

    @Override
    public <T> T deserialize(byte[] content, GenericType<T> genericType) {
        Values.notNull("content", content);
        Values.notNull("genericType", genericType);

        try {
            JavaType javaType = mapper.getTypeFactory().constructType(genericType.type());
            return mapper.readValue(content, javaType);
        } catch (IOException ex) {
            throw new UncheckedIOException("Deserialization failed.", ex);
        }
    }
}
