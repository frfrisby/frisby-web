package software.frisby.web.client;

import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import software.frisby.web.client.event.ClientEventListener;
import software.frisby.web.client.event.RequestCompletedEvent;
import software.frisby.web.client.event.RequestFailedEvent;
import software.frisby.web.client.exception.NotFoundException;
import software.frisby.web.serial.jackson.JacksonSerializer;
import software.frisby.web.server.Server;
import software.frisby.web.server.ServerConfiguration;
import software.frisby.web.test.TestLogging;
import software.frisby.web.test.TestResources;
import software.frisby.web.test.domain.Person;
import software.frisby.web.test.log.LogExpectation;
import software.frisby.web.test.log.SystemLogVerifier;

import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase C integration tests — {@link ClientEventListener} callback behavior.
 * <p>
 * Each test wires a capturing listener to a fresh {@link Client} instance so that
 * event assertions are isolated from other tests running in the same JVM.
 */
class ClientEventListenerTest {
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
                        TestLogging.forClass(ClientEventListenerTest.class)
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

    private Client clientWith(ClientEventListener listener) {
        return Client.builder()
                .configuration(
                        ClientConfiguration.builder()
                                .uri(server.uri())
                                .connectTimeout(Duration.ofSeconds(5))
                                .readTimeout(Duration.ofSeconds(30))
                                .serializer(JacksonSerializer.builder().build())
                                .build()
                )
                .eventListener(listener)
                .build();
    }

    // -------------------------------------------------------------------------
    // onRequestCompleted
    // -------------------------------------------------------------------------

    @Nested
    class OnRequestCompleted {
        /**
         * A successful GET fires {@code onRequestCompleted} with the correct method,
         * status code, and a positive latency.
         */
        @Test
        void successfulGet_firesOnRequestCompleted() {
            AtomicReference<RequestCompletedEvent> captured = new AtomicReference<>();

            Client client = clientWith(new ClientEventListener() {
                @Override
                public void onRequestCompleted(RequestCompletedEvent event) {
                    captured.set(event);
                }

                @Override
                public void onRequestFailed(RequestFailedEvent event) {
                }
            });

            HttpResponse<Person> response = client.get()
                    .path("/persons/{id}", "id", "person-1")
                    .send(Person.class);

            assertEquals(200, response.statusCode());
            assertNotNull(captured.get());
            assertEquals("GET", captured.get().method());
            assertEquals(200, captured.get().statusCode());
            assertTrue(captured.get().successful());
            assertNotNull(captured.get().uri());
        }

        /**
         * A 4xx response fires {@code onRequestFailed} only — the two callbacks are
         * mutually exclusive.  {@code onRequestCompleted} must not be called because an
         * exception will be thrown to the caller.
         */
        @Test
        void errorResponse_firesOnRequestFailedOnly() {
            AtomicReference<RequestCompletedEvent> completed = new AtomicReference<>();
            AtomicReference<RequestFailedEvent> failed = new AtomicReference<>();

            Client client = clientWith(new ClientEventListener() {
                @Override
                public void onRequestCompleted(RequestCompletedEvent event) {
                    completed.set(event);
                }

                @Override
                public void onRequestFailed(RequestFailedEvent event) {
                    failed.set(event);
                }
            });

            assertThrows(
                    NotFoundException.class,
                    () -> client.get().path("/persons/{id}", "id", "not-found").send(Person.class)
            );

            // onRequestFailed fires — it's a failed HTTP exchange
            assertNotNull(failed.get());
            assertEquals(404, failed.get().statusCode().orElse(-1));

            // onRequestCompleted must NOT fire — the callbacks are mutually exclusive
            assertNull(completed.get());
        }
    }

    // -------------------------------------------------------------------------
    // onRequestFailed
    // -------------------------------------------------------------------------

