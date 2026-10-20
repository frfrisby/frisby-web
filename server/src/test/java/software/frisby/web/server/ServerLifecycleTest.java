package software.frisby.web.server;

import org.junit.jupiter.api.Test;
import org.eclipse.jetty.util.resource.ResourceFactory;
import software.frisby.web.test.log.LogExpectation;
import software.frisby.web.test.log.SystemLogVerifier;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class ServerLifecycleTest {
    // Each test creates its own server on its own free port for full isolation.

    /**
     * Builds a server that asks the OS to assign a free port.
     */
    private static Server buildServer() {
        return Server.builder()
                .configuration(
                        ServerConfiguration.builder()
                                .port(0)
                                .host("localhost")
                                .serializer(new TestJsonSerializer())
                                .build()
                )
                .resources(new PingResource())
                .components(TestLogging.forClass(ServerLifecycleTest.class))
                .build();
    }

    /**
     * Builds a server bound to an explicit {@code port} — used to test port-conflict scenarios.
     */
    private static Server buildServer(int port) {
        return Server.builder()
                .configuration(
                        ServerConfiguration.builder()
                                .port(port)
                                .host("localhost")
                                .serializer(new TestJsonSerializer())
                                .build()
                )
                .resources(new PingResource())
                .components(TestLogging.forClass(ServerLifecycleTest.class))
                .build();
    }


    private static HttpResponse<String> get(String url) throws Exception {
        return HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(url)).GET().build(),
                HttpResponse.BodyHandlers.ofString()
        );
    }

    // -------------------------------------------------------------------------
    // Functional lifecycle
    // -------------------------------------------------------------------------

    /**
     * Replaces the private {@code jettyServer} field of a started {@link DefaultServer}
     * with an anonymous {@link org.eclipse.jetty.server.Server} stub whose {@code doStop()}
     * always throws.  The stub is started first so that Jetty's {@code AbstractLifeCycle}
     * state machine reaches the STARTED state — {@code stop()} only delegates to
     * {@code doStop()} from there, and the thrown exception propagates through the
     * final {@code stop()} method to {@link DefaultServer#stop()}'s catch block.
     */
    private static void injectThrowingJettyServer(Server server) throws Exception {
        org.eclipse.jetty.server.Server stub = new org.eclipse.jetty.server.Server() {
            @Override
            protected void doStop() throws Exception {
                throw new Exception("Simulated stop failure");
            }
        };
        stub.start();   // puts stub in STARTED state; doStop() is only invoked from there

        Field field = DefaultServer.class.getDeclaredField("jettyServer");
        field.setAccessible(true);
        field.set(server, stub);
    }

    /**
     * Replaces the private {@code staticAssetsResourceFactory} field of a started
     * {@link DefaultServer} with a dynamic proxy whose {@code stop()} always throws, so that
     * {@code DefaultServer.stopStaticAssetsResourceFactory()}'s {@code catch} block runs during
     * the next {@link Server#stop()} call.  A {@link Proxy} is used rather than a hand-written
     * stub class because {@link ResourceFactory.LifeCycle} combines {@code LifeCycle}, {@code
     * ResourceFactory}, and {@code Dumpable} — only {@code stop()} needs real behavior here.
     */
    private static void injectThrowingStaticAssetsResourceFactory(Server server) throws Exception {
        ResourceFactory.LifeCycle stub = (ResourceFactory.LifeCycle) Proxy.newProxyInstance(
                ResourceFactory.LifeCycle.class.getClassLoader(),
                new Class<?>[] {ResourceFactory.LifeCycle.class},
                (proxy, method, args) -> {
                    if ("stop".equals(method.getName())) {
                        throw new Exception("Simulated static assets resource factory stop failure");
                    }

                    Class<?> returnType = method.getReturnType();

                    if (boolean.class == returnType) {
                        return false;
                    }

                    if (int.class == returnType) {
                        return 0;
                    }

                    return null;
                }
        );

        Field field = DefaultServer.class.getDeclaredField("staticAssetsResourceFactory");
        field.setAccessible(true);
        field.set(server, stub);
    }

    private static void assertEquals200(HttpResponse<String> response) {
        if (200 != response.statusCode()) {
            throw new AssertionError(
                    "Expected HTTP 200 but got " + response.statusCode()
                            + ". Body: " + response.body()
            );
        }
    }

    @Test
    void start_serverAcceptsConnections() throws Exception {
        Server server = buildServer();

        try {
            server.start();

            assertTrue(server.isRunning());
            assertEquals200(get("http://localhost:" + server.port() + "/ping"));
        } finally {
            server.stop();
        }
    }

    @Test
    void stop_serverStopsAcceptingConnections() throws Exception {
        Server server = buildServer();

        server.start();
        assertTrue(server.isRunning());

        server.stop();
        assertFalse(server.isRunning());
    }

    @Test
    void doubleStart_noEffect() {
        Server server = buildServer();

        try {
            server.start();
            server.start();  // must be a no-op — no exception
            assertTrue(server.isRunning());
        } finally {
            server.stop();
        }
    }

    @Test
    void doubleStop_noEffect() {
        Server server = buildServer();

        server.start();
        server.stop();
        server.stop();  // must be a no-op — no exception
        assertFalse(server.isRunning());
    }

    @Test
    void stopThenStart_serverIsRestartable() throws Exception {
        // Regression: a previous stop() set shuttingDown=true, which caused every inbound
        // request to be rejected with 503 on a subsequent start().  start() now resets
        // shuttingDown so that ConcurrencyLimitHandler accepts requests after a restart.
        Server server = buildServer();

        server.start();
        server.stop();
        assertFalse(server.isRunning());

        server.start();

        try {
            assertTrue(server.isRunning());
            assertEquals200(get("http://localhost:" + server.port() + "/ping"));
        } finally {
            server.stop();
        }
    }

    @Test
    void notStarted_isRunningReturnsFalse() {
        Server server = buildServer();

        assertFalse(server.isRunning());
    }

    @Test
    void uri_returnsHttpUriWithCorrectHostAndPort() {
        Server server = buildServer();
        server.start();

        try {
            URI uri = server.uri();

            assertEquals("http", uri.getScheme());
            assertEquals("localhost", uri.getHost());
            assertEquals(server.port(), uri.getPort());
        } finally {
            server.stop();
        }
    }

    // -------------------------------------------------------------------------
    // Logging — start / stop / start failure
    // -------------------------------------------------------------------------

    @Test
    void configuration_returnsConfigurationPassedAtBuild() {
        Server server = buildServer();
        server.start();

        try {
            assertNotNull(server.configuration());
        } finally {
            server.stop();
        }
    }

    @Test
    void portAlreadyInUse_throwsUncheckedIOException() throws IOException {
        // Bind the blocker to localhost (127.0.0.1) so it conflicts with Jetty's
        // connector, which also binds to 127.0.0.1 because buildServer() uses host("localhost").
        try (ServerSocket blocker = new ServerSocket(0, 50, InetAddress.getByName("localhost"))) {
            int port = blocker.getLocalPort();
            Server server = buildServer(port);

            assertThrows(UncheckedIOException.class, server::start);
        }
    }

    @Test
    void start_logsStartedAtInfo() {
        try (SystemLogVerifier verifier = SystemLogVerifier.builder()
                .expect(LogExpectation.builder()
                        .logger(RequestLogger.class)
                        .level(System.Logger.Level.INFO)
                        .predicate(e -> e.message().contains("Server started at"))
                        .build()
                )
                .build()) {
            Server server = buildServer();

            try {
                server.start();
                verifier.assertExpectations();
            } finally {
                server.stop();
            }
        }
    }

    @Test
    void stop_logsStoppedAtInfo() {
        try (SystemLogVerifier verifier = SystemLogVerifier.builder()
                .expect(LogExpectation.builder()
                        .logger(RequestLogger.class)
                        .level(System.Logger.Level.INFO)
                        .predicate(e -> e.message().contains("Server stopped at"))
                        .build()
                )
                .build()) {
            Server server = buildServer();
            server.start();
            server.stop();
            verifier.assertExpectations();
        }
    }

    @Test
    void portAlreadyInUse_logsStartFailedAtError() throws IOException {
        // Bind the blocker to localhost (127.0.0.1) so it conflicts with Jetty's connector.
        try (ServerSocket blocker = new ServerSocket(0, 50, InetAddress.getByName("localhost"))) {
            int port = blocker.getLocalPort();
            Server server = buildServer(port);

            try (SystemLogVerifier verifier = SystemLogVerifier.builder()
                    .expect(LogExpectation.builder()
                            .logger(RequestLogger.class)
                            .level(System.Logger.Level.ERROR)
                            .predicate(e -> e.message().contains("Server failed to start"))
                            .build()
                    )
                    .build()) {
                assertThrows(UncheckedIOException.class, server::start);
                verifier.assertExpectations();
            }
        }
    }

    @Test
    void infoLevelDisabled_startDoesNotLogStarted() {
        try (SystemLogVerifier verifier = SystemLogVerifier.builder()
                .configure(RequestLogger.class, System.Logger.Level.WARNING)
                .build()) {
            Server server = buildServer();

            try {
                server.start();
            } finally {
                server.stop();
            }

            // With INFO disabled, logStarted's isLoggable guard prevents the call.
            assertEquals(0, verifier.infoCount());
        }
    }

    // -------------------------------------------------------------------------
    // executor
    // -------------------------------------------------------------------------

    @Test
    void stop_exceptionDuringStop_warningIsLogged() throws Exception {
        Server server = buildServer();
        server.start();

        injectThrowingJettyServer(server);

        try (SystemLogVerifier verifier = SystemLogVerifier.builder()
                .expect(LogExpectation.builder()
                        .logger(DefaultServer.class)
                        .level(System.Logger.Level.WARNING)
                        .predicate(e -> e.message().contains("Exception while stopping server"))
                        .build()
                )
                .build()) {
            server.stop();   // must not propagate — exception is swallowed
            verifier.assertExpectations();
        }
    }

    // -------------------------------------------------------------------------
    // stopTimeout — graceful shutdown
    // -------------------------------------------------------------------------

    @Test
    void stop_exceptionDuringStop_warningLevelDisabled_exceptionIsStillSwallowed() throws Exception {
        Server server = buildServer();
        server.start();

        injectThrowingJettyServer(server);

        try (SystemLogVerifier verifier = SystemLogVerifier.builder()
                .configure(DefaultServer.class, System.Logger.Level.ERROR)
                .build()) {
            server.stop();   // must not propagate — exception is swallowed

            assertEquals(0, verifier.warningCount());
        }
    }

    // -------------------------------------------------------------------------
    // staticAssetsResourceFactory — defensive null guard
    // -------------------------------------------------------------------------

    @Test
    void stop_staticAssetsResourceFactoryAlreadyNull_doesNotThrow() throws Exception {
        // Exercises the defensive null-guard at the top of
        // DefaultServer.stopStaticAssetsResourceFactory(). Under normal operation the field is
        // always created in start() before jettyServer is assigned and cleared alongside it in
        // stop(), so the guard is unreachable through the public API alone -- it only matters if
        // ResourceFactory.lifecycle() itself were to throw before the field assignment completes.
        // Clearing the field via reflection after a successful start() reproduces that same
        // "field is null while jettyServer is not" state so the guard clause is covered and
        // proven not to throw a NullPointerException.
        Server server = buildServer();
        server.start();

        Field field = DefaultServer.class.getDeclaredField("staticAssetsResourceFactory");
        field.setAccessible(true);
        field.set(server, null);

        server.stop();   // must not throw NullPointerException

        assertFalse(server.isRunning());
    }

    @Test
    void stop_staticAssetsResourceFactoryStopThrows_warningIsLogged() throws Exception {
        Server server = buildServer();
        server.start();

        injectThrowingStaticAssetsResourceFactory(server);

        try (SystemLogVerifier verifier = SystemLogVerifier.builder()
                .expect(LogExpectation.builder()
                        .logger(DefaultServer.class)
                        .level(System.Logger.Level.WARNING)
                        .predicate(e -> e.message().contains("Exception while releasing static assets resources"))
                        .build()
                )
                .build()) {
            server.stop();   // must not propagate — exception is swallowed
            verifier.assertExpectations();
        }
    }

    @Test
    void stop_staticAssetsResourceFactoryStopThrows_warningLevelDisabled_exceptionIsStillSwallowed()
            throws Exception {
        Server server = buildServer();
        server.start();

        injectThrowingStaticAssetsResourceFactory(server);

        try (SystemLogVerifier verifier = SystemLogVerifier.builder()
                .configure(DefaultServer.class, System.Logger.Level.ERROR)
                .build()) {
            server.stop();   // must not propagate — exception is swallowed

            assertEquals(0, verifier.warningCount());
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    @Test
    void executor_customExecutorIsUsed() throws Exception {
        // Use a fixed-thread executor configured directly — a proxy for
        // "custom executor was wired in".  The server must start and serve requests
        // correctly when a custom executor is configured.
        var pool = Executors.newFixedThreadPool(4);

        Server server = Server.builder()
                .configuration(
                        ServerConfiguration.builder()
                                .port(0)
                                .host("localhost")
                                .serializer(new TestJsonSerializer())
                                .executor(pool)
                                .build()
                )
                .resources(new PingResource())
                .components(TestLogging.forClass(ServerLifecycleTest.class))
                .build();

        server.start();

        try {
            HttpResponse<String> response = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(URI.create(server.uri() + "/ping"))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            assertEquals(200, response.statusCode());
        } finally {
            server.stop();
            pool.shutdown();
        }
    }

    @Test
    void stopTimeout_inFlightRequestCompletesBeforeShutdown() throws Exception {
        // The slow endpoint holds a latch so we can control exactly when it responds.
        // stop() is called from a background thread while the request is in-flight.
        // With stopTimeout configured, Jetty must wait for the response to complete
        // before closing the connection.
        CountDownLatch requestStarted = new CountDownLatch(1);

        Server server = Server.builder()
                .configuration(
                        ServerConfiguration.builder()
                                .port(0)
                                .host("localhost")
                                .serializer(new TestJsonSerializer())
                                .stopTimeout(Duration.ofSeconds(5))
                                .build()
                )
                .resources(new SlowPingResource(requestStarted))
                .components(TestLogging.forClass(ServerLifecycleTest.class))
                .build();

        server.start();

        // Fire the slow request asynchronously.
        CompletableFuture<HttpResponse<String>> future = CompletableFuture.supplyAsync(() -> {
            try {
                return HttpClient.newHttpClient().send(
                        HttpRequest.newBuilder(URI.create(server.uri() + "/ping/slow"))
                                .GET()
                                .build(),
                        HttpResponse.BodyHandlers.ofString()
                );
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });

        // Wait until the request has entered the resource method, then call stop().
        assertTrue(requestStarted.await(3, TimeUnit.SECONDS), "Request did not start in time");
        server.stop();

        // The response must still arrive successfully — Jetty waited for it.
        HttpResponse<String> response = future.get(5, TimeUnit.SECONDS);
        assertEquals(200, response.statusCode());
    }

    /**
     * Minimal JAX-RS resource with a {@code GET /ping/slow} endpoint that signals
     * the provided latch when it enters the resource method, then sleeps briefly
     * before returning.  Used to test graceful shutdown — the caller controls when
     * to trigger stop() relative to the in-flight request.
     */
    @jakarta.ws.rs.Path("/ping")
    @jakarta.ws.rs.Produces(jakarta.ws.rs.core.MediaType.APPLICATION_JSON)
    public static final class SlowPingResource {
        private final CountDownLatch requestStarted;

        public SlowPingResource(CountDownLatch requestStarted) {
            this.requestStarted = requestStarted;
        }

        @jakarta.ws.rs.GET
        @jakarta.ws.rs.Path("/slow")
        public jakarta.ws.rs.core.Response slow() throws InterruptedException {
            requestStarted.countDown();
            Thread.sleep(500);
            return jakarta.ws.rs.core.Response.ok("{\"status\":\"slow\"}").build();
        }
    }
}

