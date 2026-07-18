package software.frisby.web.server;

import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpURI;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.Callback;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import jakarta.ws.rs.core.MediaType;
import software.frisby.web.serial.GenericType;
import software.frisby.web.serial.JsonSerializer;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for private static helper methods on {@link DefaultServer} that cannot be
 * exercised through the full integration test stack without unrealistic infrastructure.
 * <p>
 * All calls use reflection — the methods are intentionally package-private in design
 * terms (they are purely internal utilities), but Java visibility prevents direct access
 * from the test class without this indirection.
 */
class DefaultServerUnitTest {

    // -------------------------------------------------------------------------
    // serializeEntityForLog
    // -------------------------------------------------------------------------

    /**
     * Tests for the {@code serializeEntityForLog} private static utility that converts
     * a response entity to a log-safe string.
     * <p>
     * The {@code String} path, the {@code null} entity path, and the normal-serialization
     * path are all exercised indirectly by the existing integration test suite (health-check
     * responses, empty 500 bodies, and serializable POJOs respectively).  Only the
     * {@code InputStream} path and the serialization-failure catch block require targeted
     * unit tests, since producing those conditions through a live server is impractical.
     */
    @Nested
    class SerializeEntityForLog {
        private static final Method METHOD = resolveMethod(
                "serializeEntityForLog",
                Object.class,
                JsonSerializer.class
        );

