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

class ServerGzipTest {
    // Two servers: one with gzip enabled, one without.
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private static Server gzipServer;
    private static int gzipPort;

    private static Server plainServer;
    private static int plainPort;

    @BeforeAll
    static void startServers() {
        gzipServer = Server.builder()
                .configuration(
                        ServerConfiguration.builder()
                                .port(0)
                                .host("localhost")
                                .serializer(new TestJsonSerializer())
                                .gzip()
                                .build()
                )
                .resources(new PingResource())
                .components(TestLogging.forClass(ServerGzipTest.class))
                .build();

        plainServer = Server.builder()
                .configuration(
                        ServerConfiguration.builder()
                                .port(0)
                                .host("localhost")
                                .serializer(new TestJsonSerializer())
                                .build()
                )
                .resources(new PingResource())
                .components(TestLogging.forClass(ServerGzipTest.class))
                .build();

        gzipServer.start();
        plainServer.start();

        gzipPort = gzipServer.port();
        plainPort = plainServer.port();
    }

    @AfterAll
    static void stopServers() {
        if (null != gzipServer) {
            gzipServer.stop();
        }

        if (null != plainServer) {
            plainServer.stop();
        }
    }

    // -------------------------------------------------------------------------
    // Gzip enabled
    // -------------------------------------------------------------------------

    @Test
    void gzipEnabled_withAcceptEncoding_responseIsCompressed() throws Exception {
        HttpResponse<byte[]> response = HTTP.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + gzipPort + "/ping"))
                        .header("Accept-Encoding", "gzip")
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofByteArray()
        );

        assertEquals(200, response.statusCode());

        // The JDK HttpClient does not auto-decompress HTTP/1.1 gzip responses, so the
        // Content-Encoding header is visible and the body contains compressed bytes.
        assertTrue(
                response.headers()
                        .firstValue("Content-Encoding")
                        .orElse("")
                        .contains("gzip"),
                "Expected Content-Encoding: gzip but got: "
                        + response.headers().firstValue("Content-Encoding").orElse("(none)")
        );
    }

    @Test
    void gzipEnabled_withoutAcceptEncoding_responseIsNotCompressed() throws Exception {
        HttpResponse<String> response = HTTP.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + gzipPort + "/ping"))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );

        assertEquals(200, response.statusCode());
        assertFalse(
                response.headers()
                        .firstValue("Content-Encoding")
                        .orElse("")
                        .contains("gzip")
        );
        assertTrue(response.body().contains("pong"));
    }

    // -------------------------------------------------------------------------
    // Gzip disabled
    // -------------------------------------------------------------------------

    @Test
    void gzipDisabled_withAcceptEncoding_responseIsNotCompressed() throws Exception {
        HttpResponse<String> response = HTTP.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + plainPort + "/ping"))
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
                        .contains("gzip")
        );
        assertTrue(response.body().contains("pong"));
    }

    @Test
    void nullMediaTypeResponse_notCompressed() throws Exception {
        // GET /ping/no-content returns 204 No Content — no entity, no media type.
        // shouldCompress() hits the null == responseContext.getMediaType() guard and returns false.
        HttpResponse<byte[]> response = HTTP.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + gzipPort + "/ping/no-content"))
                        .header("Accept-Encoding", "gzip")
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofByteArray()
        );

        assertEquals(204, response.statusCode());
        assertFalse(
                response.headers()
                        .firstValue("Content-Encoding")
                        .orElse("")
                        .contains("gzip"),
                "204 No Content response must not be gzip-compressed"
        );
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------
}

