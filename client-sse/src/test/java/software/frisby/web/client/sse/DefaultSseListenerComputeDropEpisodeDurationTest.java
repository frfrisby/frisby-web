package software.frisby.web.client.sse;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Unit tests for {@link DefaultSseListener#computeDropEpisodeDuration}.
 * <p>
 * The {@code null == start} branch is structurally unreachable through the real reader
 * thread — see the method's own javadoc — so this test exercises both branches directly
 * rather than trying to drive a live connection into that state.
 */
class DefaultSseListenerComputeDropEpisodeDurationTest {
    @Test
    void nullStart_returnsZeroDuration() {
        assertEquals(Duration.ZERO, DefaultSseListener.computeDropEpisodeDuration(null));
    }

    @Test
    void nonNullStart_returnsElapsedDuration() throws InterruptedException {
        Instant start = Instant.now();
        Thread.sleep(10);

        Duration elapsed = DefaultSseListener.computeDropEpisodeDuration(start);

        assertFalse(elapsed.isNegative());
        assertFalse(elapsed.isZero(), "Expected a measurable elapsed duration, was " + elapsed);
    }
}

