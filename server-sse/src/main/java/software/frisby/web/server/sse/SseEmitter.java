package software.frisby.web.server.sse;

import java.util.concurrent.CompletableFuture;

/**
 * Server-side SSE emitter backed by Jersey's {@code SseEventSink}.
 * <p>
 * Instances are created through {@link #builder()} and are typically scoped to a single
 * HTTP request/response stream.
 * <p>
 * Typical long-lived resource methods loop while {@link #isOpen()} remains true and call
 * {@link #send(SseEvent)} for each outbound event. Because transport state can change
 * between those two operations, callers should still handle exceptional completion from
 * {@code send(...)} (or a {@link java.util.concurrent.CompletionException} from
 * {@code join()}) and terminate the stream cleanly when a send fails.
 * <p>
 * A newly built emitter always writes one leading SSE comment frame immediately —
 * unconditionally, whether or not {@link SseEmitterBuilder#heartbeat(java.time.Duration)}
 * was configured. A per-request client read timeout typically bounds only
 * time-to-first-byte, not an already-established stream; without this, a resource method
 * that legitimately takes a while to produce its own first event (e.g. it is waiting on a
 * slow upstream call) could see the client give up and disconnect before anything was ever
 * actually wrong. This is a single, one-time frame, not a substitute for a recurring
 * heartbeat — a stream that stays genuinely idle afterward still needs
 * {@link SseEmitterBuilder#heartbeat(java.time.Duration)} configured to stay alive through
 * intermediary proxies/load balancers.
 */
public interface SseEmitter extends AutoCloseable {
    /**
     * Creates a new SSE emitter builder.
     *
     * @return A new {@link SseEmitterBuilder}.
     */
    static SseEmitterBuilder builder() {
        return new DefaultSseEmitterBuilder();
    }

    /**
     * Sends one pre-built SSE event to the connected client.
     * <p>
     * The returned future completes when Jersey's sink send operation completes. If the
     * client disconnects, the sink is closed, or another transport/write failure occurs,
     * the future completes exceptionally with the underlying cause.
     * <p>
     * Callers should treat exceptional completion as terminal for the current stream and
     * exit the resource method (after any desired logging/cleanup).
     *
     * @param event The outbound event to send.
     * @return A future that completes when the send operation completes.
     * @throws software.frisby.core.validation.NullValueException if {@code event} is null.
     */
    CompletableFuture<Void> send(SseEvent event);

    /**
     * Returns whether this emitter is currently open.
     * <p>
     * This reflects current sink state, but it is only a point-in-time check. The
     * connection may still close immediately after this method returns, so callers must
     * still handle send failures even when this method returns {@code true}.
     *
     * @return {@code true} if open; otherwise {@code false}.
     */
    boolean isOpen();

    /**
     * Closes this emitter.
     * <p>
     * Idempotent. Also stops the optional heartbeat scheduler.
     */
    @Override
    void close();
}
