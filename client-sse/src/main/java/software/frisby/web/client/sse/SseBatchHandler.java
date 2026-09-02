package software.frisby.web.client.sse;

import software.frisby.web.serial.GenericType;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * A single registered {@code onEvent(String, SseBatchHandler)} handler's dispatch
 * configuration — target type token, callback, buffer capacity, dispatch concurrency,
 * batch size, and batch timeout.
 * <p>
 * Obtained via one of three static {@code of(...)} factories, mirroring
 * {@link SseHandler}'s own three factory shapes exactly, but delivering batches
 * ({@code List<SseMessage<T>>}) rather than single events. A factory is the
 * <em>only</em> way to obtain an instance — there is no no-argument builder — so a
 * callback is always present; {@link #capacity(int)}, {@link #concurrency(int)},
 * {@link #batchSize(int)}, and {@link #batchTimeout(Duration)} are optional fluent
 * overrides of this handler's own independent defaults (capacity {@code 1024},
 * concurrency {@code 1}, batch size {@code 100}, batch timeout {@code 250ms}), applied
 * only to this handler's own dispatch pipeline, never shared with any other registered
 * handler.
 * <p>
 * At most one of {@link #type()} / {@link #genericType()} is present. Both absent means
 * this is a raw handler.
 * <p>
 * See {@link SseHandler} for the full rationale behind this idiom (mirrors
 * {@code software.frisby.core.concurrency.fluent}'s {@code Buffer.of(X.class)
 * .capacity(...)} shape) and the same "not intended to be reused across registrations"
 * caution.
 *
 * @see SseHandler
 * @see SseListenerBuilder
 */
public interface SseBatchHandler {
    /**
     * Creates a batch handler that deserializes each event's {@code data} into
     * {@code type} via the connection's configured {@code JsonSerializer}.
     *
     * @param type    The type to deserialize each event's data into.
     * @param handler The callback invoked with each batch of deserialized messages.
     * @param <T>     The deserialized payload type.
     * @return A new handler, with default capacity ({@code 1024}), concurrency
     * ({@code 1}), batch size ({@code 100}), and batch timeout ({@code 250ms}).
     * @throws software.frisby.core.validation.NullValueException if {@code type} or {@code handler} is null.
     */
    static <T> SseBatchHandler of(Class<T> type, Consumer<List<SseMessage<T>>> handler) {
        return DefaultSseBatchHandler.ofType(type, handler);
    }

    /**
     * Creates a batch handler that deserializes each event's {@code data} into a
     * generic type such as {@code List<Item>}, via the connection's configured
     * {@code JsonSerializer}.
     *
     * @param type    The generic type to deserialize each event's data into.
     * @param handler The callback invoked with each batch of deserialized messages.
     * @param <T>     The deserialized payload type.
     * @return A new handler, with default capacity ({@code 1024}), concurrency
     * ({@code 1}), batch size ({@code 100}), and batch timeout ({@code 250ms}).
     * @throws software.frisby.core.validation.NullValueException if {@code type} or {@code handler} is null.
     */
    static <T> SseBatchHandler of(GenericType<T> type, Consumer<List<SseMessage<T>>> handler) {
        return DefaultSseBatchHandler.ofGenericType(type, handler);
    }

    /**
     * Creates a raw batch handler operating directly on each event's untouched
     * wire-format {@code data} string — no deserialization occurs.
     *
     * @param handler The callback invoked with each batch of raw messages.
     * @return A new handler, with default capacity ({@code 1024}), concurrency
     * ({@code 1}), batch size ({@code 100}), and batch timeout ({@code 250ms}).
     * @throws software.frisby.core.validation.NullValueException if {@code handler} is null.
     */
    static SseBatchHandler of(Consumer<List<SseMessage<String>>> handler) {
        return DefaultSseBatchHandler.ofRaw(handler);
    }

    /**
     * Sets the capacity of this handler's own dispatch buffer.
     * <p>
     * Optional; defaults to {@code 1024}. Independent of every other registered
     * handler's capacity.
     *
     * @param capacity The buffer capacity; must be positive.
     * @return This handler instance.
     * @throws software.frisby.core.validation.NumericValueOutsideRangeException if {@code capacity} is not positive.
     */
    SseBatchHandler capacity(int capacity);

    /**
     * Sets the number of concurrent worker arms dispatching to this handler's callback.
     * <p>
     * Optional; defaults to {@code 1} (serial, in-order delivery of batches).
     * {@code concurrency > 1} forfeits in-order delivery of batches for this handler and
     * requires a thread-safe callback — see
     * {@link SseListenerBuilder#onEvent(String, SseBatchHandler) onEvent} for the full
     * explanation of this trade-off.
     *
     * @param concurrency The number of concurrent worker arms; must be positive.
     * @return This handler instance.
     * @throws software.frisby.core.validation.NumericValueOutsideRangeException if {@code concurrency} is not
     *                                                                          positive.
     */
    SseBatchHandler concurrency(int concurrency);

    /**
     * Sets the maximum number of events collected into a single batch before it is
     * delivered to this handler's callback.
     * <p>
     * This is a ceiling, not a target size to wait for — a batch is delivered as soon
     * as either {@code batchSize} is reached or {@link #batchTimeout(Duration)} elapses,
     * whichever comes first. Under low event volume, batches will routinely be
     * delivered well below {@code batchSize}, including a "batch" of a single event.
     * <p>
     * Optional; defaults to {@code 100}.
     *
     * @param batchSize The maximum batch size; must be positive.
     * @return This handler instance.
     * @throws software.frisby.core.validation.NumericValueOutsideRangeException if {@code batchSize} is not
     *                                                                          positive.
     */
    SseBatchHandler batchSize(int batchSize);

    /**
     * Sets the maximum time a partially filled batch waits before being flushed to
     * this handler's callback.
     * <p>
     * Optional; defaults to {@code 250ms}.
     *
     * @param batchTimeout The flush timeout; must be positive.
     * @return This handler instance.
     * @throws software.frisby.core.validation.NullValueException            if {@code batchTimeout} is null.
     * @throws software.frisby.core.validation.DurationOutsideRangeException if {@code batchTimeout} is not
     *                                                                       positive.
     */
    SseBatchHandler batchTimeout(Duration batchTimeout);

    /**
     * Returns this handler's dispatch buffer capacity.
     *
     * @return The buffer capacity.
     */
    int capacity();

    /**
     * Returns the number of concurrent worker arms dispatching to this handler's callback.
     *
     * @return The concurrency.
     */
    int concurrency();

    /**
     * Returns this handler's maximum batch size.
     *
     * @return The maximum batch size.
     */
    int batchSize();

    /**
     * Returns this handler's batch flush timeout.
     *
     * @return The batch timeout.
     */
    Duration batchTimeout();

    /**
     * Returns the {@link Class} this handler deserializes into, if it was created via
     * {@link #of(Class, Consumer)}.
     *
     * @return The target type, or empty if this handler is generically-typed or raw.
     */
    Optional<Class<?>> type();

    /**
     * Returns the {@link GenericType} this handler deserializes into, if it was created
     * via {@link #of(GenericType, Consumer)}.
     *
     * @return The target generic type, or empty if this handler is {@code Class}-typed or raw.
     */
    Optional<GenericType<?>> genericType();

    /**
     * Returns the registered callback.
     *
     * @return The callback; always a {@code List<SseMessage<?>>} consumer.
     */
    Consumer<?> callback();
}

