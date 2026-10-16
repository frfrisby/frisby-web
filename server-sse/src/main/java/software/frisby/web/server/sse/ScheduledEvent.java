package software.frisby.web.server.sse;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * A recurring, application-level {@link SseEvent} that an {@link SseEmitter} sends on its
 * own schedule, independent of whatever a resource method's own main loop is doing.
 * <p>
 * Unlike {@link SseEmitterBuilder#heartbeat(Duration)} — which writes an invisible SSE
 * <em>comment</em> frame purely to survive proxy/load-balancer idle timeouts — a scheduled
 * event is a real, named {@link SseEvent} dispatched through the exact same {@code onEvent}
 * pipeline on the client as any event a resource method sends manually. The two mechanisms
 * The two mechanisms are independently configurable and commonly serve different cadences
 * (for example, a 15 second keep-alive alongside a 15 minute "report your stats" prompt).
 * <p>
 * Configure one or more via {@code SseEmitterBuilder.scheduledEvent(ScheduledEvent)}. Keep
 * {@link #supplier()} fast — every scheduled event and the comment heartbeat share the same
 * single-thread scheduler owned by the {@link SseEmitter}, so a slow supplier delays every
 * other tick due to fire around the same moment.
 *
 * @see SseEmitterBuilder
 */
public interface ScheduledEvent {
    /**
     * Creates a new builder for a {@link ScheduledEvent}.
     *
     * @return A new {@link ScheduledEventBuilder}.
     */
    static ScheduledEventBuilder builder() {
        return new DefaultScheduledEventBuilder();
    }

    /**
     * Returns the interval between ticks.
     * <p>
     * The first tick fires after one full interval has elapsed, not immediately — unlike
     * {@link SseEmitterBuilder#heartbeat(Duration)}'s first tick.
     *
     * @return The positive tick interval.
     */
    Duration interval();

    /**
     * Returns the supplier invoked on every tick to produce the event to send.
     * <p>
     * A {@code null} return value from the supplier is a first-class, documented signal
     * meaning "no event to send this tick" — it is not an error and is not logged as a
     * failure. A supplier that throws does not prevent subsequent ticks from firing.
     *
     * @return The non-null supplier.
     */
    Supplier<SseEvent> supplier();
}


