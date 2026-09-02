package software.frisby.web.server.sse;

import jakarta.ws.rs.sse.Sse;
import jakarta.ws.rs.sse.SseEventSink;

import java.time.Duration;

/**
 * Fluent builder for {@link SseEmitter}.
 * <p>
 * Field requirements:
 * <ul>
 *     <li>{@link #sink(SseEventSink)} is required.</li>
 *     <li>{@link #sse(Sse)} is required.</li>
 *     <li>{@link #heartbeat(Duration)} is optional.</li>
 * </ul>
 */
public interface SseEmitterBuilder {
    /**
     * Sets the required Jersey SSE sink.
     *
     * @param sink The sink to write events to.
     * @return This builder.
     * @throws software.frisby.core.validation.NullValueException if {@code sink} is null.
     */
    SseEmitterBuilder sink(SseEventSink sink);

    /**
     * Sets the required Jersey SSE factory.
     *
     * @param sse The Jersey SSE context.
     * @return This builder.
     * @throws software.frisby.core.validation.NullValueException if {@code sse} is null.
     */
    SseEmitterBuilder sse(Sse sse);

    /**
     * Enables heartbeat comments at a fixed interval.
     *
     * @param heartbeatInterval The heartbeat interval. Must be positive.
     * @return This builder.
     * @throws software.frisby.core.validation.NullValueException            if {@code heartbeatInterval} is null.
     * @throws software.frisby.core.validation.DurationOutsideRangeException if {@code heartbeatInterval} is not
     *                                                                       positive.
     */
    SseEmitterBuilder heartbeat(Duration heartbeatInterval);

    /**
     * Creates a new emitter.
     *
     * @return A new {@link SseEmitter}.
     * @throws software.frisby.core.validation.NullValueException if required fields were not set.
     */
    SseEmitter build();
}
