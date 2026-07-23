package software.frisby.web.client.event;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import software.frisby.core.validation.BlankValueException;
import software.frisby.core.validation.NullValueException;
import software.frisby.core.validation.NumericValueOutsideRangeException;

import java.net.URI;
import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class RequestFailedEventTest {
    private static final URI TEST_URI = URI.create("https://api.example.com/orders/42");
    private static final Duration LATENCY = Duration.ofMillis(150);
    private static final RuntimeException CAUSE = new RuntimeException("timeout");

    // -------------------------------------------------------------------------
    // Compact constructor validation
    // -------------------------------------------------------------------------

    @Nested
    class Validation {
        @Test
        void nullMethod_throwsException() {
            assertThrows(
                    NullValueException.class,
                    () -> new RequestFailedEvent(null, TEST_URI, Optional.empty(), LATENCY, CAUSE, Optional.empty())
            );
        }

        @Test
        void blankMethod_throwsException() {
            assertThrows(
                    BlankValueException.class,
                    () -> new RequestFailedEvent("  ", TEST_URI, Optional.empty(), LATENCY, CAUSE, Optional.empty())
            );
        }

        @Test
        void nullUri_throwsException() {
            assertThrows(
                    NullValueException.class,
                    () -> new RequestFailedEvent("GET", null, Optional.empty(), LATENCY, CAUSE, Optional.empty())
            );
        }

        @Test
        void nullStatusCode_throwsException() {
            assertThrows(
                    NullValueException.class,
                    () -> new RequestFailedEvent("GET", TEST_URI, null, LATENCY, CAUSE, Optional.empty())
            );
        }

        @Test
        void nullLatency_throwsException() {
            assertThrows(
                    NullValueException.class,
                    () -> new RequestFailedEvent("GET", TEST_URI, Optional.empty(), null, CAUSE, Optional.empty())
            );
        }

        @Test
        void nullCause_throwsException() {
            assertThrows(
                    NullValueException.class,
                    () -> new RequestFailedEvent("GET", TEST_URI, Optional.empty(), LATENCY, null, Optional.empty())
            );
        }

        @Test
        void nullRetryAttempt_throwsException() {
            assertThrows(
                    NullValueException.class,
                    () -> new RequestFailedEvent("GET", TEST_URI, Optional.empty(), LATENCY, CAUSE, null)
            );
        }

        @Test
        void retryAttemptZero_throwsException() {
            assertThrows(
                    NumericValueOutsideRangeException.class,
                    () -> new RequestFailedEvent("GET", TEST_URI, Optional.empty(), LATENCY, CAUSE, Optional.of(0))
            );
        }

        @Test
        void retryAttemptNegative_throwsException() {
            assertThrows(
                    NumericValueOutsideRangeException.class,
                    () -> new RequestFailedEvent("GET", TEST_URI, Optional.empty(), LATENCY, CAUSE, Optional.of(-1))
            );
        }
    }

    // -------------------------------------------------------------------------
    // transportFailure factory
    // -------------------------------------------------------------------------

    @Nested
    class TransportFailure {
        @Test
        void factory_setsAllFields() {
            RequestFailedEvent event = RequestFailedEvent.transportFailure("GET", TEST_URI, LATENCY, CAUSE);

            assertEquals("GET", event.method());
            assertEquals(TEST_URI, event.uri());
            assertEquals(LATENCY, event.latency());
            assertEquals(CAUSE, event.cause());
        }

        @Test
        void factory_statusCodeIsEmpty() {
            RequestFailedEvent event = RequestFailedEvent.transportFailure("GET", TEST_URI, LATENCY, CAUSE);

            assertTrue(event.statusCode().isEmpty());
        }

        @Test
        void factory_retryAttemptIsEmpty() {
            RequestFailedEvent event = RequestFailedEvent.transportFailure("GET", TEST_URI, LATENCY, CAUSE);

            assertTrue(event.retryAttempt().isEmpty());
        }

        @Test
        void toString_containsMethodAndUri() {
            RequestFailedEvent event = RequestFailedEvent.transportFailure("GET", TEST_URI, LATENCY, CAUSE);
            String s = event.toString();

            assertTrue(s.contains("GET"));
            assertTrue(s.contains(TEST_URI.toString()));
        }

        @Test
        void toString_containsLatencyAndCauseSimpleName() {
            RequestFailedEvent event = RequestFailedEvent.transportFailure("GET", TEST_URI, LATENCY, CAUSE);
            String s = event.toString();

            assertTrue(s.contains("150ms"));
            assertTrue(s.contains("RuntimeException"));
        }

        @Test
        void toString_doesNotContainArrow() {
            RequestFailedEvent event = RequestFailedEvent.transportFailure("GET", TEST_URI, LATENCY, CAUSE);

            assertFalse(event.toString().contains("→"));
        }
    }

    // -------------------------------------------------------------------------
    // httpFailure factory
    // -------------------------------------------------------------------------

    @Nested
    class HttpFailure {
        @Test
        void factory_setsAllFields() {
            RequestFailedEvent event = RequestFailedEvent.httpFailure("POST", TEST_URI, 422, LATENCY, CAUSE);

            assertEquals("POST", event.method());
            assertEquals(TEST_URI, event.uri());
            assertEquals(LATENCY, event.latency());
            assertEquals(CAUSE, event.cause());
        }

        @Test
        void factory_statusCodeIsPresent() {
            RequestFailedEvent event = RequestFailedEvent.httpFailure("POST", TEST_URI, 422, LATENCY, CAUSE);

            assertTrue(event.statusCode().isPresent());
            assertEquals(422, event.statusCode().get());
        }

        @Test
        void factory_retryAttemptIsEmpty() {
            RequestFailedEvent event = RequestFailedEvent.httpFailure("POST", TEST_URI, 422, LATENCY, CAUSE);

            assertTrue(event.retryAttempt().isEmpty());
        }

        @Test
        void toString_containsStatusCode() {
            RequestFailedEvent event = RequestFailedEvent.httpFailure("POST", TEST_URI, 404, LATENCY, CAUSE);
            String s = event.toString();

            assertTrue(s.contains("404"));
            assertTrue(s.contains("→"));
        }
    }

    // -------------------------------------------------------------------------
    // withRetryAttempt
    // -------------------------------------------------------------------------

    @Nested
    class WithRetryAttempt {
        @Test
        void setsRetryAttemptOnCopy() {
            RequestFailedEvent base = RequestFailedEvent.transportFailure("GET", TEST_URI, LATENCY, CAUSE);
            RequestFailedEvent withAttempt = base.withRetryAttempt(2);

            assertTrue(withAttempt.retryAttempt().isPresent());
            assertEquals(2, withAttempt.retryAttempt().get());
        }

        @Test
        void doesNotMutateOriginal() {
            RequestFailedEvent base = RequestFailedEvent.transportFailure("GET", TEST_URI, LATENCY, CAUSE);
            base.withRetryAttempt(1);

            assertTrue(base.retryAttempt().isEmpty());
        }

        @Test
        void preservesAllOtherFields() {
            RequestFailedEvent base = RequestFailedEvent.httpFailure("DELETE", TEST_URI, 503, LATENCY, CAUSE);
            RequestFailedEvent withAttempt = base.withRetryAttempt(3);

            assertEquals(base.method(), withAttempt.method());
            assertEquals(base.uri(), withAttempt.uri());
            assertEquals(base.statusCode(), withAttempt.statusCode());
            assertEquals(base.latency(), withAttempt.latency());
            assertEquals(base.cause(), withAttempt.cause());
        }

        @Test
        void zeroAttempt_throwsException() {
            RequestFailedEvent base = RequestFailedEvent.transportFailure("GET", TEST_URI, LATENCY, CAUSE);

            assertThrows(
                    NumericValueOutsideRangeException.class,
                    () -> base.withRetryAttempt(0)
            );
        }

        @Test
        void toString_includesAttemptNumber() {
            RequestFailedEvent event = RequestFailedEvent.transportFailure("GET", TEST_URI, LATENCY, CAUSE)
                    .withRetryAttempt(2);

            assertTrue(event.toString().contains("attempt 2"));
        }

        @Test
        void toString_withoutRetryAttempt_doesNotContainAttempt() {
            RequestFailedEvent event = RequestFailedEvent.transportFailure("GET", TEST_URI, LATENCY, CAUSE);

            assertFalse(event.toString().contains("attempt"));
        }
    }

    // -------------------------------------------------------------------------
    // record equality
    // -------------------------------------------------------------------------

    @Nested
    class RecordEquality {
        @Test
        void twoTransportFailureEventsWithSameFields_areEqual() {
            RequestFailedEvent a = RequestFailedEvent.transportFailure("GET", TEST_URI, LATENCY, CAUSE);
            RequestFailedEvent b = RequestFailedEvent.transportFailure("GET", TEST_URI, LATENCY, CAUSE);

            assertEquals(a, b);
            assertEquals(a.hashCode(), b.hashCode());
        }

        @Test
        void twoHttpFailureEventsWithSameFields_areEqual() {
            RequestFailedEvent a = RequestFailedEvent.httpFailure("DELETE", TEST_URI, 404, LATENCY, CAUSE);
            RequestFailedEvent b = RequestFailedEvent.httpFailure("DELETE", TEST_URI, 404, LATENCY, CAUSE);

            assertEquals(a, b);
        }

        @Test
        void eventsWithDifferentRetryAttempt_areNotEqual() {
            RequestFailedEvent a = RequestFailedEvent.transportFailure("GET", TEST_URI, LATENCY, CAUSE)
                    .withRetryAttempt(1);
            RequestFailedEvent b = RequestFailedEvent.transportFailure("GET", TEST_URI, LATENCY, CAUSE)
                    .withRetryAttempt(2);

            assertNotEquals(a, b);
        }
    }
}

