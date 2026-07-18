package software.frisby.web.server;

import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityResponseFilterTest {
    private final SecurityResponseFilter filter = new SecurityResponseFilter();

    // -------------------------------------------------------------------------
    // Stripped status codes — entity must be set to null
    // -------------------------------------------------------------------------

    @Nested
    class StrippedStatusCodes {
        @Test
        void status401_entityIsStripped() {
            MultivaluedHashMap<String, Object> headers = headersWithContentType();
            AtomicBoolean stripped = new AtomicBoolean(false);

            filter.filter(null, stubResponse(401, stripped, new AtomicReference<>(), headers));

            assertTrue(stripped.get(), "entity was not stripped for 401");
            assertFalse(headers.containsKey("Content-Type"), "Content-Type was not removed for 401");
        }

        @Test
        void status403_entityIsStripped() {
            MultivaluedHashMap<String, Object> headers = headersWithContentType();
            AtomicBoolean stripped = new AtomicBoolean(false);

            filter.filter(null, stubResponse(403, stripped, new AtomicReference<>(), headers));

            assertTrue(stripped.get(), "entity was not stripped for 403");
            assertFalse(headers.containsKey("Content-Type"), "Content-Type was not removed for 403");
        }

        @Test
        void status500_entityIsStripped() {
            MultivaluedHashMap<String, Object> headers = headersWithContentType();
            AtomicBoolean stripped = new AtomicBoolean(false);

            filter.filter(null, stubResponse(500, stripped, new AtomicReference<>(), headers));

            assertTrue(stripped.get(), "entity was not stripped for 500");
            assertFalse(headers.containsKey("Content-Type"), "Content-Type was not removed for 500");
        }
    }

    // -------------------------------------------------------------------------
    // Pass-through status codes — entity and Content-Type must not be touched
    // -------------------------------------------------------------------------

    @Nested
    class PassThroughStatusCodes {
        @Test
        void status200_entityIsUntouched() {
            MultivaluedHashMap<String, Object> headers = headersWithContentType();
            AtomicReference<Object> entityRef = new AtomicReference<>("original");

            filter.filter(null, stubResponse(200, new AtomicBoolean(), entityRef, headers));

            assertFalse(entityRef.get() == null, "entity should not be set for 200");
            assertTrue(headers.containsKey("Content-Type"), "Content-Type should be preserved for 200");
        }

        @Test
        void status400_entityIsUntouched() {
            MultivaluedHashMap<String, Object> headers = headersWithContentType();
            AtomicReference<Object> entityRef = new AtomicReference<>("original");

            filter.filter(null, stubResponse(400, new AtomicBoolean(), entityRef, headers));

            assertFalse(entityRef.get() == null, "entity should not be set for 400");
            assertTrue(headers.containsKey("Content-Type"), "Content-Type should be preserved for 400");
        }

        @Test
        void status404_entityIsUntouched() {
            MultivaluedHashMap<String, Object> headers = headersWithContentType();
            AtomicReference<Object> entityRef = new AtomicReference<>("original");

            filter.filter(null, stubResponse(404, new AtomicBoolean(), entityRef, headers));

            assertFalse(entityRef.get() == null, "entity should not be set for 404");
            assertTrue(headers.containsKey("Content-Type"), "Content-Type should be preserved for 404");
        }

        @Test
        void status422_entityIsUntouched() {
            MultivaluedHashMap<String, Object> headers = headersWithContentType();
            AtomicReference<Object> entityRef = new AtomicReference<>("original");

            filter.filter(null, stubResponse(422, new AtomicBoolean(), entityRef, headers));

            assertFalse(entityRef.get() == null, "entity should not be set for 422");
            assertTrue(headers.containsKey("Content-Type"), "Content-Type should be preserved for 422");
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static MultivaluedHashMap<String, Object> headersWithContentType() {
        MultivaluedHashMap<String, Object> headers = new MultivaluedHashMap<>();

        headers.add("Content-Type", "application/json");

        return headers;
    }

    /**
     * Returns a minimal {@link ContainerResponseContext} proxy that:
     * <ul>
     *   <li>{@code getStatus()} returns {@code status}</li>
     *   <li>{@code getHeaders()} returns {@code headers}</li>
     *   <li>{@code setEntity(Object, Annotation[], MediaType)} records whether the entity
     *       was set to {@code null} in {@code entityStripped}, and stores the entity value
     *       in {@code entityRef}</li>
     *   <li>all other methods throw {@link UnsupportedOperationException}</li>
     * </ul>
     * The {@code requestContext} parameter is {@code null} throughout these tests because
     * {@link SecurityResponseFilter} never accesses the request context.
     */
    private static ContainerResponseContext stubResponse(
            int status,
            AtomicBoolean entityStripped,
            AtomicReference<Object> entityRef,
            MultivaluedMap<String, Object> headers
    ) {
        return (ContainerResponseContext) Proxy.newProxyInstance(
                ContainerResponseContext.class.getClassLoader(),
                new Class[]{ContainerResponseContext.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getStatus" -> status;
                    case "getHeaders" -> headers;
                    case "setEntity" -> {
                        entityRef.set(args[0]);
                        entityStripped.set(null == args[0]);
                        yield null;
                    }
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }
}

