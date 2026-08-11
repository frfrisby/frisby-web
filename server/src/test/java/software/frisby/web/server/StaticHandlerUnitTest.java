package software.frisby.web.server;

import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.http.HttpURI;
import org.eclipse.jetty.server.Context;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.resource.Resource;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import software.frisby.core.util.StopWatch;
import software.frisby.web.server.event.RequestCompletedEvent;
import software.frisby.web.server.event.ServerEventListener;
import software.frisby.web.test.log.LogExpectation;
import software.frisby.web.test.log.SystemLogVerifier;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.*;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for package-private static helper methods on {@link StaticHandler} that cannot
 * be exercised through the full integration test stack without unrealistic infrastructure.
 */
class StaticHandlerUnitTest {

    // -------------------------------------------------------------------------
    // writeErrorIfNotServed
    // -------------------------------------------------------------------------

    private static Method resolveMethod(String name, Class<?>... paramTypes) {
        try {
            Method m = StaticHandler.class.getDeclaredMethod(name, paramTypes);
            m.setAccessible(true);
            return m;
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("Could not find StaticHandler." + name, e);
        }
    }

    // -------------------------------------------------------------------------
    // resourceExists
    // -------------------------------------------------------------------------

    /**
     * Minimal concrete {@link Resource} subclass whose {@link #resolve} always returns
     * {@code null}.  Used to cover the {@code null == resource} defensive guard in
     * {@code StaticHandler.resourceExists()}.
     */
    private static final class NullResolvingResource extends Resource {
        @Override
        public Path getPath() {
            return null;
        }

        @Override
        public boolean isDirectory() {
            return true;
        }

        @Override
        public boolean isReadable() {
            return false;
        }

        @Override
        public URI getURI() {
            return null;
        }

        @Override
        public String getName() {
            return "";
        }

        @Override
        public String getFileName() {
            return "";
        }

        @Override
        public Resource resolve(String subUriPath) {
            return null;
        }

        @Override
        public Iterator<Resource> iterator() {
            return Collections.emptyIterator();
        }
    }

    // -------------------------------------------------------------------------
    // serveErrorPage
    // -------------------------------------------------------------------------

    /**
     * Minimal concrete {@link Resource} subclass that reports itself as an existing,
     * non-readable, non-directory resource (e.g. a broken symlink or special file).
     * {@link #resolve} returns {@code this}, so {@code baseResource.resolve(path)}
     * produces the same stub.  Used to cover the {@code return false} branch at the
     * end of {@code StaticHandler.resourceExists()} when {@code isDirectory()} is
     * {@code false}.
     */
    private static final class ExistingNonDirectoryResource extends Resource {
        @Override
        public boolean exists() {
            return true;
        }

        @Override
        public Path getPath() {
            return null;
        }

        @Override
        public boolean isDirectory() {
            return false;
        }

        @Override
        public boolean isReadable() {
            return false;
        }

        @Override
        public URI getURI() {
            return null;
        }

        @Override
        public String getName() {
            return "";
        }

        @Override
        public String getFileName() {
            return "";
        }

        @Override
        public Resource resolve(String subUriPath) {
            return this;
        }

        @Override
        public Iterator<Resource> iterator() {
            return Collections.emptyIterator();
        }
    }

    // -------------------------------------------------------------------------
    // isDotfilePath
    // -------------------------------------------------------------------------

    /**
     * Minimal concrete {@link Resource} subclass that reports itself as an existing,
     * readable, non-directory resource and returns an in-memory stream from
     * {@link #newInputStream()}.  {@link #resolve} returns {@code this}, so
     * {@code baseResource.resolve(notFoundPath)} yields the same stub.
     * <p>
     * Used to drive {@code StaticHandler.serveNotFoundPage()} past the
     * {@code Resources.isReadableFile()} guard and into the
     * {@code response.write()} call, where a proxy {@link Response} can then
     * throw to trigger the {@code catch} block.
     */
    private static final class ReadableInMemoryResource extends Resource {
        private final byte[] content;

        private ReadableInMemoryResource(String content) {
            this.content = content.getBytes();
        }

        @Override
        public boolean exists() {
            return true;
        }

        @Override
        public Path getPath() {
            return null;
        }

