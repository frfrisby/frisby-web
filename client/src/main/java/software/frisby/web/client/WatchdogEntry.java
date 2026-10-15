package software.frisby.web.client;

import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Per-stream bookkeeping held by {@link StreamWatchdog}'s registry: the stream's own
 * {@code idleTimeout}, the {@link System#nanoTime()} timestamp of its most recently
 * observed activity, and the underlying {@link Closeable} to close on expiry.
 * <p>
 * {@code idleTimeout} is carried per-entry, rather than assumed uniform across the
 * registry, because per-call overrides mean two streams tracked by the same shared
 * watchdog can legitimately have different values (see {@code SseSpec#idleTimeout}/
 * {@code DownloadSpec#idleTimeout}).
 * <p>
 * {@link #closeSafely()} guards against invoking {@link Closeable#close()} more than
 * once on the same entry via an internal compare-and-set flag. Although {@code
 * StreamWatchdog.expire()} itself never calls this method twice on the same entry, the
 * guard is part of this class's own public contract — not merely a defensive leftover —
 * since any caller may legitimately call {@link #closeSafely()} more than once.
 */
final class WatchdogEntry {
    private static final System.Logger LOGGER = System.getLogger(WatchdogEntry.class.getName());

    private final AtomicLong lastActivityNanos;
    private final long idleTimeoutNanos;
    private final Closeable closeable;
    private final URI uri;
    private final AtomicBoolean closed;

    WatchdogEntry(Duration idleTimeout, Closeable closeable, URI uri) {
        this.lastActivityNanos = new AtomicLong(System.nanoTime());

        // Duration.toNanos() performs an overflow-checked multiplication on every call;
        // isExpired() is invoked once per entry on every StreamWatchdog sweep tick for
        // this entry's entire registered lifetime, so it is computed once here instead.
        this.idleTimeoutNanos = idleTimeout.toNanos();
        this.closeable = closeable;
        this.uri = uri;

        this.closed = new AtomicBoolean(false);
    }

    /**
     * Records that a byte was just successfully read, resetting the idle deadline.
     */
    void recordActivity() {
        lastActivityNanos.set(System.nanoTime());
    }

    /**
     * Reports whether this entry's {@code idleTimeout} has elapsed since its last
     * recorded activity, as of {@code nowNanos}.
     *
     * @param nowNanos The current {@link System#nanoTime()} value, supplied by the
     *                 caller so every entry in a single sweep is compared against the
     *                 same instant.
     * @return {@code true} if this entry is past its deadline.
     */
    boolean isExpired(long nowNanos) {
        return nowNanos - lastActivityNanos.get() >= idleTimeoutNanos;
    }

    /**
     * Closes the underlying stream at most once — safe to call more than once on the
     * same entry, since a compare-and-set guard ensures only the first call reaches the
     * underlying {@link Closeable#close()} — logging at {@code WARNING} rather than
     * throwing if {@code close()} itself fails.
     */
    void closeSafely() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }

        try {
            closeable.close();
        } catch (IOException e) {
            LOGGER.log(
                    System.Logger.Level.WARNING,
                    String.format("GET %s → failed to close idle stream after its idleTimeout elapsed", uri),
                    e
            );
        }
    }
}
