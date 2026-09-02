package software.frisby.web.client;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.frisby.web.client.exception.NotFoundException;
import software.frisby.web.serial.jackson.JacksonSerializer;
import software.frisby.web.server.Server;
import software.frisby.web.server.ServerConfiguration;
import software.frisby.web.test.TestLogging;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpCookie;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Chunk 2 integration tests — {@link SseSpec#stream()} / {@link SseSpec#streamAsync()}
 * against {@link SseStreamTestResource}, a minimal hand-written SSE-shaped resource local
 * to this test (the shared {@code test-support} resource is added separately).
 */
class ClientSseStreamTest {
    private static Server server;
    private static Client client;

    @BeforeAll
    static void startServer() {
        server = Server.builder()
                .configuration(
                        ServerConfiguration.builder()
                                .port(0)
                                .host("localhost")
                                .serializer(JacksonSerializer.builder().build())
                                .build()
                )
                .resources(new SseStreamTestResource())
                .components(TestLogging.forClass(ClientSseStreamTest.class))
                .build();

        server.start();

        // Deliberately short read timeout — proves a stream held open longer than this
        // (see LongHold below) is not prematurely closed, since SseRequest.stream() only
        // needs the timeout to bound time-to-headers, not the full body read.
        client = Client.builder()
                .configuration(c -> c
                        .uri(server.uri())
                        .connectTimeout(Duration.ofSeconds(5))
                        .readTimeout(Duration.ofMillis(500))
                        .serializer(JacksonSerializer.builder().build())
                )
                .build();
    }

    @AfterAll
    static void stopServer() {
        if (null != server) {
            server.stop();
        }
    }

    @Test
    void stream_defaultAcceptHeader_isTextEventStream() throws IOException {
        HttpResponse<InputStream> response = client.sse()
                .path("/sse-stream-test/accept-echo")
                .stream();

        assertEquals(200, response.statusCode());

        try (InputStream stream = response.body()) {
            String body = new String(stream.readAllBytes());
            assertTrue(body.contains("data: text/event-stream"));
        }
    }

    @Test
    void stream_callerSetAcceptHeader_isNotOverridden() throws IOException {
        HttpResponse<InputStream> response = client.sse()
                .path("/sse-stream-test/accept-echo")
                .header(Headers.ACCEPT, "text/csv")
                .stream();

        assertEquals(200, response.statusCode());

        try (InputStream stream = response.body()) {
            String body = new String(stream.readAllBytes());
            assertTrue(body.contains("data: text/csv"));
        }
    }

    /**
     * The server holds the connection open for 2 seconds between the first and second
     * events — well past the client's 500 ms {@code readTimeout()}.  If the timeout
     * bounded the full body read (rather than just time-to-headers), this would throw
     * a {@code ReadTimeoutException} before the second event ever arrived.
     */
    @Test
    void stream_longHeldConnectionPastReadTimeout_isNotPrematurelyClosed() throws IOException {
        HttpResponse<InputStream> response = client.sse()
                .path("/sse-stream-test/slow")
                .stream();

        assertEquals(200, response.statusCode());

        try (InputStream stream = response.body()) {
            String body = new String(stream.readAllBytes());

            assertTrue(body.contains("event: first"));
            assertTrue(body.contains("event: second"));
        }
    }

    @Test
    void streamAsync_returnsFullPayload() throws ExecutionException, InterruptedException, IOException {
        HttpResponse<InputStream> response = client.sse()
                .path("/sse-stream-test/accept-echo")
                .streamAsync()
                .get();

        assertEquals(200, response.statusCode());
        assertNotNull(response.body());

        try (InputStream stream = response.body()) {
            String body = new String(stream.readAllBytes());
            assertTrue(body.contains("data: text/event-stream"));
        }
    }

    /**
     * {@code /does-not-exist} is an unmapped path; in this embedded Jetty/Jersey stack
     * that produces a blank {@code 404} body, exercising the same
     * {@code body.isBlank() = true} branch as {@code /empty-error} below.
     */
    @Test
    void stream_notFoundPath_throwsNotFoundException() {
        assertThrows(
                NotFoundException.class,
                () -> client.sse().path("/sse-stream-test/does-not-exist").stream()
        );
    }

    /**
     * Exercises the blank-body branch of the error-mapping lambda in
     * {@code streamBodyHandler} via a resource that explicitly returns a {@code 404}
     * with no entity at all.
     */
    @Test
    void stream_notFoundWithEmptyBody_throwsNotFoundException() {
        assertThrows(
                NotFoundException.class,
                () -> client.sse().path("/sse-stream-test/empty-error").stream()
        );
    }

    /**
     * Exercises the {@code body.isBlank() = false} branch of the error-mapping lambda
     * in {@code streamBodyHandler}, confirming the raw response body is preserved on
     * the thrown exception.
     */
    @Test
    void stream_notFoundWithNonBlankBody_throwsNotFoundExceptionWithBody() {
        NotFoundException ex = assertThrows(
                NotFoundException.class,
                () -> client.sse().path("/sse-stream-test/error-with-body").stream()
        );

        assertTrue(ex.body().isPresent());
        assertEquals("stream not found", ex.body().get());
    }

    // -------------------------------------------------------------------------
    // Navigation methods — path(), parameter(), header(String, String...),
    // cookie(), security()
    // -------------------------------------------------------------------------

    @Test
    void stream_pathWithSingleParameter_substitutesPlaceholder() throws IOException {
        HttpResponse<InputStream> response = client.sse()
                .path("/sse-stream-test/{id}/echo-all", "id", "single-param-id")
                .stream();

        assertEquals(200, response.statusCode());

        try (InputStream stream = response.body()) {
            String body = new String(stream.readAllBytes());
            assertTrue(body.contains("id=single-param-id"));
        }
    }

    @Test
    void stream_pathWithParameterVarargs_substitutesPlaceholders() throws IOException {
        HttpResponse<InputStream> response = client.sse()
                .path("/sse-stream-test/{id}/echo-all", PathParameter.of("id", "varargs-id"))
                .stream();

        assertEquals(200, response.statusCode());

        try (InputStream stream = response.body()) {
            String body = new String(stream.readAllBytes());
            assertTrue(body.contains("id=varargs-id"));
        }
    }

    @Test
    void stream_singleQueryParameter_isSent() throws IOException {
        HttpResponse<InputStream> response = client.sse()
                .path("/sse-stream-test/{id}/echo-all", "id", "query-single")
                .parameter("tag", "alpha")
                .stream();

        assertEquals(200, response.statusCode());

        try (InputStream stream = response.body()) {
            String body = new String(stream.readAllBytes());
            assertTrue(body.contains("tags=alpha"));
        }
    }

    @Test
    void stream_multiValueQueryParameter_isSent() throws IOException {
        HttpResponse<InputStream> response = client.sse()
                .path("/sse-stream-test/{id}/echo-all", "id", "query-multi")
                .parameter("tag", "alpha", "beta")
                .stream();

        assertEquals(200, response.statusCode());

        try (InputStream stream = response.body()) {
            String body = new String(stream.readAllBytes());
            assertTrue(body.contains("tags=alpha,beta"));
        }
    }

    @Test
    void stream_multiValueHeader_isSent() throws IOException {
        HttpResponse<InputStream> response = client.sse()
                .path("/sse-stream-test/{id}/echo-all", "id", "header-multi")
                .header("X-Test-Header", "v1", "v2")
                .stream();

        assertEquals(200, response.statusCode());

        try (InputStream stream = response.body()) {
            String body = new String(stream.readAllBytes());
            assertTrue(body.contains("header=v1,v2"));
        }
    }

    @Test
    void stream_cookie_isSent() throws IOException {
        HttpResponse<InputStream> response = client.sse()
                .path("/sse-stream-test/{id}/echo-all", "id", "cookie-test")
                .cookie(new HttpCookie("session", "cookie-value-1"))
                .stream();

        assertEquals(200, response.statusCode());

        try (InputStream stream = response.body()) {
            String body = new String(stream.readAllBytes());
            assertTrue(body.contains("cookie=cookie-value-1"));
        }
    }

    @Test
    void stream_securityProviderOverride_appliesHeader() throws IOException {
        HttpResponse<InputStream> response = client.sse()
                .path("/sse-stream-test/{id}/echo-all", "id", "security-test")
                .security(request -> request.addHeader(Headers.AUTHORIZATION, "Bearer test-token"))
                .stream();

        assertEquals(200, response.statusCode());

        try (InputStream stream = response.body()) {
            String body = new String(stream.readAllBytes());
            assertTrue(body.contains("auth=Bearer test-token"));
        }
    }
}