        @Override
        public boolean isDirectory() {
            return false;
        }

        @Override
        public boolean isReadable() {
            return true;
        }

        @Override
        public URI getURI() {
            return null;
        }

        @Override
        public String getName() {
            return "";
        }

        @Override
        public String getFileName() {
            return "";
        }

        @Override
        public InputStream newInputStream() {
            return new ByteArrayInputStream(content);
        }

        @Override
        public Resource resolve(String subUriPath) {
            return this;
        }

        @Override
        public Iterator<Resource> iterator() {
            return Collections.emptyIterator();
        }
    }

    // -------------------------------------------------------------------------
    // hasFileExtension
    // -------------------------------------------------------------------------

    /**
     * Tests for {@link StaticHandler#writeErrorIfNotServed}.
     *
     * <p>The {@code served = true} branch (no-op) and the normal resource-serving path
     * are covered by the integration tests in {@link ServerStaticAssetsTest}.  The
     * {@code served = false} branch represents a file-disappearance race condition that
     * cannot be triggered reliably from integration tests without flaky timing-dependent
     * setup.  The extracted static method allows it to be driven directly here.
     *
     * <p>Both tests use a {@link Proxy}-based {@link Request} and {@link Response} to
     * satisfy the Jetty objects required by {@link Response#writeError}, without needing
     * a real embedded server.  The proxy handles exactly the methods that
     * {@code Response.writeError} calls in Jetty 12.0.x:
     * {@code isCommitted()}, {@code consumeAvailable()}, {@code setStatus()},
     * {@code getContext()} (returns {@code null} to bypass the error-handler path),
     * {@code getHeaders()}, and {@code write()}.
     */
    @Nested
    class WriteErrorIfNotServed {

        @Test
        void served_doesNothing() throws Exception {
            AtomicBoolean callbackCalled = new AtomicBoolean(false);
            AtomicBoolean statusSet = new AtomicBoolean(false);

            Request mockRequest = mockRequest();

            Response mockResponse = (Response) Proxy.newProxyInstance(
                    getClass().getClassLoader(),
                    new Class[]{Response.class},
                    (proxy, method, args) -> {
                        switch (method.getName()) {
                            case "isCommitted" -> {
                                return false;
                            }
                            case "setStatus" -> {
                                statusSet.set(true);
                                return null;
                            }
                            case "getHeaders" -> {
                                return HttpFields.build();
                            }
                            case "write" -> {
                                callbackCalled.set(true);
                                ((Callback) args[2]).succeeded();
                                return null;
                            }
                        }

                        return null;
                    }
            );

            Callback noOpCallback = new Callback() {
                @Override
                public void failed(Throwable t) {
                    fail("callback.failed() should not be called when served=true");
                }
            };

            StaticHandler.writeErrorIfNotServed(true, mockRequest, mockResponse, noOpCallback);

            assertFalse(statusSet.get(), "setStatus() must not be called when served=true");
            assertFalse(callbackCalled.get(), "write() must not be called when served=true");
        }

        @Test
        void notServed_writes404AndCallsCallback() throws Exception {
            AtomicInteger capturedStatus = new AtomicInteger(-1);
            AtomicBoolean callbackCalled = new AtomicBoolean(false);

            Request mockRequest = mockRequest();

            Response mockResponse = (Response) Proxy.newProxyInstance(
                    getClass().getClassLoader(),
                    new Class[]{Response.class},
                    (proxy, method, args) -> {
                        switch (method.getName()) {
                            case "isCommitted", "consumeAvailable" -> {
                                return false;
                            }
                            case "setStatus" -> {
                                capturedStatus.set((Integer) args[0]);
                                return null;
                            }
                            case "getHeaders" -> {
                                return HttpFields.build();
                            }
                            case "write" -> {
                                callbackCalled.set(true);
                                ((Callback) args[2]).succeeded();
                                return null;
                            }
                        }

                        return null;
                    }
            );

            Callback noOpCallback = new Callback() {
                @Override
                public void failed(Throwable t) {
                    fail("callback.failed() should not be called: " + t);
                }
            };

            StaticHandler.writeErrorIfNotServed(false, mockRequest, mockResponse, noOpCallback);

            assertEquals(HttpStatus.NOT_FOUND_404, capturedStatus.get(),
                    "response status must be 404 Not Found");
            assertTrue(callbackCalled.get(), "write() must be called to complete the response");
        }

