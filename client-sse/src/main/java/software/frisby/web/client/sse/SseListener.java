package software.frisby.web.client.sse;

/**
 * A live, typed-callback connection to an SSE stream.
 * <p>
 * Assembled via {@code SseListener.builder(Client)} and {@link SseListenerBuilder#build()},
 * which validate the configured navigation, handlers, and options up front but perform
 * no I/O and start no threads. {@link #connectAsync()} opens the connection —
 * starting the background reader thread, dispatch pipeline, and reconnect loop — and
 * returns immediately.
 * <p>
 * Reconnects unconditionally and indefinitely on failure; there is no configurable
 * retry limit and no way to disable reconnection. The only way a connection ever
 * stops is an explicit {@link #close()} call, whether invoked directly by application
 * code or from within a registered {@code onError} handler once the caller decides a
 * run of failures is unrecoverable. See {@link SseListenerBuilder#onError} for details.
 * <p>
 * Once closed, an {@code SseListener} cannot be reopened — build a fresh instance via
 * {@code SseListener.builder(Client)} instead.
 * <p>
 * The static {@code builder(Client)} factory method is added in a later chunk, once
 * {@link SseListenerBuilder}'s implementation exists to back it.
 *
 * @see SseListenerBuilder
 * @see SseMessage
 */
public interface SseListener extends AutoCloseable {
    /**
     * Opens the connection and returns immediately.
     * <p>
     * The reader thread, dispatch pipeline, and reconnect loop are started as part of
     * this call. Registered handlers fire on the dispatch executor, not the calling
     * thread.
     */
    void connectAsync();

    /**
     * Returns whether this connection is currently open.
     * <p>
     * Remains {@code true} across automatic reconnect attempts, no matter how many
     * consecutive failures occur; it only becomes {@code false} after an explicit call
     * to {@link #close()}.
     *
     * @return {@code true} if this connection has not been closed; {@code false} otherwise.
     */
    boolean isOpen();

    /**
     * Closes this connection, stopping the reader thread and disabling any further
     * reconnect attempts.
     * <p>
     * Blocks until in-flight dispatch work has completed. Idempotent — calling
     * {@code close()} more than once has no additional effect.
     */
    @Override
    void close();
}




