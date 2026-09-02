package software.frisby.web.client.sse;

import software.frisby.web.serial.GenericType;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * A single registered {@code onEvent}/{@code onUnhandledEvent} handler's dispatch
 * configuration — target type token, callback, buffer capacity, and dispatch
 * concurrency.
 * <p>
 * Obtained via one of three static {@code of(...)} factories, each mirroring one of
 * {@code onEvent}'s original overload shapes: {@link #of(Class, Consumer)} for
 * {@code Class}-typed deserialization, {@link #of(GenericType, Consumer)} for
 * generically-typed deserialization (e.g. {@code List<Item>}), and {@link #of(Consumer)}
 * for a raw handler operating directly on the untouched wire-format {@code data} string.
 * A factory is the <em>only</em> way to obtain an instance — there is no no-argument
 * builder — so a callback is always present; {@link #capacity(int)} and
 * {@link #concurrency(int)} are optional fluent overrides of this handler's own
 * independent defaults (capacity {@code 1024}, concurrency {@code 1}), applied only to
 * this handler's own dispatch pipeline, never shared with any other registered handler.
 * <p>
 * At most one of {@link #type()} / {@link #genericType()} is present. Both absent means
 * this is a raw handler.
 * <p>
 * Deliberately mirrors {@code software.frisby.core.concurrency.fluent}'s
 * {@code Buffer.of(X.class).capacity(...)} idiom rather than this module's own
 * separate-builder-plus-terminal-{@code build()} idiom (see {@link SseListenerBuilder}) —
 * an instance is both the descriptor and its own fluent builder; there is no separate
 * terminal step.
 * <p>
 * <strong>Not intended to be reused</strong> across multiple
 * {@link SseListenerBuilder#onEvent(String, SseHandler) onEvent} /
 * {@link SseListenerBuilder#onUnhandledEvent(SseHandler) onUnhandledEvent} registrations
 * — create a fresh instance per registration, mirroring the same convention documented
 * for reusing a {@code Buffer}/{@code Transform} fluent stage instance across multiple
 * pipelines.
 *
 * @see SseBatchHandler
 * @see SseListenerBuilder
 */
public interface SseHandler {
    /**
     * Creates a handler that deserializes each event's {@code data} into {@code type}
     * via the connection's configured {@code JsonSerializer}.
     *
     * @param type    The type to deserialize the event data into.
     * @param handler The callback invoked with the deserialized message.
     * @param <T>     The deserialized payload type.
     * @return A new handler, with default capacity ({@code 1024}) and concurrency ({@code 1}).
     * @throws software.frisby.core.validation.NullValueException if {@code type} or {@code handler} is null.
     */
    static <T> SseHandler of(Class<T> type, Consumer<SseMessage<T>> handler) {
        return DefaultSseHandler.ofType(type, handler);
    }

    /**
     * Creates a handler that deserializes each event's {@code data} into a generic type
     * such as {@code List<Item>}, via the connection's configured {@code JsonSerializer}.
     *
     * @param type    The generic type to deserialize the event data into.
     * @param handler The callback invoked with the deserialized message.
     * @param <T>     The deserialized payload type.
     * @return A new handler, with default capacity ({@code 1024}) and concurrency ({@code 1}).
     * @throws software.frisby.core.validation.NullValueException if {@code type} or {@code handler} is null.
     */
    static <T> SseHandler of(GenericType<T> type, Consumer<SseMessage<T>> handler) {
        return DefaultSseHandler.ofGenericType(type, handler);
    }

    /**
     * Creates a raw handler operating directly on the untouched wire-format {@code data}
     * string — no deserialization occurs.
     *
     * @param handler The callback invoked with the raw message.
     * @return A new handler, with default capacity ({@code 1024}) and concurrency ({@code 1}).
     * @throws software.frisby.core.validation.NullValueException if {@code handler} is null.
     */
    static SseHandler of(Consumer<SseMessage<String>> handler) {
        return DefaultSseHandler.ofRaw(handler);
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
    SseHandler capacity(int capacity);

    /**
     * Sets the number of concurrent worker arms dispatching to this handler's callback.
     * <p>
     * Optional; defaults to {@code 1} (serial, in-order delivery). {@code concurrency > 1}
     * forfeits in-order delivery for this handler and requires a thread-safe callback —
     * see {@link SseListenerBuilder#onEvent(String, SseHandler) onEvent} for the full
     * explanation of this trade-off.
     *
     * @param concurrency The number of concurrent worker arms; must be positive.
     * @return This handler instance.
     * @throws software.frisby.core.validation.NumericValueOutsideRangeException if {@code concurrency} is not
     *                                                                          positive.
     */
    SseHandler concurrency(int concurrency);

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
     * @return The callback; always an {@code SseMessage} consumer.
     */
    Consumer<?> callback();
}



