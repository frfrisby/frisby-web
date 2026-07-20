package software.frisby.web.server;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for CORS support in {@link DefaultServer}.
 * <p>
 * Each nested class spins up a single server for the group of related tests
 * and tears it down in {@code @AfterEach}.  The JDK {@link HttpClient} is used
 * to send requests with an {@code Origin} header — the standard HTTP client
 * API does not restrict this header (unlike browser XMLHttpRequest).
 */
class ServerCorsTest {
    private static final String ALLOWED_ORIGIN = "https://app.example.com";
    private static final String OTHER_ALLOWED_ORIGIN = "https://admin.example.com";
    private static final String DISALLOWED_ORIGIN = "https://evil.example.com";

    private static final String HEADER_ALLOW_ORIGIN = "Access-Control-Allow-Origin";
    private static final String HEADER_ALLOW_METHODS = "Access-Control-Allow-Methods";
    private static final String HEADER_ALLOW_HEADERS = "Access-Control-Allow-Headers";
    private static final String HEADER_ALLOW_CREDENTIALS = "Access-Control-Allow-Credentials";
    private static final String HEADER_MAX_AGE = "Access-Control-Max-Age";
    private static final String HEADER_VARY = "Vary";

    private final HttpClient httpClient = HttpClient.newHttpClient();

    // -------------------------------------------------------------------------
    // Actual request handling (non-preflight)
    // -------------------------------------------------------------------------

    @Nested
    class ActualRequests {
        private Server server;
        private URI baseUri;

        @BeforeEach
        void setUp() throws Exception {
            server = Server.builder()
                    .configuration(
                            ServerConfiguration.builder()
                                    .port(0)
                                    .serializer(new TestJsonSerializer())
                                    .cors(
                                            CorsConfiguration.builder()
                                                    .allowedOrigins(ALLOWED_ORIGIN, OTHER_ALLOWED_ORIGIN)
                                                    .allowedMethods("GET", "POST", "DELETE")
                                                    .build()
                                    )
                                    .build()
                    )
                    .resources(new PingResource())
                    .components(TestLogging.forClass(ServerCorsTest.class))
                    .build();
            server.start();
            baseUri = server.uri();
        }

        @AfterEach
        void tearDown() {
            server.stop();
        }

