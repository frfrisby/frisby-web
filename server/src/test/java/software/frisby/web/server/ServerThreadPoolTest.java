package software.frisby.web.server;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
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
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the concurrency model — verifies that
 * {@code maxConcurrentRequests} caps concurrent request processing and triggers
 * HTTP 503 with a {@code Retry-After} header, that graceful shutdown rejects new
 * requests with 503 during the drain window, and that a custom executor is accepted
 * without error.
 */
class ServerThreadPoolTest {
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    // -------------------------------------------------------------------------
    // maxConcurrentRequests — bounded concurrency + 503 on overflow
    // -------------------------------------------------------------------------

    @Nested
    class MaxConcurrentRequests {
        /**
         * Starts a server with {@code maxConcurrentRequests(2)}.  Two concurrent blocking
         * requests occupy both semaphore permits in {@code ConcurrencyLimitHandler}.  A
         * third request arrives while both permits are held; the handler writes an HTTP
         * {@code 503 Service Unavailable} response immediately with a
         * {@code Retry-After: 1} header — the TCP connection is fully accepted and HTTP
         * is spoken, giving callers an actionable signal rather than a connection drop.
         */
        @Test
        void maxConcurrentRequestsExceeded_returns503() throws Exception {
            CountDownLatch server1HasRequest = new CountDownLatch(1);
            CountDownLatch server2HasRequest = new CountDownLatch(1);
            CountDownLatch requestsCanProceed = new CountDownLatch(1);

            BlockingResource resource = new BlockingResource(
                    server1HasRequest,
                    server2HasRequest,
                    requestsCanProceed
            );

            Server server = Server.builder()
                    .configuration(
                            ServerConfiguration.builder()
                                    .port(0)
                                    .host("localhost")
                                    .serializer(new TestJsonSerializer())
                                    .maxConcurrentRequests(2)
                                    .build()
                    )
                    .resources(resource)
                    .build();

            server.start();

            ExecutorService executor = Executors.newFixedThreadPool(2);

            try {
                int port = server.port();
                URI blockUri = URI.create("http://localhost:" + port + "/block");
                URI pingUri = URI.create("http://localhost:" + port + "/ping-simple");

                // Fire two blocking requests — each acquires one of the two semaphore permits.
                executor.submit(() -> HTTP.send(
                        HttpRequest.newBuilder(blockUri).GET().build(),
                        HttpResponse.BodyHandlers.discarding()
                ));

                executor.submit(() -> HTTP.send(
                        HttpRequest.newBuilder(blockUri).GET().build(),
                        HttpResponse.BodyHandlers.discarding()
                ));

                // Wait until both requests are in-flight (both permits held).
                assertTrue(server1HasRequest.await(5, TimeUnit.SECONDS));
                assertTrue(server2HasRequest.await(5, TimeUnit.SECONDS));

                // Third request — both permits are held; ConcurrencyLimitHandler writes
                // a 503 with Retry-After immediately.  The client receives a complete
                // HTTP response, not a connection error.  The verifier confirms that the
                // rejection is logged at WARNING (not ERROR) with the [capacity limit] tag.
                try (SystemLogVerifier verifier = SystemLogVerifier.builder()
                        .expect(LogExpectation.builder()
                                .logger(RequestLogger.class)
                                .level(System.Logger.Level.WARNING)
                                .predicate(e -> e.message().contains("/ping-simple")
                                        && e.message().contains("503")
                                        && e.message().contains("[capacity limit]"))
                                .build()
                        )
                        .build()) {

                    HttpResponse<String> response = HTTP.send(
                            HttpRequest.newBuilder(pingUri).GET().build(),
                            HttpResponse.BodyHandlers.ofString()
                    );

                    assertEquals(503, response.statusCode());
                    assertEquals("1", response.headers().firstValue("Retry-After").orElse(null));

                    // The log callback fires on the Jetty response-write thread — wait for it.
                    verifier.assertExpectations(Duration.ofSeconds(2));
                }
            } finally {
                requestsCanProceed.countDown();
                executor.shutdown();
                executor.awaitTermination(5, TimeUnit.SECONDS);
                server.stop();
            }
        }

