package software.frisby.web.server.event;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

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
                128L,
                Optional.empty(),
                false
        );

        String result = event.toString();

        assertTrue(result.contains("GET"));
        assertTrue(result.contains("/orders/1"));
        assertTrue(result.contains("200"));
        assertTrue(result.contains("14ms"));
    }

    // -------------------------------------------------------------------------
    // endpoint()
    // -------------------------------------------------------------------------

    @Nested
    class EndpointField {
        @Test
        void emptyEndpoint_endpointIsEmpty() {
            assertTrue(event(200).endpoint().isEmpty());
        }

        @Test
        void presentEndpoint_endpointIsPresent() throws Exception {
            Method method = SampleResource.class.getMethod("handle");
            Endpoint endpoint = new Endpoint(SampleResource.class, method);

            RequestCompletedEvent event = new RequestCompletedEvent(
                    "GET",
                    "/sample",
                    200,
                    Duration.ofMillis(5),
                    0L,
                    0L,
                    Optional.of(endpoint),
                    false
            );

            assertTrue(event.endpoint().isPresent());
            assertSame(SampleResource.class, event.endpoint().get().resourceClass());
            assertEquals(method, event.endpoint().get().method());
        }
    }

    // -------------------------------------------------------------------------
    // staticAsset()
    // -------------------------------------------------------------------------

    @Nested
    class StaticAssetField {
        @Test
        void staticAssetFalse_returnsFalse() {
            assertFalse(event(200).staticAsset());
        }

        @Test
        void staticAssetTrue_returnsTrue() {
            RequestCompletedEvent event = new RequestCompletedEvent(
                    "GET",
                    "/app.js",
                    200,
                    Duration.ofMillis(3),
                    0L,
                    0L,
                    Optional.empty(),
                    true
            );

            assertTrue(event.staticAsset());
        }
    }

    // -------------------------------------------------------------------------
    // Endpoint record
    // -------------------------------------------------------------------------

    @Nested
    class EndpointRecord {
        @Test
        void name_formatsAsClassDotMethod() throws Exception {
            Method method = SampleResource.class.getMethod("handle");

            Endpoint endpoint = new Endpoint(SampleResource.class, method);

            assertEquals("SampleResource.handle", endpoint.name());
        }

        @Test
        void equality_sameClassAndMethod_areEqual() throws Exception {
            Method method = SampleResource.class.getMethod("handle");

            Endpoint a = new Endpoint(SampleResource.class, method);
            Endpoint b = new Endpoint(SampleResource.class, method);

            assertEquals(a, b);
            assertEquals(a.hashCode(), b.hashCode());
        }

        @Test
        void equality_differentMethods_areNotEqual() throws Exception {
            Method handle = SampleResource.class.getMethod("handle");
            Method other = SampleResource.class.getMethod("other");

            assertNotEquals(
                    new Endpoint(SampleResource.class, handle),
                    new Endpoint(SampleResource.class, other)
            );
        }
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
                0L,
                Optional.empty(),
                false
        );
    }

    /** Minimal resource class used as a reflection fixture in tests. */
    public static class SampleResource {
        public void handle() {}
        public void other() {}
    }
}
