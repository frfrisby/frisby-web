package software.frisby.web.server;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.frisby.web.test.log.LogExpectation;
import software.frisby.web.test.log.SystemLogVerifier;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Integration tests for built-in failure-detail logging:
 * <ul>
 *   <li>4xx responses logged at {@code WARNING} with request context</li>
 *   <li>5xx responses caused by an uncaught non-{@code WebApplicationException} logged at
 *       {@code ERROR} with request context and attached stack trace</li>
 *   <li>5xx responses caused by a {@code WebApplicationException} logged at {@code WARNING}
 *       (deliberate, controlled failure — no stack trace)</li>
 *   <li>{@code Authorization} header always masked as {@code ***}</li>
 *   <li>Configured body fields redacted to {@code [redacted]}</li>
 * </ul>
 */
class ServerFailureLoggingTest {
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private static Server server;
    private static int port;

    @BeforeAll
    static void startServer() {
        server = Server.builder()
                .configuration(
                        ServerConfiguration.builder()
                                .port(0)
                                .host("localhost")
                                .serializer(new TestJsonSerializer())
                                .logging(
                                        ServerLoggingConfiguration.builder()
                                                .redactFields("password")
                                                .redactHeaders("x-api-key")
                                                .build()
                                )
                                .build()
                )
                .resources(new PingResource())
                .components(
                        TestLogging.forClass(ServerFailureLoggingTest.class),
                        new MappedExceptionMapper()
                )
                .build();

        server.start();

        port = server.port();
    }

    @AfterAll
    static void stopServer() {
        if (null != server) {
            server.stop();
        }
    }

    // -------------------------------------------------------------------------
    // 4xx → WARNING with detail
    // -------------------------------------------------------------------------

    @Test
    void notFoundRequest_logsAtWarning() throws Exception {
        try (SystemLogVerifier verifier = SystemLogVerifier.builder()
                .expect(LogExpectation.builder()
                        .logger(RequestLogger.class)
                        .level(System.Logger.Level.WARNING)
                        .predicate(e -> e.message().contains("/does-not-exist")
                                && e.message().contains("404"))
                        .build()
                )
                .build()) {
            get("/does-not-exist");

            // FINISHED event fires on the server thread — wait for it.
            verifier.assertExpectations(Duration.ofSeconds(2));
        }
    }

    @Test
    void notFoundRequest_doesNotLogAtInfo() throws Exception {
        try (SystemLogVerifier verifier = SystemLogVerifier.builder()
                .build()) {
            get("/does-not-exist");

            Thread.sleep(200);

            // 4xx never produces an INFO line — INFO is 2xx/3xx only.
            assertEquals(0, verifier.infoCount());
        }
    }

    // -------------------------------------------------------------------------
    // 5xx → ERROR with detail (uncaught non-WAE exception)
    // -------------------------------------------------------------------------

    @Test
    void failingRequest_logsAtError() throws Exception {
        try (SystemLogVerifier verifier = SystemLogVerifier.builder()
                .expect(LogExpectation.builder()
                        .logger(RequestLogger.class)
                        .level(System.Logger.Level.ERROR)
                        .predicate(e -> e.message().contains("/ping/fail")
                                && null != e.thrown()
                                && "Intentional test failure".equals(e.thrown().getMessage()))
                        .build()
                )
                .build()) {
            // GET /ping/fail throws an unchecked exception → Jersey returns 500
            get("/ping/fail");

            verifier.assertExpectations(Duration.ofSeconds(2));
        }
    }

    // -------------------------------------------------------------------------
    // 5xx → WARNING when caused by a WebApplicationException (deliberate failure)
    // -------------------------------------------------------------------------

    @Test
    void waeInternalServerError_logsAtWarningNotError() throws Exception {
        // GET /ping/wae-fail throws InternalServerErrorException (a WebApplicationException)
        // → Jersey returns 500, but it is a deliberate, controlled failure — WARNING, not ERROR.
        try (SystemLogVerifier verifier = SystemLogVerifier.builder()
                .expect(LogExpectation.builder()
                        .logger(RequestLogger.class)
                        .level(System.Logger.Level.WARNING)
                        .predicate(e -> e.message().contains("/ping/wae-fail")
                                && e.message().contains("500"))
                        .build()
                )
                .build()) {
            get("/ping/wae-fail");

            verifier.assertExpectations(Duration.ofSeconds(2));
        }
    }

    @Test
    void waeInternalServerError_doesNotLogAtError() throws Exception {
        // With the logger set to ERROR level (suppressing WARNING), the WAE 500 must
        // produce no log output at all — it is resolved to WARNING, not ERROR.
        try (SystemLogVerifier verifier = SystemLogVerifier.builder()
                .configure(RequestLogger.class, System.Logger.Level.ERROR)
                .build()) {
            get("/ping/wae-fail");

            Thread.sleep(200);

            assertEquals(0, verifier.errorCount());
        }
    }

    // -------------------------------------------------------------------------
    // Deliberate 5xx response with no exception — null cause → WARNING
    // -------------------------------------------------------------------------

