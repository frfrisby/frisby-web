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
     * <p>
     * <strong>Pitfall:</strong> a handler whose callback is consistently slower than the
     * incoming event rate never gets a chance to actually drain under this policy — its
     * dispatch buffer fills, the connection disconnects and reconnects, the buffer fills
     * again almost immediately, and so on indefinitely. This looks like "backpressure is
     * working" in isolation, but in aggregate it is an unbounded reconnect loop that
     * hammers the server. Two settings determine how bad that loop is:
     * <ul>
     *     <li>{@link SseHandler#capacity(int)} / {@link SseBatchHandler#capacity(int)} —
     *     too small a capacity for the handler's real throughput turns brief, ordinary
     *     bursts into constant disconnects.</li>
     *     <li>{@link SseListenerBuilder#reconnectDelay(software.frisby.web.client.RetryDelay)}
     *     — a non-escalating strategy (e.g. {@code RetryDelay.fixed(...)}) never lets a
     *     persistently overwhelmed handler catch up; prefer an escalating strategy (the
     *     builder's default, {@code exponential(3s)}, already escalates) so a genuine
     *     storm backs off instead of retrying at a constant rate forever.</li>
     * </ul>
     */
    DISCONNECT
}

