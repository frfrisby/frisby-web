package software.frisby.web.server;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.frisby.web.test.log.LogExpectation;
import software.frisby.web.test.log.SystemLogVerifier;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerRoutingTest {
    private static final HttpClient HTTP = HttpClient.newHttpClient();

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
                                .build()
                )
                .resources(new PingResource())
                .components(TestLogging.forClass(ServerRoutingTest.class))
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
    // Routing
    // -------------------------------------------------------------------------

    @Test
    void getRequest_returns200WithJsonBody() throws Exception {
        HttpResponse<String> response = get("/ping");

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("pong"));
    }

    @Test
    void getRequestWithPathParam_returns200WithId() throws Exception {
        HttpResponse<String> response = get("/ping/abc-123");

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("abc-123"));
    }

    @Test
    void postRequest_echoesBodyBack() throws Exception {
        String body = "{\"key\":\"value\"}";

        HttpResponse<String> response = HTTP.send(
                HttpRequest.newBuilder(uri("/ping"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("value"));
    }

    @Test
    void postRequest_typedPojo_echoesBodyBack() throws Exception {
        // POST /ping/typed accepts and returns a PingRequest record (no generic type
        // parameters).  JsonMessageBodyProvider.readFrom is called with
        // type = genericType = PingRequest.class, taking the simple class-based
        // deserialize path.  writeTo serializes the POJO back to JSON.
        String body = "{\"message\":\"typed-pong\"}";

        HttpResponse<String> response = HTTP.send(
                HttpRequest.newBuilder(uri("/ping/typed"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );

        assertEquals(200, response.statusCode());
        assertTrue(
                response.body().contains("typed-pong"),
                "Response must echo the message field; got: " + response.body()
        );
    }

    @Test
    void unknownPath_returns404() throws Exception {
        HttpResponse<String> response = get("/does-not-exist");

        assertEquals(404, response.statusCode());
    }

    // -------------------------------------------------------------------------
    // Request logging
    // -------------------------------------------------------------------------

    @Test
    void completedRequest_logsAtInfo() throws Exception {
        // With TRACE suppressed and INFO enabled the one-liner fires at INFO.
        try (SystemLogVerifier verifier = SystemLogVerifier.builder()
                .configure(RequestLogger.class, System.Logger.Level.INFO)
                .expect(LogExpectation.builder()
                        .logger(RequestLogger.class)
                        .level(System.Logger.Level.INFO)
                        .predicate(e -> e.message().contains("GET")
                                && e.message().contains("/ping")
                                && e.message().contains("200"))
                        .build()
                )
                .build()) {
            get("/ping");

            // The server logs from a background thread (Jersey's server thread), so a
            // short wait is needed to guarantee the log event is dispatched before we assert.
            verifier.assertExpectations(Duration.ofSeconds(2));
        }
    }

    @Test
    void infoLevelDisabled_completedRequestNotLogged() throws Exception {
        try (SystemLogVerifier verifier = SystemLogVerifier.builder()
                .configure(RequestLogger.class, System.Logger.Level.WARNING)
                .build()) {
            get("/ping");

            // With INFO disabled, logRequest's isLoggable guard prevents the call.
            assertEquals(0, verifier.infoCount());
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
}