        private static String invoke(Object entity, JsonSerializer serializer) {
            try {
                return (String) METHOD.invoke(null, entity, serializer);
            } catch (InvocationTargetException e) {
                throw new RuntimeException(e.getCause() != null ? e.getCause() : e);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Test
        void inputStreamEntity_returnsNull() {
            // InputStream entities are binary / streaming and cannot be safely re-read
            // after the response has been committed.  The method must return null so the
            // caller omits the body from the log rather than attempting serialization.
            assertNull(invoke(new ByteArrayInputStream(new byte[0]), new TestJsonSerializer()));
        }

        @Test
        void serializerThrows_returnsEntityClassName() {
            // When the serializer fails (e.g. unregistered type, cyclic reference),
            // the method falls back to "[SimpleClassName]" rather than propagating.
            assertEquals("[Object]", invoke(new Object(), new ThrowingSerializer()));
        }
    }

    // -------------------------------------------------------------------------
    // unwrapJerseyException
    // -------------------------------------------------------------------------

    /**
     * Tests for the {@code unwrapJerseyException} private static utility that strips
     * Jersey's internal {@code MappableException} wrapper from resource-method exceptions.
     * <p>
     * The non-null paths are exercised by every 5xx integration test.  Only the
     * {@code null} guard needs a targeted unit test — Jersey should never pass null to
     * this method (the ON_EXCEPTION event always carries a throwable), but the guard
     * exists as a defensive measure.
     */
    @Nested
    class UnwrapJerseyException {
        private static final Method METHOD = resolveMethod(
                "unwrapJerseyException",
                Throwable.class
        );

        private static Throwable invoke(Throwable t) {
            try {
                return (Throwable) METHOD.invoke(null, t);
            } catch (InvocationTargetException e) {
                throw new RuntimeException(e.getCause() != null ? e.getCause() : e);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Test
        void nullCause_returnsNull() {
            assertNull(invoke(null));
        }
    }

    // -------------------------------------------------------------------------
    // isTextBody
    // -------------------------------------------------------------------------

    /**
     * Unit tests for the {@code isTextBody} private static utility.
     * <p>
     * The {@code application/json} and {@code application/x-www-form-urlencoded} branches
     * are exercised by the existing integration test suite (every JSON POST and form POST
     * goes through {@link DefaultServer.RequestBodyBufferingFilter}).  The missed branches
     * are the remaining {@code text/*} family and the less-common {@code application/*}
     * subtypes ({@code +json}, {@code xml}, {@code +xml}, {@code graphql}).
     */
    @Nested
    class IsTextBody {
        private static final Method METHOD = resolveMethod(
                "isTextBody",
                MediaType.class
        );

        private static boolean invoke(MediaType mediaType) {
            try {
                return (boolean) METHOD.invoke(null, mediaType);
            } catch (InvocationTargetException e) {
                throw new RuntimeException(e.getCause() != null ? e.getCause() : e);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Test
        void textPlain_returnsTrue() {
            assertTrue(invoke(MediaType.valueOf("text/plain")));
        }

        @Test
        void applicationPlusJson_returnsTrue() {
            // e.g. application/ld+json, application/vnd.api+json
            assertTrue(invoke(MediaType.valueOf("application/ld+json")));
        }

        @Test
        void applicationXml_returnsTrue() {
            assertTrue(invoke(MediaType.valueOf("application/xml")));
        }

        @Test
        void applicationPlusXml_returnsTrue() {
            // e.g. application/soap+xml, application/rss+xml
            assertTrue(invoke(MediaType.valueOf("application/soap+xml")));
        }

        @Test
        void applicationGraphql_returnsTrue() {
            assertTrue(invoke(MediaType.valueOf("application/graphql")));
        }
    }

    // -------------------------------------------------------------------------
    // ConcurrencyLimitHandler — handle() edge paths
    // -------------------------------------------------------------------------

    /**
     * Unit tests for {@code ConcurrencyLimitHandler.handle()} paths that require
     * a controlled downstream handler rather than a live HTTP server.
     * <p>
     * Two paths are exercised here:
     * <ul>
     *   <li><strong>releasing.failed()</strong> — the downstream handler calls
     *       {@code callback.failed(t)} after a permit was granted.  Jetty 12 does not
     *       propagate TCP-level failures through the handle callback chain, so this path
     *       is only reachable when the downstream handler explicitly signals failure.</li>
     *   <li><strong>catch(Exception)</strong> — the downstream handler throws synchronously
     *       from {@code handle()}.  The catch block must release the semaphore permit
     *       (which was already acquired before {@code super.handle()} was called) and
     *       rethrow the exception so the caller sees it unchanged.</li>
     * </ul>
     */
    @Nested
    class ConcurrencyLimitHandlerHandle {
        @Test
        void downstreamCallsFailed_releasesSemaphoreAndForwardsThrowable() throws Exception {
            Handler.Wrapper limitHandler = buildLimitHandler(1);

            // Downstream handler that immediately calls callback.failed(t).
            RuntimeException testError = new RuntimeException("downstream failure");

            limitHandler.setHandler(new Handler.Abstract() {
                @Override
                public boolean handle(Request request, Response response, Callback callback) {
                    callback.failed(testError);
                    return true;
                }
            });

            // Track what happens on the original callback.
            AtomicReference<Throwable> capturedFailure = new AtomicReference<>();

            Callback original = new Callback() {
                @Override
                public void succeeded() {
                    fail("succeeded() should not be called");
                }

                @Override
                public void failed(Throwable t) {
                    capturedFailure.set(t);
                }
            };

            // Invoke handle() — permit acquired, downstream fails, releasing.failed() fires.
            boolean handled = limitHandler.handle(null, null, original);

            assertTrue(handled);

            // releasing.failed() must have forwarded the throwable to the original callback.
            assertSame(testError, capturedFailure.get(),
                    "releasing.failed() must forward the throwable to the original callback");

            assertPermitCount(limitHandler, 1,
                    "releasing.failed() must release the semaphore permit");
        }

        /**
         * The downstream handler throws synchronously from {@code handle()}.
         * <p>
         * {@code ConcurrencyLimitHandler} acquires the semaphore permit before calling
         * {@code super.handle()}.  If {@code super.handle()} throws, the permit would
         * be permanently leaked unless the {@code catch(Exception)} block releases it.
         * This test verifies that the catch block releases the permit and rethrows the
         * original exception unchanged.
         */
        @Test
        void downstreamThrows_releasesSemaphoreAndRethrows() throws Exception {
            Handler.Wrapper limitHandler = buildLimitHandler(1);

            RuntimeException testError = new RuntimeException("downstream threw");

            limitHandler.setHandler(new Handler.Abstract() {
                @Override
                public boolean handle(Request request, Response response, Callback callback)
                        throws Exception {
                    throw testError;
                }
            });

            Callback original = new Callback() {
                @Override
                public void succeeded() {
                    fail("succeeded() should not be called");
                }

                @Override
                public void failed(Throwable t) {
                    fail("failed() should not be called — exception is rethrown, not forwarded");
                }
            };

            // handle() must rethrow the downstream exception.
            Exception thrown = assertThrows(
                    RuntimeException.class,
                    () -> limitHandler.handle(null, null, original)
            );

            assertSame(testError, thrown, "must rethrow the original exception unchanged");

            assertPermitCount(limitHandler, 1,
                    "catch block must release the semaphore permit so capacity is not permanently lost");
        }

        /**
         * The downstream handler returns {@code false}, signalling that no handler
         * claimed the request.
         * <p>
         * When {@code super.handle()} returns {@code false} the {@code releasing} wrapper
         * callback is never handed to a handler, so the permit would be permanently
         * leaked unless the {@code if (!handled)} block releases it.  The original
         * callback must not be invoked — the framework will use it directly when no
         * handler claims the request.
         */
        @Test
        void downstreamReturnsNotHandled_releasesSemaphore() throws Exception {
            Handler.Wrapper limitHandler = buildLimitHandler(1);

            limitHandler.setHandler(new Handler.Abstract() {
                @Override
                public boolean handle(Request request, Response response, Callback callback) {
                    return false;
                }
            });

            Callback original = new Callback() {
                @Override
                public void succeeded() {
                    fail("succeeded() should not be called when request is unhandled");
                }

                @Override
                public void failed(Throwable t) {
                    fail("failed() should not be called when request is unhandled");
                }
            };

            boolean handled = limitHandler.handle(null, null, original);

            assertFalse(handled);

            assertPermitCount(limitHandler, 1,
                    "if (!handled) block must release the semaphore permit");
        }

        // -------------------------------------------------------------------------
        // Shared helpers
        // -------------------------------------------------------------------------

        private static Handler.Wrapper buildLimitHandler(int maxConcurrent) throws Exception {
            Class<?> handlerClass = Arrays.stream(DefaultServer.class.getDeclaredClasses())
                    .filter(c -> c.getSimpleName().equals("ConcurrencyLimitHandler"))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("ConcurrencyLimitHandler not found"));

            Constructor<?> ctor = handlerClass.getDeclaredConstructor(
                    java.util.concurrent.Semaphore.class,
                    java.util.concurrent.atomic.AtomicBoolean.class,
                    RequestLogger.class,
                    software.frisby.web.server.event.ServerEventListener.class,
                    String.class
            );
            ctor.setAccessible(true);

            return (Handler.Wrapper) ctor.newInstance(
                    new java.util.concurrent.Semaphore(maxConcurrent),
                    new java.util.concurrent.atomic.AtomicBoolean(false),
                    new RequestLogger(),
                    NoOpServerEventListener.INSTANCE,
                    null
            );
        }

        private static void assertPermitCount(Handler.Wrapper limitHandler,
                                              int expected,
                                              String message) throws Exception {
            Class<?> handlerClass = limitHandler.getClass();
            Field semaphoreField = handlerClass.getDeclaredField("semaphore");
            semaphoreField.setAccessible(true);
            Semaphore semaphore = (Semaphore) semaphoreField.get(limitHandler);
            assertEquals(expected, semaphore.availablePermits(), message);
        }
    }

    // -------------------------------------------------------------------------
    // ConcurrencyLimitHandler — 503-write Callback.failed() path
    // -------------------------------------------------------------------------

    /**
     * Covers the {@code failed()} method of the anonymous {@code Callback} passed to
     * {@code response.write()} in the capacity-rejection (503) path.
     * <p>
     * In normal operation this callback's {@code succeeded()} fires after the 503 body
     * is written successfully; the {@code failed()} path fires only if the write itself
     * fails (e.g. the client disconnects before the 97-byte response is delivered).
     * Writing a 97-byte response completes in a single TCP segment, so there is no
     * reliable timing window to interrupt it in a real HTTP test.
     * <p>
     * Instead, a {@link Proxy} implementation of {@link Response} is used: when
     * {@code response.write()} is called, the proxy immediately calls
     * {@code callback.failed(t)} on the provided {@code Callback}.  This drives the
     * code path without requiring a real server or a network fault.
     * <p>
     * Note: both {@code succeeded()} and {@code failed()} call the same
     * {@code logAndFire()} method — there is no additional error-specific log entry.
     * The throwable is forwarded to the original {@code Callback.failed(t)} for Jetty
     * to handle at the connection layer.
     */
    @Nested
    class WriteCallbackFailed {
        @Test
        void writeFailure_logsCapacityRejectionAndForwardsThrowable() throws Exception {
            // maxConcurrent=0 means tryAcquire() always returns false — every request
            // goes directly to the 503 path without needing to exhaust any permits.
            Handler.Wrapper limitHandler = buildLimitHandler(0);

            RuntimeException writeError = new RuntimeException("write failure");

            // Minimal proxy for Request — only getMethod() and getHttpURI() are called.
            HttpURI httpUri = HttpURI.from("http://localhost/test");

            Request mockRequest = (Request) Proxy.newProxyInstance(
                    getClass().getClassLoader(),
                    new Class[]{Request.class},
                    (proxy, m, args) -> switch (m.getName()) {
                        case "getMethod" -> "GET";
                        case "getHttpURI" -> httpUri;
                        default -> null;
                    }
            );

            // Minimal proxy for Response — setStatus() and getHeaders() are no-ops;
            // write() immediately calls callback.failed(writeError) to drive the
            // uncovered failed() path in the anonymous write-completion Callback.
            Response mockResponse = (Response) Proxy.newProxyInstance(
                    getClass().getClassLoader(),
                    new Class[]{Response.class},
                    (proxy, m, args) -> {
                        switch (m.getName()) {
                            case "setStatus" -> {
                                return null;
                            }
                            case "getHeaders" -> {
                                return HttpFields.build();
                            }
                            case "write" -> {
                                // args[2] is the Callback passed to response.write() —
                                // invoke failed() on it to simulate a write failure.
                                ((Callback) args[2]).failed(writeError);
                                return null;
                            }
                        }

                        return null;
                    }
            );

            AtomicReference<Throwable> capturedFailure = new AtomicReference<>();

            Callback original = new Callback() {
                @Override
                public void succeeded() {
                    fail("succeeded() should not be called on a write failure");
                }

                @Override
                public void failed(Throwable t) {
                    capturedFailure.set(t);
                }
            };

            boolean handled = limitHandler.handle(mockRequest, mockResponse, original);

            assertTrue(handled, "503 path always returns true");

            // The write error must be forwarded to the original callback.
            assertSame(writeError, capturedFailure.get(),
                    "write Callback.failed() must forward the throwable to the original callback");
        }

        private static Handler.Wrapper buildLimitHandler(int maxConcurrent) throws Exception {
            Class<?> handlerClass = Arrays.stream(DefaultServer.class.getDeclaredClasses())
                    .filter(c -> c.getSimpleName().equals("ConcurrencyLimitHandler"))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("ConcurrencyLimitHandler not found"));

            Constructor<?> ctor = handlerClass.getDeclaredConstructor(
                    java.util.concurrent.Semaphore.class,
                    java.util.concurrent.atomic.AtomicBoolean.class,
                    RequestLogger.class,
                    software.frisby.web.server.event.ServerEventListener.class,
                    String.class
            );
            ctor.setAccessible(true);

            return (Handler.Wrapper) ctor.newInstance(
                    new java.util.concurrent.Semaphore(maxConcurrent),
                    new java.util.concurrent.atomic.AtomicBoolean(false),
                    new RequestLogger(),
                    NoOpServerEventListener.INSTANCE,
                    null
            );
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** A {@link JsonSerializer} whose {@code serialize()} always throws. */
    private static final class ThrowingSerializer implements JsonSerializer {
        @Override
        public byte[] serialize(Object value) {
            throw new RuntimeException("Simulated serialization failure");
        }

        @Override
        public <T> T deserialize(byte[] content, Class<T> type) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> T deserialize(byte[] content, GenericType<T> genericType) {
            throw new UnsupportedOperationException();
        }
    }

    private static Method resolveMethod(String name, Class<?>... paramTypes) {
        try {
            Method m = DefaultServer.class.getDeclaredMethod(name, paramTypes);
            m.setAccessible(true);
            return m;
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("Could not find DefaultServer." + name, e);
        }
    }
}