        @Test
        void maxConcurrentRequestsExceeded_atErrorLogging_returns503() throws Exception {
            CountDownLatch server1HasRequest = new CountDownLatch(1);
            CountDownLatch server2HasRequest = new CountDownLatch(1);
            CountDownLatch requestsCanProceed = new CountDownLatch(1);

            BlockingResource resource = new BlockingResource(
                    server1HasRequest,
                    server2HasRequest,
                    requestsCanProceed
            );

            Server server = Server.builder()
                    .configuration(
                            ServerConfiguration.builder()
                                    .port(0)
                                    .host("localhost")
                                    .serializer(new TestJsonSerializer())
                                    .maxConcurrentRequests(2)
                                    .build()
                    )
                    .resources(resource)
                    .build();

            server.start();

            ExecutorService executor = Executors.newFixedThreadPool(2);

            try {
                int port = server.port();
                URI blockUri = URI.create("http://localhost:" + port + "/block");
                URI pingUri = URI.create("http://localhost:" + port + "/ping-simple");

                executor.submit(() -> HTTP.send(
                        HttpRequest.newBuilder(blockUri).GET().build(),
                        HttpResponse.BodyHandlers.discarding()
                ));

                executor.submit(() -> HTTP.send(
                        HttpRequest.newBuilder(blockUri).GET().build(),
                        HttpResponse.BodyHandlers.discarding()
                ));

                assertTrue(server1HasRequest.await(5, TimeUnit.SECONDS));
                assertTrue(server2HasRequest.await(5, TimeUnit.SECONDS));

                try (SystemLogVerifier verifier = SystemLogVerifier.builder()
                        .configure(RequestLogger.class, System.Logger.Level.ERROR)
                        .build()) {

                    HttpResponse<String> response = HTTP.send(
                            HttpRequest.newBuilder(pingUri).GET().build(),
                            HttpResponse.BodyHandlers.ofString()
                    );

                    assertEquals(503, response.statusCode());
                    assertEquals("1", response.headers().firstValue("Retry-After").orElse(null));

                    assertEquals(0, verifier.warningCount());
                }
            } finally {
                requestsCanProceed.countDown();

                executor.shutdown();
                executor.awaitTermination(5, TimeUnit.SECONDS);

                server.stop();
            }
        }

        @Test
        void maxConcurrentRequestsNotExceeded_returns200() throws Exception {
            Server server = Server.builder()
                    .configuration(
                            ServerConfiguration.builder()
                                    .port(0)
                                    .host("localhost")
                                    .serializer(new TestJsonSerializer())
                                    .maxConcurrentRequests(10)
                                    .build()
                    )
                    .resources(new PingResource())
                    .build();

            server.start();

            try {
                HttpResponse<String> response = HTTP.send(
                        HttpRequest.newBuilder(URI.create("http://localhost:" + server.port() + "/ping"))
                                .GET()
                                .build(),
                        HttpResponse.BodyHandlers.ofString()
                );

                assertEquals(200, response.statusCode());
            } finally {
                server.stop();
            }
        }