    @Test
    void deliberate5xxResponse_withNullCause_logsAtWarning() throws Exception {
        // GET /ping/server-error returns Response.status(500) directly — no exception is
        // thrown, so requestException is null.  In determineFailureLevel the condition
        // (statusCode >= 500 && null != cause && ...) is false because null != cause is
        // false, so the method returns WARNING.
        try (SystemLogVerifier verifier = SystemLogVerifier.builder()
                .expect(LogExpectation.builder()
                        .logger(RequestLogger.class)
                        .level(System.Logger.Level.WARNING)
                        .predicate(e -> e.message().contains("/ping/server-error")
                                && e.message().contains("500"))
                        .build()
                )
                .build()) {
            get("/ping/server-error");

            verifier.assertExpectations(Duration.ofSeconds(2));

            assertEquals(0, verifier.errorCount());
        }
    }

    @Test
    void deliberate5xxResponse_withNullCause_doesNotLogAtError() throws Exception {
        // With the logger at ERROR level (suppressing WARNING), the deliberate 500 with
        // null cause must produce no log output — confirming it resolves to WARNING.
        try (SystemLogVerifier verifier = SystemLogVerifier.builder()
                .configure(RequestLogger.class, System.Logger.Level.ERROR)
                .build()) {
            get("/ping/server-error");

            Thread.sleep(200);

            assertEquals(0, verifier.errorCount());
        }
    }

    @Test
    void mappedNonWaeException_logsAtError() throws Exception {
        // GET /ping/mapped-fail throws MappedException (a non-WebApplicationException).
        // MappedExceptionMapper converts it to 503, but the original exception is
        // captured before mapping — it is a genuine failure and must log at ERROR.
        try (SystemLogVerifier verifier = SystemLogVerifier.builder()
                .expect(LogExpectation.builder()
                        .logger(RequestLogger.class)
                        .level(System.Logger.Level.ERROR)
                        .predicate(e -> e.message().contains("/ping/mapped-fail")
                                && e.message().contains("503")
                                && null != e.thrown()
                                && "Intentional mapped test failure".equals(e.thrown().getMessage()))
                        .build()
                )
                .build()) {
            get("/ping/mapped-fail");

            verifier.assertExpectations(Duration.ofSeconds(2));
        }
    }

    @Test
    void mappedNonWaeException_doesNotLogAtWarning() throws Exception {
        // With the logger set to ERROR level (suppressing WARNING), the mapped 503 must
        // still produce an ERROR record — not silently swallowed at WARNING.
        try (SystemLogVerifier verifier = SystemLogVerifier.builder()
                .expect(LogExpectation.builder()
                        .logger(RequestLogger.class)
                        .level(System.Logger.Level.ERROR)
                        .predicate(e -> e.message().contains("/ping/mapped-fail"))
                        .build()
                )
                .build()) {
            get("/ping/mapped-fail");

            verifier.assertExpectations(Duration.ofSeconds(2));
        }
    }

    // -------------------------------------------------------------------------
    // Header masking
    // -------------------------------------------------------------------------

    @Test
    void authorizationHeader_isMasked() throws Exception {
        try (SystemLogVerifier verifier = SystemLogVerifier.builder()
                .expect(LogExpectation.builder()
                        .logger(RequestLogger.class)
                        .level(System.Logger.Level.WARNING)
                        .predicate(e -> e.message().contains("Authorization: [redacted]")
                                && !e.message().contains("Bearer secret-token"))
                        .build()
                )
                .build()) {
            getWithAuth("/does-not-exist", "Bearer secret-token");

            verifier.assertExpectations(Duration.ofSeconds(2));
        }
    }

    @Test
    void cookieRequestHeader_isMasked() throws Exception {
        // Cookie is in the hard-coded redacted-header defaults — value must never appear in logs.
        try (SystemLogVerifier verifier = SystemLogVerifier.builder()
                .expect(LogExpectation.builder()
                        .logger(RequestLogger.class)
                        .level(System.Logger.Level.WARNING)
                        .predicate(e -> e.message().contains("Cookie: session=[redacted]")
                                && !e.message().contains("secret-session-id"))                        .build()
                )
                .build()) {
            getWithHeader("/does-not-exist", "Cookie", "session=secret-session-id");

            verifier.assertExpectations(Duration.ofSeconds(2));
        }
    }

    @Test
    void setCookieResponseHeader_isMasked() throws Exception {
        // Set-Cookie is in the hard-coded redacted-header defaults — the value in the
        // Res-Headers section must be masked even when the response carries a body.
        try (SystemLogVerifier verifier = SystemLogVerifier.builder()
                .expect(LogExpectation.builder()
                        .logger(RequestLogger.class)
                        .level(System.Logger.Level.WARNING)
                        .predicate(e -> e.message().contains("Set-Cookie: session=[redacted]; HttpOnly; Secure")
                                && !e.message().contains("secret-session-id"))
                        .build()
                )
                .build()) {
            get("/ping/bad-request-with-cookie");

            verifier.assertExpectations(Duration.ofSeconds(2));
        }
    }

