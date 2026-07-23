package software.frisby.web.client;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.junit.jupiter.api.*;
import software.frisby.web.client.exception.AbortedException;
import software.frisby.web.client.exception.ConnectException;
import software.frisby.web.client.exception.ConnectTimeoutException;
import software.frisby.web.client.exception.ServiceUnavailableException;
import software.frisby.web.serial.jackson.JacksonSerializer;
import software.frisby.web.server.Server;
import software.frisby.web.server.ServerConfiguration;
import software.frisby.web.test.TestLogging;
import software.frisby.web.test.domain.Person;
import software.frisby.web.test.log.LogExpectation;
import software.frisby.web.test.log.SystemLogVerifier;
import software.frisby.web.client.security.SecurityProvider;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the {@link RetryPolicy} retry loop in {@link HttpEngine}.
 * <p>
 * Covers:
 * <ul>
 *   <li>Sync retry — GET succeeds after N failures</li>
 *   <li>Sync retry — respects {@code maxAttempts}</li>
 *   <li>Sync retry — multipart body is never retried</li>
 *   <li>Sync retry — non-idempotent method blocked by default</li>
 *   <li>Sync retry — non-idempotent allowed when explicitly permitted</li>
 *   <li>Async retry — GET succeeds after N failures via {@code sendAsync()}</li>
 * </ul>
 */
class ClientRetryTest {
    // Each resource owns its own AtomicInteger so tests can inspect call counts.
    private static final FailableGetResource failableGet = new FailableGetResource();
    private static final FailablePostResource failablePost = new FailablePostResource();
    private static final FailableMultipartResource failableMultipart = new FailableMultipartResource();
    private static Server server;

