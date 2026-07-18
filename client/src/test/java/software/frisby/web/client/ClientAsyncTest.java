package software.frisby.web.client;

import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import software.frisby.web.client.exception.NotFoundException;
import software.frisby.web.client.exception.TransportException;
import software.frisby.web.serial.GenericType;
import software.frisby.web.serial.jackson.JacksonSerializer;
import software.frisby.web.server.Server;
import software.frisby.web.server.ServerConfiguration;
import software.frisby.web.test.TestLogging;
import software.frisby.web.test.TestResources;
import software.frisby.web.test.domain.CreatePersonRequest;
import software.frisby.web.test.domain.Person;
import software.frisby.web.test.domain.UpdatePersonRequest;

import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the {@code sendAsync()} and {@code downloadAsync()} terminals
 * across all six HTTP verb spec types.
 * <p>
 * Covers:
 * <ul>
 *   <li>Happy-path completion for all verbs — validates the async code paths in the verb
 *       request classes and in {@link HttpEngine#sendAsync}.</li>
 *   <li>HTTP error propagation — a non-2xx response wraps the exception in a
 *       {@link CompletionException} as specified.</li>
 *   <li>Transport error propagation — a connect failure wraps the exception in a
 *       {@link CompletionException}.</li>
 * </ul>
 */
class ClientAsyncTest {
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
                .resources(TestResources.all())
                .components(
                        new MultiPartFeature(),
                        TestLogging.forClass(ClientAsyncTest.class)
                )
                .build();

        server.start();

        client = Client.builder()
                .configuration(
                        ClientConfiguration.builder()
                                .uri(server.uri())
                                .connectTimeout(Duration.ofSeconds(5))
                                .readTimeout(Duration.ofSeconds(30))
                                .serializer(JacksonSerializer.builder().build())
                                .build()
                )
                .build();
    }

    @AfterAll
    static void stopServer() {
        if (null != server) {
            server.stop();
        }
    }

    // -------------------------------------------------------------------------
    // GET async
    // -------------------------------------------------------------------------

    @Nested
    class GetAsync {
        @Test
        void sendAsync_typedResponse_completesCorrectly() {
            HttpResponse<Person> response = client.get()
                    .path("/persons/{id}", "id", "person-1")
                    .sendAsync(Person.class)
                    .join();

            assertEquals(200, response.statusCode());
            assertNotNull(response.body());
            assertEquals("person-1", response.body().id());
        }

        @Test
        void sendAsync_genericType_completesCorrectly() {
            HttpResponse<List<Person>> response = client.get()
                    .path("/persons")
                    .sendAsync(new GenericType<List<Person>>() {
                    })
                    .join();

            assertEquals(200, response.statusCode());
            assertNotNull(response.body());
            assertEquals(2, response.body().size());
        }

        @Test
        void downloadAsync_completesCorrectly() {
            HttpResponse<InputStream> response = client.get()
                    .path("/stream")
                    .downloadAsync()
                    .join();

            assertEquals(200, response.statusCode());
            assertNotNull(response.body());
        }
    }

    // -------------------------------------------------------------------------
    // POST async
    // -------------------------------------------------------------------------

    @Nested
    class PostAsync {
        @Test
        void sendAsync_withJsonBody_typedResponse_completesCorrectly() {
            HttpResponse<Person> response = client.post()
                    .path("/persons")
                    .body(new CreatePersonRequest("Async Person", "async@example.com"))
                    .sendAsync(Person.class)
                    .join();

            assertEquals(201, response.statusCode());
            assertNotNull(response.body());
            assertEquals("Async Person", response.body().name());
        }

        @Test
        void sendAsync_voidTerminal_completesCorrectly() {
            HttpResponse<Void> response = client.post()
                    .path("/persons")
                    .body(new CreatePersonRequest("Void Person", "void@example.com"))
                    .sendAsync()
                    .join();

            assertEquals(201, response.statusCode());
        }

        @Test
        void sendAsync_genericType_completesCorrectly() {
            HttpResponse<Person> response = client.post()
                    .path("/persons")
                    .body(new CreatePersonRequest("Generic Person", "generic@example.com"))
                    .sendAsync(new GenericType<Person>() {
                    })
                    .join();

            assertEquals(201, response.statusCode());
            assertNotNull(response.body());
        }
    }

    // -------------------------------------------------------------------------
    // PUT async
    // -------------------------------------------------------------------------

    @Nested
    class PutAsync {
        @Test
        void sendAsync_withJsonBody_typedResponse_completesCorrectly() {
            HttpResponse<Person> response = client.put()
                    .path("/persons/{id}", "id", "person-1")
                    .body(new UpdatePersonRequest("Async Updated", "async-updated@example.com"))
                    .sendAsync(Person.class)
                    .join();

            assertEquals(200, response.statusCode());
            assertNotNull(response.body());
            assertEquals("Async Updated", response.body().name());
        }

        @Test
        void sendAsync_voidTerminal_completesCorrectly() {
            HttpResponse<Void> response = client.put()
                    .path("/persons/{id}", "id", "person-1")
                    .body(new UpdatePersonRequest("Void Updated", "void-updated@example.com"))
                    .sendAsync()
                    .join();

            assertEquals(200, response.statusCode());
        }

        @Test
        void sendAsync_genericType_completesCorrectly() {
            HttpResponse<Person> response = client.put()
                    .path("/persons/{id}", "id", "person-1")
                    .body(new UpdatePersonRequest("Generic Updated", "gen@example.com"))
                    .sendAsync(new GenericType<Person>() {
                    })
                    .join();

            assertEquals(200, response.statusCode());
            assertNotNull(response.body());
        }
    }

    // -------------------------------------------------------------------------
    // PATCH async
    // -------------------------------------------------------------------------

    @Nested
    class PatchAsync {
        @Test
        void sendAsync_withJsonBody_typedResponse_completesCorrectly() {
            HttpResponse<Person> response = client.patch()
                    .path("/persons/{id}", "id", "person-1")
                    .body(new UpdatePersonRequest("Async Patched", "async-patched@example.com"))
                    .sendAsync(Person.class)
                    .join();

            assertEquals(200, response.statusCode());
            assertNotNull(response.body());
            assertEquals("Async Patched", response.body().name());
        }

        @Test
        void sendAsync_voidTerminal_completesCorrectly() {
            HttpResponse<Void> response = client.patch()
                    .path("/persons/{id}", "id", "person-1")
                    .body(new UpdatePersonRequest("Void Patched", "void-patched@example.com"))
                    .sendAsync()
                    .join();

            assertEquals(200, response.statusCode());
        }

        @Test
        void sendAsync_genericType_completesCorrectly() {
            HttpResponse<Person> response = client.patch()
                    .path("/persons/{id}", "id", "person-1")
                    .body(new UpdatePersonRequest("Generic Patched", "gen@example.com"))
                    .sendAsync(new GenericType<Person>() {
                    })
                    .join();

            assertEquals(200, response.statusCode());
            assertNotNull(response.body());
        }
    }

    // -------------------------------------------------------------------------
    // DELETE async
    // -------------------------------------------------------------------------

    @Nested
    class DeleteAsync {
        @Test
        void sendAsync_completesWithNoContent() {
            HttpResponse<Void> response = client.delete()
                    .path("/persons/{id}", "id", "person-1")
                    .sendAsync()
                    .join();

            assertEquals(204, response.statusCode());
        }
    }

    // -------------------------------------------------------------------------
    // HEAD async
    // -------------------------------------------------------------------------

    @Nested
    class HeadAsync {
        @Test
        void sendAsync_completesWithHeaders() {
            HttpResponse<Void> response = client.head()
                    .path("/persons/{id}", "id", "person-1")
                    .sendAsync()
                    .join();

            assertEquals(200, response.statusCode());
            assertTrue(response.headers().firstValue("Content-Type").isPresent());
        }
    }

    // -------------------------------------------------------------------------
    // Async error propagation
    // -------------------------------------------------------------------------

    @Nested
    class AsyncErrorPropagation {
        /**
         * A non-2xx response throws a {@link CompletionException} wrapping the typed HTTP
         * exception — verifying the {@code HttpEngine.sendAsync()} error path that unwraps
         * the JDK's {@code IOException → HttpResponseException} chain.
         */
        @Test
        void httpError_wrapsTypedExceptionInCompletionException() {
            CompletionException ex = assertThrows(
                    CompletionException.class,
                    () -> client.get()
                            .path("/status/404")
                            .sendAsync(Person.class)
                            .join()
            );

            assertInstanceOf(NotFoundException.class, ex.getCause());
        }

        /**
         * A transport-level failure throws a {@link CompletionException} wrapping a
         * {@link TransportException} — verifying the async error path in
         * {@code HttpEngine.sendAsync()}.  Note that unlike the synchronous path, the async
         * handler wraps all {@link java.io.IOException} subtypes as {@link TransportException}
         * (it has no individual catch clauses for {@code ConnectException} etc.).
         */
        @Test
        void transportError_wrapsTransportExceptionInCompletionException() throws IOException {
            int port;

            try (ServerSocket socket = new ServerSocket(0)) {
                port = socket.getLocalPort();
            }

            Client badClient = Client.builder()
                    .configuration(
                            ClientConfiguration.builder()
                                    .uri(URI.create("http://localhost:" + port))
                                    .connectTimeout(Duration.ofSeconds(5))
                                    .readTimeout(Duration.ofSeconds(5))
                                    .serializer(JacksonSerializer.builder().build())
                                    .build()
                    )
                    .build();

            CompletionException ex = assertThrows(
                    CompletionException.class,
                    () -> badClient.get()
                            .path("/anything")
                            .sendAsync(String.class)
                            .join()
            );

            assertInstanceOf(TransportException.class, ex.getCause());
        }
    }
}
