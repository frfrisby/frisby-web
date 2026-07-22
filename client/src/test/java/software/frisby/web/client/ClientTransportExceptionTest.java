package software.frisby.web.client;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import software.frisby.web.client.exception.AbortedException;
import software.frisby.web.client.exception.ConnectTimeoutException;
import software.frisby.web.client.exception.ReadTimeoutException;
import software.frisby.web.client.exception.TransportException;
import software.frisby.web.serial.jackson.JacksonSerializer;
import software.frisby.web.server.Server;
import software.frisby.web.server.ServerConfiguration;
import software.frisby.web.test.TestLogging;
import software.frisby.web.test.log.LogExpectation;
import software.frisby.web.test.log.SystemLogVerifier;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Phase E integration tests — transport-level exception mapping.
 * <p>
 * Each nested class exercises a distinct failure mode that cannot be triggered via a
 * well-behaved {@code test-support} server:
 * <ul>
 *   <li>{@link ConnectTimeout} — non-routable IP absorbs the TCP SYN without replying</li>
 *   <li>{@link ConnectError} — nothing listening on the target port (immediate RST)</li>
 *   <li>{@link ReadTimeout} — server deliberately delays past the configured read timeout</li>
 *   <li>{@link Transport} — TLS client connected to a plain-HTTP server (handshake failure)</li>
 *   <li>{@link Aborted} — calling thread interrupted while a synchronous request is in flight</li>
 * </ul>
 * <p>
 * {@link ReadTimeout} and {@link Aborted} share the single slow server started in
 * {@link #startServer()}.  {@link Transport} reuses the same server but connects to it
 * with an {@code https://} URI, forcing an SSL handshake failure.
 * {@link ConnectTimeout} and {@link ConnectError} require no running server.
 */
class ClientTransportExceptionTest {

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
                .resources(new SlowResource())
                .components(TestLogging.forClass(ClientTransportExceptionTest.class))
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
    // Slow resource — used by ReadTimeout and Aborted tests
    // -------------------------------------------------------------------------

    /**
     * JAX-RS resource that sleeps for 30 seconds before responding.
     * <p>
     * Used to trigger {@link ReadTimeoutException} (client times out waiting for
     * the response) and {@link AbortedException} (calling thread is interrupted
     * while the request is in flight).
     */
    @Path("/slow")
    public static class SlowResource {
        private static final int SLEEP_MILLIS = 30_000;

        @GET
        public Response get() {
            try {
                Thread.sleep(SLEEP_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            return Response.ok("done").build();
        }
    }

    // -------------------------------------------------------------------------
    // ConnectTimeoutException
    // -------------------------------------------------------------------------

    /**
     * Points the client at {@code 203.0.113.0} (RFC 5737 TEST-NET-3 — non-routable)
     * with a 200 ms connect timeout.  The TCP SYN packet is never acknowledged, so
     * the JDK raises {@link java.net.http.HttpConnectTimeoutException}, which the
     * engine maps to {@link ConnectTimeoutException}.
     */
    @Nested
    class ConnectTimeout {
        @Test
        void nonRoutableAddress_throwsConnectTimeoutException() {
            Client client = Client.builder()
                    .configuration(
                            ClientConfiguration.builder()
                                    .uri(URI.create("http://203.0.113.0:81"))
                                    .connectTimeout(Duration.ofMillis(200))
                                    .readTimeout(Duration.ofSeconds(5))
                                    .serializer(JacksonSerializer.builder().build())
                                    .build()
                    )
                    .build();

            try (SystemLogVerifier verifier = SystemLogVerifier.builder()
                    .configure(RequestLogger.class, System.Logger.Level.ERROR)
                    .expect(
                            LogExpectation.builder()
                                    .logger(RequestLogger.class)
                                    .level(System.Logger.Level.ERROR)
                                    .predicate(e -> e.message().contains("GET") && e.message().contains("✕"))
                                    .failureMessage("Expected ERROR log entry when ConnectTimeoutException is thrown.")
                                    .build()
                    )
                    .build()) {
                assertThrows(
                        ConnectTimeoutException.class,
                        () -> client.get().path("/anything").send(String.class)
                );

                verifier.assertExpectations();
            }
        }
    }

    // -------------------------------------------------------------------------
    // ConnectException
    // -------------------------------------------------------------------------

    /**
     * Acquires a free ephemeral port and releases it immediately, then points the
     * client at that port.  Nothing is listening → immediate TCP RST → the JDK raises
     * {@link java.net.ConnectException}, which the engine maps to
     * {@link exception.ConnectException}.
     */
    @Nested
    class ConnectError {
        @Test
        void nothingListeningOnPort_throwsConnectException() throws IOException {
            int port;

            try (ServerSocket socket = new ServerSocket(0)) {
                port = socket.getLocalPort();
            }

            Client client = Client.builder()
                    .configuration(
                            ClientConfiguration.builder()
                                    .uri(URI.create("http://localhost:" + port))
                                    .connectTimeout(Duration.ofSeconds(5))
                                    .readTimeout(Duration.ofSeconds(5))
                                    .serializer(JacksonSerializer.builder().build())
                                    .build()
                    )
                    .build();

            try (SystemLogVerifier verifier = SystemLogVerifier.builder()
                    .configure(RequestLogger.class, System.Logger.Level.ERROR)
                    .expect(
                            LogExpectation.builder()
                                    .logger(RequestLogger.class)
                                    .level(System.Logger.Level.ERROR)
                                    .predicate(e -> e.message().contains("GET") && e.message().contains("✕"))
                                    .failureMessage("Expected ERROR log entry when ConnectException is thrown.")
                                    .build()
                    )
                    .build()) {
                assertThrows(
                        software.frisby.web.client.exception.ConnectException.class,
                        () -> client.get().path("/anything").send(String.class)
                );

                verifier.assertExpectations();
            }
        }
    }

    // -------------------------------------------------------------------------
    // ReadTimeoutException
    // -------------------------------------------------------------------------

    /**
     * Configures a 500 ms read timeout and sends a request to {@link SlowResource},
     * which sleeps for 30 seconds.  The JDK raises
     * {@link java.net.http.HttpTimeoutException} after the read timeout elapses,
     * which the engine maps to {@link ReadTimeoutException}.
     */
    @Nested
    class ReadTimeout {
        @Test
        void serverSlowerThanTimeout_throwsReadTimeoutException() {
            Client client = Client.builder()
                    .configuration(
                            ClientConfiguration.builder()
                                    .uri(server.uri())
                                    .connectTimeout(Duration.ofSeconds(5))
                                    .readTimeout(Duration.ofMillis(500))
                                    .serializer(JacksonSerializer.builder().build())
                                    .build()
                    )
                    .build();

            try (SystemLogVerifier verifier = SystemLogVerifier.builder()
                    .configure(RequestLogger.class, System.Logger.Level.ERROR)
                    .expect(
                            LogExpectation.builder()
                                    .logger(RequestLogger.class)
                                    .level(System.Logger.Level.ERROR)
                                    .predicate(e -> e.message().contains("GET") && e.message().contains("✕"))
                                    .failureMessage("Expected ERROR log entry when ReadTimeoutException is thrown.")
                                    .build()
                    )
                    .build()) {
                assertThrows(
                        ReadTimeoutException.class,
                        () -> client.get().path("/slow").send(String.class)
                );

                verifier.assertExpectations();
            }
        }
    }

    // -------------------------------------------------------------------------
    // TransportException
    // -------------------------------------------------------------------------

    /**
     * Constructs an {@code https://} URI targeting the same port as the plain-HTTP
     * test server.  The JDK {@code HttpClient} initiates a TLS handshake; the server
     * responds with plain HTTP bytes, which the TLS layer rejects with an
     * {@link javax.net.ssl.SSLException} — a subtype of {@link IOException} that the
     * engine maps to {@link TransportException}.
     */
    @Nested
    class Transport {
        @Test
        void tlsClientAgainstPlainHttpServer_throwsTransportException() {
            URI httpsUri = URI.create("https://localhost:" + server.uri().getPort());

            Client client = Client.builder()
                    .configuration(
                            ClientConfiguration.builder()
                                    .uri(httpsUri)
                                    .connectTimeout(Duration.ofSeconds(5))
                                    .readTimeout(Duration.ofSeconds(5))
                                    .serializer(JacksonSerializer.builder().build())
                                    .build()
                    )
                    .build();

            try (SystemLogVerifier verifier = SystemLogVerifier.builder()
                    .configure(RequestLogger.class, System.Logger.Level.ERROR)
                    .expect(
                            LogExpectation.builder()
                                    .logger(RequestLogger.class)
                                    .level(System.Logger.Level.ERROR)
                                    .predicate(e -> e.message().contains("GET") && e.message().contains("✕"))
                                    .failureMessage("Expected ERROR log entry when TransportException is thrown.")
                                    .build()
                    )
                    .build()) {
                assertThrows(
                        TransportException.class,
                        () -> client.get().path("/slow").send(String.class)
                );

                verifier.assertExpectations();
            }
        }
    }

    // -------------------------------------------------------------------------
    // Async transport error — non-retry-eligible request
    // -------------------------------------------------------------------------

    /**
     * A non-idempotent {@code POST} without {@code allowNonIdempotent()} is not retry-eligible,
     * so {@code sendAsync()} bypasses the retry loop and calls {@code doSingleSendAsync} with
     * {@code retryAttempt == 0}.  Pointing the request at a port with nothing listening triggers
     * a transport error through that path, covering
     * {@code requestLogger.logTransportError(outbound, cause)} (no attempt number).
     */
    @Nested
    class AsyncNonRetryEligible {
        @Test
        void nonRetryEligibleAsyncPost_transportError_completesExceptionally() throws IOException {
            int port;

            try (ServerSocket socket = new ServerSocket(0)) {
                port = socket.getLocalPort();
            }

            Client client = Client.builder()
                    .configuration(
                            ClientConfiguration.builder()
                                    .uri(URI.create("http://localhost:" + port))
                                    .connectTimeout(Duration.ofSeconds(5))
                                    .readTimeout(Duration.ofSeconds(5))
                                    .serializer(JacksonSerializer.builder().build())
                                    .build()
                    )
                    .build();

            try (SystemLogVerifier verifier = SystemLogVerifier.builder()
                    .configure(RequestLogger.class, System.Logger.Level.ERROR)
                    .expect(
                            LogExpectation.builder()
                                    .logger(RequestLogger.class)
                                    .level(System.Logger.Level.ERROR)
                                    .predicate(e -> e.message().contains("POST") && e.message().contains("✕"))
                                    .failureMessage("Expected ERROR log entry for async transport failure on non-retry-eligible POST.")
                                    .build()
                    )
                    .build()) {
                // POST without allowNonIdempotent() → isRetryEligible() == false →
                // doSingleSendAsync(outbound, bodyHandler, 0) → retryAttempt == 0
                CompletionException ex = assertThrows(
                        CompletionException.class,
                        () -> client.post()
                                .path("/anything")
                                .body("payload")
                                .sendAsync(String.class)
                                .join()
                );

                assertInstanceOf(TransportException.class, ex.getCause());

                verifier.assertExpectations();
            }
        }
    }

    // -------------------------------------------------------------------------
    // AbortedException
    // -------------------------------------------------------------------------

    /**
     * Sends a request to {@link SlowResource} (30-second response) on a dedicated
     * thread, then interrupts that thread 200 ms into the flight.  The JDK propagates
     * the interrupt as {@link InterruptedException} from
     * {@link java.net.http.HttpClient#send}, which the engine maps to
     * {@link AbortedException} and re-sets the thread's interrupt flag.
     */
    @Nested
    class Aborted {
        @Test
        void callingThreadInterrupted_throwsAbortedException() throws InterruptedException {
            Client client = Client.builder()
                    .configuration(
                            ClientConfiguration.builder()
                                    .uri(server.uri())
                                    .connectTimeout(Duration.ofSeconds(5))
                                    .readTimeout(Duration.ofSeconds(60))
                                    .serializer(JacksonSerializer.builder().build())
                                    .build()
                    )
                    .build();

            AtomicReference<Throwable> thrown = new AtomicReference<>();

            Thread requestThread = new Thread(() -> {
                try {
                    client.get().path("/slow").send(String.class);
                } catch (AbortedException e) {
                    thrown.set(e);
                }
            });

            requestThread.start();

            Thread.sleep(200);

            requestThread.interrupt();
            requestThread.join(5_000);

            assertInstanceOf(
                    AbortedException.class,
                    thrown.get(),
                    "Expected AbortedException to be thrown when the calling thread is interrupted."
            );
        }
    }
}
