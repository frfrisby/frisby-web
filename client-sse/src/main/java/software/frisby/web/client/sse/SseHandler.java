package software.frisby.web.client.sse;

import software.frisby.web.serial.GenericType;

import java.util.function.Consumer;

/**
 * Internal pairing of a registered {@code onEvent} handler's target type token, callback,
 * and requested dispatch concurrency.
 * <p>
 * At most one of {@link #type()} / {@link #genericType()} is present. Both absent means
 * this is a raw handler operating directly on the wire-format {@code data} string
 * ({@code Consumer<SseMessage<String>>}). {@link #concurrency()} drives whether
 * {@code DefaultSseListener} builds this handler's {@code Target<RawSseEvent>} as a plain
 * single-arm {@code Pipeline} ({@code concurrency == 1}) or a {@code Router}-wrapped
 * fan-out ({@code concurrency > 1}).
 *
 * @param type        The target {@link Class}, if this is a {@code Class}-typed handler;
 *                    otherwise {@code null}.
 * @param genericType The target {@link GenericType}, if this is a generically-typed
 *                    handler; otherwise {@code null}.
 * @param callback    The registered callback; always an {@code SseMessage} consumer.
 * @param concurrency The number of concurrent worker arms dispatching to
 *                    {@code callback}; always positive.
 */
record SseHandler(Class<?> type, GenericType<?> genericType, Consumer<?> callback, int concurrency) {
}


