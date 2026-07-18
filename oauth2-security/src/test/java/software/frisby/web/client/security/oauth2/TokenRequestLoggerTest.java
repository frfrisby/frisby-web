package software.frisby.web.client.security.oauth2;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import software.frisby.web.test.log.LogExpectation;
import software.frisby.web.test.log.SystemLogVerifier;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.net.ssl.SSLSession;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for {@link TokenRequestLogger} that cover the {@code else if} logging branches
 * that the integration tests cannot reach.
 *
 * <h2>Why these branches are unreachable in integration tests</h2>
 * <p>
 * The JUL root logger is configured to {@code ALL} in {@code logging.properties}, which makes
 * {@code LOGGER.isLoggable(TRACE)} return {@code true} for every test class that does not
 * explicitly reconfigure the logger.  Because of the
 * {@code if (isLoggable(TRACE)) { … } else if (isLoggable(INFO)) { … }} structure, the
 * {@code INFO}, {@code WARNING}, and {@code ERROR} fallback branches are never reached when
 * TRACE is active.
 * <p>
 * Each test here explicitly restricts the logger to the target level so that the otherwise-shadowed
 * path fires.  {@link SystemLogVerifier} restores the previous level when closed.
 */
class TokenRequestLoggerTest {

    private static final String TOKEN_URI = "http://localhost:9999/oauth/token";
    private static final Duration LATENCY = Duration.ofMillis(42);
    private static final RuntimeException TRANSPORT_CAUSE = new RuntimeException("Connection refused");

