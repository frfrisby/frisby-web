package software.frisby.web.client;

import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.frisby.web.serial.jackson.JacksonSerializer;
import software.frisby.web.server.Server;
import software.frisby.web.test.TestLogging;
import software.frisby.web.test.TestResources;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpCookie;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase C integration tests — streaming binary download via {@link DownloadSpec#stream()}.
 * <p>
 * Uses {@code StreamResource} ({@code GET /stream}) which returns a fixed 64 KB payload
 * of repeating {@code 0x42} bytes as {@code application/octet-stream}, and the same
 * {@link SseStreamTestResource} {@link ClientSseStreamTest} uses — reused here rather
 * than duplicated, since {@link DownloadSpec#stream()} doesn't care about {@code
 * Content-Type} any more than {@code SseSpec#stream()} does.
 */
class ClientDownloadTest {
    private static final int STREAM_SIZE_BYTES = 64 * 1024;
    private static final byte FILL_BYTE = 0x42;

    private static Server server;
    private static Client client;

    /**
     * A separate client with a deliberately short {@code readTimeout()} (and thus,
     * since {@link ClientConfiguration#idleTimeout()} is never set explicitly, the same
     * short fallback {@code idleTimeout}), used only by the {@code idleTimeout}-related
     * tests below — kept separate from {@link #client} so those tests don't affect the
     * generous 30-second timeout the large-payload tests above rely on.
     */
    private static Client shortTimeoutClient;

    @BeforeAll
    static void startServer() {
        server = Server.builder()
                .configuration(c -> c
                        .port(0)
                        .host("localhost")
                        .serializer(JacksonSerializer.builder().build())
                )
                .resources(TestResources.all())
                .resources(new SseStreamTestResource())
                .components(
                        new MultiPartFeature(),
                        TestLogging.forClass(ClientDownloadTest.class)
                )
                .build();

        server.start();

        client = Client.builder()
                .configuration(c -> c
                        .uri(server.uri())
                        .connectTimeout(Duration.ofSeconds(5))
                        .readTimeout(Duration.ofSeconds(30))
                        .serializer(JacksonSerializer.builder().build())
                )
                .build();

        shortTimeoutClient = Client.builder()
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

    /**
     * {@code stream()} returns an {@link InputStream} over the raw response bytes.
     * The caller reads the stream and closes it.
     */
    @Test
    void download_returnsFullPayloadAsInputStream() throws IOException {
        HttpResponse<InputStream> response = client.download()
                .path("/stream")
                .stream();

        assertEquals(200, response.statusCode());
        assertNotNull(response.body());

        try (InputStream stream = response.body()) {
            byte[] bytes = stream.readAllBytes();

            assertEquals(STREAM_SIZE_BYTES, bytes.length);

            for (byte b : bytes) {
                assertEquals(FILL_BYTE, b);
            }
        }
    }

    /**
     * {@code stream()} returns HTTP 200 with a non-null body stream even when the
     * payload is large — confirms the body is streamed, not buffered in full before the
     * handler returns.
     */
    @Test
    void download_responseStatusIs200() throws IOException {
        HttpResponse<InputStream> response = client.download()
                .path("/stream")
                .stream();

        // try-with-resources so the stream is closed even if an assertion below
        // throws — otherwise this test's registration lingers in the shared,
        // JVM-wide StreamWatchdog registry for up to the client's default 30s idle
        // timeout, keeping the registry non-empty for the rest of this module's
        // test suite (which runs in a single, reused Surefire fork) and starving
        // StreamWatchdogTest's own coverage of the registry-becomes-empty
        // cancellation path.
        try (InputStream stream = response.body()) {
            assertEquals(200, response.statusCode());
            assertNotNull(stream);
        }
    }

    // -------------------------------------------------------------------------
    // Navigation methods — path(), parameter(), header(String, String...),
    // cookie(), security() — mirrors ClientSseStreamTest's equivalent section,
    // exercised here against DownloadRequest instead of SseRequest.
    // -------------------------------------------------------------------------

    @Test
    void download_pathWithSingleParameter_substitutesPlaceholder() throws IOException {
        HttpResponse<InputStream> response = client.download()
                .path("/sse-stream-test/{id}/echo-all", "id", "single-param-id")
                .stream();

        assertEquals(200, response.statusCode());

        try (InputStream stream = response.body()) {
            String body = new String(stream.readAllBytes());
            assertTrue(body.contains("id=single-param-id"));
        }
    }

    @Test
    void download_pathWithParameterVarargs_substitutesPlaceholders() throws IOException {
        HttpResponse<InputStream> response = client.download()
                .path("/sse-stream-test/{id}/echo-all", PathParameter.of("id", "varargs-id"))
                .stream();

        assertEquals(200, response.statusCode());

        try (InputStream stream = response.body()) {
            String body = new String(stream.readAllBytes());
            assertTrue(body.contains("id=varargs-id"));
        }
    }

    @Test
    void download_singleQueryParameter_isSent() throws IOException {
        HttpResponse<InputStream> response = client.download()
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
    void download_multiValueQueryParameter_isSent() throws IOException {
        HttpResponse<InputStream> response = client.download()
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
    void download_singleHeader_isSent() throws IOException {
        HttpResponse<InputStream> response = client.download()
                .path("/sse-stream-test/{id}/echo-all", "id", "header-single")
                .header("X-Test-Header", "v1")
                .stream();

        assertEquals(200, response.statusCode());

        try (InputStream stream = response.body()) {
            String body = new String(stream.readAllBytes());
            assertTrue(body.contains("header=v1"));
        }
    }

    @Test
    void download_multiValueHeader_isSent() throws IOException {
        HttpResponse<InputStream> response = client.download()
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
    void download_cookie_isSent() throws IOException {
        HttpResponse<InputStream> response = client.download()
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
    void download_securityProviderOverride_appliesHeader() throws IOException {
        HttpResponse<InputStream> response = client.download()
                .path("/sse-stream-test/{id}/echo-all", "id", "security-test")
                .security(request -> request.addHeader(Headers.AUTHORIZATION, "Bearer test-token"))
                .stream();

        assertEquals(200, response.statusCode());

        try (InputStream stream = response.body()) {
            String body = new String(stream.readAllBytes());
            assertTrue(body.contains("auth=Bearer test-token"));
        }
    }

    // -------------------------------------------------------------------------
    // idleTimeout(Duration) — mirrors the equivalent ClientSseStreamTest section.
    // -------------------------------------------------------------------------

    /**
     * The {@code /sse-stream-test/slow} endpoint holds the connection open for 2
     * seconds between its first and second writes — well past
     * {@link #shortTimeoutClient}'s 500 ms {@code readTimeout()}, which {@link
     * ClientConfiguration#idleTimeout()} falls back to since it is never set
     * explicitly. An explicit per-call {@link DownloadSpec#idleTimeout(Duration)}
     * override wide enough to cover the gap lets the full payload arrive.
     */
    @Test
    void download_idleTimeoutOverride_survivesSilencePastGlobalReadTimeout() throws IOException {
        HttpResponse<InputStream> response = shortTimeoutClient.download()
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
     * Complements {@code download_idleTimeoutOverride_survivesSilencePastGlobalReadTimeout}
     * above: the same 2-second gap, but with no per-call override, so {@link
     * #shortTimeoutClient}'s 500 ms {@code readTimeout()} applies as the fallback
     * {@code idleTimeout} — proving the post-first-byte idle watchdog (wired through
     * {@link StreamRequestSupport}, shared with {@link SseSpec}) is genuinely in effect
     * for downloads too, closing the connection and unblocking the pending read with an
     * {@link IOException} well before the second write would otherwise arrive.
     */
    @Test
    void download_longHeldConnectionPastDefaultIdleTimeout_isClosedByWatchdog() throws IOException {
        HttpResponse<InputStream> response = shortTimeoutClient.download()
                .path("/sse-stream-test/slow")
                .stream();

        assertEquals(200, response.statusCode());

        try (InputStream stream = response.body()) {
            assertThrows(IOException.class, stream::readAllBytes);
        }
    }

    @Test
    void download_nullIdleTimeout_throwsNullValueException() {
        assertThrows(
                software.frisby.core.validation.NullValueException.class,
                () -> client.download().path("/stream").idleTimeout(null)
        );
    }

    @Test
    void download_zeroIdleTimeout_throwsDurationOutsideRangeException() {
        assertThrows(
                software.frisby.core.validation.DurationOutsideRangeException.class,
                () -> client.download().path("/stream").idleTimeout(Duration.ZERO)
        );
    }

    @Test
    void download_negativeIdleTimeout_throwsDurationOutsideRangeException() {
        assertThrows(
                software.frisby.core.validation.DurationOutsideRangeException.class,
                () -> client.download().path("/stream").idleTimeout(Duration.ofSeconds(-1))
        );
    }
}


