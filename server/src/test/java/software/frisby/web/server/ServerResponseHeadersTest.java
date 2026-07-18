package software.frisby.web.server;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests asserting that Jetty does not add response headers that leak
 * implementation details or add noise to API responses.
 * <p>
 * Three connector paths are covered: plain HTTP, HTTPS (TLS/HTTP 1.1), and
 * HTTP/2 over TLS (ALPN).  Each path produces a distinct connector and
 * {@link org.eclipse.jetty.server.HttpConfiguration} instance, so all three are
 * tested independently.
 */
class ServerResponseHeadersTest {
    // -------------------------------------------------------------------------
    // Plain HTTP
    // -------------------------------------------------------------------------

    @Nested
    class Http {
        private static final HttpClient HTTP = HttpClient.newHttpClient();

        private static Server server;

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
                    .components(TestLogging.forClass(ServerResponseHeadersTest.class))
                    .build();

            server.start();
        }

        @AfterAll
        static void stopServer() {
            if (null != server) {
                server.stop();
            }
        }

        @Test
        void serverHeader_isAbsent() throws Exception {
            HttpResponse<Void> response = HTTP.send(
                    HttpRequest.newBuilder(URI.create(server.uri() + "/ping"))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.discarding()
            );

            assertTrue(
                    response.headers().firstValue("server").isEmpty(),
                    "Plain HTTP response must not contain a 'server' header"
            );
        }

        @Test
        void dateHeader_isAbsent() throws Exception {
            HttpResponse<Void> response = HTTP.send(
                    HttpRequest.newBuilder(URI.create(server.uri() + "/ping"))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.discarding()
            );

            assertTrue(
                    response.headers().firstValue("date").isEmpty(),
                    "Plain HTTP response must not contain a 'date' header"
            );
        }
    }

    // -------------------------------------------------------------------------
    // HTTPS — TLS / HTTP 1.1
    // -------------------------------------------------------------------------

    @Nested
    class Https {
        private static Server server;
        private static HttpClient httpsClient;

        @BeforeAll
        static void startServer() throws Exception {
            server = Server.builder()
                    .configuration(
                            ServerConfiguration.builder()
                                    .port(0)
                                    .host("localhost")
                                    .serializer(new TestJsonSerializer())
                                    .ssl(SslTestSupport.serverSslContext())
                                    .build()
                    )
                    .resources(new PingResource())
                    .components(TestLogging.forClass(ServerResponseHeadersTest.class))
                    .build();

            server.start();

            httpsClient = HttpClient.newBuilder()
                    .sslContext(SslTestSupport.clientSslContext())
                    .version(HttpClient.Version.HTTP_1_1)
                    .build();
        }

        @AfterAll
        static void stopServer() {
            if (null != server) {
                server.stop();
            }
        }

        @Test
        void serverHeader_isAbsent() throws Exception {
            HttpResponse<Void> response = httpsClient.send(
                    HttpRequest.newBuilder(URI.create("https://localhost:" + server.port() + "/ping"))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.discarding()
            );

            assertTrue(
                    response.headers().firstValue("server").isEmpty(),
                    "HTTPS response must not contain a 'server' header"
            );
        }

        @Test
        void dateHeader_isAbsent() throws Exception {
            HttpResponse<Void> response = httpsClient.send(
                    HttpRequest.newBuilder(URI.create("https://localhost:" + server.port() + "/ping"))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.discarding()
            );

            assertTrue(
                    response.headers().firstValue("date").isEmpty(),
                    "HTTPS response must not contain a 'date' header"
            );
        }
    }

    // -------------------------------------------------------------------------
    // HTTP/2 over TLS (ALPN)
    // -------------------------------------------------------------------------

    @Nested
    class Http2 {
        private static Server server;
        private static HttpClient http2Client;

        @BeforeAll
        static void startServer() throws Exception {
            server = Server.builder()
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
                    .components(TestLogging.forClass(ServerResponseHeadersTest.class))
                    .build();

            server.start();

            http2Client = HttpClient.newBuilder()
                    .sslContext(SslTestSupport.clientSslContext())
                    .version(HttpClient.Version.HTTP_2)
                    .build();
        }

        @AfterAll
        static void stopServer() {
            if (null != server) {
                server.stop();
            }
        }

        @Test
        void serverHeader_isAbsent() throws Exception {
            HttpResponse<Void> response = http2Client.send(
                    HttpRequest.newBuilder(URI.create("https://localhost:" + server.port() + "/ping"))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.discarding()
            );

            assertTrue(
                    response.headers().firstValue("server").isEmpty(),
                    "HTTP/2 response must not contain a 'server' header"
            );
        }

        @Test
        void dateHeader_isAbsent() throws Exception {
            HttpResponse<Void> response = http2Client.send(
                    HttpRequest.newBuilder(URI.create("https://localhost:" + server.port() + "/ping"))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.discarding()
            );

            assertTrue(
                    response.headers().firstValue("date").isEmpty(),
                    "HTTP/2 response must not contain a 'date' header"
            );
        }
    }
}