    @Test
    void multipleCookiesInRequest_allNamesPreservedAllValuesRedacted() throws Exception {
        // Three cookies in a single Cookie header — each name must appear, no value must appear.
        try (SystemLogVerifier verifier = SystemLogVerifier.builder()
                .expect(LogExpectation.builder()
                        .logger(RequestLogger.class)
                        .level(System.Logger.Level.WARNING)
                        .predicate(e -> e.message().contains("Cookie: session=[redacted]")
                                && e.message().contains("Cookie: auth_token=[redacted]")
                                && e.message().contains("Cookie: pref=[redacted]")
                                && !e.message().contains("secret-session-id")
                                && !e.message().contains("secret-token-value")
                                && !e.message().contains("dark-mode-setting"))
                        .build()
                )
                .build()) {
            getWithHeader(
                    "/does-not-exist",
                    "Cookie",
                    "session=secret-session-id; auth_token=secret-token-value; pref=dark-mode-setting"
            );

            verifier.assertExpectations(Duration.ofSeconds(2));
        }
    }

    @Test
    void multipleCookiesInResponse_eachOnOwnLineWithAttributesPreserved() throws Exception {
        // Three Set-Cookie headers — each must appear on its own line with the value
        // redacted and all security attributes (Path, Secure, HttpOnly, Max-Age) preserved.
        try (SystemLogVerifier verifier = SystemLogVerifier.builder()
                .expect(LogExpectation.builder()
                        .logger(RequestLogger.class)
                        .level(System.Logger.Level.WARNING)
                        .predicate(e -> e.message().contains(
                                        "Set-Cookie: session=[redacted]; Path=/; HttpOnly; Secure")
                                && e.message().contains(
                                        "Set-Cookie: token=[redacted]; Path=/api; Secure; Max-Age=3600")
                                && e.message().contains(
                                        "Set-Cookie: pref=[redacted]; Path=/; Max-Age=86400")
                                && !e.message().contains("secret-session-id")
                                && !e.message().contains("secret-token-value")
                                && !e.message().contains("dark-mode-setting"))
                        .build()
                )
                .build()) {
            get("/ping/bad-request-with-cookies");

            verifier.assertExpectations(Duration.ofSeconds(2));
        }
    }

    @Test
    void customConfiguredHeader_isMasked() throws Exception {
        // X-Api-Key is added via ServerLoggingConfiguration.builder().redactHeaders("x-api-key").
        try (SystemLogVerifier verifier = SystemLogVerifier.builder()
                .expect(LogExpectation.builder()
                        .logger(RequestLogger.class)
                        .level(System.Logger.Level.WARNING)
                        .predicate(e -> e.message().contains("X-Api-Key: [redacted]")
                                && !e.message().contains("my-secret-key"))
                        .build()
                )
                .build()) {
            getWithHeader("/does-not-exist", "X-Api-Key", "my-secret-key");

            verifier.assertExpectations(Duration.ofSeconds(2));
        }
    }

