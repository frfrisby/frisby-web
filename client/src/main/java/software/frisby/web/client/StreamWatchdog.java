package software.frisby.web.client;

import java.io.Closeable;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * A shared, single-thread idle-stream watchdog used by every stream-producing spec
 * ({@code DownloadSpec}, {@code SseSpec}) to bound a post-first-byte stall — a server
 * that accepts a connection, sends a few bytes, then goes silent, leaving a caller's
 * thread parked in {@code InputStream#read()} forever.
 * <p>
 * Modeled on {@link HttpEngine}'s {@code DEFAULT_RETRY_SCHEDULER}: one shared,
 * daemon-threaded {@link ScheduledExecutorService}, rather than a thread per tracked
 * stream. Unlike {@code DEFAULT_RETRY_SCHEDULER}'s eager static init, the background
 * thread behind this scheduler is created <strong>lazily</strong> — not until the very
 * first call to {@link #register} anywhere in the JVM, via the {@link SchedulerHolder}
 * initialization-on-demand holder idiom. Many applications never call {@code
 * download()} or {@code sse()} at all; those applications should never pay for a
 * background thread they never needed. The JVM's own class-initialization semantics
 * guarantee {@link SchedulerHolder} initializes at most once, safely under concurrent
 * first calls, with no explicit locking required here.
 * <p>
 * Beyond that one-time lazy start, the sweep itself is also <strong>paused</strong>
 * whenever the registry is empty, rather than ticking forever once started: the
 * fixed-delay sweep task is cancelled the moment the last tracked stream is removed
 * (whether by expiry or by {@link #deregister}), and a fresh one is scheduled the next
 * time {@link #register} transitions the registry from empty back to non-empty. This
 * matters for applications whose stream-producing calls are bursty — e.g. a scheduled
 * job that downloads a batch of files once an hour and is otherwise idle — so the
 * shared thread goes back to doing nothing between bursts instead of ticking every
 * second indefinitely once the first stream is ever tracked.
 * <p>
 * A single fixed-cadence tick ({@link #TICK_INTERVAL_MILLIS}) sweeps a registry of
 * every currently-open stream, comparing each entry's own {@code idleTimeout} against
 * its own last-activity timestamp — not a per-entry cadence, since a shared watchdog
 * services entries with potentially different {@code idleTimeout} values and can't run
 * on a different schedule for each one. There is no configuration knob for the tick
 * interval; correctness (comfortably smaller than any realistic {@code idleTimeout})
 * is guaranteed by construction, not left to a caller who could misconfigure it.
 * <p>
 * A stream registers itself on construction ({@link WatchdogTrackingInputStream}) and
 * deregisters on {@code close()} or natural end-of-stream, so a caller who properly
 * closes or drains a stream costs nothing once done. A caller who never does gets a
 * free, partial self-healing improvement: the stream is eventually closed once idle
 * past its {@code idleTimeout}, rather than leaking its underlying socket forever.
 *
 * @see WatchdogEntry
 * @see WatchdogTrackingInputStream
 */
final class StreamWatchdog {
    /**
     * The fixed sweep cadence. Comfortably smaller than any realistic {@code
     * idleTimeout} a caller is likely to configure (seconds-to-minutes range), so the
     * worst-case detection latency past an entry's own deadline stays small relative to
     * that deadline.
     */
    static final long TICK_INTERVAL_MILLIS = 1000L;

    private static final SchedulerHolder SCHEDULER_HOLDER = new SchedulerHolder();

    private StreamWatchdog() {
    }

    /**
     * Owns both the registry of currently-tracked entries and the lifecycle of the
     * single fixed-delay sweep task that services them. {@link #put} and {@link
     * #remove} are the only two entry points that mutate {@link #registry} or {@link
     * #sweepTask}, and both synchronize on the same {@link #syncRoot} so the "registry
     * just became non-empty / just became empty" transitions used to start or stop the
     * sweep task are always evaluated against a consistent, non-racing view of the
     * registry's size.
     * <p>
     * {@link #sweepTask} is cancelled via {@code cancel(false)} — never {@code
     * true} — since {@link #cancelTask()} is sometimes invoked from {@link #expire}
     * while running <em>on</em> the scheduler thread, i.e. from inside the very
     * invocation of {@link #sweep()} that the future being cancelled represents.
     * {@code cancel(false)} only prevents that future's <em>subsequent</em>
     * scheduled executions; it does not interrupt the current one, so the in-progress
     * sweep always finishes examining every remaining entry normally.
     */
    private static final class SchedulerHolder {
        private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "frisby-stream-watchdog");
            t.setDaemon(true);
            return t;
        });

        private final Object syncRoot = new Object();

        private final ConcurrentMap<Object, WatchdogEntry> registry;

        private ScheduledFuture<?> sweepTask;

        private SchedulerHolder() {
            this.registry = new ConcurrentHashMap<>();
        }

        void put(Object key, WatchdogEntry entry) {
            synchronized (syncRoot) {
                this.registry.put(key, entry);

                // A re-registration of an already-tracked key leaves size() unchanged,
                // so this only fires on the empty-to-non-empty transition — exactly the
                // moment a (re)start of the sweep task is needed.
                if (this.registry.size() == 1) {
                    scheduleSweep();
                }
            }
        }

        void remove(Object key) {
            synchronized (syncRoot) {
                this.registry.remove(key);

                if (this.registry.isEmpty()) {
                    cancelTask();
                }
            }
        }

        WatchdogEntry get(Object key) {
            return this.registry.get(key);
        }

        boolean containsKey(Object key) {
            return this.registry.containsKey(key);
        }

        private void cancelTask() {
            if (null != this.sweepTask) {
                this.sweepTask.cancel(false);
                this.sweepTask = null;
            }
        }

        private void scheduleSweep() {
            cancelTask();

            this.sweepTask = SCHEDULER.scheduleWithFixedDelay(
                    this::sweep,
                    TICK_INTERVAL_MILLIS,
                    TICK_INTERVAL_MILLIS,
                    TimeUnit.MILLISECONDS
            );
        }

        private void sweep() {
            long now = System.nanoTime();

            for (Map.Entry<Object, WatchdogEntry> entry : this.registry.entrySet()) {
                if (entry.getValue().isExpired(now)) {
                    expire(entry.getKey(), entry.getValue());
                }
            }
        }

        private void expire(Object key, WatchdogEntry entry) {
            this.remove(key);
            entry.closeSafely();
        }
    }

    /**
     * Registers {@code key} for idle tracking, with its last-activity timestamp seeded
     * to "now". Lazily starts the shared scheduler on the very first call across the
     * whole JVM — see {@link SchedulerHolder}.
     *
     * @param key         The identity under which this stream is tracked — the owning
     *                    {@link WatchdogTrackingInputStream} instance itself in
     *                    practice. Must be stable across {@link #recordActivity}/
     *                    {@link #deregister} calls for the same stream.
     * @param idleTimeout How long this entry may go without activity before being
     *                    forcibly closed.
     * @param closeable   The underlying resource to close on expiry.
     * @param uri         The URI of the endpoint that returned the stream being tracked, for logging purposes.
     */
    static void register(Object key, Duration idleTimeout, Closeable closeable, URI uri) {
        SCHEDULER_HOLDER.put(key, new WatchdogEntry(idleTimeout, closeable, uri));
    }

    /**
     * Resets {@code key}'s idle deadline. A no-op if {@code key} is not currently
     * registered — e.g. a tick already expired and removed it, or it was never
     * registered to begin with.
     *
     * @param key The identity previously passed to {@link #register}.
     */
    static void recordActivity(Object key) {
        WatchdogEntry entry = SCHEDULER_HOLDER.get(key);

        if (null != entry) {
            entry.recordActivity();
        }
    }

    /**
     * Removes {@code key} from the registry without closing anything. Called when a
     * stream reaches end-of-stream or is closed by its own caller — in either case the
     * stream no longer needs watching, but its underlying resource is closed by the
     * caller's own code path, not by this method.
     *
     * @param key The identity previously passed to {@link #register}.
     */
    static void deregister(Object key) {
        SCHEDULER_HOLDER.remove(key);
    }

    /**
     * Reports whether {@code key} is currently tracked. Package-private, for direct
     * test assertions on registration/deregistration.
     *
     * @param key The identity previously passed to {@link #register}.
     * @return {@code true} if {@code key} is currently registered.
     */
    static boolean isRegistered(Object key) {
        return SCHEDULER_HOLDER.containsKey(key);
    }
}