    @Nested
    class OnRequestFailed {
        /**
         * A 404 response fires {@code onRequestFailed} with the correct status code and
         * the exception instance that is thrown to the caller.
         */
        @Test
        void notFoundResponse_firesOnRequestFailedWithStatusAndCause() {
            AtomicReference<RequestFailedEvent> captured = new AtomicReference<>();

            Client client = clientWith(new ClientEventListener() {
                @Override
                public void onRequestCompleted(RequestCompletedEvent event) {
                }

                @Override
                public void onRequestFailed(RequestFailedEvent event) {
                    captured.set(event);
                }
            });

            assertThrows(
                    NotFoundException.class,
                    () -> client.get().path("/persons/{id}", "id", "not-found").send(Person.class)
            );

            assertNotNull(captured.get());
            assertEquals("GET", captured.get().method());
            assertTrue(captured.get().statusCode().isPresent());
            assertEquals(404, captured.get().statusCode().get());
            assertInstanceOf(NotFoundException.class, captured.get().cause());
        }
    }

    // -------------------------------------------------------------------------
    // Exception safety
    // -------------------------------------------------------------------------

    @Nested
    class ExceptionSafety {
        /**
         * An exception thrown inside {@code onRequestCompleted} is swallowed — the
         * request still completes and the caller receives the response.
         * <p>
         * Verified via {@link HttpEngine} {@code WARNING} log: the swallowed exception
         * must appear in the log so operators can detect a broken listener.
         */
        @Test
        void onRequestCompletedThrows_responseReturnedAndWarningLogged() {
            Client client = clientWith(new ClientEventListener() {
                @Override
                public void onRequestCompleted(RequestCompletedEvent event) {
                    throw new RuntimeException("Simulated onRequestCompleted failure");
                }

                @Override
                public void onRequestFailed(RequestFailedEvent event) {
                }
            });

            try (SystemLogVerifier verifier = SystemLogVerifier.builder()
                    .configure(HttpEngine.class, System.Logger.Level.WARNING)
                    .expect(
                            LogExpectation.builder()
                                    .logger(HttpEngine.class)
                                    .level(System.Logger.Level.WARNING)
                                    .predicate(e -> e.message().contains(
                                            "ClientEventListener.onRequestCompleted threw an unexpected exception"))
                                    .failureMessage("Expected WARNING log when onRequestCompleted throws.")
                                    .build()
                    )
                    .build()) {
                HttpResponse<Person> response = client.get()
                        .path("/persons/{id}", "id", "person-1")
                        .send(Person.class);

                assertEquals(200, response.statusCode());
                assertNotNull(response.body());

                verifier.assertExpectations();
                assertEquals(1, verifier.warningCount());
            }
        }

        /**
         * An exception thrown inside {@code onRequestFailed} is swallowed — the original
         * HTTP exception is still propagated to the caller.
         * <p>
         * Verified via {@link HttpEngine} {@code WARNING} log: the swallowed exception
         * must appear in the log so operators can detect a broken listener.
         */
        @Test
        void onRequestFailedThrows_exceptionPropagatedAndWarningLogged() {
            Client client = clientWith(new ClientEventListener() {
                @Override
                public void onRequestCompleted(RequestCompletedEvent event) {
                }

                @Override
                public void onRequestFailed(RequestFailedEvent event) {
                    throw new RuntimeException("Simulated onRequestFailed failure");
                }
            });

            try (SystemLogVerifier verifier = SystemLogVerifier.builder()
                    .configure(HttpEngine.class, System.Logger.Level.WARNING)
                    .expect(
                            LogExpectation.builder()
                                    .logger(HttpEngine.class)
                                    .level(System.Logger.Level.WARNING)
                                    .predicate(e -> e.message().contains(
                                            "ClientEventListener.onRequestFailed threw an unexpected exception"))
                                    .failureMessage("Expected WARNING log when onRequestFailed throws.")
                                    .build()
                    )
                    .build()) {
                assertThrows(
                        NotFoundException.class,
                        () -> client.get().path("/persons/{id}", "id", "not-found").send(Person.class)
                );

                verifier.assertExpectations();
            }
        }
    }
}

