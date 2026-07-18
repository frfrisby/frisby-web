package software.frisby.web.server;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.frisby.web.server.event.RequestCompletedEvent;
import software.frisby.web.server.event.ServerEventListener;
import software.frisby.web.test.log.LogExpectation;
import software.frisby.web.test.log.SystemLogVerifier;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerRequestSizeLimitTest {
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final int MAX_BYTES = 256;

    private static Server server;
    private static int port;

    @BeforeAll
    static void startServer() {
        server = Server.builder()
                .configuration(
                        ServerConfiguration.builder()
                                .port(0)
                                .host("localhost")
                                .maxRequestSize(MAX_BYTES)
                                .serializer(new TestJsonSerializer())
                                .build()
                )
                .resources(new PingResource())
                .components(TestLogging.forClass(ServerRequestSizeLimitTest.class))
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

    @Test
    void requestBodyWithinLimit_returns200() throws Exception {
        String body = "{\"key\":\"small\"}";

        HttpResponse<String> response = HTTP.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/ping"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );

        assertEquals(200, response.statusCode());
    }

    @Test
    void requestBodyExceedsLimit_returns413WithJsonBody() throws Exception {
        // Body is well above the 256-byte limit.
        String largeBody = "{\"key\":\"" + "x".repeat(512) + "\"}";

        HttpResponse<String> response = HTTP.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/ping"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(largeBody))
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );

        assertEquals(413, response.statusCode());
        assertTrue(
                response.headers().firstValue("Content-Type")
                        .orElse("")
                        .contains("application/json"),
                "413 response must have Content-Type: application/json"
        );
        assertTrue(
                response.body().contains("\"status\":413"),
                "413 response body must contain the status code; got: " + response.body()
        );
        assertTrue(
                response.body().contains("\"message\""),
                "413 response body must contain a message field; got: " + response.body()
        );
    }

    @Test
    void requestBodyExceedsLimit_logsAtWarning() throws Exception {
        try (SystemLogVerifier verifier = SystemLogVerifier.builder()
                .expect(LogExpectation.builder()
                        .logger(RequestLogger.class)
                        .level(System.Logger.Level.WARNING)
                        .predicate(e -> e.message().contains("413")
                                && e.message().contains("POST")
                                && e.message().contains("Request Headers:")
                                && e.message().contains("Content-Length:")
                                && e.message().contains("Request Body:")
                                && e.message().contains("bytes exceeds server limit")
                                && e.message().contains("Response Body:"))
                        .build()
                )
                .build()) {
            String largeBody = "{\"key\":\"" + "x".repeat(512) + "\"}";

            HTTP.send(
                    HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/ping"))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(largeBody))
                            .build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            verifier.assertExpectations(Duration.ofSeconds(2));
        }
    }

    @Test
    void chunkedBodyExceedsLimit_returns413WithJsonBody() throws Exception {
        // InputStream publisher → Transfer-Encoding: chunked (no Content-Length header).
        // BadMessageExceptionMapper must intercept the BadMessageException thrown by
        // SizeLimitHandler during body-read and return the same 413 JSON format as
        // JsonErrorHandler (which only handles the known-Content-Length case).
        byte[] largeBody = new byte[MAX_BYTES + 100];

        HttpResponse<String> response = HTTP.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/ping"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofInputStream(() -> new ByteArrayInputStream(largeBody)))
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );

        assertEquals(413, response.statusCode());
        assertTrue(
                response.headers().firstValue("Content-Type")
                        .orElse("")
                        .contains("application/json"),
                "413 response must have Content-Type: application/json"
        );
        assertTrue(
                response.body().contains("\"status\":413"),
                "413 response body must contain the status code; got: " + response.body()
        );
    }

    @Test
    void chunkedBodyExceedsLimit_logsAtWarning() throws Exception {
        // BadMessageExceptionMapper returns 413, so Jersey fires FINISHED with 413 →
        // ServerRequestEventListener logs at WARNING.  Jersey's internal ServerLoggingFilter
        // (auto-registered, priority 3000) runs before our RequestBodyBufferingFilter
        // (priority USER-1 = 4999) and triggers the BadMessageException first, so the
        // request body is NOT buffered — Req-Body will not appear in the detail.
        // The chunked upload also causes HTTP.send() to block for a few seconds while TCP
        // drains, so we allow a generous assertExpectations timeout.
        try (SystemLogVerifier verifier = SystemLogVerifier.builder()
                .expect(LogExpectation.builder()
                        .logger(RequestLogger.class)
                        .level(System.Logger.Level.WARNING)
                        .predicate(e -> e.message().contains("413")
                                && e.message().contains("POST")
                                && e.message().contains("Request Headers:"))
                        .build()
                )
                .build()) {
            byte[] largeBody = new byte[MAX_BYTES + 100];

            HTTP.send(
                    HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/ping"))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofInputStream(() -> new ByteArrayInputStream(largeBody)))
                            .build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            verifier.assertExpectations(Duration.ofSeconds(5));
        }
    }

    @Test
    void warningLevelDisabled_413DoesNotLogDetail() throws Exception {
        // When WARNING is disabled for RequestLogger, isDetailLoggable(413) returns false
        // and JsonErrorHandler skips buildDetail() — the ternary false-branch (L83) is taken.
        try (SystemLogVerifier verifier = SystemLogVerifier.builder()
                .configure(RequestLogger.class, System.Logger.Level.ERROR)
                .build()) {
            String largeBody = "{\"key\":\"" + "x".repeat(512) + "\"}";

            HTTP.send(
                    HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/ping"))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(largeBody))
                            .build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            assertEquals(0, verifier.warningCount());
        }
    }

    @Test
    void throwingEventListener_413ExceptionIsSwallowed() throws Exception {
        // JsonErrorHandler catches exceptions from eventListener.onRequestCompleted() and
        // logs them at WARNING.  Build a one-off server whose listener always throws from
        // onRequestCompleted; the 413 response must still be sent and the exception must
        // appear in the WARNING log.
        Server throwingServer = Server.builder()
                .configuration(
                        ServerConfiguration.builder()
                                .port(0)
                                .host("localhost")
                                .maxRequestSize(MAX_BYTES)
                                .serializer(new TestJsonSerializer())
                                .build()
                )
                .resources(new PingResource())
                .eventListener(new ThrowingEventListener())
                .components(TestLogging.forClass(ServerRequestSizeLimitTest.class))
                .build();

        throwingServer.start();

        try (SystemLogVerifier verifier = SystemLogVerifier.builder()
                .expect(LogExpectation.builder()
                        .logger(JsonErrorHandler.class)
                        .level(System.Logger.Level.WARNING)
                        .predicate(e -> e.message().contains("onRequestCompleted")
                                && null != e.thrown()
                                && e.thrown().getMessage().contains("Intentional test exception"))
                        .build()
                )
                .build()) {
            String largeBody = "{\"key\":\"" + "x".repeat(512) + "\"}";

            HttpResponse<String> response = HTTP.send(
                    HttpRequest.newBuilder(URI.create("http://localhost:" + throwingServer.port() + "/ping"))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(largeBody))
                            .build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            // The listener threw, but the 413 response must still reach the client.
            assertEquals(413, response.statusCode());

            verifier.assertExpectations(Duration.ofSeconds(2));
        } finally {
            throwingServer.stop();
        }
    }

    @Test
    void throwingEventListener_warningLevelDisabled_exceptionIsStillSwallowed() throws Exception {
        // Same as the test above but with WARNING disabled for JsonErrorHandler — exercises
        // the isLoggable() false branch so the log call is skipped.  The 413 must still
        // be returned to the client.
        Server throwingServer = Server.builder()
                .configuration(
                        ServerConfiguration.builder()
                                .port(0)
                                .host("localhost")
                                .maxRequestSize(MAX_BYTES)
                                .serializer(new TestJsonSerializer())
                                .build()
                )
                .resources(new PingResource())
                .eventListener(new ThrowingEventListener())
                .components(TestLogging.forClass(ServerRequestSizeLimitTest.class))
                .build();

        throwingServer.start();

        try (SystemLogVerifier verifier = SystemLogVerifier.builder()
                .configure(JsonErrorHandler.class, System.Logger.Level.ERROR)
                .configure(RequestLogger.class, System.Logger.Level.ERROR)
                .build()) {
            String largeBody = "{\"key\":\"" + "x".repeat(512) + "\"}";

            HttpResponse<String> response = HTTP.send(
                    HttpRequest.newBuilder(URI.create("http://localhost:" + throwingServer.port() + "/ping"))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(largeBody))
                            .build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            assertEquals(413, response.statusCode());
            assertEquals(0, verifier.warningCount());
        } finally {
            throwingServer.stop();
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static final class ThrowingEventListener implements ServerEventListener {
        @Override
        public void onRequestCompleted(RequestCompletedEvent event) {
            throw new RuntimeException("Intentional test exception from onRequestCompleted");
        }
    }
}

