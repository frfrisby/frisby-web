package software.frisby.web.client.sse;

import software.frisby.core.validation.Durations;
import software.frisby.core.validation.Values;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * The value delivered to {@link SseListenerObserver#onEventProcessed(SseEventProcessed)}
 * the moment a handler's callback returns, or throws, for a given event.
 * <p>
 * Carries the same identity fields as {@link SseEventReceived}, plus two latency
 * measurements and {@link #succeeded()}. Fires unconditionally once the callback has been
 * attempted — {@link #succeeded()} is {@code false} whenever the callback threw, rather
 * than suppressing the event entirely, so a caller building throughput/latency telemetry
 * always sees every dispatched item's timing, not just the ones that happened to succeed.
 * A failed callback is still fully reported, with its exception and raw context, via
 * {@link SseListenerObserver#onError} exactly as before — {@link #succeeded()} is a plain
 * outcome tag for telemetry purposes, not a substitute for that channel; this record never
 * itself carries the causing {@link Throwable}.
 * <p>
 * Deliberately computed directly around the callback invocation itself (inside
 * {@code DefaultSseListener}'s own dispatch wrapper) rather than derived from any
 * {@code frisby-core} pipeline block's own notification hooks — see the class-level
 * javadoc on {@code DefaultSseListener} for why that distinction matters.
 * <p>
 * For a batch handler ({@code onEvent(String, SseBatchHandler)}), one
 * {@code SseEventProcessed} fires per item in the delivered batch — mirroring one
 * {@link SseEventReceived} per raw item — each with its own accurate {@link #totalLatency()}
 * measured against its own {@link #receivedAt()}, but all sharing the same
 * {@link #processingDuration()} and {@link #succeeded()}, since the callback ran once for
 * the whole batch.
 *
 * @param event              The event's raw wire {@code event} field, if present.
 * @param registeredAs       The handler event type this event was dispatched under; empty
 *                           for the catch-all unhandled-event pipeline. See
 *                           {@link SseEventReceived#registeredAs()}.
 * @param id                 The event's {@code id} field, if present.
 * @param receivedAt         The instant this event finished assembling off the wire.
 * @param totalLatency       The elapsed time from {@link #receivedAt()} to the callback
 *                           returning or throwing — includes queueing, deserialization, and
 *                           the callback's own execution time.
 * @param processingDuration The callback's own wall-clock execution time only, excluding
 *                           any time spent queued or being deserialized beforehand.
 * @param succeeded          {@code true} if the callback returned normally; {@code false} if
 *                           it threw. A failed callback's exception and raw context are
 *                           reported separately via {@link SseListenerObserver#onError}.
 * @see SseEventReceived
 * @see SseListenerObserver
 */
public record SseEventProcessed(Optional<String> event,
                                Optional<String> registeredAs,
                                Optional<String> id,
                                Instant receivedAt,
                                Duration totalLatency,
                                Duration processingDuration,
                                boolean succeeded) {
    /**
     * Compact constructor — validates that all fields satisfy their documented constraints.
     *
     * @param event              the raw wire event field; must not be {@code null}
     * @param registeredAs       the handler event type; must not be {@code null}
     * @param id                 the event id; must not be {@code null}
     * @param receivedAt         the instant this event finished assembling; must not be {@code null}
     * @param totalLatency       the end-to-end latency; must not be {@code null} or negative
     * @param processingDuration the callback's own execution time; must not be {@code null} or negative
     * @param succeeded          whether the callback returned normally rather than throwing
     * @throws software.frisby.core.validation.NullValueException            if any of {@code event},
     *                                                                       {@code registeredAs}, {@code id},
     *                                                                       {@code receivedAt},
     *                                                                       {@code totalLatency}, or
     *                                                                       {@code processingDuration} is
     *                                                                       {@code null}.
     * @throws software.frisby.core.validation.DurationOutsideRangeException if {@code totalLatency} or
     *                                                                       {@code processingDuration} is negative.
     */
    public SseEventProcessed {
        Values.notNull("event", event);
        Values.notNull("registeredAs", registeredAs);
        Values.notNull("id", id);
        Values.notNull("receivedAt", receivedAt);
        Durations.notNegative("totalLatency", totalLatency);
        Durations.notNegative("processingDuration", processingDuration);
    }
}
