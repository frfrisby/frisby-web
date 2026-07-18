package software.frisby.web.server;

import org.junit.jupiter.api.Test;
import software.frisby.web.test.log.LogExpectation;
import software.frisby.web.test.log.SystemLogVerifier;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerLifecycleTest {
    // Each test creates its own server on its own free port for full isolation.

    /** Builds a server that asks the OS to assign a free port. */
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

    /** Builds a server bound to an explicit {@code port} — used to test port-conflict scenarios. */
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

    // -------------------------------------------------------------------------
    // Logging — start / stop / start failure
    // -------------------------------------------------------------------------

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
    // executor
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

    // -------------------------------------------------------------------------
    // stopTimeout — graceful shutdown
    // -------------------------------------------------------------------------

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

    // -------------------------------------------------------------------------
    // Helpers
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

    private static void assertEquals200(HttpResponse<String> response) {
        if (200 != response.statusCode()) {
            throw new AssertionError(
                    "Expected HTTP 200 but got " + response.statusCode()
                            + ". Body: " + response.body()
            );
        }
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

