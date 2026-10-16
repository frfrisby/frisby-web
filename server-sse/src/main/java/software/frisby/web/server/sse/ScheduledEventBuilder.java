package software.frisby.web.server.sse;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Fluent builder for {@link ScheduledEvent}.
 * <p>
 * Field requirements:
 * <ul>
 *     <li>{@link #interval(Duration)} is required before {@link #build()}.</li>
 *     <li>{@link #supplier(Supplier)} is required before {@link #build()}.</li>
 * </ul>
 */
public interface ScheduledEventBuilder {
    /**
     * Sets the required tick interval.
     *
     * @param interval The interval between ticks. Must be positive.
     * @return This builder.
     * @throws software.frisby.core.validation.NullValueException            if {@code interval} is null.
     * @throws software.frisby.core.validation.DurationOutsideRangeException if {@code interval} is not positive.
     */
    ScheduledEventBuilder interval(Duration interval);

    /**
     * Sets the required event supplier.
     * <p>
     * Invoked once per tick. Returning {@code null} from the supplier skips that tick — no
     * event is sent, and nothing is logged. A supplier that throws does not stop subsequent
     * ticks from firing.
     *
     * @param supplier The supplier invoked on every tick. Must not be null.
     * @return This builder.
     * @throws software.frisby.core.validation.NullValueException if {@code supplier} is null.
     */
    ScheduledEventBuilder supplier(Supplier<SseEvent> supplier);

    /**
     * Creates the scheduled event from this builder.
     *
     * @return A new {@link ScheduledEvent}.
     * @throws software.frisby.core.validation.NullValueException if {@link #interval(Duration)} or
     *                                                             {@link #supplier(Supplier)} was not called.
     */
    ScheduledEvent build();
}

