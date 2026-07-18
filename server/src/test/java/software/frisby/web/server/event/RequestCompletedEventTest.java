package software.frisby.web.server.event;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestCompletedEventTest {
    // -------------------------------------------------------------------------
    // successful()
    // -------------------------------------------------------------------------

    @Nested
    class Successful {
        @Test
        void statusCode200_returnsTrue() {
            assertTrue(event(200).successful());
        }

        @Test
        void statusCode201_returnsTrue() {
            assertTrue(event(201).successful());
        }

        @Test
        void statusCode299_returnsTrue() {
            assertTrue(event(299).successful());
        }

        @Test
        void statusCode199_returnsFalse() {
            assertFalse(event(199).successful());
        }

        @Test
        void statusCode300_returnsFalse() {
            assertFalse(event(300).successful());
        }

        @Test
        void statusCode404_returnsFalse() {
            assertFalse(event(404).successful());
        }

        @Test
        void statusCode500_returnsFalse() {
            assertFalse(event(500).successful());
        }
    }

    // -------------------------------------------------------------------------
    // toString()
    // -------------------------------------------------------------------------

    @Test
    void toString_formatsCorrectly() {
        RequestCompletedEvent event = new RequestCompletedEvent(
                "GET",
                "/orders/1",
                200,
                Duration.ofMillis(14),
                0L,
                128L
        );

        String result = event.toString();

        assertTrue(result.contains("GET"));
        assertTrue(result.contains("/orders/1"));
        assertTrue(result.contains("200"));
        assertTrue(result.contains("14ms"));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static RequestCompletedEvent event(int statusCode) {
        return new RequestCompletedEvent(
                "GET",
                "/test",
                statusCode,
                Duration.ofMillis(1),
                0L,
                0L
        );
    }
}

