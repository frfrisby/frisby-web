package software.frisby.web.client;

import org.junit.jupiter.api.Test;

import java.io.Closeable;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Exercises {@link WatchdogEntry} directly, in isolation from {@link StreamWatchdog}'s
 * scheduler and registry.
 */
class WatchdogEntryTest {
    private static final Duration IDLE_TIMEOUT = Duration.ofSeconds(30L);

    /**
     * A {@link Closeable} test double that counts how many times {@code close()} was
     * invoked.
     */
    private static final class CountingCloseable implements Closeable {
        private final AtomicInteger closeCount = new AtomicInteger(0);

        @Override
        public void close() {
            closeCount.incrementAndGet();
        }

        int closeCount() {
            return closeCount.get();
        }
    }

    /**
     * {@link WatchdogEntry#closeSafely()} guards against invoking {@link
     * Closeable#close()} more than once on the same entry via an internal
     * compare-and-set flag. Calling it twice directly on the same entry proves the
     * guard, rather than the lack of a second call path, is what prevents the double
     * close.
     */
    @Test
    void closeSafely_calledTwice_closesUnderlyingCloseableOnlyOnce() {
        CountingCloseable closeable = new CountingCloseable();
        WatchdogEntry entry = new WatchdogEntry(IDLE_TIMEOUT, closeable, URI.create("https://example.com"));

        entry.closeSafely();
        entry.closeSafely();

        assertEquals(1, closeable.closeCount());
    }
}

