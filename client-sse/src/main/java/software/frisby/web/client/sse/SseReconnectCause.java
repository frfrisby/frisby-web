package software.frisby.web.client.sse;

/**
 * Why a connection is about to reconnect, reported on {@link SseReconnectEvent#cause()}.
 *
 * @see SseReconnectEvent
 * @see SseListenerObserver#onReconnect(SseReconnectEvent)
 */
public enum SseReconnectCause {
    /**
     * An actual transport failure — an {@link java.io.IOException} while reading the
     * stream, or an unexpected {@link RuntimeException} — or the initial connection
     * attempt itself failing. Always paired with the same failure also being reported to
     * {@link SseListenerObserver#onError(SseErrorEvent)}; {@code onReconnect} and
     * {@code onError} fire for the same underlying event, from two different angles
     * (lifecycle vs. failure detail).
     */
    FAILURE,

    /**
     * A policy-driven disconnect under {@link BufferFullPolicy#DISCONNECT} — the
     * connection closed and is reconnecting because a handler's dispatch buffer was
     * full, not because of any transport-level problem. Never paired with
     * {@code onError} — see {@link BufferFullPolicy#DISCONNECT} for why a policy-driven
     * disconnect is not treated as a failure.
     */
    BUFFER_FULL,

    /**
     * A watchdog-driven disconnect under {@link SseListenerBuilder#idleTimeout}
     * — no bytes at all (not even a heartbeat comment line) were received within the
     * configured timeout, so the connection was forcibly closed and is reconnecting even
     * though the underlying transport never itself reported an error. This exists for a
     * failure mode no {@link java.io.IOException} can ever surface: a silently-dead
     * connection (e.g. a dropped NAT/conntrack entry, a torn-down network namespace, or a
     * middlebox black-holing packets with no RST) leaves the reader thread's blocking
     * socket read waiting forever for bytes that are never coming, with nothing to catch.
     * Never paired with {@code onError} — like {@link #BUFFER_FULL}, this is a deliberate,
     * self-detected disconnect rather than a transport-reported failure.
     */
    STALE_CONNECTION
}

