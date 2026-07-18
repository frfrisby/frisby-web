package software.frisby.web.client;

import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import software.frisby.web.serial.jackson.JacksonSerializer;
import software.frisby.web.server.Server;
import software.frisby.web.server.ServerConfiguration;
import software.frisby.web.test.TestLogging;
import software.frisby.web.test.TestResources;
import software.frisby.web.test.domain.CreatePersonRequest;
import software.frisby.web.test.domain.Person;
import software.frisby.web.test.log.LogExpectation;
import software.frisby.web.test.log.SystemLogVerifier;

import java.net.http.HttpResponse;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Phase C integration tests — gzip request body compression and gzip response decompression.
 * <p>
 * Requires a server configured with {@code gzip()} to decompress incoming compressed request
 * bodies and to compress outgoing responses when the client advertises
 * {@code Accept-Encoding: gzip}.
 */
class ClientCompressionTest {
    private static Server server;

    @BeforeAll
    static void startServer() {
        server = Server.builder()
                .configuration(
                        ServerConfiguration.builder()
                                .port(0)
                                .host("localhost")
                                .serializer(JacksonSerializer.builder().build())
                                .gzip()
                                .build()
                )
                .resources(TestResources.all())
                .components(
                        new MultiPartFeature(),
                        TestLogging.forClass(ClientCompressionTest.class)
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

    // -------------------------------------------------------------------------
    // Gzip response decompression
    // -------------------------------------------------------------------------

    @Nested
    class ResponseDecompression {
        /**
         * {@code decompress()} enables gzip response decompression.  The client advertises
         * {@code Accept-Encoding: gzip}, the server compresses the response, and the client
         * decompresses it transparently — the caller receives a fully deserialized object
         * with no awareness of the transport encoding.
         * <p>
         * Verified via {@link RequestLogger} TRACE output: {@code Accept-Encoding: gzip} must
         * appear in the outbound request headers.
         */
        @Test
        void decompress_serverCompressesResponse_clientDecompressesTransparently() {
            Client gzipClient = Client.builder()
                    .configuration(
                            ClientConfiguration.builder()
                                    .uri(server.uri())
                                    .connectTimeout(Duration.ofSeconds(5))
                                    .readTimeout(Duration.ofSeconds(30))
                                    .serializer(JacksonSerializer.builder().build())
                                    .decompress()
                                    .build()
                    )
                    .build();

            try (SystemLogVerifier verifier = SystemLogVerifier.builder()
                    .configure(RequestLogger.class, System.Logger.Level.TRACE)
                    .expect(
                            LogExpectation.builder()
                                    .logger(RequestLogger.class)
                                    .level(System.Logger.Level.TRACE)
                                    .predicate(e -> e.message().contains("/persons/person-1") &&
                                            e.message().contains("Accept-Encoding: gzip"))
                                    .failureMessage("Expected Accept-Encoding: gzip in TRACE log for gzip-enabled client.")
                                    .build()
                    )
                    .build()) {
                HttpResponse<Person> response = gzipClient.get()
                        .path("/persons/{id}", "id", "person-1")
                        .send(Person.class);

                assertEquals(200, response.statusCode());
                assertNotNull(response.body());
                assertEquals("person-1", response.body().id());
                assertEquals("Test Person", response.body().name());

                verifier.assertExpectations();
            }
        }

        /**
         * Without calling {@code decompress()}, the client does not send
         * {@code Accept-Encoding: gzip} and the server returns an uncompressed response —
         * the same deserialized result is produced regardless of compression.
         * <p>
         * Verified via {@link RequestLogger} TRACE output: {@code Accept-Encoding: gzip} must
         * NOT appear in the outbound request headers.
         */
        @Test
        void noDecompress_serverRespondsUncompressed_clientDeserializesNormally() {
            Client noGzipClient = Client.builder()
                    .configuration(
                            ClientConfiguration.builder()
                                    .uri(server.uri())
                                    .connectTimeout(Duration.ofSeconds(5))
                                    .readTimeout(Duration.ofSeconds(30))
                                    .serializer(JacksonSerializer.builder().build())
                                    .build()
                    )
                    .build();

            try (SystemLogVerifier verifier = SystemLogVerifier.builder()
                    .configure(RequestLogger.class, System.Logger.Level.TRACE)
                    .expect(
                            LogExpectation.builder()
                                    .logger(RequestLogger.class)
                                    .level(System.Logger.Level.TRACE)
                                    .predicate(e -> e.message().contains("/persons/person-1") &&
                                            !e.message().contains("Accept-Encoding: gzip"))
                                    .failureMessage("Expected TRACE log without Accept-Encoding: gzip for gzip-disabled client.")
                                    .build()
                    )
                    .build()) {
                HttpResponse<Person> response = noGzipClient.get()
                        .path("/persons/{id}", "id", "person-1")
                        .send(Person.class);

                assertEquals(200, response.statusCode());
                assertNotNull(response.body());
                assertEquals("person-1", response.body().id());

                verifier.assertExpectations();
            }
        }
    }

    // -------------------------------------------------------------------------
    // Gzip request body compression
    // -------------------------------------------------------------------------

    @Nested
    class RequestCompression {
        /**
         * POST with {@code compress(ContentEncoding.GZIP)} — the client compresses the
         * JSON body and sets {@code Content-Encoding: gzip}.  The server (with {@code gzip()}
         * enabled) decompresses it and deserializes the entity correctly.
         * <p>
         * Verified via {@link RequestLogger} TRACE output: {@code Content-Encoding: gzip} must
         * appear in the outbound request headers.
         */
        @Test
        void post_compressedRequestBody_serverDecompressesAndResponds() {
            Client client = Client.builder()
                    .configuration(
                            ClientConfiguration.builder()
                                    .uri(server.uri())
                                    .connectTimeout(Duration.ofSeconds(5))
                                    .readTimeout(Duration.ofSeconds(30))
                                    .serializer(JacksonSerializer.builder().build())
                                    .build()
                    )
                    .build();

            CreatePersonRequest request = new CreatePersonRequest("Alice", "alice@example.com");

            try (SystemLogVerifier verifier = SystemLogVerifier.builder()
                    .configure(RequestLogger.class, System.Logger.Level.TRACE)
                    .expect(
                            LogExpectation.builder()
                                    .logger(RequestLogger.class)
                                    .level(System.Logger.Level.TRACE)
                                    .predicate(e -> e.message().contains("/persons") &&
                                            e.message().contains("Content-Encoding: gzip"))
                                    .failureMessage("Expected Content-Encoding: gzip in TRACE log for compressed POST request.")
                                    .build()
                    )
                    .build()) {
                HttpResponse<Person> response = client.post()
                        .path("/persons")
                        .compress()
                        .body(request)
                        .send(Person.class);

                assertEquals(201, response.statusCode());
                assertNotNull(response.body());
                assertEquals("Alice", response.body().name());
                assertEquals("alice@example.com", response.body().email());

                verifier.assertExpectations();
            }
        }

        /**
         * PUT with {@code compress(ContentEncoding.GZIP)} — same as POST but for the
         * PUT verb.
         * <p>
         * Verified via {@link RequestLogger} TRACE output: {@code Content-Encoding: gzip} must
         * appear in the outbound request headers.
         */
        @Test
        void put_compressedRequestBody_serverDecompressesAndResponds() {
            Client client = Client.builder()
                    .configuration(
                            ClientConfiguration.builder()
                                    .uri(server.uri())
                                    .connectTimeout(Duration.ofSeconds(5))
                                    .readTimeout(Duration.ofSeconds(30))
                                    .serializer(JacksonSerializer.builder().build())
                                    .build()
                    )
                    .build();

            software.frisby.web.test.domain.UpdatePersonRequest request =
                    new software.frisby.web.test.domain.UpdatePersonRequest("Updated", "updated@example.com");

            try (SystemLogVerifier verifier = SystemLogVerifier.builder()
                    .configure(RequestLogger.class, System.Logger.Level.TRACE)
                    .expect(
                            LogExpectation.builder()
                                    .logger(RequestLogger.class)
                                    .level(System.Logger.Level.TRACE)
                                    .predicate(e -> e.message().contains("/persons/person-1") &&
                                            e.message().contains("Content-Encoding: gzip"))
                                    .failureMessage("Expected Content-Encoding: gzip in TRACE log for compressed PUT request.")
                                    .build()
                    )
                    .build()) {
                HttpResponse<software.frisby.web.test.domain.Person> response = client.put()
                        .path("/persons/{id}", "id", "person-1")
                        .compress()
                        .body(request)
                        .send(software.frisby.web.test.domain.Person.class);

                assertEquals(200, response.statusCode());
                assertNotNull(response.body());
                assertEquals("Updated", response.body().name());

                verifier.assertExpectations();
            }
        }

        /**
         * PATCH with {@code compress(ContentEncoding.GZIP)} — exercises the compressed
         * body path in {@code PatchRequest.buildJsonRequest()}.
         * <p>
         * Verified via {@link RequestLogger} TRACE output: {@code Content-Encoding: gzip} must
         * appear in the outbound request headers.
         */
        @Test
        void patch_compressedRequestBody_serverDecompressesAndResponds() {
            Client client = Client.builder()
                    .configuration(
                            ClientConfiguration.builder()
                                    .uri(server.uri())
                                    .connectTimeout(Duration.ofSeconds(5))
                                    .readTimeout(Duration.ofSeconds(30))
                                    .serializer(JacksonSerializer.builder().build())
                                    .build()
                    )
                    .build();

            software.frisby.web.test.domain.UpdatePersonRequest request =
                    new software.frisby.web.test.domain.UpdatePersonRequest("Patched", "patched@example.com");

            try (SystemLogVerifier verifier = SystemLogVerifier.builder()
                    .configure(RequestLogger.class, System.Logger.Level.TRACE)
                    .expect(
                            LogExpectation.builder()
                                    .logger(RequestLogger.class)
                                    .level(System.Logger.Level.TRACE)
                                    .predicate(e -> e.message().contains("/persons/person-1") &&
                                            e.message().contains("Content-Encoding: gzip"))
                                    .failureMessage("Expected Content-Encoding: gzip in TRACE log for compressed PATCH request.")
                                    .build()
                    )
                    .build()) {
                HttpResponse<software.frisby.web.test.domain.Person> response = client.patch()
                        .path("/persons/{id}", "id", "person-1")
                        .compress()
                        .body(request)
                        .send(software.frisby.web.test.domain.Person.class);

                assertEquals(200, response.statusCode());
                assertNotNull(response.body());
                assertEquals("Patched", response.body().name());

                verifier.assertExpectations();
            }
        }
    }
}

