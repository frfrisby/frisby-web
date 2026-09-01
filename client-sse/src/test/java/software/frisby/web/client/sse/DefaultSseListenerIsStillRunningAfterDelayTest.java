package software.frisby.web.client.sse;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link DefaultSseListener#isStillRunningAfterDelay}, extracted
 * specifically so this check — the outcome of {@code ReaderTask.awaitReconnectDelay}'s
 * {@code Thread.sleep} returning normally — can be exercised directly.
 * <p>
 * {@link DefaultSseListener#close()} always pairs setting {@code closed} with an
 * immediate {@code readerThread.interrupt()}, so under any realistic integration test
 * the sleeping reader thread almost always observes an {@link InterruptedException}
 * instead of ever reaching this check with {@code closed} already {@code true} — that
 * would require the sleep to complete naturally in the narrow window before the
 * interrupt is delivered, a race no integration test can force on demand. Testing the
 * extracted method directly with both an already-closed and a still-open flag sidesteps
 * the race entirely.
 */
class DefaultSseListenerIsStillRunningAfterDelayTest {
    @Test
    void notClosed_returnsTrue() {
        assertTrue(DefaultSseListener.isStillRunningAfterDelay(new AtomicBoolean(false)));
    }

    @Test
    void closed_returnsFalse() {
        assertFalse(DefaultSseListener.isStillRunningAfterDelay(new AtomicBoolean(true)));
    }
}