    // -------------------------------------------------------------------------
    // Server resources
    // -------------------------------------------------------------------------

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
                .resources(failableGet, failablePost, failableMultipart)
                .components(
                        new MultiPartFeature(),
                        TestLogging.forClass(ClientRetryTest.class)
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

    private static Client buildClient(RetryPolicy retryPolicy) {
        return Client.builder()
                .configuration(
                        ClientConfiguration.builder()
                                .uri(server.uri())
                                .connectTimeout(Duration.ofSeconds(5))
                                .readTimeout(Duration.ofSeconds(10))
                                .serializer(JacksonSerializer.builder().build())
                                .build()
                )
                .retryPolicy(retryPolicy)
                .build();
    }

    // -------------------------------------------------------------------------
    // Test lifecycle
    // -------------------------------------------------------------------------

    @BeforeEach
    void resetResources() {
        failableGet.reset();
        failablePost.reset();
        failableMultipart.reset();
    }

    /**
     * {@code GET /retry/get} — returns 503 for the first {@link #failCount} calls,
     * then 200 with a small JSON body.
     */
    @Path("/retry/get")
    @Produces(MediaType.APPLICATION_JSON)
    public static class FailableGetResource {
        private final AtomicInteger calls = new AtomicInteger();
        volatile int failCount = 2;
        volatile int failStatus = 503;
        volatile String retryAfterHeader = null;

        void reset() {
            calls.set(0);
            failCount = 2;
            failStatus = 503;
            retryAfterHeader = null;
        }

        int callCount() {
            return calls.get();
        }

        @GET
        public Response get() {
            int call = calls.incrementAndGet();

            if (call <= failCount) {
                Response.ResponseBuilder builder = Response.status(failStatus)
                        .entity("{\"status\":" + failStatus + "}")
                        .type(MediaType.APPLICATION_JSON);

                if (null != retryAfterHeader) {
                    builder.header("Retry-After", retryAfterHeader);
                }

                return builder.build();
            }

            return Response.ok("{\"name\":\"Alice\",\"age\":30}")
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        }
    }

    /**
     * {@code POST /retry/post} — returns 503 for the first {@link #failCount} calls,
     * then 200 with a small JSON body.
     */
    @Path("/retry/post")
    @Produces(MediaType.APPLICATION_JSON)
    public static class FailablePostResource {
        private final AtomicInteger calls = new AtomicInteger();
        volatile int failCount = 2;

        void reset() {
            calls.set(0);
            failCount = 2;
        }

        int callCount() {
            return calls.get();
        }

        @POST
        @Consumes(MediaType.APPLICATION_JSON)
        public Response post(String body) {
            int call = calls.incrementAndGet();

            if (call <= failCount) {
                return Response.status(503)
                        .entity("{\"status\":503}")
                        .type(MediaType.APPLICATION_JSON)
                        .build();
            }

            return Response.ok("{\"name\":\"Alice\",\"age\":30}")
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        }
    }

    /**
     * {@code POST /retry/multipart} — returns 503 for the first {@link #failCount} calls,
     * then 200.  Used to verify that multipart requests are never retried.
     */
    @Path("/retry/multipart")
    @Produces(MediaType.APPLICATION_JSON)
    public static class FailableMultipartResource {
        private final AtomicInteger calls = new AtomicInteger();
        volatile int failCount = 2;

        void reset() {
            calls.set(0);
            failCount = 2;
        }

        int callCount() {
            return calls.get();
        }

        @POST
        @Consumes(MediaType.MULTIPART_FORM_DATA)
        public Response upload() {
            int call = calls.incrementAndGet();

            if (call <= failCount) {
                return Response.status(503)
                        .entity("{\"status\":503}")
                        .type(MediaType.APPLICATION_JSON)
                        .build();
            }

            return Response.ok("{\"name\":\"Alice\",\"age\":30}")
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        }
    }

    // -------------------------------------------------------------------------
    // Sync retry — GET
    // -------------------------------------------------------------------------

    @Nested
    class SyncGetRetry {
        @Test
        void serviceUnavailable_retriedUntilSuccess() {
            failableGet.failCount = 2;

            RetryPolicy policy = RetryPolicy.builder()
                    .maxAttempts(4)
                    .on(RetryOn.SERVICE_UNAVAILABLE)
                    .delay(RetryDelay.fixed(Duration.ofMillis(10)))
                    .build();
            Client client = buildClient(policy);

            HttpResponse<Person> response = client.get()
                    .path("/retry/get")
                    .send(Person.class);

            assertEquals(200, response.statusCode());
            assertEquals(3, failableGet.callCount());
        }

        @Test
        void maxAttemptsExhausted_throwsLastException() {
            failableGet.failCount = 5;

            RetryPolicy policy = RetryPolicy.builder()
                    .maxAttempts(3)
                    .on(RetryOn.SERVICE_UNAVAILABLE)
                    .delay(RetryDelay.fixed(Duration.ofMillis(10)))
                    .build();
            Client client = buildClient(policy);

            assertThrows(
                    ServiceUnavailableException.class,
                    () -> client.get().path("/retry/get").send(Person.class)
            );

            assertEquals(3, failableGet.callCount());
        }

        @Test
        void noMatchingRetryOn_notRetried() {
            failableGet.failCount = 2;
            failableGet.failStatus = 503;

            RetryPolicy policy = RetryPolicy.builder()
                    .maxAttempts(4)
                    .on(RetryOn.BAD_GATEWAY)          // 503 is not in this set
                    .delay(RetryDelay.fixed(Duration.ofMillis(10)))
                    .build();
            Client client = buildClient(policy);

            assertThrows(
                    ServiceUnavailableException.class,
                    () -> client.get().path("/retry/get").send(Person.class)
            );

            assertEquals(1, failableGet.callCount());
        }

        @Test
        void retryPolicyNone_notRetried() {
            failableGet.failCount = 2;

            Client client = buildClient(RetryPolicy.none());

            assertThrows(
                    ServiceUnavailableException.class,
                    () -> client.get().path("/retry/get").send(Person.class)
            );

            assertEquals(1, failableGet.callCount());
        }
    }

    // -------------------------------------------------------------------------
    // Sync retry — INFO log includes attempt number on retry success
    // -------------------------------------------------------------------------

    /**
     * Verifies the {@code INFO} one-liner in {@link RequestLogger#logSuccess} when
     * {@code retryAttempt > 1}.  TRACE must be off so the {@code else if (INFO)} branch
     * is reached; the request must succeed after at least one retry so {@code retryAttempt}
     * is {@code 2}.
     */
    @Nested
    class SyncRetrySuccessLogging {
        @Test
        void successAfterRetry_atInfoLevel_logsAttemptNumber() {
            failableGet.failCount = 1;

            RetryPolicy policy = RetryPolicy.builder()
                    .maxAttempts(3)
                    .on(RetryOn.SERVICE_UNAVAILABLE)
                    .delay(RetryDelay.fixed(Duration.ofMillis(10)))
                    .build();
            Client client = buildClient(policy);

            try (SystemLogVerifier verifier = SystemLogVerifier.builder()
                    .configure(RequestLogger.class, System.Logger.Level.INFO)
                    .expect(
                            LogExpectation.builder()
                                    .logger(RequestLogger.class)
                                    .level(System.Logger.Level.INFO)
                                    .predicate(e -> e.message().contains("GET")
                                            && e.message().contains("200")
                                            && e.message().contains("attempt 2"))
                                    .failureMessage("Expected INFO log with 'attempt 2' after retry success.")
                                    .build()
                    )
                    .build()) {
                client.get().path("/retry/get").send(Person.class);

                verifier.assertExpectations();
            }
        }

        @Test
        void successAfterRetry_atTraceLevel_includesAttemptNumberInBlock() {
            failableGet.failCount = 1;

            RetryPolicy policy = RetryPolicy.builder()
                    .maxAttempts(3)
                    .on(RetryOn.SERVICE_UNAVAILABLE)
                    .delay(RetryDelay.fixed(Duration.ofMillis(10)))
                    .build();
            Client client = buildClient(policy);

            try (SystemLogVerifier verifier = SystemLogVerifier.builder()
                    .configure(RequestLogger.class, System.Logger.Level.TRACE)
                    .expect(
                            LogExpectation.builder()
                                    .logger(RequestLogger.class)
                                    .level(System.Logger.Level.TRACE)
                                    .predicate(e -> e.message().contains("200")
                                            && e.message().contains("attempt 2"))
                                    .failureMessage("Expected TRACE log with 'attempt 2' in the success block.")
                                    .build()
                    )
                    .build()) {
                client.get().path("/retry/get").send(Person.class);

                verifier.assertExpectations();
            }
        }
    }

    // -------------------------------------------------------------------------
    // Sync retry — [attempt N] prefix in transport error log
    // -------------------------------------------------------------------------

    /**
     * Verifies the {@code [attempt N]} prefix in {@link RequestLogger#logTransportError}
     * when {@code retryAttempt > 1}.  Retrying on {@link RetryOn#CONNECT_FAILURE} against
     * a port with nothing listening guarantees a {@link ConnectException} on every attempt;
     * the second attempt (retryAttempt = 2) should produce an ERROR log with {@code [attempt 2]}.
     */
    @Nested
    class SyncRetryTransportErrorLogging {
        @Test
        void transportErrorOnRetry_logsAttemptNumber() throws IOException {
            int port;

            try (ServerSocket socket = new ServerSocket(0)) {
                port = socket.getLocalPort();
            }

            RetryPolicy policy = RetryPolicy.builder()
                    .maxAttempts(3)
                    .on(RetryOn.CONNECT_FAILURE)
                    .delay(RetryDelay.fixed(Duration.ofMillis(10)))
                    .build();

            Client client = Client.builder()
                    .configuration(
                            ClientConfiguration.builder()
                                    .uri(URI.create("http://localhost:" + port))
                                    .connectTimeout(Duration.ofSeconds(5))
                                    .readTimeout(Duration.ofSeconds(5))
                                    .serializer(JacksonSerializer.builder().build())
                                    .build()
                    )
                    .retryPolicy(policy)
                    .build();

            try (SystemLogVerifier verifier = SystemLogVerifier.builder()
                    .configure(RequestLogger.class, System.Logger.Level.ERROR)
                    .expect(
                            LogExpectation.builder()
                                    .logger(RequestLogger.class)
                                    .level(System.Logger.Level.ERROR)
                                    .predicate(e -> e.message().contains("[attempt 2]"))
                                    .failureMessage("Expected ERROR log with '[attempt 2]' on second transport failure.")
                                    .build()
                    )
                    .build()) {
                assertThrows(
                        ConnectException.class,
                        () -> client.get().path("/anything").send(String.class)
                );

                verifier.assertExpectations();
            }
        }
    }

    // -------------------------------------------------------------------------
    // Logging disabled — effectiveLevel returns null, methods exit early
    // -------------------------------------------------------------------------

    /**
     * Verifies that all three {@link RequestLogger} methods that gate on
     * {@code RequestLogger.effectiveLevel()} handle logging being fully disabled
     * gracefully — when neither the preferred nor the fallback level is loggable,
     * the method exits early without producing any output or throwing an exception.
     */
    @Nested
    class LoggingDisabled {

        /**
         * {@code logError} calls {@code effectiveLevel(TRACE, WARNING)}.
         * Configuring the logger at {@code ERROR} makes both levels non-loggable,
         * so {@code effectiveLevel} returns {@code null} and the method returns
         * immediately without logging.
         */
        @Test
        void httpError_withLoggerAboveWarning_silentlyIgnored() {
            failableGet.failCount = 5;   // always returns 503

            Client client = buildClient(RetryPolicy.none());

            try (SystemLogVerifier verifier = SystemLogVerifier.builder()
                    .configure(RequestLogger.class, System.Logger.Level.ERROR)
                    .configure("software.frisby.web.server.RequestLogger", System.Logger.Level.ERROR)
                    .build()) {
                assertThrows(
                        ServiceUnavailableException.class,
                        () -> client.get().path("/retry/get").send(Person.class)
                );

                assertEquals(0, verifier.warningCount(), "logError must not emit a WARNING when the level is suppressed.");
            }
        }

        /**
         * {@code logTransportError(OutboundRequest, Throwable, int)} calls
         * {@code effectiveLevel(TRACE, ERROR)}.  Configuring the logger at {@code OFF}
         * makes both levels non-loggable → {@code null} → early return.
         */
        @Test
        void transportError_withLoggerOff_silentlyIgnored() throws IOException {
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
                    .retryPolicy(RetryPolicy.none())
                    .build();

            try (SystemLogVerifier verifier = SystemLogVerifier.builder()
                    .configure(RequestLogger.class, System.Logger.Level.OFF)
                    .build()) {
                assertThrows(
                        ConnectException.class,
                        () -> client.get().path("/anything").send(String.class)
                );

                assertEquals(0, verifier.errorCount(), "logTransportError must not emit an ERROR when the level is suppressed.");
            }
        }

        /**
         * {@code logTransportError(Throwable, int)} (the no-outbound auth-phase overload)
         * also calls {@code effectiveLevel(TRACE, ERROR)}.  Configuring at {@code OFF}
         * triggers the same {@code null} → early-return path.
         */
        @Test
        void authTransportError_withLoggerOff_silentlyIgnored() {
            SecurityProvider alwaysFailing = ctx -> {
                throw new ConnectTimeoutException("Simulated auth failure", null);
            };

            Client client = buildClient(RetryPolicy.none());

            try (SystemLogVerifier verifier = SystemLogVerifier.builder()
                    .configure(RequestLogger.class, System.Logger.Level.OFF)
                    .build()) {
                assertThrows(
                        ConnectTimeoutException.class,
                        () -> client.get()
                                .path("/retry/get")
                                .security(alwaysFailing)
                                .send(Person.class)
                );

                assertEquals(0, verifier.errorCount(), "logTransportError must not emit an ERROR when the level is suppressed.");
            }
        }
    }

    // -------------------------------------------------------------------------
    // Sync retry — interrupted during retry delay sleep
    // -------------------------------------------------------------------------


    /**
     * Verifies that interrupting the calling thread while it is sleeping through a
     * retry delay (inside the {@code TimeUnit.MILLISECONDS.sleep()} in the retry loop)
     * results in {@link AbortedException} and restores the thread's interrupt flag.
     */
    @Nested
    class SyncRetryInterrupted {
        @Test
        void threadInterruptedDuringRetryDelay_throwsAbortedException() throws InterruptedException {
            failableGet.failCount = 2;

            // Long delay so the thread is reliably asleep when we interrupt it.
            RetryPolicy policy = RetryPolicy.builder()
                    .maxAttempts(4)
                    .on(RetryOn.SERVICE_UNAVAILABLE)
                    .delay(RetryDelay.fixed(Duration.ofSeconds(10)))
                    .build();
            Client client = buildClient(policy);

            AtomicReference<Throwable> thrown = new AtomicReference<>();

            Thread requestThread = new Thread(() -> {
                try {
                    client.get().path("/retry/get").send(Person.class);
                } catch (AbortedException e) {
                    thrown.set(e);
                }
            });

            requestThread.start();

            // Allow time for the first 503 to arrive and the retry sleep to begin.
            Thread.sleep(500);

            requestThread.interrupt();
            requestThread.join(5_000);

            assertInstanceOf(
                    AbortedException.class,
                    thrown.get(),
                    "Expected AbortedException when calling thread is interrupted during retry delay sleep."
            );
        }
    }

    // -------------------------------------------------------------------------
    // Sync retry — multipart never retried
    // -------------------------------------------------------------------------

    @Nested
    class SyncMultipartNotRetried {
        @Test
        void multipartPost_notRetried_evenWithMatchingPolicy() {
            failableMultipart.failCount = 2;

            RetryPolicy policy = RetryPolicy.builder()
                    .maxAttempts(4)
                    .on(RetryOn.SERVICE_UNAVAILABLE)
                    .allowNonIdempotent()
                    .delay(RetryDelay.fixed(Duration.ofMillis(10)))
                    .build();
            Client client = buildClient(policy);

            assertThrows(
                    ServiceUnavailableException.class,
                    () -> client.post()
                            .path("/retry/multipart")
                            .body(FormData.of(
                                    FormPart.file("file", new ByteArrayInputStream(new byte[]{1, 2, 3}), "test.bin")
                            ))
                            .send(Person.class)
            );

            assertEquals(1, failableMultipart.callCount());
        }
    }

    // -------------------------------------------------------------------------
    // Sync retry — non-idempotent methods
    // -------------------------------------------------------------------------

    @Nested
    class SyncNonIdempotent {
        @Test
        void post_withoutAllowNonIdempotent_notRetried() {
            failablePost.failCount = 2;

            RetryPolicy policy = RetryPolicy.builder()
                    .maxAttempts(4)
                    .on(RetryOn.SERVICE_UNAVAILABLE)
                    .delay(RetryDelay.fixed(Duration.ofMillis(10)))
                    // no allowNonIdempotent()
                    .build();
            Client client = buildClient(policy);

            assertThrows(
                    ServiceUnavailableException.class,
                    () -> client.post()
                            .path("/retry/post")
                            .body("{\"name\":\"Alice\"}")
                            .send(Person.class)
            );

            assertEquals(1, failablePost.callCount());
        }

        @Test
        void post_withAllowNonIdempotent_retried() {
            failablePost.failCount = 2;

            RetryPolicy policy = RetryPolicy.builder()
                    .maxAttempts(4)
                    .on(RetryOn.SERVICE_UNAVAILABLE)
                    .allowNonIdempotent()
                    .delay(RetryDelay.fixed(Duration.ofMillis(10)))
                    .build();
            Client client = buildClient(policy);

            HttpResponse<Person> response = client.post()
                    .path("/retry/post")
                    .body("{\"name\":\"Alice\"}")
                    .send(Person.class);

            assertEquals(200, response.statusCode());
            assertEquals(3, failablePost.callCount());
        }
    }

    // -------------------------------------------------------------------------
    // Retry-After header honored (sync)
    // -------------------------------------------------------------------------

    @Nested
    class RetryAfterHeader {
        @Test
        void retryAfterHeader_withinCap_usedAsDelay() {
            failableGet.failCount = 1;
            failableGet.failStatus = 429;
            failableGet.retryAfterHeader = "0";    // 0 s — still retried, just immediately

            RetryPolicy policy = RetryPolicy.builder()
                    .maxAttempts(3)
                    .on(RetryOn.TOO_MANY_REQUESTS)
                    .delay(RetryDelay.fixed(Duration.ofMillis(10)))
                    .honorRetryAfterHeader(Duration.ofSeconds(5))
                    .build();
            Client client = buildClient(policy);

            HttpResponse<Person> response = client.get()
                    .path("/retry/get")
                    .send(Person.class);

            assertEquals(200, response.statusCode());
            assertEquals(2, failableGet.callCount());
        }
    }

    // -------------------------------------------------------------------------
    // Async retry
    // -------------------------------------------------------------------------

    @Nested
    class AsyncRetry {
        @Test
        void serviceUnavailable_retriedUntilSuccess_async() {
            failableGet.failCount = 2;

            RetryPolicy policy = RetryPolicy.builder()
                    .maxAttempts(4)
                    .on(RetryOn.SERVICE_UNAVAILABLE)
                    .delay(RetryDelay.fixed(Duration.ofMillis(10)))
                    .build();
            Client client = buildClient(policy);

            HttpResponse<Person> response = client.get()
                    .path("/retry/get")
                    .sendAsync(Person.class)
                    .join();

            assertEquals(200, response.statusCode());
            assertEquals(3, failableGet.callCount());
        }

        @Test
        void maxAttemptsExhausted_completesExceptionally_async() {
            failableGet.failCount = 5;

            RetryPolicy policy = RetryPolicy.builder()
                    .maxAttempts(3)
                    .on(RetryOn.SERVICE_UNAVAILABLE)
                    .delay(RetryDelay.fixed(Duration.ofMillis(10)))
                    .build();
            Client client = buildClient(policy);

            CompletionException ex = assertThrows(
                    CompletionException.class,
                    () -> client.get()
                            .path("/retry/get")
                            .sendAsync(Person.class)
                            .join()
            );

            assertInstanceOf(ServiceUnavailableException.class, ex.getCause());
            assertEquals(3, failableGet.callCount());
        }

        @Test
        void multipartPost_notRetried_async() {
            failableMultipart.failCount = 2;

            RetryPolicy policy = RetryPolicy.builder()
                    .maxAttempts(4)
                    .on(RetryOn.SERVICE_UNAVAILABLE)
                    .allowNonIdempotent()
                    .delay(RetryDelay.fixed(Duration.ofMillis(10)))
                    .build();
            Client client = buildClient(policy);

            assertThrows(
                    CompletionException.class,
                    () -> client.post()
                            .path("/retry/multipart")
                            .body(FormData.of(
                                    FormPart.file("file", new ByteArrayInputStream(new byte[]{1, 2, 3}), "test.bin")
                            ))
                            .sendAsync(Person.class)
                            .join()
            );

            assertEquals(1, failableMultipart.callCount());
        }
    }

    // -------------------------------------------------------------------------
    // Auth-phase retry — security provider failure is retried
    // -------------------------------------------------------------------------

    /**
     * Verifies that an exception thrown by a {@link SecurityProvider} during the auth
     * phase (before the {@link OutboundRequest} is built) is subject to the retry policy.
     * <p>
     * Because the request never reached the server, the idempotency guard is bypassed:
     * the retry policy applies regardless of the HTTP method.
     */
    @Nested
    class AuthPhaseRetry {

        @Test
        void connectTimeoutFromAuthProvider_retriedUntilSuccess() {
            failableGet.failCount = 0;   // server always returns 200 immediately

            AtomicInteger authCalls = new AtomicInteger();

            SecurityProvider flakyAuth = ctx -> {
                if (authCalls.incrementAndGet() <= 2) {
                    throw new ConnectTimeoutException("Simulated token endpoint timeout", null);
                }
                // Third call: succeed without adding any header
            };

            RetryPolicy policy = RetryPolicy.builder()
                    .maxAttempts(4)
                    .on(RetryOn.CONNECT_TIMEOUT)
                    .delay(RetryDelay.fixed(Duration.ofMillis(10)))
                    .build();
            Client client = buildClient(policy);

            HttpResponse<Person> response = client.get()
                    .path("/retry/get")
                    .security(flakyAuth)
                    .send(Person.class);

            assertEquals(200, response.statusCode());
            assertEquals(3, authCalls.get());
            assertEquals(1, failableGet.callCount());   // only one HTTP request reached the server
        }

        /**
         * An auth-phase failure on a non-idempotent method (POST) is retried without
         * requiring {@code allowNonIdempotent()} — the request never reached the server
         * so the "only replay idempotent methods" guard does not apply.
         */
        @Test
        void authFailureOnPost_retriedWithoutAllowNonIdempotent() {
            failablePost.failCount = 0;   // server always returns 200 immediately

            AtomicInteger authCalls = new AtomicInteger();

            SecurityProvider flakyAuth = ctx -> {
                if (authCalls.incrementAndGet() <= 2) {
                    throw new ConnectTimeoutException("Simulated token endpoint timeout", null);
                }
            };

            RetryPolicy policy = RetryPolicy.builder()
                    .maxAttempts(4)
                    .on(RetryOn.CONNECT_TIMEOUT)
                    .delay(RetryDelay.fixed(Duration.ofMillis(10)))
                    // no allowNonIdempotent() — auth failures bypass the idempotency guard
                    .build();
            Client client = buildClient(policy);

            HttpResponse<Person> response = client.post()
                    .path("/retry/post")
                    .body("{\"name\":\"Alice\"}")
                    .security(flakyAuth)
                    .send(Person.class);

            assertEquals(200, response.statusCode());
            assertEquals(3, authCalls.get());
            assertEquals(1, failablePost.callCount());   // only one HTTP request reached the server
        }

        /**
         * Verifies that interrupting the calling thread while sleeping between retries,
         * when the auth phase threw (so {@code outbound} is {@code null}), produces an
         * {@link AbortedException} whose URI comes from the client configuration and
         * whose method is {@code "UNKNOWN"}.
         */
        @Test
        void threadInterruptedDuringRetryDelay_afterAuthFailure_throwsAbortedException()
                throws InterruptedException {
            // Auth always fails — the request never reaches the server.
            SecurityProvider alwaysFailing = ctx -> {
                throw new ConnectTimeoutException("Simulated token endpoint timeout", null);
            };

            // Long delay so the thread is reliably asleep when we interrupt it.
            RetryPolicy policy = RetryPolicy.builder()
                    .maxAttempts(4)
                    .on(RetryOn.CONNECT_TIMEOUT)
                    .delay(RetryDelay.fixed(Duration.ofSeconds(10)))
                    .build();
            Client client = buildClient(policy);

            AtomicReference<Throwable> thrown = new AtomicReference<>();

            Thread requestThread = new Thread(() -> {
                try {
                    client.get()
                            .path("/retry/get")
                            .security(alwaysFailing)
                            .send(Person.class);
                } catch (AbortedException e) {
                    thrown.set(e);
                }
            });

            requestThread.start();

            // Allow time for the first auth failure and the retry sleep to begin.
            Thread.sleep(500);

            requestThread.interrupt();
            requestThread.join(5_000);

            AbortedException ex = assertInstanceOf(AbortedException.class, thrown.get(),
                    "Expected AbortedException when thread is interrupted during auth-failure retry sleep.");

            // When outbound is null the engine falls back to configuration.uri() and "UNKNOWN".
            assertEquals(Optional.of(server.uri()), ex.uri());
            assertEquals(Optional.of("UNKNOWN"), ex.method());
        }

        /**
         * Verifies that when auth throws inside {@code retryAsync} and the retry policy
         * does <em>not</em> match the exception, the future completes exceptionally with
         * the original auth failure rather than being retried.
         * <p>
         * Setup: the probe (call 1) succeeds so {@code retryAsync} is entered; the first
         * {@code retryAsync} auth call (call 2) throws; the policy does not cover
         * {@link RetryOn#CONNECT_TIMEOUT} so the future is completed exceptionally.
         */
        @Test
        void asyncAuthFailureInRetryAsync_policyDoesNotMatch_completesExceptionally() {
            failableGet.failCount = 0;   // server would return 200 if reached

            AtomicInteger authCalls = new AtomicInteger();

            // Probe (call 1): succeeds.  First retryAsync attempt (call 2+): fails.
            SecurityProvider flakyAuth = ctx -> {
                if (authCalls.incrementAndGet() > 1) {
                    throw new ConnectTimeoutException("Simulated token endpoint timeout", null);
                }
            };

            // Policy matches SERVICE_UNAVAILABLE only — ConnectTimeoutException is not retried.
            RetryPolicy policy = RetryPolicy.builder()
                    .maxAttempts(4)
                    .on(RetryOn.SERVICE_UNAVAILABLE)
                    .delay(RetryDelay.fixed(Duration.ofMillis(10)))
                    .build();
            Client client = buildClient(policy);

            CompletionException ex = assertThrows(
                    CompletionException.class,
                    () -> client.get()
                            .path("/retry/get")
                            .security(flakyAuth)
                            .sendAsync(Person.class)
                            .join()
            );

            assertInstanceOf(ConnectTimeoutException.class, ex.getCause());
            assertEquals(0, failableGet.callCount());   // auth failed before any HTTP request
        }

        /**
         * Verifies that when auth throws inside {@code retryAsync} and the retry policy
         * <em>does</em> match the exception, the scheduler reschedules the attempt and the
         * request ultimately succeeds once auth recovers.
         * <p>
         * Setup: the probe (call 1) succeeds; the first {@code retryAsync} auth call
         * (call 2) throws; the policy matches so {@code DEFAULT_RETRY_SCHEDULER.schedule()}
         * is invoked; auth call 3 succeeds and the HTTP request completes.
         */
        @Test
        void asyncAuthFailureInRetryAsync_policyMatches_retriedAndSucceeds() {
            failableGet.failCount = 0;   // server always returns 200

            AtomicInteger authCalls = new AtomicInteger();

            // Probe (call 1): succeeds.  Retry attempt 1 (call 2): fails.  Attempt 2 (call 3): succeeds.
            SecurityProvider flakyAuth = ctx -> {
                int call = authCalls.incrementAndGet();

                if (call == 2) {
                    throw new ConnectTimeoutException("Simulated token endpoint timeout", null);
                }
            };

            RetryPolicy policy = RetryPolicy.builder()
                    .maxAttempts(4)
                    .on(RetryOn.CONNECT_TIMEOUT)
                    .delay(RetryDelay.fixed(Duration.ofMillis(10)))
                    .build();
            Client client = buildClient(policy);

            HttpResponse<Person> response = client.get()
                    .path("/retry/get")
                    .security(flakyAuth)
                    .sendAsync(Person.class)
                    .join();

            assertEquals(200, response.statusCode());
            assertEquals(3, authCalls.get());
            assertEquals(1, failableGet.callCount());   // only one HTTP request reached the server
        }
    }
}
