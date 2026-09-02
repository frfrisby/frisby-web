package software.frisby.web.server.sse;

import java.time.Duration;

/**
 * Fluent builder for {@link SseEvent}.
 * <p>
 * Field requirements:
 * <ul>
 *     <li>{@link #id(String)} is optional.</li>
 *     <li>{@link #event(String)} is optional.</li>
 *     <li>{@link #data(String)} is required before {@link #build()}.</li>
 *     <li>{@link #retry(Duration)} is optional.</li>
 * </ul>
 */
public interface SseEventBuilder {
    /**
     * Sets the optional event id field.
     * <p>
     * If not set, the emitted event has no {@code id} field.
     *
     * @param id The event id. Must not be blank, contain {@code '\0'}, or exceed 512 characters.
     * @return This builder.
     * @throws software.frisby.core.validation.BlankValueException               if {@code id} is blank.
     * @throws software.frisby.core.validation.PatternMismatchException          if {@code id} contains {@code '\0'}.
     * @throws software.frisby.core.validation.StringLengthOutsideRangeException if {@code id} exceeds 512
     *                                                                           characters.
     */
    SseEventBuilder id(String id);

    /**
     * Sets the optional event type field.
     * <p>
     * If not set, the emitted event has no explicit {@code event} field.
     *
     * @param event The event type. Must not be blank or contain {@code '\n'} or {@code '\r'}.
     * @return This builder.
     * @throws software.frisby.core.validation.BlankValueException      if {@code event} is blank.
     * @throws software.frisby.core.validation.PatternMismatchException if {@code event} contains {@code '\n'} or
     *                                                                  {@code '\r'}.
     */
    SseEventBuilder event(String event);

    /**
     * Sets the required event data field.
     * <p>
     * This method must be called before {@link #build()}.
     *
     * @param data The data payload. Must not be null.
     * @return This builder.
     * @throws software.frisby.core.validation.NullValueException if {@code data} is null.
     */
    SseEventBuilder data(String data);

    /**
     * Sets the optional event retry field.
     * <p>
     * If not set, the emitted event has no {@code retry} field.
     *
     * @param retry The reconnect delay hint. Must be non-negative.
     * @return This builder.
     * @throws software.frisby.core.validation.NullValueException            if {@code retry} is null.
     * @throws software.frisby.core.validation.DurationOutsideRangeException if {@code retry} is negative.
     */
    SseEventBuilder retry(Duration retry);

    /**
     * Creates the event from this builder.
     *
     * @return A new {@link SseEvent}.
     * @throws software.frisby.core.validation.NullValueException if {@link #data(String)} was not called.
     */
    SseEvent build();
}