        /**
         * Registers a {@link software.frisby.web.server.event.ServerEventListener} that
         * always throws on {@code onRequestCompleted}.  When a capacity rejection fires
         * the throwing listener, {@code ConcurrencyLimitHandler.logAndFire} catches the
         * exception and logs a {@code WARNING} via {@code DefaultServer.LOGGER}.  This test
         * confirms that the warning is emitted and that the 503 response still reaches the
         * client — a buggy listener must never suppress the HTTP response.
         */
        @Test
        void throwingEventListener_logsWarning_onCapacityRejection() throws Exception {
            CountDownLatch server1HasRequest = new CountDownLatch(1);
            CountDownLatch server2HasRequest = new CountDownLatch(1);
            CountDownLatch requestsCanProceed = new CountDownLatch(1);

            BlockingResource resource = new BlockingResource(
                    server1HasRequest,
                    server2HasRequest,
                    requestsCanProceed
            );

            Server server = Server.builder()
                    .configuration(
                            ServerConfiguration.builder()
                                    .port(0)
                                    .host("localhost")
                                    .serializer(new TestJsonSerializer())
                                    .maxConcurrentRequests(2)
                                    .build()
                    )
                    .resources(resource)
                    .eventListener(event -> {
                        throw new RuntimeException("Simulated event listener failure");
                    })
                    .build();

            server.start();

            ExecutorService executor = Executors.newFixedThreadPool(2);

            try {
                int port = server.port();
                URI blockUri = URI.create("http://localhost:" + port + "/block");
                URI pingUri = URI.create("http://localhost:" + port + "/ping-simple");

                executor.submit(() -> HTTP.send(
                        HttpRequest.newBuilder(blockUri).GET().build(),
                        HttpResponse.BodyHandlers.discarding()
                ));

                executor.submit(() -> HTTP.send(
                        HttpRequest.newBuilder(blockUri).GET().build(),
                        HttpResponse.BodyHandlers.discarding()
                ));

                assertTrue(server1HasRequest.await(5, TimeUnit.SECONDS));
                assertTrue(server2HasRequest.await(5, TimeUnit.SECONDS));

                try (SystemLogVerifier verifier = SystemLogVerifier.builder()
                        .expect(LogExpectation.builder()
                                .logger(DefaultServer.class)
                                .level(System.Logger.Level.WARNING)
                                .predicate(e -> e.message().contains(
                                        "ServerEventListener.onRequestCompleted threw an exception"))
                                .build()
                        )
                        .build()) {

                    HttpResponse<String> response = HTTP.send(
                            HttpRequest.newBuilder(pingUri).GET().build(),
                            HttpResponse.BodyHandlers.ofString()
                    );

                    assertEquals(503, response.statusCode());

                    verifier.assertExpectations(Duration.ofSeconds(2));
                }
            } finally {
                requestsCanProceed.countDown();
                executor.shutdown();
                executor.awaitTermination(5, TimeUnit.SECONDS);
                server.stop();
            }
        }

        /**
         * Same throwing-listener scenario, but with {@code DefaultServer} logging suppressed
         * to {@code ERROR}.  When {@code isLoggable(WARNING)} returns {@code false}, the
         * catch block swallows the exception silently.  The 503 response must still reach
         * the client — the log level must not affect the response path.
         */
        @Test
        void throwingEventListener_warningSuppressed_returns503() throws Exception {
            CountDownLatch server1HasRequest = new CountDownLatch(1);
            CountDownLatch server2HasRequest = new CountDownLatch(1);
            CountDownLatch requestsCanProceed = new CountDownLatch(1);

            BlockingResource resource = new BlockingResource(
                    server1HasRequest,
                    server2HasRequest,
                    requestsCanProceed
            );

            Server server = Server.builder()
                    .configuration(
                            ServerConfiguration.builder()
                                    .port(0)
                                    .host("localhost")
                                    .serializer(new TestJsonSerializer())
                                    .maxConcurrentRequests(2)
                                    .build()
                    )
                    .resources(resource)
                    .eventListener(event -> {
                        throw new RuntimeException("Simulated event listener failure");
                    })
                    .build();

            server.start();

            ExecutorService executor = Executors.newFixedThreadPool(2);

            try {
                int port = server.port();
                URI blockUri = URI.create("http://localhost:" + port + "/block");
                URI pingUri = URI.create("http://localhost:" + port + "/ping-simple");

                executor.submit(() -> HTTP.send(
                        HttpRequest.newBuilder(blockUri).GET().build(),
                        HttpResponse.BodyHandlers.discarding()
                ));

                executor.submit(() -> HTTP.send(
                        HttpRequest.newBuilder(blockUri).GET().build(),
                        HttpResponse.BodyHandlers.discarding()
                ));

                assertTrue(server1HasRequest.await(5, TimeUnit.SECONDS));
                assertTrue(server2HasRequest.await(5, TimeUnit.SECONDS));

                // Suppress WARNING on DefaultServer so the isLoggable(WARNING) guard
                // returns false — confirming the false branch is reached without logging.
                // RequestLogger is also suppressed to prevent its capacity-rejection
                // WARNING from appearing in the warning count (separate logger).
                try (SystemLogVerifier verifier = SystemLogVerifier.builder()
                        .configure(DefaultServer.class, System.Logger.Level.ERROR)
                        .configure(RequestLogger.class, System.Logger.Level.ERROR)
                        .build()) {

                    HttpResponse<String> response = HTTP.send(
                            HttpRequest.newBuilder(pingUri).GET().build(),
                            HttpResponse.BodyHandlers.ofString()
                    );

                    assertEquals(503, response.statusCode());
                    assertEquals(0, verifier.warningCount());
                }
            } finally {
                requestsCanProceed.countDown();
                executor.shutdown();
                executor.awaitTermination(5, TimeUnit.SECONDS);
                server.stop();
            }
        }
    }

