package software.frisby.web.client;

import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import software.frisby.web.client.security.SecurityProvider;
import software.frisby.web.serial.GenericType;
import software.frisby.web.serial.jackson.JacksonSerializer;
import software.frisby.web.server.Server;
import software.frisby.web.server.ServerConfiguration;
import software.frisby.web.test.TestLogging;
import software.frisby.web.test.TestResources;

import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Phase D integration tests — {@link SecurityProvider} wiring and per-request override.
 * <p>
 * Uses {@code HeaderResource} ({@code GET /headers}) which echoes the {@code Authorization}
 * header back in the response, allowing tests to verify that the security provider was
 * invoked and set the header correctly on the outgoing request.
 * <p>
 * Lambda-based providers are used throughout — correctness of the Base64 encoding in
 * {@code BasicSecurityProvider} and the token formatting in {@code BearerTokenSecurityProvider}
 * are covered by their respective module unit tests.
 */
class ClientSecurityTest {
    private static final String BASIC_CREDENTIALS = Base64.getEncoder()
            .encodeToString("alice:s3cr3t".getBytes(StandardCharsets.UTF_8));
    private static final String BASIC_AUTHORIZATION = "Basic " + BASIC_CREDENTIALS;
    private static final String STATIC_BEARER_TOKEN = "my-static-token";
    private static final String BEARER_AUTHORIZATION = "Bearer " + STATIC_BEARER_TOKEN;

    private static Server server;

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
                .resources(TestResources.all())
                .components(
                        new MultiPartFeature(),
                        TestLogging.forClass(ClientSecurityTest.class)
                )
                .build();

        server.start();
    }

    @AfterAll
    static void stopServer() {
        if (null != server) {
            server.stop();
        }
    }

    private Client clientWith(SecurityProvider security) {
        return Client.builder()
                .configuration(
                        ClientConfiguration.builder()
                                .uri(server.uri())
                                .connectTimeout(Duration.ofSeconds(5))
                                .readTimeout(Duration.ofSeconds(30))
                                .serializer(JacksonSerializer.builder().build())
                                .build()
                )
                .security(security)
                .build();
    }

    // -------------------------------------------------------------------------
    // Default security provider (client-level)
    // -------------------------------------------------------------------------

    @Nested
    class DefaultSecurity {
        /**
         * A Basic Auth provider registered at the client level sets
         * {@code Authorization: Basic <token>} on every request.
         */
        @Test
        void basicAuth_defaultProvider_authorizationHeaderSetOnEveryRequest() {
            Client client = clientWith(
                    request -> request.addHeader("Authorization", BASIC_AUTHORIZATION)
            );

            HttpResponse<Map<String, String>> first = client.get().path("/headers").send(new GenericType<>() {
            });
            HttpResponse<Map<String, String>> second = client.get().path("/headers").send(new GenericType<>() {
            });

            assertEquals(200, first.statusCode());
            assertEquals(BASIC_AUTHORIZATION, first.body().get("Authorization"));

            assertEquals(200, second.statusCode());
            assertEquals(BASIC_AUTHORIZATION, second.body().get("Authorization"));
        }

        /**
         * A Bearer Token provider registered at the client level sets
         * {@code Authorization: Bearer <token>} on every request.
         */
        @Test
        void bearerToken_staticToken_authorizationHeaderSetOnEveryRequest() {
            Client client = clientWith(
                    request -> request.addHeader("Authorization", BEARER_AUTHORIZATION)
            );

            HttpResponse<Map<String, String>> first = client.get().path("/headers").send(new GenericType<>() {
            });
            HttpResponse<Map<String, String>> second = client.get().path("/headers").send(new GenericType<>() {
            });

            assertEquals(200, first.statusCode());
            assertEquals(BEARER_AUTHORIZATION, first.body().get("Authorization"));

            assertEquals(200, second.statusCode());
            assertEquals(BEARER_AUTHORIZATION, second.body().get("Authorization"));
        }

        /**
         * A dynamic Bearer Token supplier is invoked on every request — each call
         * receives the value returned by the supplier at that moment, not a value
         * captured once at construction time.
         * <p>
         * Verified by incrementing a counter inside the supplier; the two requests
         * receive distinct tokens, proving the supplier is called per-request.
         */
        @Test
        void bearerToken_dynamicSupplier_supplierCalledPerRequest() {
            AtomicInteger counter = new AtomicInteger(0);

            Client client = clientWith(
                    request -> request.addHeader(
                            "Authorization",
                            "Bearer token-" + counter.incrementAndGet()
                    )
            );

            HttpResponse<Map<String, String>> first = client.get().path("/headers").send(new GenericType<>() {
            });
            HttpResponse<Map<String, String>> second = client.get().path("/headers").send(new GenericType<>() {
            });

            assertEquals(200, first.statusCode());
            assertEquals("Bearer token-1", first.body().get("Authorization"));

            assertEquals(200, second.statusCode());
            assertEquals("Bearer token-2", second.body().get("Authorization"));
        }

        /**
         * Without any security provider the {@code Authorization} header is absent —
         * confirms no default credential is silently injected.
         */
        @Test
        void noSecurityProvider_authorizationHeaderAbsent() {
            Client noSecurityClient = Client.builder()
                    .configuration(
                            ClientConfiguration.builder()
                                    .uri(server.uri())
                                    .connectTimeout(Duration.ofSeconds(5))
                                    .readTimeout(Duration.ofSeconds(30))
                                    .serializer(JacksonSerializer.builder().build())
                                    .build()
                    )
                    .build();

            HttpResponse<Map<String, String>> response = noSecurityClient.get().path("/headers").send(new GenericType<>() {
            });

            assertEquals(200, response.statusCode());
            assertNull(
                    response.body().get("Authorization"),
                    "Expected no Authorization header when no security provider is configured"
            );
        }
    }

    // -------------------------------------------------------------------------
    // Per-request security override
    // -------------------------------------------------------------------------

    @Nested
    class PerRequestOverride {
        /**
         * A per-request {@code .security()} call overrides the client-level default —
         * the override provider's header value reaches the server, not the default's.
         */
        @Test
        void perRequestOverride_headerReflectsOverrideNotDefault() {
            Client client = clientWith(
                    request -> request.addHeader("Authorization", "Bearer default-token")
            );

            HttpResponse<Map<String, String>> response = client.get()
                    .path("/headers")
                    .security(req -> req.addHeader("Authorization", "Bearer override-token"))
                    .send(new GenericType<>() {
                    });

            assertEquals(200, response.statusCode());
            assertEquals("Bearer override-token", response.body().get("Authorization"));
        }

        /**
         * The per-request override applies only to that single request — the next request
         * using the same client receives the client-level default again.
         */
        @Test
        void perRequestOverride_doesNotAffectSubsequentRequests() {
            Client client = clientWith(
                    request -> request.addHeader("Authorization", BEARER_AUTHORIZATION)
            );

            // First request: override
            HttpResponse<Map<String, String>> overrideResponse = client.get()
                    .path("/headers")
                    .security(req -> req.addHeader("Authorization", "Bearer override-token"))
                    .send(new GenericType<>() {
                    });

            // Second request: no override — default fires
            HttpResponse<Map<String, String>> defaultResponse = client.get()
                    .path("/headers")
                    .send(new GenericType<>() {
                    });

            assertEquals("Bearer override-token", overrideResponse.body().get("Authorization"));
            assertEquals(BEARER_AUTHORIZATION, defaultResponse.body().get("Authorization"));
        }
    }
}

