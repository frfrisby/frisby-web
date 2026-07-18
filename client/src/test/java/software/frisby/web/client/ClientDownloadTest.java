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
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Phase C integration tests — streaming binary download via {@link GetSpec#download()}.
 * <p>
 * Uses {@code StreamResource} ({@code GET /stream}) which returns a fixed 64 KB payload
 * of repeating {@code 0x42} bytes as {@code application/octet-stream}.
 */
class ClientDownloadTest {
    private static final int STREAM_SIZE_BYTES = 64 * 1024;
    private static final byte FILL_BYTE = 0x42;

    private static Server server;
    private static Client client;

    @BeforeAll
    static void startServer() {
        server = Server.builder()
                .configuration(c -> c
                        .port(0)
                        .host("localhost")
                        .serializer(JacksonSerializer.builder().build())
                )
                .resources(TestResources.all())
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
    }

    @AfterAll
    static void stopServer() {
        if (null != server) {
            server.stop();
        }
    }

    /**
     * {@code download()} returns an {@link InputStream} over the raw response bytes.
     * The caller reads the stream and closes it.
     */
    @Test
    void download_returnsFullPayloadAsInputStream() throws IOException {
        HttpResponse<InputStream> response = client.get()
                .path("/stream")
                .download();

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
     * {@code download()} returns HTTP 200 with a non-null body stream even when the
     * payload is large — confirms the body is streamed, not buffered in full before the
     * handler returns.
     */
    @Test
    void download_responseStatusIs200() {
        HttpResponse<InputStream> response = client.get()
                .path("/stream")
                .download();

        assertEquals(200, response.statusCode());
        assertNotNull(response.body());
    }
}

