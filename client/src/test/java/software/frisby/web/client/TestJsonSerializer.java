package software.frisby.web.client;

import software.frisby.web.serial.GenericType;
import software.frisby.web.serial.JsonSerializer;

/**
 * Minimal {@link JsonSerializer} stub for use in unit tests that need a non-null
 * serializer instance but do not exercise serialization logic.
 */
final class TestJsonSerializer implements JsonSerializer {
    @Override
    public byte[] serialize(Object value) {
        throw new UnsupportedOperationException("TestJsonSerializer does not support serialize()");
    }

    @Override
    public <T> T deserialize(byte[] json, Class<T> type) {
        throw new UnsupportedOperationException("TestJsonSerializer does not support deserialize()");
    }

    @Override
    public <T> T deserialize(byte[] json, GenericType<T> type) {
        throw new UnsupportedOperationException("TestJsonSerializer does not support deserialize()");
    }
}
