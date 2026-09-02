package software.frisby.web.client.sse;

/**
 * Determines how a connection's dispatch buffer behaves when it fills faster than
 * registered handlers can drain it.
 *
 * @see SseListener
 */
public enum BufferFullPolicy {
    /**
     * The reader thread stalls when the dispatch buffer is full.
     * <p>
     * Backpressure may propagate to the server via TCP flow control once the OS socket
     * buffers also fill. Safe from memory explosion; may increase server-side resource
     * usage if handlers are slow for an extended period.
     */
    BLOCK,

    /**
     * Overflow events are silently discarded.
     * <p>
     * The reader thread stays healthy and the server is unaffected. Suitable for
     * dashboards and metrics use cases where occasional event loss is acceptable.
     */
    DROP,

    /**
     * The stream is closed and reconnected when the buffer fills.
     * <p>
     * On reconnect, the last successfully processed event's {@code id} is sent as the
     * {@code Last-Event-ID} header so the server can replay missed events. Provides
     * clean "I'm not ready" backpressure semantics paired with at-least-once delivery
     * via server-side event replay.
     */
    DISCONNECT
}