        // -------------------------------------------------------------------------
        // Shared helpers
        // -------------------------------------------------------------------------

        /**
         * Returns a minimal proxy {@link Request} that satisfies the calls made by
         * {@link Response#writeError} in Jetty 12.0.x:
         * <ul>
         *   <li>{@code getMethod()} — returns {@code "GET"} (used in log output)</li>
         *   <li>{@code getHttpURI()} — returns a stable URI (used in log output)</li>
         *   <li>{@code consumeAvailable()} — returns {@code false} (return value discarded)</li>
         *   <li>{@code getContext()} — returns a proxy {@link Context} whose
         *       {@code getErrorHandler()} returns {@code null}, so that
         *       {@link Response#writeError} skips the error-handler path and falls
         *       through directly to {@code response.write()}</li>
         *   <li>{@code setAttribute()} — no-op</li>
         * </ul>
         */
        private Request mockRequest() {
            HttpURI httpUri = HttpURI.from("http://localhost/test.txt");

            // Proxy Context whose getErrorHandler() returns null — this causes
            // Response.writeError to skip the error-handler path and fall through
            // directly to response.write().  Jetty 12.0.x does NOT null-check the
            // Context before calling getErrorHandler(), so we must return a real
            // (proxy) Context rather than null.
            Context mockContext = (Context) Proxy.newProxyInstance(
                    getClass().getClassLoader(),
                    new Class[]{Context.class},
                    (proxy, method, args) -> null
            );

            return (Request) Proxy.newProxyInstance(
                    getClass().getClassLoader(),
                    new Class[]{Request.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "getMethod" -> "GET";
                        case "getHttpURI" -> httpUri;
                        case "consumeAvailable" -> false;
                        case "getContext" -> mockContext;
                        case "setAttribute" -> null;
                        default -> null;
                    }
            );
        }
    }

    // -------------------------------------------------------------------------
    // EventFiringCallback — failed() path
    // -------------------------------------------------------------------------

    /**
     * Tests for the {@code resourceExists} private instance method.
     *
     * <p>The normal (non-null {@code baseResource}) path is exercised by every
     * content-serving and 404 integration test.  The {@code null == baseResource}
     * guard is defensive dead code — it is only reachable if {@code handle()} were
     * somehow called before {@code doStart()} sets the field.  It can only be driven
     * by constructing a {@link StaticHandler} without starting it and invoking the
     * method directly via reflection.
     *
     * <p>The {@code null == resource} guard (returned by {@code baseResource.resolve()})
     * is similarly defensive — Jetty never returns {@code null} from {@code resolve()}
     * for valid classpath roots.  It is tested by injecting a {@link NullResolvingResource}
     * stub via reflection.
     */
    @Nested
    class ResourceExists {
        @Test
        void baseResourceNull_returnsFalse() throws Exception {
            StaticAssetsConfiguration config = StaticAssetsConfiguration
                    .classpath("/static-test-assets")
                    .build();

            // Construct but do NOT start — baseResource stays null.
            StaticHandler handler = new StaticHandler(config, NoOpServerEventListener.INSTANCE, new RequestLogger(), Set.of());

            Method method = StaticHandler.class.getDeclaredMethod("resourceExists", String.class);
            method.setAccessible(true);

            boolean result = (boolean) method.invoke(handler, "/index.html");

            assertFalse(result);
        }

        @Test
        void resolveReturnsNull_returnsFalse() throws Exception {
            StaticAssetsConfiguration config = StaticAssetsConfiguration
                    .classpath("/static-test-assets")
                    .build();

            StaticHandler handler = new StaticHandler(config, NoOpServerEventListener.INSTANCE, new RequestLogger(), Set.of());

            // Inject a NullResolvingResource as baseResource so that resolve() returns null.
            Field baseResourceField = StaticHandler.class.getDeclaredField("baseResource");
            baseResourceField.setAccessible(true);
            baseResourceField.set(handler, new NullResolvingResource());

            Method method = StaticHandler.class.getDeclaredMethod("resourceExists", String.class);
            method.setAccessible(true);

            boolean result = (boolean) method.invoke(handler, "/index.html");

            assertFalse(result);
        }

