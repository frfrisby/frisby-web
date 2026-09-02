package software.frisby.web.server.sse;

import org.junit.jupiter.api.Test;
import software.frisby.core.validation.NullValueException;
import software.frisby.web.serial.GenericType;
import software.frisby.web.serial.JsonSerializer;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SseEventsTest {
    private static final String NULL_SERIALIZER_MESSAGE = "The 'serializer' value is invalid. The value must not be null.";
    private static final String NULL_VALUE_MESSAGE = "The 'value' value is invalid. The value must not be null.";

    @Test
    void nullSerializer_throwsNullValueException() {
        NullValueException ex = assertThrows(
                NullValueException.class,
                () -> SseEvents.of(null)
        );

        assertEquals(NULL_SERIALIZER_MESSAGE, ex.getMessage());
    }

    @Test
    void nullTypedValue_throwsNullValueException() {
        SseEvents events = SseEvents.of(new CapturingSerializer());

        NullValueException ex = assertThrows(
                NullValueException.class,
                () -> events.data((Object) null)
        );

        assertEquals(NULL_VALUE_MESSAGE, ex.getMessage());
    }

    @Test
    void fluentCalls_returnSameHelper() {
        SseEvents events = SseEvents.of(new CapturingSerializer());

        assertSame(events, events.id("id"));
        assertSame(events, events.event("event"));
        assertSame(events, events.data("data"));
        assertSame(events, events.retry(Duration.ZERO));
    }

    @Test
    void rawData_buildsEventWithoutSerializerUsage() {
        CapturingSerializer serializer = new CapturingSerializer();

        SseEvent event = SseEvents.of(serializer)
                .id("abc")
                .event("demo")
                .data("plain")
                .retry(Duration.ofMillis(10))
                .toEvent();

        assertEquals("plain", event.data());
        assertTrue(event.id().isPresent());
        assertTrue(event.event().isPresent());
        assertTrue(event.retry().isPresent());
        assertEquals("abc", event.id().get());
        assertEquals("demo", event.event().get());
        assertEquals(Duration.ofMillis(10), event.retry().get());
        assertEquals(0, serializer.serializeCalls);
    }

    @Test
    void typedData_serializesToUtf8AndBuildsEvent() {
        CapturingSerializer serializer = new CapturingSerializer();

        SseEvent event = SseEvents.of(serializer)
                .data(42)
                .toEvent();

        assertEquals(1, serializer.serializeCalls);
        assertEquals(42, serializer.lastSerializedValue);
        assertEquals("json-42", event.data());
    }

    @Test
    void serializerFailure_isPropagated() {
        IllegalArgumentException failure = new IllegalArgumentException("boom");

        CapturingSerializer serializer = new CapturingSerializer();
        serializer.serializeException = failure;

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> SseEvents.of(serializer).data(5)
        );

        assertSame(failure, ex);
    }

    @Test
    void toEventWithoutData_throwsNullValueException() {
        NullValueException ex = assertThrows(
                NullValueException.class,
                () -> SseEvents.of(new CapturingSerializer()).toEvent()
        );

        assertEquals("The 'data' value is invalid. The value must not be null.", ex.getMessage());
    }

    private static final class CapturingSerializer implements JsonSerializer {
        private int serializeCalls;
        private Object lastSerializedValue;
        private RuntimeException serializeException;

        @Override
        public byte[] serialize(Object value) {
            if (null != serializeException) {
                throw serializeException;
            }

            this.serializeCalls++;
            this.lastSerializedValue = value;

            return ("json-" + value).getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public <T> T deserialize(byte[] content, Class<T> type) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> T deserialize(byte[] content, GenericType<T> genericType) {
            throw new UnsupportedOperationException();
        }
    }
}