    // -------------------------------------------------------------------------
    // Graceful shutdown drain — 503 during drain window
    // -------------------------------------------------------------------------

    @Nested
    class GracefulShutdown {
        /**
         * Verifies that once {@code stop()} is called, new requests receive a 503
         * response immediately throughout the entire drain window — even when semaphore
         * permits are still available.
         * ...existing code...
         */
        @Test
        void stopCalled_newRequestsGet503DuringDrain() throws Exception {
            CountDownLatch serverHasRequest = new CountDownLatch(1);
            CountDownLatch serverHasRequest2 = new CountDownLatch(1);
            CountDownLatch requestCanProceed = new CountDownLatch(1);

            BlockingResource resource = new BlockingResource(
                    serverHasRequest,
                    serverHasRequest2,
                    requestCanProceed
            );

            Server server = Server.builder()
                    .configuration(
                            ServerConfiguration.builder()
                                    .port(0)
                                    .host("localhost")
                                    .serializer(new TestJsonSerializer())
                                    .maxConcurrentRequests(2)
                                    .stopTimeout(Duration.ofSeconds(5))
                                    .build()
                    )
                    .resources(resource)
                    .build();

            server.start();

            int port = server.port();
            URI blockUri = URI.create("http://localhost:" + port + "/block");
            URI pingUri = URI.create("http://localhost:" + port + "/ping-simple");

            ExecutorService clientExecutor = Executors.newFixedThreadPool(1);
            ExecutorService stopExecutor = Executors.newSingleThreadExecutor();

            try {
                // Send one blocking request to hold one of the two permits.
                clientExecutor.submit(() -> HTTP.send(
                        HttpRequest.newBuilder(blockUri).GET().build(),
                        HttpResponse.BodyHandlers.discarding()
                ));

                // Wait until the blocking request is in-flight (one permit held).
                assertTrue(serverHasRequest.await(5, TimeUnit.SECONDS));

                // Start shutdown on a background thread.  stop() sets shuttingDown and
                // then blocks on the semaphore drain — it will not return until we release
                // the blocking request below.
                stopExecutor.submit(server::stop);

                // Poll until the server rejects our probe with 503.  The stop() thread
                // sets shuttingDown before blocking on the semaphore, so the probe will
                // get 503 as soon as the flag is visible.
                // One permit is still free, so without the shuttingDown check the probe
                // would succeed with 200 — this is the key assertion.
                HttpResponse<String> probeResponse = null;
                for (int attempt = 0; attempt < 20; attempt++) {
                    HttpResponse<String> r = HTTP.send(
                            HttpRequest.newBuilder(pingUri).GET().build(),
                            HttpResponse.BodyHandlers.ofString()
                    );

                    if (503 == r.statusCode()) {
                        probeResponse = r;
                        break;
                    }

                    Thread.sleep(50);
                }

                assertNotNull(probeResponse, "Expected a 503 response during drain window");
                assertEquals(503, probeResponse.statusCode());
                assertEquals("1", probeResponse.headers().firstValue("Retry-After").orElse(null));
            } finally {
                // Release the in-flight blocking request so the drain completes.
                requestCanProceed.countDown();

                stopExecutor.shutdown();
                stopExecutor.awaitTermination(10, TimeUnit.SECONDS);

                clientExecutor.shutdown();
                clientExecutor.awaitTermination(5, TimeUnit.SECONDS);
            }
        }

