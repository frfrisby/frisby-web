package software.frisby.web.server;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Integration tests for the HTTPS / SSL connector path in {@link DefaultServer}.
 * <p>
 * Each test creates its own server on an OS-assigned ephemeral port so there are
 * no port-conflict races between tests.  The JDK {@link HttpClient} is configured
 * with {@link SslTestSupport#clientSslContext()} so it trusts the self-signed
 * test certificate without any changes to the JVM's default trust store.
 */
class ServerSslTest {
    private static Server buildHttpsServer() throws Exception {
        return Server.builder()
                .configuration(
                        ServerConfiguration.builder()
                                .port(0)
                                .host("localhost")
                                .serializer(new TestJsonSerializer())
                                .ssl(SslTestSupport.serverSslContext())
                                .build()
                )
                .resources(new PingResource())
                .components(TestLogging.forClass(ServerSslTest.class))
                .build();
    }

    private static Server buildHttp2Server() throws Exception {
        return Server.builder()
                .configuration(
                        ServerConfiguration.builder()
                                .port(0)
                                .host("localhost")
                                .serializer(new TestJsonSerializer())
                                .ssl(SslTestSupport.serverSslContext())
                                .http2()
                                .build()
                )
                .resources(new PingResource())
                .components(TestLogging.forClass(ServerSslTest.class))
                .build();
    }

    private static HttpClient buildHttpsClient() throws Exception {
        return HttpClient.newBuilder()
                .sslContext(SslTestSupport.clientSslContext())
                .build();
    }

    private static HttpClient buildHttp2Client() throws Exception {
        return HttpClient.newBuilder()
                .sslContext(SslTestSupport.clientSslContext())
                .version(HttpClient.Version.HTTP_2)
                .build();
    }

    private static HttpClient buildHttp11Client() throws Exception {
        return HttpClient.newBuilder()
                .sslContext(SslTestSupport.clientSslContext())
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    // -------------------------------------------------------------------------
    // Functional SSL behaviour
    // -------------------------------------------------------------------------

    @Test
    void httpsServer_startsAndServesRequests() throws Exception {
        Server server = buildHttpsServer();

        try {
            server.start();

            HttpResponse<String> response = buildHttpsClient().send(
                    HttpRequest.newBuilder(URI.create("https://localhost:" + server.port() + "/ping"))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            assertEquals(200, response.statusCode());
        } finally {
            server.stop();
        }
    }

    @Test
    void uri_withSsl_returnsHttpsScheme() throws Exception {
        Server server = buildHttpsServer();

        try {
            server.start();

            URI uri = server.uri();

            assertEquals("https", uri.getScheme());
            assertEquals("localhost", uri.getHost());
            assertEquals(server.port(), uri.getPort());
        } finally {
            server.stop();
        }
    }

    // -------------------------------------------------------------------------
    // HTTP/2 over TLS (h2)
    // -------------------------------------------------------------------------

    @Test
    void http2Server_negotiatesHttp2_whenClientAdvertisesH2() throws Exception {
        Server server = buildHttp2Server();

        try {
            server.start();

            HttpResponse<String> response = buildHttp2Client().send(
                    HttpRequest.newBuilder(URI.create("https://localhost:" + server.port() + "/ping"))
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
    void http2Server_fallsBackToHttp11_whenClientOffersOnlyHttp11() throws Exception {
        Server server = buildHttp2Server();

        try {
            server.start();

            HttpResponse<String> response = buildHttp11Client().send(
                    HttpRequest.newBuilder(URI.create("https://localhost:" + server.port() + "/ping"))
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
    void http2Server_http1ServerStillServesHttp11Requests() throws Exception {
        // An HTTP/1.1-only TLS server should still serve HTTP/1.1 clients normally.
        Server server = buildHttpsServer();

        try {
            server.start();

            HttpResponse<String> response = buildHttp11Client().send(
                    HttpRequest.newBuilder(URI.create("https://localhost:" + server.port() + "/ping"))
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
}

