package software.frisby.web.server.sse;

import software.frisby.core.validation.Values;
import software.frisby.web.serial.JsonSerializer;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Fluent helper for creating {@link SseEvent} values from raw strings or typed values.
 * <p>
 * Obtained via {@link #of(JsonSerializer)}. The serializer is required up front so every
 * typed {@link #data(Object)} call can serialize immediately.
 * <p>
 * <strong>Not intended for reuse</strong> across multiple events. Create a fresh instance
 * per event.
 */
public final class SseEvents {
    private static final String SERIALIZER_ARGUMENT_NAME = "serializer";
    private static final String VALUE_ARGUMENT_NAME = "value";

    private final JsonSerializer serializer;
    private final SseEventBuilder delegate;

    private SseEvents(JsonSerializer serializer) {
        this.serializer = serializer;
        this.delegate = SseEvent.builder();
    }

    /**
     * Creates a new fluent helper with the serializer to use for typed data values.
     *
     * @param serializer The serializer used by {@link #data(Object)}.
     * @return A new {@link SseEvents} helper.
     * @throws software.frisby.core.validation.NullValueException if {@code serializer} is null.
     */
    public static SseEvents of(JsonSerializer serializer) {
        return new SseEvents(
                Values.notNull(
                        SERIALIZER_ARGUMENT_NAME,
                        serializer
                )
        );
    }

    /**
     * Sets the optional event id field.
     *
     * @param id The event id value.
     * @return This helper.
     */
    public SseEvents id(String id) {
        delegate.id(id);
        return this;
    }

    /**
     * Sets the optional event type field.
     *
     * @param event The event type value.
     * @return This helper.
     */
    public SseEvents event(String event) {
        delegate.event(event);
        return this;
    }

    /**
     * Sets raw string event data (no serialization).
     *
     * @param data The raw event data string.
     * @return This helper.
     */
    public SseEvents data(String data) {
        delegate.data(data);
        return this;
    }

    /**
     * Serializes a typed value to UTF-8 JSON and sets it as event data.
     *
     * @param value The value to serialize.
     * @param <T> The value type.
     * @return This helper.
     * @throws software.frisby.core.validation.NullValueException if {@code value} is null.
     * @throws IllegalArgumentException if serialization fails.
     */
    public <T> SseEvents data(T value) {
        Values.notNull(VALUE_ARGUMENT_NAME, value);

        delegate.data(
                new String(
                        serializer.serialize(value),
                        StandardCharsets.UTF_8
                )
        );

        return this;
    }

    /**
     * Sets the optional retry field.
     *
     * @param retry The retry hint value.
     * @return This helper.
     */
    public SseEvents retry(Duration retry) {
        delegate.retry(retry);
        return this;
    }

    /**
     * Builds the final wire-ready event.
     *
     * @return A new {@link SseEvent}.
     */
    public SseEvent toEvent() {
        return delegate.build();
    }
}
