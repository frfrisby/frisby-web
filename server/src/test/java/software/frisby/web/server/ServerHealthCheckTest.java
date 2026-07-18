package software.frisby.web.server;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import software.frisby.web.server.event.RequestCompletedEvent;
import software.frisby.web.server.event.ServerEventListener;
import software.frisby.web.test.log.LogExpectation;
import software.frisby.web.test.log.SystemLogVerifier;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerHealthCheckTest {
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private static Server server;
    private static int port;
    private static CountingEventListener eventListener;

    @BeforeAll
    static void startServer() {
        eventListener = new CountingEventListener();

        server = Server.builder()
                .configuration(
                        ServerConfiguration.builder()
                                .port(0)
                                .host("localhost")
                                .serializer(new TestJsonSerializer())
                                .build()
                )
                .healthCheck()
                .resources(new PingResource())
                .eventListener(eventListener)
                .components(TestLogging.forClass(ServerHealthCheckTest.class))
                .build();

        server.start();

        port = server.port();
    }

    @AfterAll
    static void stopServer() {
        if (null != server) {
            server.stop();
        }
    }

    // -------------------------------------------------------------------------
    // Health check endpoint
    // -------------------------------------------------------------------------

    @Test
    void healthCheck_defaultPath_returns200() throws Exception {
        HttpResponse<String> response = get("/health");

        assertEquals(200, response.statusCode());
    }

    @Test
    void healthCheck_defaultPath_returnsUpBody() throws Exception {
        HttpResponse<String> response = get("/health");

        assertTrue(response.body().contains("UP"));
    }

    @Test
    void healthCheck_contentTypeIsJson() throws Exception {
        HttpResponse<String> response = get("/health");

        assertTrue(
                response.headers()
                        .firstValue("Content-Type")
                        .orElse("")
                        .contains("application/json")
        );
    }

    // -------------------------------------------------------------------------
    // Health check on custom path
    // -------------------------------------------------------------------------

    @Test
    void healthCheck_customPath_returns200() throws Exception {
        Server customServer = Server.builder()
                .configuration(
                        ServerConfiguration.builder()
                                .port(0)
                                .host("localhost")
                                .serializer(new TestJsonSerializer())
                                .build()
                )
                .resources(new PingResource())
                .healthCheck("/readyz")
                .components(TestLogging.forClass(ServerHealthCheckTest.class))
                .build();

        try {
            customServer.start();

            HttpResponse<String> response = HTTP.send(
                    HttpRequest.newBuilder(URI.create("http://localhost:" + customServer.port() + "/readyz"))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("UP"));
        } finally {
            customServer.stop();
        }
    }

    // -------------------------------------------------------------------------
    // Health check logging — TRACE only, not INFO
    // -------------------------------------------------------------------------

    @Test
    void healthCheck_logsAtTraceNotInfo() throws Exception {
        try (SystemLogVerifier verifier = SystemLogVerifier.builder()
                .configure(RequestLogger.class, System.Logger.Level.TRACE)
                .expect(LogExpectation.builder()
                        .logger(RequestLogger.class)
                        .level(System.Logger.Level.TRACE)
                        .predicate(e -> e.message().contains("/health"))
                        .build()
                )
                .build()) {
            get("/health");

            verifier.assertExpectations(Duration.ofSeconds(2));
            assertEquals(0, verifier.infoCount());
        }
    }

    @Test
    void healthCheck_notLoggedAtInfo() throws Exception {
        try (SystemLogVerifier verifier = SystemLogVerifier.builder()
                .configure(ServerHealthCheckTest.class, System.Logger.Level.DEBUG)
                .configure(RequestLogger.class, System.Logger.Level.INFO)
                .build()) {
            get("/health");

            assertEquals(0, verifier.infoCount());
            assertEquals(0, verifier.traceCount());
        }
    }

    // -------------------------------------------------------------------------
    // Health check event suppression
    // -------------------------------------------------------------------------

    @Test
    void healthCheck_doesNotFireEventListenerCallback() throws Exception {
        int countBefore = eventListener.completedCount();

        // A normal request DOES fire the event listener.
        // The FINISHED event fires on the server thread, so we poll briefly to
        // allow it to arrive before asserting.
        get("/ping");
        assertEquals(countBefore + 1, eventListener.awaitCount(countBefore + 1));

        int countAfterPing = eventListener.completedCount();

        // A health check request must NOT fire the event listener.
        get("/health");
        assertEquals(countAfterPing, eventListener.completedCount());
    }

    // -------------------------------------------------------------------------
    // Health check under load — concurrency gate bypass
    // -------------------------------------------------------------------------

    /**
     * Verifies the liveness-under-load behaviour: when the server is at capacity but
     * healthy (not shutting down), the health check endpoint bypasses the concurrency
     * gate and returns 200.  Without the bypass, a fully-loaded server would return 503
     * and trick the load balancer into recycling a live instance.
     */
    @Nested
    class UnderLoad {
        @Test
        void atCapacity_healthCheckStillReturns200() throws Exception {
            CountDownLatch serverHasRequest = new CountDownLatch(1);
            CountDownLatch requestCanProceed = new CountDownLatch(1);

            Server loadedServer = Server.builder()
                    .configuration(
                            ServerConfiguration.builder()
                                    .port(0)
                                    .host("localhost")
                                    .serializer(new TestJsonSerializer())
                                    .maxConcurrentRequests(1)
                                    .build()
                    )
                    .resources(new SingleBlockingResource(serverHasRequest, requestCanProceed))
                    .healthCheck()
                    .components(TestLogging.forClass(ServerHealthCheckTest.class))
                    .build();

            loadedServer.start();

            ExecutorService executor = Executors.newSingleThreadExecutor();

            try {
                // Hold the only permit with a blocking request.
                executor.submit(() -> HTTP.send(
                        HttpRequest.newBuilder(
                                URI.create("http://localhost:" + loadedServer.port() + "/block")
                        ).GET().build(),
                        HttpResponse.BodyHandlers.discarding()
                ));

                // Wait until the blocking request is in-flight — the only permit is held.
                assertTrue(serverHasRequest.await(5, TimeUnit.SECONDS));

                // Health check must bypass the gate and return 200.
                // The server is healthy; it is simply at full capacity.
                HttpResponse<String> response = HTTP.send(
                        HttpRequest.newBuilder(
                                URI.create("http://localhost:" + loadedServer.port() + "/health")
                        ).GET().build(),
                        HttpResponse.BodyHandlers.ofString()
                );

                assertEquals(200, response.statusCode());
                assertTrue(response.body().contains("UP"));
            } finally {
                requestCanProceed.countDown();
                executor.shutdown();
                executor.awaitTermination(5, TimeUnit.SECONDS);
                loadedServer.stop();
            }
        }

        /**
         * Verifies that the concurrency gate still returns 503 for non-health-check paths
         * when the server is at capacity.  The health check bypass is path-specific:
         * {@code healthCheckPath != null} but {@code healthCheckPath.equals(path)} is
         * {@code false}, so the request must not slip through the gate.
         */
        @Test
        void atCapacity_nonHealthCheckRequestGets503() throws Exception {
            CountDownLatch serverHasRequest = new CountDownLatch(1);
            CountDownLatch requestCanProceed = new CountDownLatch(1);

            Server loadedServer = Server.builder()
                    .configuration(
                            ServerConfiguration.builder()
                                    .port(0)
                                    .host("localhost")
                                    .serializer(new TestJsonSerializer())
                                    .maxConcurrentRequests(1)
                                    .build()
                    )
                    .resources(new SingleBlockingResource(serverHasRequest, requestCanProceed))
                    .healthCheck()
                    .components(TestLogging.forClass(ServerHealthCheckTest.class))
                    .build();

            loadedServer.start();

            ExecutorService executor = Executors.newSingleThreadExecutor();

            try {
                // Hold the only permit with a blocking request.
                executor.submit(() -> HTTP.send(
                        HttpRequest.newBuilder(
                                URI.create("http://localhost:" + loadedServer.port() + "/block")
                        ).GET().build(),
                        HttpResponse.BodyHandlers.discarding()
                ));

                assertTrue(serverHasRequest.await(5, TimeUnit.SECONDS));

                // A non-health-check path must still get 503 — the bypass is
                // path-specific; healthCheckPath.equals(path) is false here.
                HttpResponse<String> response = HTTP.send(
                        HttpRequest.newBuilder(
                                URI.create("http://localhost:" + loadedServer.port() + "/ping-simple")
                        ).GET().build(),
                        HttpResponse.BodyHandlers.ofString()
                );

                assertEquals(503, response.statusCode());
                assertEquals("1", response.headers().firstValue("Retry-After").orElse(null));
            } finally {
                requestCanProceed.countDown();
                executor.shutdown();
                executor.awaitTermination(5, TimeUnit.SECONDS);
                loadedServer.stop();
            }
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private static HttpResponse<String> get(String path) throws Exception {
        return HTTP.send(
                HttpRequest.newBuilder(uri(path)).GET().build(),
                HttpResponse.BodyHandlers.ofString()
        );
    }

    private static final class CountingEventListener implements ServerEventListener {
        private final AtomicInteger completed = new AtomicInteger(0);

        @Override
        public void onRequestCompleted(RequestCompletedEvent event) {
            completed.incrementAndGet();
        }


        int completedCount() {
            return completed.get();
        }

        /**
         * Polls until {@code completed >= expected} or 2 seconds elapse, then returns
         * the current count.  Used to absorb the small window between the client
         * receiving an HTTP response and the server-side FINISHED event firing.
         */
        int awaitCount(int expected) throws InterruptedException {
            long deadline = System.nanoTime() + 2_000_000_000L;

            while (completed.get() < expected && System.nanoTime() < deadline) {
                Thread.sleep(10);
            }

            return completed.get();
        }
    }

    /**
     * A JAX-RS resource with two endpoints:
     * <ul>
     *   <li>{@code GET /block} — signals the latch then blocks until
     *       {@code requestCanProceed} is counted down, allowing tests to hold the
     *       semaphore permit while issuing a concurrent health check request.</li>
     *   <li>{@code GET /ping-simple} — returns 200 immediately.</li>
     * </ul>
     */
    @Path("/")
    @Produces(MediaType.APPLICATION_JSON)
    public static final class SingleBlockingResource {
        private final CountDownLatch serverHasRequest;
        private final CountDownLatch requestCanProceed;

        private SingleBlockingResource(CountDownLatch serverHasRequest,
                                       CountDownLatch requestCanProceed) {
            this.serverHasRequest = serverHasRequest;
            this.requestCanProceed = requestCanProceed;
        }

        @GET
        @Path("/block")
        public Response block() throws InterruptedException {
            serverHasRequest.countDown();
            requestCanProceed.await(10, TimeUnit.SECONDS);

            return Response.ok("{\"status\":\"done\"}").build();
        }

        @GET
        @Path("/ping-simple")
        public Response ping() {
            return Response.ok("{\"status\":\"ok\"}").build();
        }
    }
}