        /**
         * Covers the {@code Thread.currentThread().interrupt()} line in the
         * {@code catch (InterruptedException)} block of {@code DefaultServer.stop()}'s
         * semaphore drain.
         * <p>
         * Protocol: start a server with a 30-second stop timeout so the drain will not
         * expire on its own, hold the only permit with a blocking request, then call
         * {@code stop()} on a background thread so it blocks in
         * {@code semaphore.tryAcquire()}.  Interrupt the stop thread; the
         * {@code InterruptedException} is caught, the interrupt flag is re-set, and
         * {@code stop()} completes immediately by proceeding to {@code jettyServer.stop()}
         * rather than hanging for the full 30-second timeout.
         */
        @Test
        void drainInterrupted_stopCompletesWithoutHanging() throws Exception {
            CountDownLatch serverHasRequest = new CountDownLatch(1);
            CountDownLatch serverHasRequest2 = new CountDownLatch(1);
            CountDownLatch requestCanProceed = new CountDownLatch(1);

            BlockingResource resource = new BlockingResource(
                    serverHasRequest,
                    serverHasRequest2,
                    requestCanProceed
            );

            Server server = Server.builder()
                    .configuration(
                            ServerConfiguration.builder()
                                    .port(0)
                                    .host("localhost")
                                    .serializer(new TestJsonSerializer())
                                    .maxConcurrentRequests(1)
                                    .stopTimeout(Duration.ofSeconds(30))
                                    .build()
                    )
                    .resources(resource)
                    .build();

            server.start();

            ExecutorService clientExecutor = Executors.newFixedThreadPool(1);
            CountDownLatch drainAboutToStart = new CountDownLatch(1);

            try {
                // Hold the only permit with a blocking request.
                clientExecutor.submit(() -> HTTP.send(
                        HttpRequest.newBuilder(
                                URI.create("http://localhost:" + server.port() + "/block")
                        ).GET().build(),
                        HttpResponse.BodyHandlers.discarding()
                ));

                assertTrue(serverHasRequest.await(5, TimeUnit.SECONDS));

                // stop() on a background thread — will block in tryAcquire() while the
                // permit is held.  The latch fires just before blocking so we know the
                // drain is imminent.
                Thread stopThread = new Thread(() -> {
                    drainAboutToStart.countDown();
                    server.stop();
                });
                stopThread.start();

                // Wait for stop() to pass the latch, then give it a moment to reach the
                // tryAcquire() call before interrupting.
                assertTrue(drainAboutToStart.await(5, TimeUnit.SECONDS));
                Thread.sleep(200);

                // Interrupt the drain — the InterruptedException catch block re-sets the flag
                // and stop() proceeds to jettyServer.stop() rather than hanging for 30 seconds.
                stopThread.interrupt();

                // stop() must complete well within the 30-second drain timeout, because
                // the interrupt breaks out of tryAcquire immediately.
                stopThread.join(10_000);
                assertFalse(stopThread.isAlive(), "stop() must complete after the drain is interrupted");
            } finally {
                requestCanProceed.countDown();
                clientExecutor.shutdown();
                clientExecutor.awaitTermination(5, TimeUnit.SECONDS);
            }
        }
    }

