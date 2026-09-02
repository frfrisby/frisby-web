package software.frisby.web.server.sse;

import java.util.concurrent.CompletableFuture;

/**
 * Server-side SSE emitter backed by Jersey's {@code SseEventSink}.
 * <p>
 * Instances are created through {@link #builder()} and are typically scoped to a single
 * HTTP request/response stream.
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
     *
     * @param event The outbound event to send.
     * @return A future that completes when the send operation completes.
     * @throws software.frisby.core.validation.NullValueException if {@code event} is null.
     */
    CompletableFuture<Void> send(SseEvent event);

    /**
     * Returns whether this emitter is currently open.
     *
     * @return {@code true} if open; otherwise {@code false}.
     */
    boolean isOpen();

    /**
     * Closes this emitter.
     */
    @Override
    void close();
}