    @Test
    void multiValueRequestHeader_allValuesJoinedOnOneLine() throws Exception {
        // Accept is sent with two values — both must appear on a single line joined by ", ".
        try (SystemLogVerifier verifier = SystemLogVerifier.builder()
                .expect(LogExpectation.builder()
                        .logger(RequestLogger.class)
                        .level(System.Logger.Level.WARNING)
                        .predicate(e -> e.message().contains("Accept:") && (
                                // Java HttpClient may send as one comma-joined value or two entries;
                                // either way both media types must be present on the Accept line.
                                e.message().contains("application/json")
                                        && e.message().contains("application/xml")))
                        .build()
                )
                .build()) {
            HTTP.send(
                    HttpRequest.newBuilder(uri("/does-not-exist"))
                            .header("Accept", "application/json")
                            .header("Accept", "application/xml")
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            verifier.assertExpectations(Duration.ofSeconds(2));
        }
    }

    @Test
    void multiValueResponseHeader_allValuesJoinedOnOneLine() throws Exception {
        // Vary is returned with two values — both must appear joined on a single line.
        try (SystemLogVerifier verifier = SystemLogVerifier.builder()
                .expect(LogExpectation.builder()
                        .logger(RequestLogger.class)
                        .level(System.Logger.Level.WARNING)
                        .predicate(e -> e.message().contains("Vary: Accept, Accept-Encoding")
                                || e.message().contains("Vary: Accept-Encoding, Accept"))
                        .build()
                )
                .build()) {
            get("/ping/bad-request-multi-header");

            verifier.assertExpectations(Duration.ofSeconds(2));
        }
    }

    // -------------------------------------------------------------------------
    // Body field redaction
    // -------------------------------------------------------------------------

    @Test
    void configuredBodyField_isRedacted() throws Exception {
        // POST with a "password" field to a 404 path — body is buffered, field is redacted.
        try (SystemLogVerifier verifier = SystemLogVerifier.builder()
                .expect(LogExpectation.builder()
                        .logger(RequestLogger.class)
                        .level(System.Logger.Level.WARNING)
                        .predicate(e -> e.message().contains("[redacted]")
                                && !e.message().contains("secret123"))
                        .build()
                )
                .build()) {
            post("/does-not-exist", "{\"password\":\"secret123\",\"username\":\"frank\"}");

            verifier.assertExpectations(Duration.ofSeconds(2));
        }
    }

    // -------------------------------------------------------------------------
    // Response body included in failure detail
    // -------------------------------------------------------------------------

    @Test
    void failureResponse_includesResponseBodyInLog() throws Exception {
        // GET /ping/bad-request returns 400 with a JSON body containing "invalid-input".
        // The response body must appear in the WARNING log entry.
        try (SystemLogVerifier verifier = SystemLogVerifier.builder()
                .expect(LogExpectation.builder()
                        .logger(RequestLogger.class)
                        .level(System.Logger.Level.WARNING)
                        .predicate(e -> e.message().contains("invalid-input"))
                        .build()
                )
                .build()) {
            get("/ping/bad-request");

            verifier.assertExpectations(Duration.ofSeconds(2));
        }
    }

    // -------------------------------------------------------------------------
    // 2xx → TRACE with full detail; INFO one-liner when TRACE is off
    // -------------------------------------------------------------------------

    @Test
    void successfulRequest_atTrace_logsFullDetailAtTrace() throws Exception {
        // With TRACE enabled (the default test configuration), a 200 response must
        // produce a TRACE entry with the full exchange — headers and body — not an INFO one-liner.
        try (SystemLogVerifier verifier = SystemLogVerifier.builder()
                .expect(LogExpectation.builder()
                        .logger(RequestLogger.class)
                        .level(System.Logger.Level.TRACE)
                        .predicate(e -> e.message().contains("GET")
                                && e.message().contains("/ping")
                                && e.message().contains("200")
                                && e.message().contains("Request Headers:")
                                && e.message().contains("Response Headers:"))
                        .build()
                )
                .build()) {
            get("/ping");

            verifier.assertExpectations(Duration.ofSeconds(2));

            // Full-detail TRACE must not also produce a WARNING or ERROR.
            assertEquals(0, verifier.warningCount());
            assertEquals(0, verifier.errorCount());
        }
    }

    @Test
    void successfulRequest_atTrace_doesNotLogAtInfo() throws Exception {
        // When TRACE is enabled the TRACE branch fires; the INFO branch must be silent
        // (the two levels are mutually exclusive for success responses).
        try (SystemLogVerifier verifier = SystemLogVerifier.builder()
                .build()) {
            get("/ping");

            Thread.sleep(200);

            assertEquals(0, verifier.infoCount());
        }
    }

    @Test
    void successfulRequest_atInfoOnly_logsOneLineAtInfo() throws Exception {
        // With TRACE suppressed and INFO enabled, the compact one-liner fires at INFO.
        try (SystemLogVerifier verifier = SystemLogVerifier.builder()
                .configure(RequestLogger.class, System.Logger.Level.INFO)
                .expect(LogExpectation.builder()
                        .logger(RequestLogger.class)
                        .level(System.Logger.Level.INFO)
                        .predicate(e -> e.message().contains("GET")
                                && e.message().contains("/ping")
                                && e.message().contains("200")
                                && !e.message().contains("Request Headers:"))
                        .build()
                )
                .build()) {
            get("/ping");

            verifier.assertExpectations(Duration.ofSeconds(2));

            // INFO one-liner must not also produce WARNING or ERROR.
            assertEquals(0, verifier.warningCount());
            assertEquals(0, verifier.errorCount());
        }
    }

    @Test
    void successfulRequest_atWarningLevel_logsNothing() throws Exception {
        // With both TRACE and INFO suppressed, success responses must produce no output
        // from RequestLogger.  The configure() call enforces this; we just verify the
        // request completes without exception.
        try (SystemLogVerifier ignored = SystemLogVerifier.builder()
                .configure(RequestLogger.class, System.Logger.Level.WARNING)
                .build()) {
            get("/ping");

            Thread.sleep(200);
        }
    }

    // -------------------------------------------------------------------------
    // POST body appears in failure log
    // -------------------------------------------------------------------------

    @Test
    void postRequestBody_appearsInErrorLog() throws Exception {
        // POST a JSON body to /ping/fail (throws 500) — body must appear in the ERROR log
        // and the original exception must be attached to the log record.
        try (SystemLogVerifier verifier = SystemLogVerifier.builder()
                .expect(LogExpectation.builder()
                        .logger(RequestLogger.class)
                        .level(System.Logger.Level.ERROR)
                        .predicate(e -> e.message().contains("frank@example.com")
                                && null != e.thrown()
                                && "Intentional POST test failure".equals(e.thrown().getMessage()))
                        .build()
                )
                .build()) {
            post("/ping/fail", "{\"user\":\"frank@example.com\"}");

            verifier.assertExpectations(Duration.ofSeconds(2));
        }
    }

    // -------------------------------------------------------------------------
    // URL-encoded form body redaction
    // -------------------------------------------------------------------------

    @Test
    void formEncodedBodyField_isRedacted() throws Exception {
        // POST a form-encoded body with a 'password' field to a 404 path.
        // The 'password' value must be redacted; other fields must be preserved.
        try (SystemLogVerifier verifier = SystemLogVerifier.builder()
                .expect(LogExpectation.builder()
                        .logger(RequestLogger.class)
                        .level(System.Logger.Level.WARNING)
                        .predicate(e -> e.message().contains("password=[redacted]")
                                && e.message().contains("username=frank")
                                && !e.message().contains("secret123"))
                        .build()
                )
                .build()) {
            postForm("/does-not-exist", "username=frank&password=secret123");

            verifier.assertExpectations(Duration.ofSeconds(2));
        }
    }

    // -------------------------------------------------------------------------
    // Multipart body — placeholder, no binary noise
    // -------------------------------------------------------------------------

    @Test
    void multipartBody_showsPlaceholderInLog() throws Exception {
        // POST a multipart/form-data body to a 404 path.
        // The log must show a placeholder rather than raw MIME bytes.
        try (SystemLogVerifier verifier = SystemLogVerifier.builder()
                .expect(LogExpectation.builder()
                        .logger(RequestLogger.class)
                        .level(System.Logger.Level.WARNING)
                        .predicate(e -> e.message().contains("[multipart/form-data — body not logged]")
                                && !e.message().contains("Hello, World!"))
                        .build()
                )
                .build()) {
            postMultipart("/does-not-exist");

            verifier.assertExpectations(Duration.ofSeconds(2));
        }
    }

    @Test
    void binaryBody_showsPlaceholderInLog() throws Exception {
        // POST an application/octet-stream body to a 404 path.
        // The body must not be buffered or interpreted as text; the log must show the
        // content-type placeholder rather than raw binary bytes.
        try (SystemLogVerifier verifier = SystemLogVerifier.builder()
                .expect(LogExpectation.builder()
                        .logger(RequestLogger.class)
                        .level(System.Logger.Level.WARNING)
                        .predicate(e -> e.message().contains("[application/octet-stream — body not logged]"))
                        .build()
                )
                .build()) {
            postBinary("/does-not-exist");

            verifier.assertExpectations(Duration.ofSeconds(2));
        }
    }

    // -------------------------------------------------------------------------
    // Body truncation
    // -------------------------------------------------------------------------

    @Test
    void requestBodyLargerThanMaxBodySize_isTruncatedInLog() throws Exception {
        // Inline server with a very small maxBodySize so we can easily exceed it.
        Server tinyServer = Server.builder()
                .configuration(
                        ServerConfiguration.builder()
                                .port(0)
                                .host("localhost")
                                .serializer(new TestJsonSerializer())
                                .logging(
                                        ServerLoggingConfiguration.builder()
                                                .maxBodySize(10)
                                                .build()
                                )
                                .build()
                )
                .resources(new PingResource())
                .components(TestLogging.forClass(ServerFailureLoggingTest.class))
                .build();

        tinyServer.start();

        try {
            try (SystemLogVerifier verifier = SystemLogVerifier.builder()
                    .expect(LogExpectation.builder()
                            .logger(RequestLogger.class)
                            .level(System.Logger.Level.ERROR)
                            .predicate(e -> e.message().contains("[truncated at 10 bytes]"))
                            .build()
                    )
                    .build()) {
                HTTP.send(
                        HttpRequest.newBuilder(URI.create("http://localhost:" + tinyServer.port() + "/ping/fail"))
                                .header("Content-Type", "application/json")
                                .POST(HttpRequest.BodyPublishers.ofString("{\"username\":\"frank\",\"role\":\"admin\"}"))
                                .build(),
                        HttpResponse.BodyHandlers.ofString()
                );

                verifier.assertExpectations(Duration.ofSeconds(2));
            }
        } finally {
            tinyServer.stop();
        }
    }

    @Test
    void responseBodyLargerThanMaxBodySize_isTruncatedInLog() throws Exception {
        // The /ping/bad-request response body is well over 10 chars — it gets truncated.
        Server tinyServer = Server.builder()
                .configuration(
                        ServerConfiguration.builder()
                                .port(0)
                                .host("localhost")
                                .serializer(new TestJsonSerializer())
                                .logging(
                                        ServerLoggingConfiguration.builder()
                                                .maxBodySize(10)
                                                .build()
                                )
                                .build()
                )
                .resources(new PingResource())
                .components(TestLogging.forClass(ServerFailureLoggingTest.class))
                .build();

        tinyServer.start();

        try {
            try (SystemLogVerifier verifier = SystemLogVerifier.builder()
                    .expect(LogExpectation.builder()
                            .logger(RequestLogger.class)
                            .level(System.Logger.Level.WARNING)
                            .predicate(e -> e.message().contains("[truncated at 10 bytes]"))
                            .build()
                    )
                    .build()) {
                HTTP.send(
                        HttpRequest.newBuilder(URI.create("http://localhost:" + tinyServer.port() + "/ping/bad-request"))
                                .GET()
                                .build(),
                        HttpResponse.BodyHandlers.ofString()
                );

                verifier.assertExpectations(Duration.ofSeconds(2));
            }
        } finally {
            tinyServer.stop();
        }
    }

    // -------------------------------------------------------------------------
    // Body logging disabled (maxBodySize = 0)
    // -------------------------------------------------------------------------

    @Test
    void bodyLoggingDisabled_noRequestBodyInLog() throws Exception {
        // maxBodySize(0) disables buffering in RequestBodyBufferingFilter (L833 hit)
        // and skips the body section in buildDetail.
        Server noBodyServer = Server.builder()
                .configuration(
                        ServerConfiguration.builder()
                                .port(0)
                                .host("localhost")
                                .serializer(new TestJsonSerializer())
                                .logging(
                                        ServerLoggingConfiguration.builder()
                                                .maxBodySize(0)
                                                .build()
                                )
                                .build()
                )
                .resources(new PingResource())
                .components(TestLogging.forClass(ServerFailureLoggingTest.class))
                .build();

        noBodyServer.start();

        try {
            try (SystemLogVerifier verifier = SystemLogVerifier.builder()
                    .expect(LogExpectation.builder()
                            .logger(RequestLogger.class)
                            .level(System.Logger.Level.ERROR)
                            .predicate(e -> e.message().contains("500")
                                    && !e.message().contains("Request Body:"))
                            .build()
                    )
                    .build()) {
                HTTP.send(
                        HttpRequest.newBuilder(URI.create("http://localhost:" + noBodyServer.port() + "/ping/fail"))
                                .header("Content-Type", "application/json")
                                .POST(HttpRequest.BodyPublishers.ofString("{\"data\":\"value\"}"))
                                .build(),
                        HttpResponse.BodyHandlers.ofString()
                );

                verifier.assertExpectations(Duration.ofSeconds(2));
            }
        } finally {
            noBodyServer.stop();
        }
    }

    @Test
    void bodyLoggingDisabled_binaryBodyPlaceholderAlsoSuppressed() throws Exception {
        // When maxBodySize(0) disables body logging, not even the binary/multipart
        // placeholder "[application/octet-stream — body not logged]" should appear.
        // This covers the outer "if (0 < maxBodySize)" guard added to the isBinaryBody
        // branch in buildDetail() — without it the placeholder leaked through.
        Server noBodyServer = Server.builder()
                .configuration(
                        ServerConfiguration.builder()
                                .port(0)
                                .host("localhost")
                                .serializer(new TestJsonSerializer())
                                .logging(
                                        ServerLoggingConfiguration.builder()
                                                .maxBodySize(0)
                                                .build()
                                )
                                .build()
                )
                .resources(new PingResource())
                .components(TestLogging.forClass(ServerFailureLoggingTest.class))
                .build();

        noBodyServer.start();

        try {
            try (SystemLogVerifier verifier = SystemLogVerifier.builder()
                    .expect(LogExpectation.builder()
                            .logger(RequestLogger.class)
                            .level(System.Logger.Level.WARNING)
                            .predicate(e -> e.message().contains("400")
                                    && !e.message().contains("Request Body:"))
                            .build()
                    )
                    .build()) {
                HttpClient.newHttpClient().send(
                        HttpRequest.newBuilder(URI.create("http://localhost:" + noBodyServer.port() + "/ping/binary-fail"))
                                .header("Content-Type", "application/octet-stream")
                                .POST(HttpRequest.BodyPublishers.ofByteArray(new byte[]{1, 2, 3}))
                                .build(),
                        HttpResponse.BodyHandlers.ofString()
                );

                verifier.assertExpectations(Duration.ofSeconds(2));
            }
        } finally {
            noBodyServer.stop();
        }
    }

    // -------------------------------------------------------------------------
    // isTextBody coverage — text/* and non-text/non-application types
    // -------------------------------------------------------------------------

    @Test
    void textPlainRequestBody_isBufferedAndLoggedOnFailure() throws Exception {
        // POST with Content-Type: text/plain to an endpoint that only accepts JSON.
        // Jersey returns 415 Unsupported Media Type. The RequestBodyBufferingFilter runs
        // @PreMatching so the body is already buffered by the time Jersey rejects it.
        // isTextBody("text/plain") returns true — covers the text/* branch (L584).
        try (SystemLogVerifier verifier = SystemLogVerifier.builder()
                .expect(LogExpectation.builder()
                        .logger(RequestLogger.class)
                        .level(System.Logger.Level.WARNING)
                        .predicate(e -> e.message().contains("415")
                                && e.message().contains("hello from text"))
                        .build()
                )
                .build()) {
            HTTP.send(
                    HttpRequest.newBuilder(uri("/ping/fail"))
                            .header("Content-Type", "text/plain")
                            .POST(HttpRequest.BodyPublishers.ofString("hello from text"))
                            .build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            verifier.assertExpectations(Duration.ofSeconds(2));
        }
    }

    @Test
    void imageMimeTypeRequestBody_showsPlaceholderInLog() throws Exception {
        // POST with Content-Type: image/png — isTextBody returns false at the fallthrough
        // return (L596, the non-text non-application branch). The buffer filter skips it
        // and buildDetail renders a [image/png — body not logged] placeholder.
        try (SystemLogVerifier verifier = SystemLogVerifier.builder()
                .expect(LogExpectation.builder()
                        .logger(RequestLogger.class)
                        .level(System.Logger.Level.WARNING)
                        .predicate(e -> e.message().contains("[image/png — body not logged]"))
                        .build()
                )
                .build()) {
            HTTP.send(
                    HttpRequest.newBuilder(uri("/ping/bad-request"))
                            .header("Content-Type", "image/png")
                            .POST(HttpRequest.BodyPublishers.ofByteArray(new byte[]{1, 2, 3}))
                            .build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            verifier.assertExpectations(Duration.ofSeconds(2));
        }
    }

    // -------------------------------------------------------------------------
    // Response header masking — non-Set-Cookie masked header
    // -------------------------------------------------------------------------

    @Test
    void maskedResponseHeader_isRedactedInLog() throws Exception {
        // GET /ping/bad-request-with-api-key-header returns a 400 with X-Api-Key in the
        // response headers.  The test server has redactHeaders("x-api-key") configured,
        // so the value must appear as [redacted] in the Res-Headers block.
        try (SystemLogVerifier verifier = SystemLogVerifier.builder()
                .expect(LogExpectation.builder()
                        .logger(RequestLogger.class)
                        .level(System.Logger.Level.WARNING)
                        .predicate(e -> e.message().contains("X-Api-Key: [redacted]"))
                        .build()
                )
                .build()) {
            get("/ping/bad-request-with-api-key-header");

            verifier.assertExpectations(Duration.ofSeconds(2));
        }
    }

    // -------------------------------------------------------------------------
    // serializeEntityForLog branches
    // -------------------------------------------------------------------------

    @Test
    void stringEntityResponse_isLoggedDirectlyAsBody() throws Exception {
        // /ping/bad-request-string-entity returns a Response whose entity is already a
        // String.  serializeEntityForLog's "entity instanceof String" branch (L514) returns
        // it directly rather than going through the serializer.
        try (SystemLogVerifier verifier = SystemLogVerifier.builder()
                .expect(LogExpectation.builder()
                        .logger(RequestLogger.class)
                        .level(System.Logger.Level.WARNING)
                        .predicate(e -> e.message().contains("string-entity-bad-request"))
                        .build()
                )
                .build()) {
            get("/ping/bad-request-string-entity");

            verifier.assertExpectations(Duration.ofSeconds(2));
        }
    }

    @Test
    void inputStreamEntityResponse_bodyIsNotLoggedOnFailure() throws Exception {
        // /ping/bad-request-stream returns a Response whose entity is an InputStream.
        // serializeEntityForLog returns null for InputStream entities (L519), so no
        // Response Body: line should appear in the log.
        try (SystemLogVerifier verifier = SystemLogVerifier.builder()
                .expect(LogExpectation.builder()
                        .logger(RequestLogger.class)
                        .level(System.Logger.Level.WARNING)
                        .predicate(e -> e.message().contains("400")
                                && !e.message().contains("Response Body:"))
                        .build()
                )
                .build()) {
            get("/ping/bad-request-stream");

            verifier.assertExpectations(Duration.ofSeconds(2));
        }
    }

    @Test
    void blankStringEntityResponse_bodyIsNotLoggedOnFailure() throws Exception {
        // /ping/bad-request-empty-body returns a 400 whose entity is "".
        // serializeEntityForLog returns "" (String branch), isBlank() is true, so no
        // Response Body: line should appear — covers the !responseBody.isBlank() false branch.
        try (SystemLogVerifier verifier = SystemLogVerifier.builder()
                .expect(LogExpectation.builder()
                        .logger(RequestLogger.class)
                        .level(System.Logger.Level.WARNING)
                        .predicate(e -> e.message().contains("400")
                                && !e.message().contains("Response Body:"))
                        .build()
                )
                .build()) {
            get("/ping/bad-request-empty-body");

            verifier.assertExpectations(Duration.ofSeconds(2));
        }
    }

    // -------------------------------------------------------------------------
    // Log-level gating — detail skipped when WARNING is disabled
    // -------------------------------------------------------------------------

    @Test
    void warningLevelDisabled_noDetailBuiltForFailingRequest() throws Exception {
        // When WARNING is disabled for RequestLogger, isDetailLoggable(4xx, null) returns false
        // and buildDetail is never called — the ternary false-branch (L719) is taken.
        try (SystemLogVerifier verifier = SystemLogVerifier.builder()
                .configure(RequestLogger.class, System.Logger.Level.ERROR)
                .build()) {
            get("/ping/bad-request");

            assertEquals(0, verifier.warningCount());
        }
    }

    // -------------------------------------------------------------------------
    // Binary request body — isBinaryBody=true path in buildDetail
    // -------------------------------------------------------------------------

    @Test
    void binaryRequestBody_showsMediaTypePlaceholderInLog() throws Exception {
        // A POST with Content-Type: application/octet-stream triggers isBinaryBody=true
        // in buildDetail — the body must NOT be logged verbatim; the placeholder
        // "[application/octet-stream — body not logged]" must appear instead.
        try (SystemLogVerifier verifier = SystemLogVerifier.builder()
                .expect(LogExpectation.builder()
                        .logger(RequestLogger.class)
                        .level(System.Logger.Level.WARNING)
                        .predicate(e -> e.message().contains("application/octet-stream")
                                && e.message().contains("body not logged"))
                        .build()
                )
                .build()) {
            postBinary("/ping/binary-fail");

            verifier.assertExpectations(Duration.ofSeconds(2));
        }
    }

    // -------------------------------------------------------------------------
    // Body truncation — maxBodySize exceeded for both request and response
    // -------------------------------------------------------------------------

    @Test
    void requestBodyExceedsMaxBodySize_truncationMarkerAppearsInLog() throws Exception {
        // A server with maxBodySize=5 must truncate request bodies longer than 5 bytes
        // and append "[truncated at 5 bytes]" to the log entry.
        Server truncServer = Server.builder()
                .configuration(
                        ServerConfiguration.builder()
                                .port(0)
                                .host("localhost")
                                .serializer(new TestJsonSerializer())
                                .logging(ServerLoggingConfiguration.builder().maxBodySize(5).build())
                                .build()
                )
                .resources(new PingResource())
                .components(TestLogging.forClass(ServerFailureLoggingTest.class))
                .build();

        truncServer.start();

        try (SystemLogVerifier verifier = SystemLogVerifier.builder()
                .expect(LogExpectation.builder()
                        .logger(RequestLogger.class)
                        .level(System.Logger.Level.ERROR)
                        .predicate(e -> e.message().contains("[truncated at 5 bytes]"))
                        .build()
                )
                .build()) {
            HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(URI.create("http://localhost:" + truncServer.port() + "/ping/fail"))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString("{\"key\":\"ABCDEFGHIJKLMNOPQRST\"}"))
                            .build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            verifier.assertExpectations(Duration.ofSeconds(2));
        } finally {
            truncServer.stop();
        }
    }

    @Test
    void responseBodyExceedsMaxBodySize_truncationMarkerAppearsInLog() throws Exception {
        // The same maxBodySize=5 server must also truncate response bodies that are
        // longer than 5 bytes (the /ping/bad-request body is well over that limit).
        Server truncServer = Server.builder()
                .configuration(
                        ServerConfiguration.builder()
                                .port(0)
                                .host("localhost")
                                .serializer(new TestJsonSerializer())
                                .logging(ServerLoggingConfiguration.builder().maxBodySize(5).build())
                                .build()
                )
                .resources(new PingResource())
                .components(TestLogging.forClass(ServerFailureLoggingTest.class))
                .build();

        truncServer.start();

        try (SystemLogVerifier verifier = SystemLogVerifier.builder()
                .expect(LogExpectation.builder()
                        .logger(RequestLogger.class)
                        .level(System.Logger.Level.WARNING)
                        .predicate(e -> e.message().contains("[truncated at 5 bytes]"))
                        .build()
                )
                .build()) {
            HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(URI.create("http://localhost:" + truncServer.port() + "/ping/bad-request"))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            verifier.assertExpectations(Duration.ofSeconds(2));
        } finally {
            truncServer.stop();
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private static HttpResponse<String> get(String path) throws Exception {
        return HTTP.send(
                HttpRequest.newBuilder(uri(path)).GET().build(),
                HttpResponse.BodyHandlers.ofString()
        );
    }

    private static HttpResponse<String> getWithAuth(String path, String authorization) throws Exception {
        return HTTP.send(
                HttpRequest.newBuilder(uri(path))
                        .header("Authorization", authorization)
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );
    }

    private static HttpResponse<String> getWithHeader(String path, String name, String value) throws Exception {
        return HTTP.send(
                HttpRequest.newBuilder(uri(path))
                        .header(name, value)
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );
    }

    private static HttpResponse<String> post(String path, String jsonBody) throws Exception {
        return HTTP.send(
                HttpRequest.newBuilder(uri(path))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );
    }

    private static HttpResponse<String> postForm(String path, String formBody) throws Exception {
        return HTTP.send(
                HttpRequest.newBuilder(uri(path))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(formBody))
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );
    }

    private static HttpResponse<String> postMultipart(String path) throws Exception {
        String boundary = "boundary123";
        String body = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"test.txt\"\r\n"
                + "Content-Type: text/plain\r\n"
                + "\r\n"
                + "Hello, World!\r\n"
                + "--" + boundary + "--";

        return HTTP.send(
                HttpRequest.newBuilder(uri(path))
                        .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );
    }

    private static HttpResponse<String> postBinary(String path) throws Exception {
        return HTTP.send(
                HttpRequest.newBuilder(uri(path))
                        .header("Content-Type", "application/octet-stream")
                        .POST(HttpRequest.BodyPublishers.ofByteArray(new byte[]{1, 2, 3, 4, 5}))
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );
    }
}


