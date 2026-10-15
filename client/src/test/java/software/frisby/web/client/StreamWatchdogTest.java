package software.frisby.web.client;

import org.junit.jupiter.api.Test;
import software.frisby.web.test.log.LogExpectation;
import software.frisby.web.test.log.SystemLogVerifier;

import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Exercises {@link StreamWatchdog} in complete isolation, with no real {@link
 * java.io.InputStream} wiring — every entry here is registered directly against a
 * fake {@link Closeable}, well past {@link StreamWatchdog#TICK_INTERVAL_MILLIS}'s own
 * cadence, so each assertion waits generously (several ticks) rather than racing the
 * watchdog's shared, process-wide thread.
 */
class StreamWatchdogTest {
    private static final Duration SHORT_IDLE_TIMEOUT = Duration.ofMillis(300L);
    private static final Duration GENEROUS_WAIT = Duration.ofSeconds(5L);

    /**
     * A {@link Closeable} test double that counts down a latch and increments a
     * counter on every {@code close()} invocation, so a test can both wait for the
     * close to happen and assert it happened exactly once.
     */
    private static final class CountingCloseable implements Closeable {
        private final CountDownLatch closedLatch = new CountDownLatch(1);
        private final AtomicInteger closeCount = new AtomicInteger(0);

        @Override
        public void close() {
            closeCount.incrementAndGet();
            closedLatch.countDown();
        }

        boolean awaitClosed(Duration timeout) throws InterruptedException {
            return closedLatch.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
        }

        int closeCount() {
            return closeCount.get();
        }
    }

    @Test
    void untouchedStream_closedAutomaticallyAfterIdleTimeout() throws InterruptedException {
        Object key = new Object();
        CountingCloseable closeable = new CountingCloseable();

        try {
            StreamWatchdog.register(key, SHORT_IDLE_TIMEOUT, closeable, URI.create("https://example.com"));

            assertTrue(closeable.awaitClosed(GENEROUS_WAIT));
            assertEquals(1, closeable.closeCount());
            assertFalse(StreamWatchdog.isRegistered(key));
        } finally {
            StreamWatchdog.deregister(key);
        }
    }

    @Test
    void streamKeptActive_neverClosed() throws InterruptedException {
        Object key = new Object();
        CountingCloseable closeable = new CountingCloseable();

        try {
            StreamWatchdog.register(key, SHORT_IDLE_TIMEOUT, closeable, URI.create("https://example.com"));

            long deadline = System.nanoTime() + GENEROUS_WAIT.toNanos();
            while (System.nanoTime() < deadline) {
                StreamWatchdog.recordActivity(key);
                Thread.sleep(SHORT_IDLE_TIMEOUT.toMillis() / 3);
            }

            assertEquals(0, closeable.closeCount());
            assertTrue(StreamWatchdog.isRegistered(key));
        } finally {
            StreamWatchdog.deregister(key);
        }
    }

    @Test
    void deregisteredStream_neverTouchedBySubsequentTick() throws InterruptedException {
        Object key = new Object();
        CountingCloseable closeable = new CountingCloseable();

        StreamWatchdog.register(key, SHORT_IDLE_TIMEOUT, closeable, URI.create("https://example.com"));
        StreamWatchdog.deregister(key);

        assertFalse(StreamWatchdog.isRegistered(key));

        // Wait comfortably past the idle timeout and several ticks — if deregister()
        // failed to remove the entry, a subsequent sweep would close it here.
        Thread.sleep(GENEROUS_WAIT.toMillis());

        assertEquals(0, closeable.closeCount());
    }

    @Test
    void twoEntriesWithDifferentIdleTimeouts_evaluatedIndependently() throws InterruptedException {
        Object shortKey = new Object();
        Object longKey = new Object();
        CountingCloseable shortCloseable = new CountingCloseable();
        CountingCloseable longCloseable = new CountingCloseable();

        try {
            StreamWatchdog.register(shortKey, Duration.ofMillis(300L), shortCloseable, URI.create("https://example.com"));
            StreamWatchdog.register(longKey, Duration.ofSeconds(30L), longCloseable, URI.create("https://example.com"));

            assertTrue(shortCloseable.awaitClosed(GENEROUS_WAIT));
            assertEquals(0, longCloseable.closeCount());
            assertTrue(StreamWatchdog.isRegistered(longKey));
        } finally {
            StreamWatchdog.deregister(shortKey);
            StreamWatchdog.deregister(longKey);
        }
    }

    @Test
    void concurrentDeregisterAndExpiry_doesNotThrowOrDoubleClose() throws InterruptedException {
        Object key = new Object();
        CountingCloseable closeable = new CountingCloseable();

        try {
            // Registered with an idle timeout already in the past, so the very next tick
            // races a concurrent caller-driven deregister() against the watchdog's own
            // expiry for the same entry.
            StreamWatchdog.register(key, Duration.ofMillis(1L), closeable, URI.create("https://example.com"));
            Thread.sleep(StreamWatchdog.TICK_INTERVAL_MILLIS / 4);
        } finally {
            StreamWatchdog.deregister(key);
        }

        Thread.sleep(GENEROUS_WAIT.toMillis());

        assertTrue(closeable.closeCount() <= 1);
        assertFalse(StreamWatchdog.isRegistered(key));
    }

    /**
     * {@link StreamWatchdog#recordActivity(Object)} is a no-op for a key that was never
     * registered, or was already expired and removed by a previous sweep — exercised
     * directly here since none of the other tests in this class ever call it against an
     * absent key.
     */
    @Test
    void recordActivity_unregisteredKey_isNoOp() {
        StreamWatchdog.recordActivity(new Object());
    }

    /**
     * A {@link Closeable} whose {@code close()} always fails must not crash the shared
     * watchdog thread — {@link WatchdogEntry#closeSafely()} catches and logs the
     * failure internally rather than letting it propagate out of the scheduled sweep
     * task, which would otherwise silently cancel every future tick (per
     * {@link java.util.concurrent.ScheduledExecutorService}'s own contract for an
     * uncaught exception in a periodic task) and leave every other currently-registered
     * stream un-watched from that point on.
     */
    @Test
    void closeSafely_closeableThrows_isLoggedAndWatchdogSurvives() throws InterruptedException {
        Object throwingKey = new Object();
        Closeable throwingCloseable = () -> {
            throw new IOException("Simulated close failure.");
        };

        try (SystemLogVerifier verifier = SystemLogVerifier.builder()
                .configure(WatchdogEntry.class, System.Logger.Level.WARNING)
                .expect(
                        LogExpectation.builder()
                                .logger(WatchdogEntry.class)
                                .level(System.Logger.Level.WARNING)
                                .predicate(e -> e.message().contains("failed to close idle stream"))
                                .failureMessage("Expected a WARNING log for the failed close().")
                                .build()
                )
                .build()) {
            StreamWatchdog.register(throwingKey, SHORT_IDLE_TIMEOUT, throwingCloseable, URI.create("https://example.com"));

            verifier.assertExpectations(GENEROUS_WAIT);
            assertEquals(1, verifier.warningCount());
        } finally {
            StreamWatchdog.deregister(throwingKey);
        }

        assertFalse(StreamWatchdog.isRegistered(throwingKey));

        // Prove the shared watchdog thread is still alive and ticking after swallowing
        // that exception, rather than having silently died — a second, independent
        // entry registered afterward must still be expired on schedule.
        Object key = new Object();
        CountingCloseable closeable = new CountingCloseable();

        try {
            StreamWatchdog.register(key, SHORT_IDLE_TIMEOUT, closeable, URI.create("https://example.com"));

            assertTrue(closeable.awaitClosed(GENEROUS_WAIT));
        } finally {
            StreamWatchdog.deregister(key);
        }
    }
}