        @Test
        void resolveReturnsExistingNonDirectory_returnsFalse() throws Exception {
            StaticAssetsConfiguration config = StaticAssetsConfiguration
                    .classpath("/static-test-assets")
                    .build();

            StaticHandler handler = new StaticHandler(config, NoOpServerEventListener.INSTANCE, new RequestLogger(), Set.of());

            // ExistingNonDirectoryResource.resolve() returns itself: exists=true,
            // isReadable=false, isDirectory=false.  This exercises the final
            // return false branch — the resource exists but is neither a readable
            // file nor a directory (e.g. a broken symlink or special file).
            Field baseResourceField = StaticHandler.class.getDeclaredField("baseResource");
            baseResourceField.setAccessible(true);
            baseResourceField.set(handler, new ExistingNonDirectoryResource());

            Method method = StaticHandler.class.getDeclaredMethod("resourceExists", String.class);
            method.setAccessible(true);

            boolean result = (boolean) method.invoke(handler, "/anything");

            assertFalse(result);
        }
    }

    // -------------------------------------------------------------------------
    // Auth filter — exception + committed response
    // -------------------------------------------------------------------------

    /**
     * Tests for {@link StaticHandler#serveErrorPage}.
     *
     * <p>The normal happy path (readable file served with the configured status) is covered by
     * the {@link ServerStaticAssetsTest} integration tests.  The {@code catch} block —
     * triggered when {@code response.write()} throws — can only be reached in a live
     * server if the I/O layer fails mid-response.  It is exercised here by calling the
     * package-private method directly with a proxy {@link Response} whose {@code write()}
     * method throws a {@link RuntimeException}.
     *
     * <p>A {@link ReadableInMemoryResource} stub is injected as {@code baseResource} so
     * that {@code Resources.isReadableFile()} passes and the code reaches
     * {@code response.write()} before the simulated failure.
     */
    @Nested
    class ServeErrorPage {
        @Test
        void writeThrows_callsCallbackFailed() throws Exception {
            StaticAssetsConfiguration config = StaticAssetsConfiguration
                    .classpath("/static-test-assets")
                    .errorPage(404, "404.html")
                    .build();

            StaticHandler handler = new StaticHandler(config, NoOpServerEventListener.INSTANCE, new RequestLogger(), Set.of());

            Field baseResourceField = StaticHandler.class.getDeclaredField("baseResource");
            baseResourceField.setAccessible(true);
            baseResourceField.set(handler, new ReadableInMemoryResource("custom 404 body"));

            RuntimeException writeError = new RuntimeException("simulated write failure");

            Response mockResponse = (Response) Proxy.newProxyInstance(
                    getClass().getClassLoader(),
                    new Class[]{Response.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "setStatus", "reset" -> null;
                        case "getHeaders" -> HttpFields.build();
                        case "write" -> throw writeError;
                        default -> null;
                    }
            );

            AtomicReference<Throwable> capturedFailure = new AtomicReference<>();

            Callback callback = new Callback() {
                @Override
                public void succeeded() {
                    fail("succeeded() must not be called when write throws");
                }

                @Override
                public void failed(Throwable t) {
                    capturedFailure.set(t);
                }
            };

            handler.serveErrorPage(404, "/missing", null, mockResponse, callback);

            assertSame(writeError, capturedFailure.get(),
                    "callback.failed() must receive the exception thrown by response.write()");
        }
    }

    // -------------------------------------------------------------------------
    // Auth filter — rejection + no matching error page
    // -------------------------------------------------------------------------

    /**
     * Tests for the {@code isDotfilePath} private static utility.
     *
     * <p>The slash-present path (e.g. {@code /.env}) is exercised by the dotfile
     * integration tests in {@link ServerStaticAssetsTest}.  The slash-absent branch
     * ({@code lastSlash < 0}) is defensive dead code that Jetty never produces, so it
     * can only be reached via a direct call with a slash-free string.
     */
    @Nested
    class IsDotfilePath {
        private static final Method METHOD = resolveMethod("isDotfilePath", String.class);

