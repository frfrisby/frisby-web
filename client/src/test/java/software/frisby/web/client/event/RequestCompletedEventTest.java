package software.frisby.web.client.event;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import software.frisby.core.validation.BlankValueException;
import software.frisby.core.validation.NullValueException;

import java.net.URI;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class RequestCompletedEventTest {
    private static final URI TEST_URI = URI.create("https://api.example.com/orders/42");
    private static final Duration LATENCY = Duration.ofMillis(42);

    // -------------------------------------------------------------------------
    // Compact constructor validation
    // -------------------------------------------------------------------------

    private static RequestCompletedEvent event(int statusCode) {
        return new RequestCompletedEvent("GET", TEST_URI, statusCode, LATENCY);
    }

    // -------------------------------------------------------------------------
    // successful()
    // -------------------------------------------------------------------------

    @Nested
    class Validation {
        @Test
        void nullMethod_throwsException() {
            assertThrows(
                    NullValueException.class,
                    () -> new RequestCompletedEvent(null, TEST_URI, 200, LATENCY)
            );
        }

        @Test
        void blankMethod_throwsException() {
            assertThrows(
                    BlankValueException.class,
                    () -> new RequestCompletedEvent("  ", TEST_URI, 200, LATENCY)
            );
        }

        @Test
        void nullUri_throwsNullValueException() {
            assertThrows(
                    NullValueException.class,
                    () -> new RequestCompletedEvent("GET", null, 200, LATENCY)
            );
        }

        @Test
        void nullLatency_throwsNullValueException() {
            assertThrows(
                    NullValueException.class,
                    () -> new RequestCompletedEvent("GET", TEST_URI, 200, null)
            );
        }
    }

    // -------------------------------------------------------------------------
    // toString
    // -------------------------------------------------------------------------

    @Nested
    class Successful {
        @Test
        void status200_isSuccessful() {
            assertTrue(event(200).successful());
        }

        @Test
        void status299_isSuccessful() {
            assertTrue(event(299).successful());
        }

        @Test
        void status199_isNotSuccessful() {
            assertFalse(event(199).successful());
        }

        @Test
        void status300_isNotSuccessful() {
            assertFalse(event(300).successful());
        }

        @Test
        void status404_isNotSuccessful() {
            assertFalse(event(404).successful());
        }

        @Test
        void status500_isNotSuccessful() {
            assertFalse(event(500).successful());
        }
    }

    // -------------------------------------------------------------------------
    // Record equality
    // -------------------------------------------------------------------------

    @Nested
    class ToStringMethod {
        @Test
        void toString_containsMethodUriStatusAndLatency() {
            String s = event(200).toString();

            assertTrue(s.contains("GET"));
            assertTrue(s.contains(TEST_URI.toString()));
            assertTrue(s.contains("200"));
            assertTrue(s.contains("42ms"));
        }

        @Test
        void toString_containsArrow() {
            assertTrue(event(404).toString().contains("→"));
        }

        @Test
        void toString_format() {
            assertEquals(
                    "GET https://api.example.com/orders/42 → 200 (42ms)",
                    event(200).toString()
            );
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    @Nested
    class RecordEquality {
        @Test
        void twoEventsWithSameFields_areEqual() {
            RequestCompletedEvent a = event(200);
            RequestCompletedEvent b = event(200);

            assertEquals(a, b);
            assertEquals(a.hashCode(), b.hashCode());
        }
    }
}

