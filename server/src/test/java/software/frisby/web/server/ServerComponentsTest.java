package software.frisby.web.server;

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

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the optional server components:
 * <ul>
 *   <li>Gzip response filter — {@code shouldCompress()} false branches not covered by {@link ServerGzipTest}</li>
 *   <li>{@link ServerEventListener} — exception-swallowing in event callbacks</li>
 * </ul>
 */
class ServerComponentsTest {
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    // Shared server for GZipResponseFilter tests — gzip() enables GZipEncoder + GZipResponseFilter.
    private static Server server;
    private static int port;

    @BeforeAll
    static void startServer() {
        server = Server.builder()
                .configuration(
                        ServerConfiguration.builder()
                                .port(0)
                                .host("localhost")
                                .serializer(new TestJsonSerializer())
                                .gzip()
                                .build()
                )
                .resources(new PingResource())
                .components(TestLogging.forClass(ServerComponentsTest.class))
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
    // GZipResponseFilter — shouldCompress() false branches not covered by ServerGzipTest
    // -------------------------------------------------------------------------

    @Nested
    class GZipResponseFilterTests {
        @Test
        void inputStreamResponse_notCompressed() throws Exception {
            // GET /ping/bytes returns an InputStream entity (APPLICATION_OCTET_STREAM).
            // shouldCompress() returns false at the "entity instanceof InputStream" check.
            HttpResponse<byte[]> response = HTTP.send(
                    HttpRequest.newBuilder(uri("/ping/bytes"))
                            .header("Accept-Encoding", "gzip")
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofByteArray()
            );

            assertEquals(200, response.statusCode());
            assertFalse(
                    response.headers()
                            .firstValue("Content-Encoding")
                            .orElse("")
                            .contains("gzip"),
                    "InputStream response must not be gzip-compressed"
            );
        }

        @Test
        void nonJsonMediaType_notCompressed() throws Exception {
            // GET /ping/text returns text/plain.
            // shouldCompress() returns false at the "isCompatible(APPLICATION_JSON)" check.
            HttpResponse<String> response = HTTP.send(
                    HttpRequest.newBuilder(uri("/ping/text"))
                            .header("Accept-Encoding", "gzip")
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            assertEquals(200, response.statusCode());
            assertFalse(
                    response.headers()
                            .firstValue("Content-Encoding")
                            .orElse("")
                            .contains("gzip"),
                    "text/plain response must not be gzip-compressed"
            );
        }

        @Test
        void nonGzipAcceptEncoding_notCompressed() throws Exception {
            // Accept-Encoding: deflate — acceptEncoding is non-null but doesn't contain "gzip".
            // shouldCompress() returns false at the final "acceptEncoding.contains(gzip)" check.
            HttpResponse<String> response = HTTP.send(
                    HttpRequest.newBuilder(uri("/ping"))
                            .header("Accept-Encoding", "deflate")
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            assertEquals(200, response.statusCode());
            assertFalse(
                    response.headers()
                            .firstValue("Content-Encoding")
                            .orElse("")
                            .contains("gzip"),
                    "deflate-only Accept-Encoding must not trigger gzip compression"
            );
        }
    }

    // -------------------------------------------------------------------------
    // RequestBodyBufferingFilter — filter-skip transparency
    // -------------------------------------------------------------------------

    @Nested
    class BodyBufferingFilterTests {
        @Test
        void multipartUpload_doesNotCorruptRequestStream() throws Exception {
            // POST a well-formed multipart body to /ping/upload — a resource that accepts
            // multipart/form-data as a raw InputStream and returns the byte count received.
            // RequestBodyBufferingFilter skips multipart bodies; this test proves that skip
            // is transparent — Jersey still receives the intact stream and the resource
            // method can read it successfully.
            String boundary = "boundary123";
            String multipartBody = "--" + boundary + "\r\n"
                    + "Content-Disposition: form-data; name=\"file\"; filename=\"hello.txt\"\r\n"
                    + "Content-Type: text/plain\r\n"
                    + "\r\n"
                    + "Hello, World!\r\n"
                    + "--" + boundary + "--";

            HttpResponse<String> response = HTTP.send(
                    HttpRequest.newBuilder(uri("/ping/upload"))
                            .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                            .POST(HttpRequest.BodyPublishers.ofString(multipartBody))
                            .build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            assertEquals(200, response.statusCode());
            assertTrue(
                    response.body().contains("\"received\""),
                    "Response must contain a 'received' byte count confirming the body was delivered"
            );
        }
    }

    // -------------------------------------------------------------------------
    // ServerEventListener exception swallowing
    // -------------------------------------------------------------------------

    @Test
    void throwingEventListener_onRequestCompleted_exceptionIsLogged() throws Exception {
        // Build a separate server whose event listener always throws from onRequestCompleted.
        // The exception must be swallowed and logged at WARNING — the server must stay healthy.
        Server throwingServer = Server.builder()
                .configuration(
                        ServerConfiguration.builder()
                                .port(0)
                                .host("localhost")
                                .serializer(new TestJsonSerializer())
                                .build()
                )
                .resources(new PingResource())
                .eventListener(new ThrowingEventListener())
                .components(TestLogging.forClass(ServerComponentsTest.class))
                .build();

        throwingServer.start();

        try (SystemLogVerifier verifier = SystemLogVerifier.builder()
                .expect(LogExpectation.builder()
                        .logger(ServerRequestEventListener.class)
                        .level(System.Logger.Level.WARNING)
                        .predicate(e -> e.message()
                                .contains("ServerEventListener.onRequestCompleted threw an exception."))
                        .build()
                )
                .build()) {
            // First request: onRequestCompleted throws — must be caught and logged as WARNING.
            HttpResponse<String> first = HTTP.send(
                    HttpRequest.newBuilder(URI.create("http://localhost:" + throwingServer.port() + "/ping"))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            assertEquals(200, first.statusCode());

            // Second request: server must still be running after the swallowed exception.
            HttpResponse<String> second = HTTP.send(
                    HttpRequest.newBuilder(URI.create("http://localhost:" + throwingServer.port() + "/ping"))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            assertEquals(200, second.statusCode());

            verifier.assertExpectations(Duration.ofSeconds(2));
            assertTrue(verifier.warningCount() > 0);
        } finally {
            throwingServer.stop();
        }
    }

    @Test
    void throwingEventListener_onRequestCompleted_exceptionIsNotLogged() throws Exception {
        // Build a separate server whose event listener always throws from onRequestCompleted.
        // The exception must be swallowed and logged at WARNING — the server must stay healthy.
        Server throwingServer = Server.builder()
                .configuration(
                        ServerConfiguration.builder()
                                .port(0)
                                .host("localhost")
                                .serializer(new TestJsonSerializer())
                                .build()
                )
                .resources(new PingResource())
                .eventListener(new ThrowingEventListener())
                .components(TestLogging.forClass(ServerComponentsTest.class))
                .build();

        throwingServer.start();

        try (SystemLogVerifier verifier = SystemLogVerifier.builder()
                .configure(ServerRequestEventListener.class, System.Logger.Level.ERROR) // ERROR is too high — the exception is logged at WARNING.
                .build()) {
            // First request: onRequestCompleted throws — must be caught and logged as WARNING.
            HttpResponse<String> first = HTTP.send(
                    HttpRequest.newBuilder(URI.create("http://localhost:" + throwingServer.port() + "/ping"))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            assertEquals(200, first.statusCode());

            // Second request: server must still be running after the swallowed exception.
            HttpResponse<String> second = HTTP.send(
                    HttpRequest.newBuilder(URI.create("http://localhost:" + throwingServer.port() + "/ping"))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            assertEquals(200, second.statusCode());

            verifier.assertExpectations(Duration.ofSeconds(2));
            assertEquals(0, verifier.warningCount(), "No WARNING log expected from the thrown exception.");
        } finally {
            throwingServer.stop();
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

    private static final class ThrowingEventListener implements ServerEventListener {
        @Override
        public void onRequestCompleted(RequestCompletedEvent event) {
            throw new RuntimeException("Intentional test exception from onRequestCompleted");
        }
    }
}
