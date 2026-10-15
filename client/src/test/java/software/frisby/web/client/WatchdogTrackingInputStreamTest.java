package software.frisby.web.client;

import org.junit.jupiter.api.Test;
import software.frisby.web.client.exception.IdleTimeoutException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Exercises {@link WatchdogTrackingInputStream} against real blocking/EOF semantics,
 * complementing {@link StreamWatchdogTest}'s isolated exercise of the registry/sweep
 * mechanism itself.
 */
class WatchdogTrackingInputStreamTest {
    private static final Duration SHORT_IDLE_TIMEOUT = Duration.ofMillis(300L);
    private static final Duration GENEROUS_WAIT = Duration.ofSeconds(5L);

    /**
     * An {@link InputStream} that returns a handful of bytes, then blocks (polling a
     * {@code closed} flag) rather than returning {@code -1} — simulating a connection
     * that goes silent mid-stream without an EOF or a transport-level error. Closing
     * this stream unblocks a parked {@link #read()} with an {@link IOException}, the
     * exact mechanism {@link StreamWatchdog}'s expiry relies on.
     */
    private static final class StallingInputStream extends InputStream {
        private final byte[] data;
        private final AtomicBoolean closed = new AtomicBoolean(false);
        private int index = 0;

        private StallingInputStream(byte[] data) {
            this.data = data;
        }

        @Override
        public int read() throws IOException {
            while (true) {
                if (closed.get()) {
                    throw new IOException("Simulated closed stream.");
                }

                if (index < data.length) {
                    return data[index++] & 0xFF;
                }

                try {
                    Thread.sleep(20L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException(e);
                }
            }
        }

        @Override
        public void close() {
            closed.set(true);
        }
    }

    @Test
    void idlePastTimeout_underlyingStreamClosedAndPendingReadThrows() throws Exception {
        StallingInputStream raw = new StallingInputStream(new byte[]{1, 2});
        WatchdogTrackingInputStream tracked = new WatchdogTrackingInputStream(raw, SHORT_IDLE_TIMEOUT, URI.create("https://example.com"));

        assertEquals(1, tracked.read());
        assertEquals(2, tracked.read());

        CountDownLatch readFailed = new CountDownLatch(1);
        AtomicReference<Exception> thrown = new AtomicReference<>();

        Thread reader = new Thread(() -> {
            try {
                tracked.read();
            } catch (Exception e) {
                thrown.set(e);
            } finally {
                readFailed.countDown();
            }
        });
        reader.start();

        assertTrue(readFailed.await(GENEROUS_WAIT.toMillis(), TimeUnit.MILLISECONDS));
        assertInstanceOf(IdleTimeoutException.class, thrown.get());
        assertFalse(StreamWatchdog.isRegistered(tracked));

        reader.join(GENEROUS_WAIT.toMillis());
    }

    @Test
    void repeatedReadsFasterThanIdleTimeout_neverClosed() throws IOException, InterruptedException {
        // Sized generously relative to GENEROUS_WAIT's loop duration and sleep interval
        // below, so the data never runs out mid-test (which would otherwise make the
        // stream block on its own, starving recordActivity() and triggering a spurious
        // idle-timeout close unrelated to what this test actually verifies).
        byte[] data = new byte[500];
        StallingInputStream raw = new StallingInputStream(data);
        WatchdogTrackingInputStream tracked = new WatchdogTrackingInputStream(raw, SHORT_IDLE_TIMEOUT, URI.create("https://example.com"));

        long deadline = System.nanoTime() + GENEROUS_WAIT.toNanos();
        while (System.nanoTime() < deadline) {
            assertEquals(0, tracked.read());
            Thread.sleep(SHORT_IDLE_TIMEOUT.toMillis() / 4);
        }

        assertTrue(StreamWatchdog.isRegistered(tracked));

        tracked.close();
    }

    @Test
    void endOfStream_deregistersAndNeverClosedByWatchdog() throws IOException, InterruptedException {
        ByteArrayInputStream raw = new ByteArrayInputStream(new byte[]{9});
        WatchdogTrackingInputStream tracked = new WatchdogTrackingInputStream(raw, SHORT_IDLE_TIMEOUT, URI.create("https://example.com"));

        assertEquals(9, tracked.read());
        assertEquals(-1, tracked.read());

        assertFalse(StreamWatchdog.isRegistered(tracked));

        // Wait comfortably past the idle timeout and several ticks — confirms a
        // subsequent sweep never touches the already-deregistered entry.
        Thread.sleep(GENEROUS_WAIT.toMillis());

        assertEquals(-1, tracked.read());
    }

    @Test
    void explicitClose_deregistersAndIsIdempotent() throws IOException {
        ByteArrayInputStream raw = new ByteArrayInputStream(new byte[]{1});
        WatchdogTrackingInputStream tracked = new WatchdogTrackingInputStream(raw, SHORT_IDLE_TIMEOUT, URI.create("https://example.com"));

        assertTrue(StreamWatchdog.isRegistered(tracked));

        tracked.close();
        assertFalse(StreamWatchdog.isRegistered(tracked));

        // Closing an already-closed stream must not throw.
        tracked.close();
    }
}