    // Standard POST request — no Authorization header
    private static final HttpRequest POST_REQUEST = HttpRequest.newBuilder()
            .uri(URI.create(TOKEN_URI))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.noBody())
            .build();

    // POST request with Authorization header — for authorization header redaction coverage
    private static final HttpRequest POST_REQUEST_WITH_AUTH = HttpRequest.newBuilder()
            .uri(URI.create(TOKEN_URI))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Authorization", "Basic dGVzdDp0ZXN0")
            .POST(HttpRequest.BodyPublishers.noBody())
            .build();

    // -------------------------------------------------------------------------
    // logSuccess
    // -------------------------------------------------------------------------

    @Nested
    class LogSuccess {
        @Test
        void traceEnabled_logsRequestAndResponseHeaders() {
            try (SystemLogVerifier verifier = SystemLogVerifier.builder()
                    .configure(TokenRequestLogger.class, System.Logger.Level.TRACE)
                    .expect(LogExpectation.builder()
                            .logger(TokenRequestLogger.class)
                            .level(System.Logger.Level.TRACE)
                            .predicate(e -> e.message().contains("Request Headers:")
                                    && e.message().contains("Response Headers:")
                                    && e.message().contains("200"))
                            .build())
                    .build()) {

                TokenRequestLogger.logSuccess(POST_REQUEST, fakeResponse(200), LATENCY);

                verifier.assertExpectations();
                assertEquals(1, verifier.traceCount());
                assertEquals(0, verifier.infoCount());
            }
        }

        @Test
        void traceDisabledInfoEnabled_logsStatusOneLiner() {
            try (SystemLogVerifier verifier = SystemLogVerifier.builder()
                    .configure(TokenRequestLogger.class, System.Logger.Level.INFO)
                    .expect(LogExpectation.builder()
                            .logger(TokenRequestLogger.class)
                            .level(System.Logger.Level.INFO)
                            .predicate(e -> e.message().contains("200")
                                    && e.message().contains("42ms"))
                            .build())
                    .build()) {

                TokenRequestLogger.logSuccess(POST_REQUEST, fakeResponse(200), LATENCY);

                verifier.assertExpectations();
                assertEquals(1, verifier.infoCount());
                assertEquals(0, verifier.traceCount());
            }
        }

        @Test
        void loggerOff_logsNothing() {
            try (SystemLogVerifier verifier = SystemLogVerifier.builder()
                    .configure(TokenRequestLogger.class, System.Logger.Level.OFF)
                    .build()) {

                TokenRequestLogger.logSuccess(POST_REQUEST, fakeResponse(200), LATENCY);

                assertEquals(0, verifier.traceCount());
                assertEquals(0, verifier.infoCount());
            }
        }
    }

    // -------------------------------------------------------------------------
    // logTokenError
    // -------------------------------------------------------------------------

    @Nested
    class LogTokenError {
        @Test
        void traceEnabled_logsRequestAndResponseHeaders() {
            try (SystemLogVerifier verifier = SystemLogVerifier.builder()
                    .configure(TokenRequestLogger.class, System.Logger.Level.TRACE)
                    .expect(LogExpectation.builder()
                            .logger(TokenRequestLogger.class)
                            .level(System.Logger.Level.TRACE)
                            .predicate(e -> e.message().contains("Request Headers:")
                                    && e.message().contains("401"))
                            .build())
                    .build()) {

                TokenRequestLogger.logTokenError(POST_REQUEST, fakeResponse(401), LATENCY);

                verifier.assertExpectations();
                assertEquals(1, verifier.traceCount());
                assertEquals(0, verifier.warningCount());
            }
        }

        @Test
        void traceDisabledWarningEnabled_logsStatusOneLiner() {
            try (SystemLogVerifier verifier = SystemLogVerifier.builder()
                    .configure(TokenRequestLogger.class, System.Logger.Level.WARNING)
                    .expect(LogExpectation.builder()
                            .logger(TokenRequestLogger.class)
                            .level(System.Logger.Level.WARNING)
                            .predicate(e -> e.message().contains("401")
                                    && e.message().contains("42ms"))
                            .build())
                    .build()) {

                TokenRequestLogger.logTokenError(POST_REQUEST, fakeResponse(401), LATENCY);

                verifier.assertExpectations();
                assertEquals(1, verifier.warningCount());
                assertEquals(0, verifier.traceCount());
            }
        }

        @Test
        void loggerOff_logsNothing() {
            try (SystemLogVerifier verifier = SystemLogVerifier.builder()
                    .configure(TokenRequestLogger.class, System.Logger.Level.OFF)
                    .build()) {

                TokenRequestLogger.logTokenError(POST_REQUEST, fakeResponse(401), LATENCY);

                assertEquals(0, verifier.traceCount());
                assertEquals(0, verifier.warningCount());
            }
        }
    }

    // -------------------------------------------------------------------------
    // logTransportError
    // -------------------------------------------------------------------------

    @Nested
    class LogTransportError {
        @Test
        void traceEnabled_logsRequestHeadersAndException() {
            try (SystemLogVerifier verifier = SystemLogVerifier.builder()
                    .configure(TokenRequestLogger.class, System.Logger.Level.TRACE)
                    .expect(LogExpectation.builder()
                            .logger(TokenRequestLogger.class)
                            .level(System.Logger.Level.TRACE)
                            .predicate(e -> e.message().contains("Request Headers:")
                                    && e.message().contains("Connection refused"))
                            .build())
                    .build()) {

                TokenRequestLogger.logTransportError(POST_REQUEST, TRANSPORT_CAUSE);

                verifier.assertExpectations();
                assertEquals(1, verifier.traceCount());
                assertEquals(0, verifier.errorCount());
            }
        }

        @Test
        void traceDisabledErrorEnabled_logsExceptionOneLiner() {
            try (SystemLogVerifier verifier = SystemLogVerifier.builder()
                    .configure(TokenRequestLogger.class, System.Logger.Level.ERROR)
                    .expect(LogExpectation.builder()
                            .logger(TokenRequestLogger.class)
                            .level(System.Logger.Level.ERROR)
                            .predicate(e -> e.message().contains("failed:")
                                    && e.message().contains("Connection refused"))
                            .build())
                    .build()) {

                TokenRequestLogger.logTransportError(POST_REQUEST, TRANSPORT_CAUSE);

                verifier.assertExpectations();
                assertEquals(1, verifier.errorCount());
                assertEquals(0, verifier.traceCount());
            }
        }

        @Test
        void loggerOff_logsNothing() {
            try (SystemLogVerifier verifier = SystemLogVerifier.builder()
                    .configure(TokenRequestLogger.class, System.Logger.Level.OFF)
                    .build()) {

                TokenRequestLogger.logTransportError(POST_REQUEST, TRANSPORT_CAUSE);

                assertEquals(0, verifier.traceCount());
                assertEquals(0, verifier.errorCount());
            }
        }
    }

    // -------------------------------------------------------------------------
    // appendRequestSection — Authorization header redaction branch
    // -------------------------------------------------------------------------

    @Nested
    class AuthorizationHeaderRedaction {
        @Test
        void traceEnabled_authorizationHeaderValueIsRedacted() {
            try (SystemLogVerifier verifier = SystemLogVerifier.builder()
                    .configure(TokenRequestLogger.class, System.Logger.Level.TRACE)
                    .expect(LogExpectation.builder()
                            .logger(TokenRequestLogger.class)
                            .level(System.Logger.Level.TRACE)
                            .predicate(e -> e.message().contains("Authorization: [redacted]")
                                    && !e.message().contains("dGVzdDp0ZXN0"))
                            .build())
                    .build()) {

                TokenRequestLogger.logSuccess(POST_REQUEST_WITH_AUTH, fakeResponse(200), LATENCY);

                verifier.assertExpectations();
            }
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static HttpResponse<String> fakeResponse(int statusCode) {
        return new HttpResponse<>() {
            @Override
            public int statusCode() {
                return statusCode;
            }

            @Override
            public HttpHeaders headers() {
                return HttpHeaders.of(
                        Map.of("content-type", List.of("application/json")),
                        (k, v) -> true
                );
            }

            @Override
            public String body() {
                return null;
            }

            @Override
            public HttpRequest request() {
                return POST_REQUEST;
            }

            @Override
            public Optional<HttpResponse<String>> previousResponse() {
                return Optional.empty();
            }

            @Override
            public URI uri() {
                return URI.create(TOKEN_URI);
            }

            @Override
            public HttpClient.Version version() {
                return HttpClient.Version.HTTP_1_1;
            }

            @Override
            public Optional<SSLSession> sslSession() {
                return Optional.empty();
            }
        };
    }
}

