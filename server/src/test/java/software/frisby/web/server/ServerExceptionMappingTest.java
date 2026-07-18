package software.frisby.web.server;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests confirming that the built-in exception mappers prevent
 * sensitive information from reaching callers on security-related responses.
 */
class ServerExceptionMappingTest {
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
                .components(TestLogging.forClass(ServerExceptionMappingTest.class))
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
    // Body stripping — security-sensitive status codes
    // -------------------------------------------------------------------------

    @Test
    void unauthorized_bodyIsStripped() throws Exception {
        HttpResponse<String> response = get("/ping/unauthorized");

        assertEquals(401, response.statusCode());
        assertTrue(
                response.body().isBlank(),
                "401 response must have no body; got: " + response.body()
        );
        assertTrue(
                response.headers().firstValue("Content-Type").isEmpty(),
                "401 response must have no Content-Type header"
        );
    }

    @Test
    void forbidden_bodyIsStripped() throws Exception {
        HttpResponse<String> response = get("/ping/forbidden");

        assertEquals(403, response.statusCode());
        assertTrue(
                response.body().isBlank(),
                "403 response must have no body; got: " + response.body()
        );
        assertTrue(
                response.headers().firstValue("Content-Type").isEmpty(),
                "403 response must have no Content-Type header"
        );
    }

    @Test
    void unhandledException_returns500WithNoBody() throws Exception {
        HttpResponse<String> response = get("/ping/fail");

        assertEquals(500, response.statusCode());
        assertTrue(
                response.body().isBlank(),
                "500 response must have no body; got: " + response.body()
        );
        assertTrue(
                response.headers().firstValue("Content-Type").isEmpty(),
                "500 response must have no Content-Type header"
        );
    }

    // POST variants — request body must not influence the stripping behaviour

    @Test
    void unauthorizedPost_bodyIsStripped() throws Exception {
        HttpResponse<String> response = postJson(
                "/ping/unauthorized",
                "{\"user\":\"frank@example.com\",\"token\":\"secret\"}"
        );

        assertEquals(401, response.statusCode());
        assertTrue(
                response.body().isBlank(),
                "401 POST response must have no body; got: " + response.body()
        );
        assertTrue(
                response.headers().firstValue("Content-Type").isEmpty(),
                "401 POST response must have no Content-Type header"
        );
    }

    @Test
    void forbiddenPost_bodyIsStripped() throws Exception {
        HttpResponse<String> response = postJson(
                "/ping/forbidden",
                "{\"user\":\"frank@example.com\",\"role\":\"guest\"}"
        );

        assertEquals(403, response.statusCode());
        assertTrue(
                response.body().isBlank(),
                "403 POST response must have no body; got: " + response.body()
        );
        assertTrue(
                response.headers().firstValue("Content-Type").isEmpty(),
                "403 POST response must have no Content-Type header"
        );
    }

    @Test
    void unhandledExceptionPost_returns500WithNoBody() throws Exception {
        HttpResponse<String> response = postJson(
                "/ping/fail",
                "{\"action\":\"trigger-failure\"}"
        );

        assertEquals(500, response.statusCode());
        assertTrue(
                response.body().isBlank(),
                "500 POST response must have no body; got: " + response.body()
        );
        assertTrue(
                response.headers().firstValue("Content-Type").isEmpty(),
                "500 POST response must have no Content-Type header"
        );
    }

    // Message-only exceptions — WebApplicationExceptionMapper IS invoked (no embedded entity)

    @Test
    void unauthorizedMessageOnly_returns401WithNoBody() throws Exception {
        HttpResponse<String> response = get("/ping/unauthorized-message");

        assertEquals(401, response.statusCode());
        assertTrue(
                response.body().isBlank(),
                "401 message-only response must have no body; got: " + response.body()
        );
    }

    @Test
    void forbiddenMessageOnly_returns403WithNoBody() throws Exception {
        HttpResponse<String> response = get("/ping/forbidden-message");

        assertEquals(403, response.statusCode());
        assertTrue(
                response.body().isBlank(),
                "403 message-only response must have no body; got: " + response.body()
        );
    }

    // -------------------------------------------------------------------------
    // Body pass-through — safe status codes
    // -------------------------------------------------------------------------

    @Test
    void badRequest_bodyIsPreserved() throws Exception {
        HttpResponse<String> response = get("/ping/bad-request");

        assertEquals(400, response.statusCode());
        assertFalse(
                response.body().isBlank(),
                "400 response must include error detail"
        );
        assertTrue(response.body().contains("invalid-input"));
    }

    @Test
    void notFound_returns404() throws Exception {
        HttpResponse<String> response = get("/does-not-exist");

        assertEquals(404, response.statusCode());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static HttpResponse<String> get(String path) throws Exception {
        return HTTP.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );
    }

    private static HttpResponse<String> postJson(String path, String jsonBody) throws Exception {
        return HTTP.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );
    }
}

