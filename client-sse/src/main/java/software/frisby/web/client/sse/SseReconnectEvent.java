package software.frisby.web.client.sse;

import software.frisby.core.validation.Numbers;
import software.frisby.core.validation.Values;

import java.time.Duration;
import java.util.Optional;

/**
 * The value delivered to {@link SseListenerObserver#onReconnect(SseReconnectEvent)} every
 * time this connection is about to reconnect following a setback — either a genuine
 * transport failure or a policy-driven {@link BufferFullPolicy#DISCONNECT}.
 * <p>
 * Fired once per reconnect attempt that follows a setback of either kind, immediately
 * before the computed {@link #delay()} is actually waited out — never fired for a clean
 * end-of-stream reconnect, since that is not a setback and does not advance the backoff
 * strategy's attempt count.
 *
 * @param cause     Why this reconnect is happening.
 * @param eventType The handler event type whose dispatch buffer triggered this reconnect,
 *                  present only when {@code cause} is {@link SseReconnectCause#BUFFER_FULL}
 *                  <em>and</em> the buffer belonged to a named handler; empty for
 *                  {@link SseReconnectCause#FAILURE}, and also empty when the full buffer
 *                  belonged to the catch-all unhandled-event pipeline.
 * @param attempt   The 1-based consecutive-setback count this reconnect represents —
 *                  the same counter driving the configured {@link SseListenerBuilder#reconnectDelay}
 *                  strategy's escalation.
 * @param delay     The computed delay before this reconnect attempt is made.
 * @see SseListenerObserver
 * @see SseListenerBuilder#reconnectDelay(software.frisby.web.client.RetryDelay)
 */
public record SseReconnectEvent(SseReconnectCause cause, Optional<String> eventType, int attempt, Duration delay) {
    /**
     * Compact constructor — validates that all fields satisfy their documented constraints.
     *
     * @param cause     why this reconnect is happening; must not be {@code null}
     * @param eventType the triggering handler event type, if any; must not be {@code null}
     * @param attempt   the 1-based consecutive-setback count; must be positive
     * @param delay     the computed delay before this reconnect attempt; must not be {@code null}
     * @throws software.frisby.core.validation.NullValueException                if {@code cause},
     *                                                                           {@code eventType}, or
     *                                                                           {@code delay} is {@code null}.
     * @throws software.frisby.core.validation.NumericValueOutsideRangeException if {@code attempt} is not positive.
     */
    public SseReconnectEvent {
        Values.notNull("cause", cause);
        Values.notNull("eventType", eventType);
        Numbers.positive("attempt", attempt);
        Values.notNull("delay", delay);
    }

    /**
     * Creates an {@link SseReconnectEvent} for a genuine transport failure.
     *
     * @param attempt The 1-based consecutive-setback count.
     * @param delay   The computed delay before this reconnect attempt.
     * @return A new {@link SseReconnectEvent} with {@link SseReconnectCause#FAILURE} and an
     * empty {@link #eventType()}.
     */
    static SseReconnectEvent failure(int attempt, Duration delay) {
        return new SseReconnectEvent(SseReconnectCause.FAILURE, Optional.empty(), attempt, delay);
    }

    /**
     * Creates an {@link SseReconnectEvent} for a policy-driven {@link BufferFullPolicy#DISCONNECT}.
     *
     * @param eventType The handler event type whose buffer triggered the disconnect, or
     *                  {@code null} if it was the unhandled-event pipeline.
     * @param attempt   The 1-based consecutive-setback count.
     * @param delay     The computed delay before this reconnect attempt.
     * @return A new {@link SseReconnectEvent} with {@link SseReconnectCause#BUFFER_FULL}.
     */
    static SseReconnectEvent bufferFull(String eventType, int attempt, Duration delay) {
        return new SseReconnectEvent(SseReconnectCause.BUFFER_FULL, Optional.ofNullable(eventType), attempt, delay);
    }
}