    @Nested
    class ExecutorTests {
        @Test
        void customExecutor_handlesRequest() throws Exception {
            var customExecutor = Executors.newCachedThreadPool();

            Server server = Server.builder()
                    .configuration(
                            ServerConfiguration.builder()
                                    .port(0)
                                    .host("localhost")
                                    .serializer(new TestJsonSerializer())
                                    .executor(customExecutor)
                                    .build()
                    )
                    .resources(new PingResource())
                    .build();

            server.start();

            try {
                HttpResponse<String> response = HTTP.send(
                        HttpRequest.newBuilder(URI.create("http://localhost:" + server.port() + "/ping"))
                                .GET()
                                .build(),
                        HttpResponse.BodyHandlers.ofString()
                );

                assertEquals(200, response.statusCode());
                assertNotNull(response.body());
            } finally {
                server.stop();
                customExecutor.shutdown();
            }
        }
    }

    // -------------------------------------------------------------------------
    // Combined maxConcurrentRequests + executor
    // -------------------------------------------------------------------------

    @Nested
    class Combined {
        @Test
        void maxConcurrentRequestsAndExecutor_handlesRequest() throws Exception {
            var customExecutor = Executors.newCachedThreadPool();

            Server server = Server.builder()
                    .configuration(
                            ServerConfiguration.builder()
                                    .port(0)
                                    .host("localhost")
                                    .serializer(new TestJsonSerializer())
                                    .maxConcurrentRequests(10)
                                    .executor(customExecutor)
                                    .build()
                    )
                    .resources(new PingResource())
                    .build();

            server.start();

            try {
                HttpResponse<String> response = HTTP.send(
                        HttpRequest.newBuilder(URI.create("http://localhost:" + server.port() + "/ping"))
                                .GET()
                                .build(),
                        HttpResponse.BodyHandlers.ofString()
                );

                assertEquals(200, response.statusCode());
            } finally {
                server.stop();
                customExecutor.shutdown();
            }
        }
    }

    // -------------------------------------------------------------------------
    // Helper resources
    // -------------------------------------------------------------------------

    /**
     * A JAX-RS resource with two endpoints:
     * <ul>
     *   <li>{@code GET /block} — signals one of two progress latches then blocks
     *       until {@code requestsCanProceed} is released, allowing tests to hold request
     *       threads while probing the rejection path.  An {@link AtomicBoolean} CAS gate
     *       eliminates the TOCTOU race that would occur if both concurrent requests read
     *       the first latch count before either has decremented it.</li>
     *   <li>{@code GET /ping-simple} — returns 200 immediately.</li>
     * </ul>
     */
    @Path("/")
    @Produces(MediaType.APPLICATION_JSON)
    public static final class BlockingResource {
        private final CountDownLatch server1HasRequest;
        private final CountDownLatch server2HasRequest;
        private final CountDownLatch requestsCanProceed;
        private final AtomicBoolean firstSignalled = new AtomicBoolean(false);

        private BlockingResource(CountDownLatch server1HasRequest,
                                 CountDownLatch server2HasRequest,
                                 CountDownLatch requestsCanProceed) {
            this.server1HasRequest = server1HasRequest;
            this.server2HasRequest = server2HasRequest;
            this.requestsCanProceed = requestsCanProceed;
        }

        @GET
        @Path("/block")
        public Response block() throws InterruptedException {
            if (firstSignalled.compareAndSet(false, true)) {
                server1HasRequest.countDown();
            } else {
                server2HasRequest.countDown();
            }

            requestsCanProceed.await(10, TimeUnit.SECONDS);

            return Response.ok("{\"status\":\"done\"}").build();
        }

        @GET
        @Path("/ping-simple")
        public Response ping() {
            return Response.ok("{\"status\":\"ok\"}").build();
        }
    }

    @Path("/ping")
    @Produces(MediaType.APPLICATION_JSON)
    public static final class PingResource {
        @GET
        public Response ping() {
            return Response.ok("{\"status\":\"ok\"}").build();
        }
    }
}
