package software.frisby.web.server;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Integration tests for the h2c (HTTP/2 cleartext) connector path in {@link DefaultServer}.
 * <p>
 * Each test creates its own server on an OS-assigned ephemeral port so there are
 * no port-conflict races between tests.  The JDK {@link HttpClient} is configured
 * with {@link HttpClient.Version#HTTP_2} to exercise the h2c upgrade path.
 */
class ServerHttp2cTest {
    private static Server buildH2cServer() {
        return Server.builder()
                .configuration(
                        ServerConfiguration.builder()
                                .port(0)
                                .host("localhost")
                                .serializer(new TestJsonSerializer())
                                .http2()
                                .build()
                )
                .resources(new PingResource())
                .components(TestLogging.forClass(ServerHttp2cTest.class))
                .build();
    }

    private static Server buildHttp11Server() {
        return Server.builder()
                .configuration(
                        ServerConfiguration.builder()
                                .port(0)
                                .host("localhost")
                                .serializer(new TestJsonSerializer())
                                .build()
                )
                .resources(new PingResource())
                .components(TestLogging.forClass(ServerHttp2cTest.class))
                .build();
    }

    // -------------------------------------------------------------------------
    // h2c — HTTP/2 cleartext
    // -------------------------------------------------------------------------

    @Test
    void h2cServer_negotiatesHttp2_whenClientAdvertisesH2() throws Exception {
        Server server = buildH2cServer();

        try {
            server.start();

            HttpClient client = HttpClient.newBuilder()
                    .version(HttpClient.Version.HTTP_2)
                    .build();

            HttpResponse<String> response = client.send(
                    HttpRequest.newBuilder(URI.create("http://localhost:" + server.port() + "/ping"))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            assertEquals(200, response.statusCode());
            assertEquals(HttpClient.Version.HTTP_2, response.version());
        } finally {
            server.stop();
        }
    }

    @Test
    void h2cServer_fallsBackToHttp11_whenClientOffersOnlyHttp11() throws Exception {
        Server server = buildH2cServer();

        try {
            server.start();

            HttpClient client = HttpClient.newBuilder()
                    .version(HttpClient.Version.HTTP_1_1)
                    .build();

            HttpResponse<String> response = client.send(
                    HttpRequest.newBuilder(URI.create("http://localhost:" + server.port() + "/ping"))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            assertEquals(200, response.statusCode());
            assertEquals(HttpClient.Version.HTTP_1_1, response.version());
        } finally {
            server.stop();
        }
    }

    @Test
    void http11Server_servesHttp11_whenHttp2NotConfigured() throws Exception {
        Server server = buildHttp11Server();

        try {
            server.start();

            HttpClient client = HttpClient.newBuilder()
                    .version(HttpClient.Version.HTTP_1_1)
                    .build();

            HttpResponse<String> response = client.send(
                    HttpRequest.newBuilder(URI.create("http://localhost:" + server.port() + "/ping"))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            assertEquals(200, response.statusCode());
            assertEquals(HttpClient.Version.HTTP_1_1, response.version());
        } finally {
            server.stop();
        }
    }

    @Test
    void h2cServer_uri_returnsHttpScheme() {
        Server server = buildH2cServer();

        try {
            server.start();

            URI uri = server.uri();

            assertEquals("http", uri.getScheme());
            assertEquals("localhost", uri.getHost());
            assertEquals(server.port(), uri.getPort());
        } finally {
            server.stop();
        }
    }
}

