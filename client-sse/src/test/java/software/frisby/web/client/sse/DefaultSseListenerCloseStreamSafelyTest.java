package software.frisby.web.client.sse;

import org.junit.jupiter.api.Test;
import software.frisby.web.test.log.LogExpectation;
import software.frisby.web.test.log.SystemLogVerifier;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for {@link DefaultSseListener#closeStreamSafely}, extracted specifically so
 * this catch-and-log behavior — shared by {@link DefaultSseListener#close()} and
 * {@link DefaultSseListener} package-private {@code ReaderTask.postWithPolicy}'s
 * policy-driven {@link BufferFullPolicy#DISCONNECT} branch — can be exercised directly.
 * <p>
 * Neither real call site can be made to hit the {@code IOException} branch on demand: a
 * normal JDK {@code HttpClient} response {@link InputStream} does not throw from its own
 * {@code close()} under any condition a test can reliably provoke (calling {@code close()}
 * twice is documented as a no-op, not a re-throw), and there is no seam to substitute a
 * custom stream through the real {@code client}/{@code SseSpec} connection path. Testing
 * the extracted method directly with an ordinary throwing {@link InputStream} test double
 * sidesteps both problems entirely — no network, no reflection, no mocking framework.
 */
class DefaultSseListenerCloseStreamSafelyTest {
    private static final String FAILURE_MESSAGE = "Failed to close the test stream.";

    @Test
    void nullStream_isANoOp() {
        assertDoesNotThrow(() -> DefaultSseListener.closeStreamSafely(null, FAILURE_MESSAGE));
    }

    @Test
    void streamClosesCleanly_noLogging() {
        InputStream cleanStream = InputStream.nullInputStream();

        try (SystemLogVerifier verifier = SystemLogVerifier.builder()
                .configure(DefaultSseListener.class, System.Logger.Level.OFF)
                .build()) {
            DefaultSseListener.closeStreamSafely(cleanStream, FAILURE_MESSAGE);

            // No LogExpectation is registered — the meaningful assertion here is the
            // warningCount() check below, not assertExpectations() (which only checks
            // registered expectations, and would pass trivially with none registered).
            assertEquals(0, verifier.warningCount());
        }
    }

    @Test
    void streamThrowsOnClose_logsFailureMessageAtWarningWithCauseAttached() {
        IOException cause = new IOException("Simulated close() failure.");
        InputStream throwingStream = new InputStream() {
            @Override
            public int read() {
                throw new UnsupportedOperationException("Not used by this test.");
            }

            @Override
            public void close() throws IOException {
                throw cause;
            }
        };

        try (SystemLogVerifier verifier = SystemLogVerifier.builder()
                .expect(LogExpectation.builder()
                        .logger(DefaultSseListener.class)
                        .level(System.Logger.Level.WARNING)
                        .predicate(e -> e.message().contains(FAILURE_MESSAGE) && cause == e.thrown())
                        .build()
                )
                .build()) {
            DefaultSseListener.closeStreamSafely(throwingStream, FAILURE_MESSAGE);

            verifier.assertExpectations(Duration.ofSeconds(10));
        }
    }
}