        private static boolean invoke(String path) {
            try {
                return (boolean) METHOD.invoke(null, path);
            } catch (InvocationTargetException e) {
                throw new RuntimeException(e.getCause() != null ? e.getCause() : e);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Test
        void noSlash_dotFile_returnsTrue() {
            // lastSlash = -1 → lastSegment = entire path → starts with "."
            assertTrue(invoke(".env"));
        }

        @Test
        void noSlash_normalFile_returnsFalse() {
            assertFalse(invoke("file.txt"));
        }
    }

    /**
     * Tests for the {@code hasFileExtension} private static utility.
     *
     * <p>The slash-present path (e.g. {@code /missing.png}) is exercised by the SPA
     * fallback integration tests in {@link ServerStaticAssetsTest}.  The slash-absent
     * branch ({@code lastSlash < 0}) is defensive dead code that Jetty never produces,
     * so it can only be reached via a direct call with a slash-free string.
     */
    @Nested
    class HasFileExtension {
        private static final Method METHOD = resolveMethod("hasFileExtension", String.class);

        private static boolean invoke(String path) {
            try {
                return (boolean) METHOD.invoke(null, path);
            } catch (InvocationTargetException e) {
                throw new RuntimeException(e.getCause() != null ? e.getCause() : e);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Test
        void noSlash_withExtension_returnsTrue() {
            // lastSlash = -1 → lastSegment = entire path → "file.txt" has extension
            assertTrue(invoke("file.txt"));
        }

        @Test
        void noSlash_noExtension_returnsFalse() {
            assertFalse(invoke("route"));
        }

        @Test
        void noSlash_trailingDot_returnsFalse() {
            // lastDot > 0 is true (dot is present and not first char),
            // but lastDot == lastSegment.length() - 1 (dot is last char) → false.
            assertFalse(invoke("file."));
        }
    }

    /**
     * Tests for the {@code failed()} method of {@link StaticHandler}'s private
     * {@code EventFiringCallback} inner class.
     *
     * <p>The {@code succeeded()} path is exercised by every integration test that
     * serves a static asset successfully.  The {@code failed()} path fires when Jetty's
     * async response writing fails (e.g. client disconnect mid-response) and cannot
     * be triggered reliably from an integration test without flaky timing-dependent
     * setup.  The callback class is accessed via reflection — the same approach used
     * in {@link DefaultServerUnitTest} for {@code ConcurrencyLimitHandler}.
     */
    @Nested
    class EventFiringCallbackFailed {
        @Test
        void failed_stopsWatchFiresEventAndForwardsThrowable() throws Exception {
            Class<?> callbackClass = Arrays.stream(StaticHandler.class.getDeclaredClasses())
                    .filter(c -> c.getSimpleName().equals("EventFiringCallback"))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("EventFiringCallback not found"));

            Constructor<?> ctor = callbackClass.getDeclaredConstructor(
                    Callback.class,
                    StopWatch.class,
                    String.class,
                    String.class,
                    Request.class,
                    Response.class,
                    ServerEventListener.class,
                    RequestLogger.class,
                    Set.class
            );
            ctor.setAccessible(true);

            AtomicReference<Throwable> capturedDelegateFailure = new AtomicReference<>();
            AtomicReference<RequestCompletedEvent> capturedEvent = new AtomicReference<>();

            Callback delegate = new Callback() {
                @Override
                public void succeeded() {
                    fail("succeeded() must not be called");
                }

                @Override
                public void failed(Throwable t) {
                    capturedDelegateFailure.set(t);
                }
            };

            Request mockRequest = (Request) Proxy.newProxyInstance(
                    getClass().getClassLoader(),
                    new Class[]{Request.class},
                    (proxy, method, args) -> null
            );

            Response mockResponse = (Response) Proxy.newProxyInstance(
                    getClass().getClassLoader(),
                    new Class[]{Response.class},
                    (proxy, method, args) -> "getStatus".equals(method.getName()) ? 500 : null
            );

            ServerEventListener capturingListener = capturedEvent::set;

            Callback eventCallback = (Callback) ctor.newInstance(
                    delegate,
                    StopWatch.start(),
                    "GET",
                    "/missing",
                    mockRequest,
                    mockResponse,
                    capturingListener,
                    new RequestLogger(),
                    Set.of()
            );

            RuntimeException testError = new RuntimeException("downstream failure");

            eventCallback.failed(testError);

            assertSame(testError, capturedDelegateFailure.get(),
                    "failed() must forward the throwable to the delegate callback");
            assertNotNull(capturedEvent.get(),
                    "failed() must fire a RequestCompletedEvent");
            assertEquals(500, capturedEvent.get().statusCode(),
                    "event status must reflect the response status at failure time");
        }

        @Test
        void listenerThrows_exceptionSwallowedAndDelegateStillCalled() throws Exception {
            try (SystemLogVerifier verifier = SystemLogVerifier.builder()
                    .expect(LogExpectation.builder()
                            .logger(StaticHandler.class)
                            .level(System.Logger.Level.WARNING)
                            .predicate(e -> e.message()
                                    .contains("ServerEventListener.onRequestCompleted threw an exception."))
                            .build()
                    )
                    .build()) {
                Class<?> callbackClass = Arrays.stream(StaticHandler.class.getDeclaredClasses())
                        .filter(c -> c.getSimpleName().equals("EventFiringCallback"))
                        .findFirst()
                        .orElseThrow(() -> new AssertionError("EventFiringCallback not found"));

                Constructor<?> ctor = callbackClass.getDeclaredConstructor(
                        Callback.class,
                        StopWatch.class,
                        String.class,
                        String.class,
                        Request.class,
                        Response.class,
                        ServerEventListener.class,
                        RequestLogger.class,
                        Set.class
                );
                ctor.setAccessible(true);

                AtomicBoolean delegateCalled = new AtomicBoolean(false);

                Callback delegate = new Callback() {
                    @Override
                    public void succeeded() {
                        delegateCalled.set(true);
                    }
                };

                Request mockRequest = (Request) Proxy.newProxyInstance(
                        getClass().getClassLoader(),
                        new Class[]{Request.class},
                        (proxy, method, args) -> null
                );

                Response mockResponse = (Response) Proxy.newProxyInstance(
                        getClass().getClassLoader(),
                        new Class[]{Response.class},
                        (proxy, method, args) -> "getStatus".equals(method.getName()) ? 200 : null
                );

                ServerEventListener throwingListener = event -> {
                    throw new RuntimeException("listener failure");
                };

                Callback eventCallback = (Callback) ctor.newInstance(
                        delegate,
                        StopWatch.start(),
                        "GET",
                        "/index.html",
                        mockRequest,
                        mockResponse,
                        throwingListener,
                        new RequestLogger(),
                        Set.of()
                );

                // Must not throw — listener exception is caught and logged internally.
                eventCallback.succeeded();

                assertTrue(delegateCalled.get(),
                        "delegate.succeeded() must still be called even when the listener throws");

                verifier.assertExpectations(Duration.ofSeconds(2));
                assertTrue(verifier.warningCount() > 0);
            }
        }
    }

    /**
     * Tests the branch in the auth filter {@code catch} block where
     * {@code response.isCommitted()} is {@code true} at the time the exception is
     * caught.
     *
     * <p>This represents the edge case where the auth filter managed to start writing a
     * response before throwing — e.g. it wrote headers then encountered an I/O error.
     * In that scenario the handler must not attempt to write another response; it should
     * just call {@code eventCallback.succeeded()} and return {@code true}.
     *
     * <p>The test drives {@link StaticHandler#handle} directly with proxy
     * {@link Request} and {@link Response} objects, skipping the embedded-server stack.
     * The proxy {@link Request} satisfies the minimum contract required by
     * {@code handle()}: {@code getHttpURI()} (prefix check + EventFiringCallback path),
     * and {@code getMethod()} (EventFiringCallback).  The proxy {@link Response} returns
     * {@code true} from {@code isCommitted()} and a stable {@code 500} from
     * {@code getStatus()} so the {@link software.frisby.web.server.event.RequestCompletedEvent}
     * can be constructed.
     */
    @Nested
    class AuthFilterExceptionCommittedResponse {
        @Test
        void filterThrows_responseAlreadyCommitted_callsSucceededWithoutWriting() throws Exception {
            StaticAssetsConfiguration config = StaticAssetsConfiguration
                    .classpath("/static-test-assets")
                    .authFilter((req, res) -> {
                        throw new RuntimeException("backend unreachable");
                    })
                    .build();

            StaticHandler handler = new StaticHandler(config, NoOpServerEventListener.INSTANCE, new RequestLogger(), Set.of());

            HttpURI uri = HttpURI.from("http://localhost/index.html");

            Request mockRequest = (Request) Proxy.newProxyInstance(
                    getClass().getClassLoader(),
                    new Class[]{Request.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "getHttpURI" -> uri;
                        case "getMethod" -> "GET";
                        case "consumeAvailable" -> false;
                        default -> null;
                    }
            );

            AtomicBoolean writeCalled = new AtomicBoolean(false);
            AtomicBoolean succeededCalled = new AtomicBoolean(false);

            Response mockResponse = (Response) Proxy.newProxyInstance(
                    getClass().getClassLoader(),
                    new Class[]{Response.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "isCommitted" -> true;
                        case "getStatus" -> 500;
                        case "write" -> {
                            writeCalled.set(true);
                            yield null;
                        }
                        default -> null;
                    }
            );

            Callback delegate = new Callback() {
                @Override
                public void succeeded() {
                    succeededCalled.set(true);
                }

                @Override
                public void failed(Throwable t) {
                    fail("failed() must not be called when response is already committed: " + t);
                }
            };

            boolean handled = handler.handle(mockRequest, mockResponse, delegate);

            assertTrue(handled, "handle() must return true — the handler claimed the request");
            assertTrue(succeededCalled.get(), "delegate.succeeded() must be called");
            assertFalse(writeCalled.get(), "response.write() must not be called on a committed response");
        }
    }

    /**
     * Tests the branch in the auth filter rejection block where the filter returns
     * {@code false}, the response is <em>not</em> committed, but no error page has been
     * configured for the response status code — so
     * {@code configuration.errorPages().containsKey(status)} is {@code false}.
     *
     * <p>In this case the handler must complete the response lifecycle by calling
     * {@code eventCallback.succeeded()} without writing anything, leaving whatever
     * the filter wrote to the response intact (typically just a status code).
     */
    @Nested
    class AuthFilterRejectionNoErrorPage {
        @Test
        void filterReturnsFalse_notCommitted_noErrorPage_callsSucceededWithoutWriting() throws Exception {
            // No error pages configured — containsKey(401) will be false.
            StaticAssetsConfiguration config = StaticAssetsConfiguration
                    .classpath("/static-test-assets")
                    .authFilter((req, res) -> {
                        res.setStatus(401);
                        return false;
                    })
                    .build();

            StaticHandler handler = new StaticHandler(config, NoOpServerEventListener.INSTANCE, new RequestLogger(), Set.of());

            HttpURI uri = HttpURI.from("http://localhost/index.html");

            Request mockRequest = (Request) Proxy.newProxyInstance(
                    getClass().getClassLoader(),
                    new Class[]{Request.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "getHttpURI" -> uri;
                        case "getMethod" -> "GET";
                        default -> null;
                    }
            );

            AtomicBoolean writeCalled = new AtomicBoolean(false);
            AtomicBoolean succeededCalled = new AtomicBoolean(false);

            Response mockResponse = (Response) Proxy.newProxyInstance(
                    getClass().getClassLoader(),
                    new Class[]{Response.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "isCommitted" -> false;
                        case "getStatus" -> 401;
                        case "setStatus" -> null;
                        case "write" -> {
                            writeCalled.set(true);
                            yield null;
                        }
                        default -> null;
                    }
            );

            Callback delegate = new Callback() {
                @Override
                public void succeeded() {
                    succeededCalled.set(true);
                }

                @Override
                public void failed(Throwable t) {
                    fail("failed() must not be called: " + t);
                }
            };

            boolean handled = handler.handle(mockRequest, mockResponse, delegate);

            assertTrue(handled, "handle() must return true — the handler claimed the request");
            assertTrue(succeededCalled.get(), "delegate.succeeded() must be called");
            assertFalse(writeCalled.get(), "response.write() must not be called — no error page is configured for 401");
        }
    }
}
