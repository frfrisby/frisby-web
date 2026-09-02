package software.frisby.web.client.sse;

import software.frisby.core.validation.Values;

import java.util.Optional;

/**
 * The value delivered to a registered {@code onError} handler, pairing the failure with
 * whatever raw event context was available at the point it occurred.
 * <p>
 * {@link #message()} is present whenever the failure occurred while processing a specific
 * event — a deserialization failure, or an exception thrown by a registered handler's own
 * callback. It always carries the untouched wire-format {@code data} string, never a
 * partially- or fully-deserialized typed payload — the raw string is the one representation
 * guaranteed to be available regardless of whether deserialization itself succeeded. It is
 * {@link Optional#empty()} for connection-level failures with no specific event in play —
 * a failed connect/reconnect attempt, or an {@code IOException} while reading the stream.
 * <p>
 * A batch handler's own callback exception ({@code onEvent(String, SseBatchHandler)})
 * is a whole-batch failure, not attributable to any single item, so {@link #message()}
 * is also empty in that case.
 *
 * @param message The raw event being processed when {@code cause} occurred, if any.
 * @param cause   The failure.
 * @see SseListenerBuilder#onError
 */
public record SseErrorEvent(Optional<SseMessage<String>> message, Throwable cause) {
    /**
     * Compact constructor — validates that all fields satisfy their documented constraints.
     *
     * @throws software.frisby.core.validation.NullValueException if {@code message} or
     *                                                             {@code cause} is {@code null}.
     */
    public SseErrorEvent {
        Values.notNull("message", message);
        Values.notNull("cause", cause);
    }

    /**
     * Creates an {@link SseErrorEvent} for a connection-level failure with no specific
     * event in play.
     *
     * @param cause The failure.
     * @return A new {@link SseErrorEvent} with an empty {@link #message()}.
     */
    static SseErrorEvent of(Throwable cause) {
        return new SseErrorEvent(Optional.empty(), cause);
    }

    /**
     * Creates an {@link SseErrorEvent} for a failure that occurred while processing a
     * specific event.
     *
     * @param message The raw event being processed when {@code cause} occurred.
     * @param cause   The failure.
     * @return A new {@link SseErrorEvent} with a present {@link #message()}.
     */
    static SseErrorEvent of(SseMessage<String> message, Throwable cause) {
        return new SseErrorEvent(Optional.of(message), cause);
    }
}

