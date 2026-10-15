package software.frisby.web.client;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.frisby.web.client.exception.NotFoundException;
import software.frisby.web.client.exception.ReadTimeoutException;
import software.frisby.web.serial.jackson.JacksonSerializer;
import software.frisby.web.server.Server;
import software.frisby.web.server.ServerConfiguration;
import software.frisby.web.test.TestLogging;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpCookie;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

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
     * events — well past the client's 500 ms {@code readTimeout()}, which
     * {@link ClientConfiguration#idleTimeout()} falls back to here since it is never set
     * explicitly. Unlike before the idle watchdog existed, {@code idleTimeout} now bounds
     * <strong>both</strong> time-to-first-byte and any subsequent post-first-byte stall
     * with the same value — so surviving a legitimately long gap between events requires
     * an explicit per-call override wide enough to cover it, exercised here via
     * {@link SseSpec#idleTimeout(Duration)}. See
     * {@code stream_longHeldConnectionPastDefaultIdleTimeout_isClosedByWatchdog} below for
     * the complementary case: the same 2-second gap, but relying on the short default,
     * which now does get closed.
     */
    @Test
    void stream_longHeldConnectionPastReadTimeout_isNotPrematurelyClosed() throws IOException {
        HttpResponse<InputStream> response = client.sse()
                .path("/sse-stream-test/slow")
                .idleTimeout(Duration.ofSeconds(5))
                .stream();

        assertEquals(200, response.statusCode());

        try (InputStream stream = response.body()) {
            String body = new String(stream.readAllBytes());

            assertTrue(body.contains("event: first"));
            assertTrue(body.contains("event: second"));
        }
    }

    /**
     * Complements {@code stream_longHeldConnectionPastReadTimeout_isNotPrematurelyClosed}
     * above: the same {@code /slow} endpoint and its 2-second gap between events, but with
     * no per-call {@link SseSpec#idleTimeout(Duration)} override, so the client's 500 ms
     * {@code readTimeout()} applies as the fallback {@code idleTimeout} — proving the
     * post-first-byte idle watchdog (wired through {@link StreamRequestSupport}, shared
     * with {@link DownloadSpec}) is genuinely in effect for SSE streams, closing the
     * connection and unblocking the pending read with an {@link IOException} well before
     * the second event would otherwise arrive.
     */
    @Test
    void stream_longHeldConnectionPastDefaultIdleTimeout_isClosedByWatchdog() throws IOException {
        HttpResponse<InputStream> response = client.sse()
                .path("/sse-stream-test/slow")
                .stream();

        assertEquals(200, response.statusCode());

        try (InputStream stream = response.body()) {
            assertThrows(IOException.class, stream::readAllBytes);
        }
    }


    /**
     * The server writes <strong>nothing at all</strong> — not even a partial frame — for
     * 2 seconds, well past the client's 500 ms {@code readTimeout()}, before writing its
     * first (and only) byte.  This is the empirical test for whether {@code readTimeout}
     * bounds "time to headers" or "time to first byte": {@code stream_longHeldConnectionPastReadTimeout_isNotPrematurelyClosed}
     * above proves reads (with a wide enough {@code idleTimeout}) can survive a gap
     * <em>after</em> a first byte has already arrived, but says nothing about what happens
     * when the server is silent from the very start of the connection — see
     * {@code temp/frisby-web-sse-reconnect-storm-investigation.md} §6.2.
     */
    @Test
    void stream_totallySilentPastReadTimeout_throwsReadTimeoutException() {
        assertThrows(
                ReadTimeoutException.class,
                () -> client.sse()
                        .path("/sse-stream-test/totally-silent")
                        .parameter("delayMs", "2000")
                        .stream()
        );
    }

    /**
     * Same scenario as above, over HTTP/2 — the JDK's internal exchange/timer plumbing
     * differs between HTTP/1.1 (chunked transfer) and HTTP/2 (headers + data frames), so
     * this confirms the "time to first byte" behavior is not an HTTP/1.1-only artifact.
     */
    @Test
    void stream_totallySilentPastReadTimeout_http2_throwsReadTimeoutException() {
        Client http2Client = Client.builder()
                .configuration(c -> c
                        .uri(server.uri())
                        .connectTimeout(Duration.ofSeconds(5))
                        .readTimeout(Duration.ofMillis(500))
                        .httpVersion(HttpClient.Version.HTTP_2)
                        .serializer(JacksonSerializer.builder().build())
                )
                .build();

        assertThrows(
                ReadTimeoutException.class,
                () -> http2Client.sse()
                        .path("/sse-stream-test/totally-silent")
                        .parameter("delayMs", "2000")
                        .stream()
        );
    }

    /**
     * {@link SseSpec#idleTimeout(Duration)} overrides the client's globally
     * configured {@code readTimeout()} (which {@link ClientConfiguration#idleTimeout()}
     * falls back to here, since it is never set explicitly) for this call only — proves
     * a stream that would otherwise throw per
     * {@code stream_totallySilentPastReadTimeout_throwsReadTimeoutException} above
     * succeeds once a longer, call-specific timeout is supplied, without touching the
     * shared {@code client}'s configuration used by every other test in this class.
     */
    @Test
    void stream_idleTimeoutOverride_survivesSilencePastGlobalReadTimeout() throws IOException {
        HttpResponse<InputStream> response = client.sse()
                .path("/sse-stream-test/totally-silent")
                .parameter("delayMs", "2000")
                .idleTimeout(Duration.ofSeconds(5))
                .stream();

        assertEquals(200, response.statusCode());

        try (InputStream stream = response.body()) {
            String body = new String(stream.readAllBytes());
            assertTrue(body.contains("event: first"));
        }
    }

    @Test
    void streamAsync_idleTimeoutOverride_survivesSilencePastGlobalReadTimeout()
            throws ExecutionException, InterruptedException, IOException {
        HttpResponse<InputStream> response = client.sse()
                .path("/sse-stream-test/totally-silent")
                .parameter("delayMs", "2000")
                .idleTimeout(Duration.ofSeconds(5))
                .streamAsync()
                .get();

        assertEquals(200, response.statusCode());

        try (InputStream stream = response.body()) {
            String body = new String(stream.readAllBytes());
            assertTrue(body.contains("event: first"));
        }
    }

    @Test
    void stream_nullIdleTimeout_throwsNullValueException() {
        assertThrows(
                software.frisby.core.validation.NullValueException.class,
                () -> client.sse().path("/sse-stream-test/accept-echo").idleTimeout(null)
        );
    }

    @Test
    void stream_zeroIdleTimeout_throwsDurationOutsideRangeException() {
        assertThrows(
                software.frisby.core.validation.DurationOutsideRangeException.class,
                () -> client.sse().path("/sse-stream-test/accept-echo").idleTimeout(Duration.ZERO)
        );
    }

    @Test
    void stream_negativeIdleTimeout_throwsDurationOutsideRangeException() {
        assertThrows(
                software.frisby.core.validation.DurationOutsideRangeException.class,
                () -> client.sse().path("/sse-stream-test/accept-echo").idleTimeout(Duration.ofSeconds(-1))
        );
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