        @Test
        void allowedOrigin_addsCorsHeader() throws Exception {
            HttpResponse<String> response = httpClient.send(
                    HttpRequest.newBuilder(baseUri.resolve("/ping"))
                            .header("Origin", ALLOWED_ORIGIN)
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            assertEquals(200, response.statusCode());
            assertEquals(ALLOWED_ORIGIN, response.headers().firstValue(HEADER_ALLOW_ORIGIN).orElse(null));
        }

        @Test
        void secondAllowedOrigin_addsCorsHeader() throws Exception {
            HttpResponse<String> response = httpClient.send(
                    HttpRequest.newBuilder(baseUri.resolve("/ping"))
                            .header("Origin", OTHER_ALLOWED_ORIGIN)
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            assertEquals(200, response.statusCode());
            assertEquals(OTHER_ALLOWED_ORIGIN, response.headers().firstValue(HEADER_ALLOW_ORIGIN).orElse(null));
        }

        @Test
        void specificOrigin_addsVaryHeader() throws Exception {
            HttpResponse<String> response = httpClient.send(
                    HttpRequest.newBuilder(baseUri.resolve("/ping"))
                            .header("Origin", ALLOWED_ORIGIN)
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            assertTrue(response.headers().allValues(HEADER_VARY).contains("Origin"));
        }

        @Test
        void disallowedOrigin_noCorsHeaders() throws Exception {
            HttpResponse<String> response = httpClient.send(
                    HttpRequest.newBuilder(baseUri.resolve("/ping"))
                            .header("Origin", DISALLOWED_ORIGIN)
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            assertEquals(200, response.statusCode());
            assertFalse(response.headers().firstValue(HEADER_ALLOW_ORIGIN).isPresent());
        }

        @Test
        void noOriginHeader_noCorsHeaders() throws Exception {
            HttpResponse<String> response = httpClient.send(
                    HttpRequest.newBuilder(baseUri.resolve("/ping"))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            assertEquals(200, response.statusCode());
            assertFalse(response.headers().firstValue(HEADER_ALLOW_ORIGIN).isPresent());
        }

        @Test
        void requestMethodHeaderPresentButNotOptions_treatedAsActualRequest() throws Exception {
            // Exercises the second condition of the isPreflight check:
            // Access-Control-Request-Method is non-null but the HTTP method is not OPTIONS,
            // so isPreflight evaluates to false and the response filter adds the CORS header.
            HttpResponse<String> response = httpClient.send(
                    HttpRequest.newBuilder(baseUri.resolve("/ping"))
                            .header("Origin", ALLOWED_ORIGIN)
                            .header("Access-Control-Request-Method", "POST")
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            assertEquals(200, response.statusCode());
            assertEquals(ALLOWED_ORIGIN, response.headers().firstValue(HEADER_ALLOW_ORIGIN).orElse(null));
        }
    }

    // -------------------------------------------------------------------------
    // Wildcard origin
    // -------------------------------------------------------------------------

    @Nested
    class WildcardOrigin {
        private Server server;
        private URI baseUri;

        @BeforeEach
        void setUp() throws Exception {
            server = Server.builder()
                    .configuration(
                            ServerConfiguration.builder()
                                    .port(0)
                                    .serializer(new TestJsonSerializer())
                                    .cors(
                                            CorsConfiguration.builder()
                                                    .allowedOrigins("*")
                                                    .allowedMethods("GET")
                                                    .build()
                                    )
                                    .build()
                    )
                    .resources(new PingResource())
                    .components(TestLogging.forClass(ServerCorsTest.class))
                    .build();
            server.start();
            baseUri = server.uri();
        }

        @AfterEach
        void tearDown() {
            server.stop();
        }

        @Test
        void anyOrigin_addsWildcardHeader() throws Exception {
            HttpResponse<String> response = httpClient.send(
                    HttpRequest.newBuilder(baseUri.resolve("/ping"))
                            .header("Origin", "https://any-origin.example.com")
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            assertEquals(200, response.statusCode());
            assertEquals("*", response.headers().firstValue(HEADER_ALLOW_ORIGIN).orElse(null));
        }

        @Test
        void wildcardOrigin_noVaryHeader() throws Exception {
            HttpResponse<String> response = httpClient.send(
                    HttpRequest.newBuilder(baseUri.resolve("/ping"))
                            .header("Origin", "https://any-origin.example.com")
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            // Vary: Origin must NOT be sent for wildcard — every origin gets the same response.
            assertFalse(response.headers().allValues(HEADER_VARY).contains("Origin"));
        }

        @Test
        void preflight_wildcard_addsWildcardAllowOriginHeader() throws Exception {
            HttpResponse<String> response = httpClient.send(
                    HttpRequest.newBuilder(baseUri.resolve("/ping"))
                            .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                            .header("Origin", "https://any-origin.example.com")
                            .header("Access-Control-Request-Method", "GET")
                            .build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            assertEquals(200, response.statusCode());
            assertEquals("*", response.headers().firstValue(HEADER_ALLOW_ORIGIN).orElse(null));
        }
    }

    // -------------------------------------------------------------------------
    // Allow-Credentials
    // -------------------------------------------------------------------------

    @Nested
    class Credentials {
        private Server server;
        private URI baseUri;

        @BeforeEach
        void setUp() throws Exception {
            server = Server.builder()
                    .configuration(
                            ServerConfiguration.builder()
                                    .port(0)
                                    .serializer(new TestJsonSerializer())
                                    .cors(
                                            CorsConfiguration.builder()
                                                    .allowedOrigins(ALLOWED_ORIGIN)
                                                    .allowedMethods("GET", "POST")
                                                    .allowCredentials()
                                                    .build()
                                    )
                                    .build()
                    )
                    .resources(new PingResource())
                    .components(TestLogging.forClass(ServerCorsTest.class))
                    .build();
            server.start();
            baseUri = server.uri();
        }

        @AfterEach
        void tearDown() {
            server.stop();
        }

        @Test
        void allowedOrigin_addsCredentialsHeader() throws Exception {
            HttpResponse<String> response = httpClient.send(
                    HttpRequest.newBuilder(baseUri.resolve("/ping"))
                            .header("Origin", ALLOWED_ORIGIN)
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            assertEquals(200, response.statusCode());
            assertEquals("true", response.headers().firstValue(HEADER_ALLOW_CREDENTIALS).orElse(null));
        }

        @Test
        void preflight_addsCredentialsHeader() throws Exception {
            HttpResponse<String> response = httpClient.send(
                    HttpRequest.newBuilder(baseUri.resolve("/ping"))
                            .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                            .header("Origin", ALLOWED_ORIGIN)
                            .header("Access-Control-Request-Method", "POST")
                            .build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            assertEquals(200, response.statusCode());
            assertEquals("true", response.headers().firstValue(HEADER_ALLOW_CREDENTIALS).orElse(null));
        }
    }


    @Nested
    class Preflight {
        private Server server;
        private URI baseUri;

        @BeforeEach
        void setUp() throws Exception {
            server = Server.builder()
                    .configuration(
                            ServerConfiguration.builder()
                                    .port(0)
                                    .serializer(new TestJsonSerializer())
                                    .cors(
                                            CorsConfiguration.builder()
                                                    .allowedOrigins(ALLOWED_ORIGIN)
                                                    .allowedMethods("GET", "POST", "PUT", "DELETE")
                                                    .allowedHeaders("Authorization", "Content-Type")
                                                    .build()
                                    )
                                    .build()
                    )
                    .resources(new PingResource())
                    .components(TestLogging.forClass(ServerCorsTest.class))
                    .build();
            server.start();
            baseUri = server.uri();
        }

        @AfterEach
        void tearDown() {
            server.stop();
        }

        @Test
        void allowedOrigin_returns200WithCorsHeaders() throws Exception {
            HttpResponse<String> response = httpClient.send(
                    HttpRequest.newBuilder(baseUri.resolve("/ping"))
                            .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                            .header("Origin", ALLOWED_ORIGIN)
                            .header("Access-Control-Request-Method", "POST")
                            .build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            assertEquals(200, response.statusCode());
            assertEquals(ALLOWED_ORIGIN, response.headers().firstValue(HEADER_ALLOW_ORIGIN).orElse(null));
            assertTrue(response.headers().firstValue(HEADER_ALLOW_METHODS).orElse("").contains("POST"));
            assertEquals("3600", response.headers().firstValue(HEADER_MAX_AGE).orElse(null));
        }

        @Test
        void allowedOrigin_preflight_includesConfiguredAllowedHeaders() throws Exception {
            HttpResponse<String> response = httpClient.send(
                    HttpRequest.newBuilder(baseUri.resolve("/ping"))
                            .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                            .header("Origin", ALLOWED_ORIGIN)
                            .header("Access-Control-Request-Method", "POST")
                            .header("Access-Control-Request-Headers", "Authorization, Content-Type")
                            .build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            String allowedHeaders = response.headers().firstValue(HEADER_ALLOW_HEADERS).orElse("");

            assertTrue(allowedHeaders.contains("Authorization"));
            assertTrue(allowedHeaders.contains("Content-Type"));
        }

        @Test
        void disallowedOrigin_returns200WithNoCorsHeaders() throws Exception {
            HttpResponse<String> response = httpClient.send(
                    HttpRequest.newBuilder(baseUri.resolve("/ping"))
                            .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                            .header("Origin", DISALLOWED_ORIGIN)
                            .header("Access-Control-Request-Method", "POST")
                            .build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            assertEquals(200, response.statusCode());
            assertFalse(response.headers().firstValue(HEADER_ALLOW_ORIGIN).isPresent());
        }
    }

    // -------------------------------------------------------------------------
    // Preflight — echo of request headers when allowedHeaders not configured
    // -------------------------------------------------------------------------

    @Nested
    class PreflightHeaderEcho {
        private Server server;
        private URI baseUri;

        @BeforeEach
        void setUp() throws Exception {
            server = Server.builder()
                    .configuration(
                            ServerConfiguration.builder()
                                    .port(0)
                                    .serializer(new TestJsonSerializer())
                                    .cors(
                                            CorsConfiguration.builder()
                                                    .allowedOrigins(ALLOWED_ORIGIN)
                                                    .allowedMethods("POST")
                                                    // intentionally no allowedHeaders() call
                                                    .build()
                                    )
                                    .build()
                    )
                    .resources(new PingResource())
                    .components(TestLogging.forClass(ServerCorsTest.class))
                    .build();
            server.start();
            baseUri = server.uri();
        }

        @AfterEach
        void tearDown() {
            server.stop();
        }

        @Test
        void preflight_echoesRequestHeaders_whenAllowedHeadersNotConfigured() throws Exception {
            HttpResponse<String> response = httpClient.send(
                    HttpRequest.newBuilder(baseUri.resolve("/ping"))
                            .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                            .header("Origin", ALLOWED_ORIGIN)
                            .header("Access-Control-Request-Method", "POST")
                            .header("Access-Control-Request-Headers", "X-Custom-Header")
                            .build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            assertEquals("X-Custom-Header",
                    response.headers().firstValue(HEADER_ALLOW_HEADERS).orElse(null)
            );
        }

        @Test
        void preflight_noAllowHeadersResponse_whenClientSendsNoRequestHeaders() throws Exception {
            HttpResponse<String> response = httpClient.send(
                    HttpRequest.newBuilder(baseUri.resolve("/ping"))
                            .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                            .header("Origin", ALLOWED_ORIGIN)
                            .header("Access-Control-Request-Method", "POST")
                            .build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            // No Access-Control-Request-Headers sent → no Access-Control-Allow-Headers in response.
            assertFalse(response.headers().firstValue(HEADER_ALLOW_HEADERS).isPresent());
        }
    }
}

