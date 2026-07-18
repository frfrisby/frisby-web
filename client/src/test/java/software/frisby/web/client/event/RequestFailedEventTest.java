package software.frisby.web.client.event;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import software.frisby.core.validation.BlankValueException;
import software.frisby.core.validation.NullValueException;

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
                    () -> new RequestFailedEvent(null, TEST_URI, Optional.empty(), LATENCY, CAUSE)
            );
        }

        @Test
        void blankMethod_throwsException() {
            assertThrows(
                    BlankValueException.class,
                    () -> new RequestFailedEvent("  ", TEST_URI, Optional.empty(), LATENCY, CAUSE)
            );
        }

        @Test
        void nullUri_throwsException() {
            assertThrows(
                    NullValueException.class,
                    () -> new RequestFailedEvent("GET", null, Optional.empty(), LATENCY, CAUSE)
            );
        }

        @Test
        void nullStatusCode_throwsException() {
            assertThrows(
                    NullValueException.class,
                    () -> new RequestFailedEvent("GET", TEST_URI, null, LATENCY, CAUSE)
            );
        }

        @Test
        void nullLatency_throwsException() {
            assertThrows(
                    NullValueException.class,
                    () -> new RequestFailedEvent("GET", TEST_URI, Optional.empty(), null, CAUSE)
            );
        }

        @Test
        void nullCause_throwsException() {
            assertThrows(
                    NullValueException.class,
                    () -> new RequestFailedEvent("GET", TEST_URI, Optional.empty(), LATENCY, null)
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
        void toString_containsStatusCode() {
            RequestFailedEvent event = RequestFailedEvent.httpFailure("POST", TEST_URI, 404, LATENCY, CAUSE);
            String s = event.toString();

            assertTrue(s.contains("404"));
            assertTrue(s.contains("→"));
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
    }
}

