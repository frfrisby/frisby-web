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
     * Configures an optional heartbeat comment interval.
     * <p>
     * If this method is not called, no <em>recurring</em> heartbeat is scheduled — but the
     * built {@link SseEmitter} still writes a single leading comment frame immediately on
     * construction regardless (see {@link SseEmitter} for why). Heartbeats are emitted as
     * SSE comment frames (for example, {@code : keep-alive}), not as named events, so no
     * {@code id}, {@code event}, {@code data}, or {@code retry} fields are included.
     * <p>
     * Heartbeats are best-effort keep-alive events. If the sink is already closed, a heartbeat
     * is skipped. If a heartbeat send races with a close and fails, the failure is logged
     * internally and not propagated to the resource method.
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
     * <p>
     * The returned {@link SseEmitter} writes one leading comment frame immediately, before
     * this method returns, whether {@link #heartbeat(Duration)} was ever called —
     * a per-request client read timeout typically bounds only time-to-first-byte, not an
     * already-established stream, so a resource method that doesn't write its own first
     * event right away (e.g. it is waiting on a slow upstream call) can otherwise cause the
     * client to see the connection fail before anything was ever wrong. When
     * {@link #heartbeat(Duration)} is configured, this frame doubles as that heartbeat's
     * own first tick, so no duplicate frame is written.
     *
     * @return A new {@link SseEmitter}.
     * @throws software.frisby.core.validation.NullValueException if required fields were not set.
     */
    SseEmitter build();
}
