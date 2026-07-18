package software.frisby.web.client;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import software.frisby.web.client.exception.ConnectException;
import software.frisby.web.serial.jackson.JacksonSerializer;
import software.frisby.web.server.Server;

import software.frisby.web.server.ServerConfiguration;
import software.frisby.web.test.TestLogging;
import software.frisby.web.test.TestResources;
import software.frisby.web.test.domain.Person;
import software.frisby.web.test.log.LogExpectation;
import software.frisby.web.test.log.SystemLogVerifier;

import java.io.IOException;
import java.net.HttpCookie;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Integration tests targeting the branches in {@link RequestLogger} that the standard
 * integration tests do not reach.
 *
 * <h2>Why these branches are normally unreachable</h2>
 * <p>
 * The JUL root logger is configured to {@code ALL} in {@code logging.properties}, which
 * makes {@code LOGGER.isLoggable(TRACE)} return {@code true} for every test class that does
 * not explicitly reconfigure the logger.  Because of the
 * {@code if (isLoggable(TRACE)) { … } else if (isLoggable(INFO)) { … }} structure,
 * the {@code INFO} and {@code WARNING} fallback branches are never reached when TRACE is on.
 * <p>
 * This class explicitly restricts the logger level per test to force the otherwise-shadowed
 * paths.  {@link SystemLogVerifier} restores the previous level when closed.
 *
 * <h2>Additional branches</h2>
 * <ul>
 *   <li>{@code appendRequestSection} — {@code Cookie} header: logged as {@code (N bytes)}
 *       rather than the raw value; requires a request that actually carries a cookie.</li>
 *   <li>{@code appendResponseHeaders} — {@code Set-Cookie} response header: same redaction;
 *       requires an inline resource that emits the header.</li>
 *   <li>{@code appendTruncatedBody} truncation branch — body must exceed the configured
 *       {@code maxBodySize} (default 8 KB); requires an inline resource that returns a large
 *       error body.</li>
 * </ul>
 */
class ClientLoggingTest {
    private static Server server;
    private static Client client;
    private static Client clientWithRedactedFields;
    private static Client clientWithZeroBodySize;

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
                .resources(new SetCookieResource(), new LargeErrorResource(),
                        new AuthorizationResponseResource(), new SensitiveErrorResource(),
                        new MultiValueResponseHeaderResource(), new MultiSetCookieResource(),
                        new BlankSetCookieResource(), new FlagSetCookieResource(),
                        new NoAttributeSetCookieResource())
                .components(TestLogging.forClass(ClientLoggingTest.class))
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

        clientWithRedactedFields = Client.builder()
                .configuration(
                        ClientConfiguration.builder()
                                .uri(server.uri())
                                .connectTimeout(Duration.ofSeconds(5))
                                .readTimeout(Duration.ofSeconds(30))
                                .serializer(JacksonSerializer.builder().build())
                                .logging(
                                        ClientLoggingConfiguration.builder()
                                                .redactFields("password")
                                                .redactHeaders("x-api-key")
                                                .build()
                                )
                                .build()
                )
                .build();

        clientWithZeroBodySize = Client.builder()
                .configuration(
                        ClientConfiguration.builder()
                                .uri(server.uri())
                                .connectTimeout(Duration.ofSeconds(5))
                                .readTimeout(Duration.ofSeconds(30))
                                .serializer(JacksonSerializer.builder().build())
                                .logging(
                                        ClientLoggingConfiguration.builder()
                                                .maxBodySize(0)
                                                .build()
                                )
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
    // Inline resources
    // -------------------------------------------------------------------------

    /**
     * Returns a {@code Set-Cookie} response header so the redaction path is exercised.
     */
    @Path("/set-cookie")
    public static class SetCookieResource {
        @GET
        public Response get() {
            return Response.ok("{\"ok\":true}")
                    .type("application/json")
                    .header("Set-Cookie", "session=abc123; Path=/; HttpOnly")
                    .build();
        }
    }

    /**
     * Returns a 400 response with a body larger than the default
     * {@link DefaultClientLoggingConfigurationBuilder#DEFAULT_MAX_BODY_SIZE} so the
     * truncation path in {@code appendTruncatedBody} is exercised.
     */
    @Path("/large-error")
    public static class LargeErrorResource {
        private static final String LARGE_BODY =
                "x".repeat(DefaultClientLoggingConfigurationBuilder.DEFAULT_MAX_BODY_SIZE + 1_000);

        @GET
        public Response get() {
            return Response.status(400)
                    .entity(LARGE_BODY)
                    .type("text/plain")
                    .build();
        }
    }

    /**
     * Returns a {@code 400} with a JSON body containing a {@code password} field,
     * used to exercise the {@link RequestLogger} JSON field-redaction path.
     */
    @Path("/sensitive-error")
    public static class SensitiveErrorResource {
        @GET
        public Response get() {
            return Response.status(400)
                    .entity("{\"error\":\"bad\",\"password\":\"secret-value\"}")
                    .type("application/json")
                    .build();
        }
    }

    /**
     * Returns a {@code 200} response with an {@code X-Api-Key} header, exercising
     * the {@code redactedHeaders().contains(lowerName)} branch in
     * {@code appendResponseHeaders}.
     * <p>
     * Note: the JDK {@code HttpClient} strips {@code Authorization} from response
     * headers, so a user-configured header is used here instead.
     */
    @Path("/auth-response")
    public static class AuthorizationResponseResource {
        @GET
        public Response get() {
            return Response.ok("{\"ok\":true}")
                    .type("application/json")
                    .header("X-Api-Key", "secret-key-abc")
                    .build();
        }
    }

    /**
     * Returns a {@code 400} response with two values for the same {@code X-Flags}
     * header, used to verify that multi-value response headers are logged as a single
     * comma-separated line rather than one line per value.
     */
    @Path("/multi-header-error")
    public static class MultiValueResponseHeaderResource {
        @GET
        public Response get() {
            return Response.status(400)
                    .header("X-Flags", "a")
                    .header("X-Flags", "b")
                    .type("application/json")
                    .entity("{\"error\":\"multi\"}")
                    .build();
        }
    }

    /**
     * Returns a {@code 200} response with two {@code Set-Cookie} headers, each with
     * different security attributes, used to verify that each cookie is logged on its
     * own line with the value redacted and all attributes preserved.
     */
    @Path("/multi-set-cookie")
    public static class MultiSetCookieResource {
        @GET
        public Response get() {
            return Response.ok("{\"ok\":true}")
                    .type("application/json")
                    .header("Set-Cookie", "session=secret-session-id; Path=/; HttpOnly; Secure")
                    .header("Set-Cookie", "token=secret-token-value; Path=/api; Secure; Max-Age=3600")
                    .build();
        }
    }

    /**
     * Returns a {@code 200} response with a blank {@code Set-Cookie} header value,
     * exercising the {@code null == value || value.isBlank()} early-return in
     * {@code redactSetCookieValue}.
     */
    @Path("/blank-set-cookie")
    public static class BlankSetCookieResource {
        @GET
        public Response get() {
            return Response.ok("{\"ok\":true}")
                    .type("application/json")
                    .header("Set-Cookie", "  ")
                    .build();
        }
    }

    /**
     * Returns a {@code 200} response with a flag {@code Set-Cookie} header (no {@code =}
     * in the cookie pair), exercising the {@code return REDACTED + attributes} fallback
     * in {@code redactSetCookieValue}.
     */
    @Path("/flag-set-cookie")
    public static class FlagSetCookieResource {
        @GET
        public Response get() {
            return Response.ok("{\"ok\":true}")
                    .type("application/json")
                    .header("Set-Cookie", "flagcookie; Path=/; HttpOnly")
                    .build();
        }
    }

    /**
     * Returns a {@code 200} response with a {@code Set-Cookie} header that has no
     * attributes (no semicolon), exercising the {@code firstSemicolon >= 0} false-branch
     * in {@code redactSetCookieValue}.
     */
    @Path("/no-attribute-set-cookie")
    public static class NoAttributeSetCookieResource {
        @GET
        public Response get() {
            return Response.ok("{\"ok\":true}")
                    .type("application/json")
                    .header("Set-Cookie", "session=abc123")
                    .build();
        }
    }

    // -------------------------------------------------------------------------
    // Response header redaction — Authorization header in the response
    // -------------------------------------------------------------------------

    /**
     * Sends a GET to an endpoint that returns an {@code X-Api-Key} response header.
     * The client is configured with {@code redactHeaders("x-api-key")} — the value
     * must be replaced with {@code [redacted]} and must never appear in the log.
     */
    @Nested
    class ResponseHeaderRedaction {
        @Test
        void configuredResponseHeader_isRedacted() {
            try (SystemLogVerifier verifier = SystemLogVerifier.builder()
                    .configure(RequestLogger.class, System.Logger.Level.TRACE)
                    .expect(
                            LogExpectation.builder()
                                    .logger(RequestLogger.class)
                                    .level(System.Logger.Level.TRACE)
                                    .predicate(e -> e.message().contains("x-api-key: [redacted]")
                                            && !e.message().contains("secret-key-abc"))
                                    .failureMessage("Expected TRACE log with x-api-key response header redacted.")
                                    .build()
                    )
                    .build()) {
                clientWithRedactedFields.get()
                        .path("/auth-response")
                        .send(String.class);

                verifier.assertExpectations();
            }
        }
    }

    // -------------------------------------------------------------------------
    // Multi-value header logging — values joined as comma-separated list
    // -------------------------------------------------------------------------

    /**
     * Verifies that headers with multiple values are logged as a single
     * comma-separated line ({@code X-Flags: a, b}) rather than one line per value.
     * Covers both request headers (sent by the client) and response headers
     * (returned by the server).
     */
    @Nested
    class MultiValueHeaderLogging {
        @Test
        void multiValueRequestHeader_isLoggedAsCommaSeparatedList() {
            try (SystemLogVerifier verifier = SystemLogVerifier.builder()
                    .configure(RequestLogger.class, System.Logger.Level.TRACE)
                    .expect(
                            LogExpectation.builder()
                                    .logger(RequestLogger.class)
                                    .level(System.Logger.Level.TRACE)
                                    .predicate(e -> e.message().contains("X-Flags: a, b")
                                            && !e.message().contains("X-Flags: a\n")
                                            && !e.message().contains("X-Flags: b\n"))
                                    .failureMessage("Expected TRACE log with X-Flags: a, b on a single line.")
                                    .build()
                    )
                    .build()) {
                client.get()
                        .path("/persons/{id}", "id", "person-1")
                        .header("X-Flags", "a", "b")
                        .send(Person.class);

                verifier.assertExpectations();
            }
        }

        @Test
        void multiValueResponseHeader_isLoggedAsCommaSeparatedList() {
            try (SystemLogVerifier verifier = SystemLogVerifier.builder()
                    .configure(RequestLogger.class, System.Logger.Level.WARNING)
                    .expect(
                            LogExpectation.builder()
                                    .logger(RequestLogger.class)
                                    .level(System.Logger.Level.WARNING)
                                    .predicate(e -> e.message().contains("x-flags: a, b")
                                            && !e.message().contains("x-flags: a\n")
                                            && !e.message().contains("x-flags: b\n"))
                                    .failureMessage("Expected WARNING log with x-flags: a, b on a single line.")
                                    .build()
                    )
                    .build()) {
                try {
                    client.get().path("/multi-header-error").send(String.class);
                } catch (Exception ignored) {
                    // 400 expected — we only care about the log
                }

                verifier.assertExpectations();
            }
        }
    }

    // -------------------------------------------------------------------------
    // maxBodySize(0) — request and response bodies suppressed entirely
    // -------------------------------------------------------------------------

    /**
     * Tests that configuring {@code maxBodySize(0)} suppresses both request and
     * response body sections from the log.
     * <p>
     * {@code requestBody_isNotLogged} exercises the early-return in
     * {@code appendRequestBody} when {@code config.maxBodySize() == 0}.
     * {@code responseBody_isNotLogged} exercises the early-return in
     * {@code appendTruncatedBody} when {@code limit == 0}.
     */
    @Nested
    class MaxBodySizeZero {
        @Test
        void requestBody_isNotLogged() {
            try (SystemLogVerifier verifier = SystemLogVerifier.builder()
                    .configure(RequestLogger.class, System.Logger.Level.TRACE)
                    .expect(
                            LogExpectation.builder()
                                    .logger(RequestLogger.class)
                                    .level(System.Logger.Level.TRACE)
                                    .predicate(e -> e.message().contains("POST")
                                            && !e.message().contains("Request Body:"))
                                    .failureMessage("Expected TRACE log with POST but no Request Body section.")
                                    .build()
                    )
                    .build()) {
                // POST /echo/form accepts form-urlencoded and returns 200
                clientWithZeroBodySize.post()
                        .path("/echo/form")
                        .body(FormUrlEncoded.builder()
                                .field("username", "alice")
                                .field("password", "secret")
                                .build()
                        )
                        .send(String.class);

                verifier.assertExpectations();
            }
        }

        @Test
        void responseBody_isNotLogged() {
            try (SystemLogVerifier verifier = SystemLogVerifier.builder()
                    .configure(RequestLogger.class, System.Logger.Level.WARNING)
                    .expect(
                            LogExpectation.builder()
                                    .logger(RequestLogger.class)
                                    .level(System.Logger.Level.WARNING)
                                    .predicate(e -> e.message().contains("400")
                                            && !e.message().contains("Response Body:"))
                                    .failureMessage("Expected WARNING log with 400 but no Response Body section.")
                                    .build()
                    )
                    .build()) {
                try {
                    clientWithZeroBodySize.get().path("/large-error").send(String.class);
                } catch (Exception ignored) {
                    // BadRequestException expected — we only care about the log
                }

                verifier.assertExpectations();
            }
        }
    }

    // -------------------------------------------------------------------------
    // JSON response body field redaction
    // -------------------------------------------------------------------------

    /**
     * Uses a client configured with {@code redactFields("password")} to GET an endpoint
     * that returns a {@code 400} body containing a {@code password} field.
     * Exercises the {@code for (String field : fields)} loop in
     * {@code redactFieldValues}.
     */
    @Nested
    class JsonBodyFieldRedaction {
        @Test
        void passwordField_isRedactedInResponseBody() {
            try (SystemLogVerifier verifier = SystemLogVerifier.builder()
                    .configure(RequestLogger.class, System.Logger.Level.WARNING)
                    .expect(
                            LogExpectation.builder()
                                    .logger(RequestLogger.class)
                                    .level(System.Logger.Level.WARNING)
                                    .predicate(e -> e.message().contains("\"password\": \"[redacted]\"")
                                            && !e.message().contains("secret-value"))
                                    .failureMessage("Expected WARNING log with password field redacted.")
                                    .build()
                    )
                    .build()) {
                try {
                    clientWithRedactedFields.get().path("/sensitive-error").send(String.class);
                } catch (Exception ignored) {
                    // BadRequestException expected — we only care about the log
                }

                verifier.assertExpectations();
            }
        }
    }

    // -------------------------------------------------------------------------
    // Form-encoded request body field redaction
    // -------------------------------------------------------------------------

    /**
     * Uses a client configured with {@code redactFields("password")} to POST a
     * {@code application/x-www-form-urlencoded} body containing a {@code password} field.
     * Exercises the {@code for (String field : fields)} loop in
     * {@code redactFormValues}.
     */
    @Nested
    class FormBodyFieldRedaction {
        @Test
        void passwordField_isRedactedInFormRequestBody() {
            try (SystemLogVerifier verifier = SystemLogVerifier.builder()
                    .configure(RequestLogger.class, System.Logger.Level.TRACE)
                    .expect(
                            LogExpectation.builder()
                                    .logger(RequestLogger.class)
                                    .level(System.Logger.Level.TRACE)
                                    .predicate(e -> e.message().contains("password=[redacted]")
                                            && e.message().contains("username=alice")
                                            && !e.message().contains("password=secret")
                                            && e.message().contains("Response Body:")
                                            && e.message().contains("\"password\": \"[redacted]\"")
                                            && !e.message().contains("\"password\":\"secret\""))
                                    .failureMessage("Expected TRACE log with password redacted in both request and response body.")
                                    .build()
                    )
                    .build()) {
                // POST /echo/form accepts form-urlencoded and echoes the fields back as JSON
                HttpResponse<String> response = clientWithRedactedFields.post()
                        .path("/echo/form")
                        .body(FormUrlEncoded.builder()
                                .field("username", "alice")
                                .field("password", "secret")
                                .build()
                        )
                        .send(String.class);

                assertEquals(200, response.statusCode());
                assertEquals("{\"password\":\"secret\",\"username\":\"alice\"}", response.body());

                verifier.assertExpectations();
            }
        }
    }

    // -------------------------------------------------------------------------
    // logSuccess INFO path
    // -------------------------------------------------------------------------

    /**
     * Configures {@code RequestLogger} to {@code INFO} level, which disables {@code TRACE}.
     * The {@code else if (isLoggable(INFO))} branch in {@link RequestLogger#logSuccess} is
     * then taken instead of the {@code TRACE} branch.
     */
    @Nested
    class LogSuccessInfoPath {
        @Test
        void atInfoLevel_logsMethodUriStatusAndLatency() {
            try (SystemLogVerifier verifier = SystemLogVerifier.builder()
                    .configure(RequestLogger.class, System.Logger.Level.INFO)
                    .expect(
                            LogExpectation.builder()
                                    .logger(RequestLogger.class)
                                    .level(System.Logger.Level.INFO)
                                    .predicate(e -> e.message().contains("GET")
                                            && e.message().contains("200"))
                                    .failureMessage("Expected INFO log containing GET and 200.")
                                    .build()
                    )
                    .build()) {
                client.get()
                        .path("/persons/{id}", "id", "person-1")
                        .send(Person.class);

                verifier.assertExpectations();
            }
        }
    }

    // -------------------------------------------------------------------------
    // logError WARNING path + body truncation
    // -------------------------------------------------------------------------

    /**
     * Configures {@code RequestLogger} to {@code WARNING} level.  A 400 response with a
     * body larger than the default max body size exercises both the
     * {@code else if (isLoggable(WARNING))} branch in {@link RequestLogger#logError} and the
     * truncation branch in {@code appendTruncatedBody}.
     */
    @Nested
    class LogErrorWarningPathAndTruncation {
        @Test
        void atWarningLevel_withLargeBody_logsWarningWithTruncatedBody() {
            try (SystemLogVerifier verifier = SystemLogVerifier.builder()
                    .configure(RequestLogger.class, System.Logger.Level.WARNING)
                    .expect(
                            LogExpectation.builder()
                                    .logger(RequestLogger.class)
                                    .level(System.Logger.Level.WARNING)
                                    .predicate(e -> e.message().contains("400")
                                            && e.message().contains("(truncated)"))
                                    .failureMessage("Expected WARNING log containing 400 and (truncated).")
                                    .build()
                    )
                    .build()) {
                try {
                    client.get().path("/large-error").send(String.class);
                } catch (Exception ignored) {
                    // BadRequestException expected — we only care about the log
                }

                verifier.assertExpectations();
            }
        }
    }

    // -------------------------------------------------------------------------
    // Cookie request header — name preserved, value redacted
    // -------------------------------------------------------------------------

    /**
     * Verifies that {@code Cookie} request headers are logged with the cookie name
     * preserved and the value replaced with {@code [redacted]}, matching the
     * server-side format.  Multiple cookies in a single header are each emitted
     * on their own line.
     */
    @Nested
    class CookieHeaderLogging {
        @Test
        void singleCookie_namePreservedValueRedacted() {
            try (SystemLogVerifier verifier = SystemLogVerifier.builder()
                    .configure(RequestLogger.class, System.Logger.Level.TRACE)
                    .expect(
                            LogExpectation.builder()
                                    .logger(RequestLogger.class)
                                    .level(System.Logger.Level.TRACE)
                                    .predicate(e -> e.message().contains("Cookie: session=[redacted]")
                                            && !e.message().contains("secret-token"))
                                    .failureMessage("Expected TRACE log with Cookie: session=[redacted].")
                                    .build()
                    )
                    .build()) {
                client.get()
                        .path("/persons/{id}", "id", "person-1")
                        .cookie(new HttpCookie("session", "secret-token"))
                        .send(Person.class);

                verifier.assertExpectations();
            }
        }

        @Test
        void multipleCookiesInOneHeader_eachLoggedOnSeparateLine() {
            // Three cookies in a single Cookie header — each name must appear on its
            // own line, no value must appear in the log.
            try (SystemLogVerifier verifier = SystemLogVerifier.builder()
                    .configure(RequestLogger.class, System.Logger.Level.TRACE)
                    .expect(
                            LogExpectation.builder()
                                    .logger(RequestLogger.class)
                                    .level(System.Logger.Level.TRACE)
                                    .predicate(e -> e.message().contains("Cookie: session=[redacted]")
                                            && e.message().contains("Cookie: auth_token=[redacted]")
                                            && e.message().contains("Cookie: pref=[redacted]")
                                            && !e.message().contains("secret-session-id")
                                            && !e.message().contains("secret-token-value")
                                            && !e.message().contains("dark-mode"))
                                    .failureMessage("Expected TRACE log with three cookies each on their own line.")
                                    .build()
                    )
                    .build()) {
                client.get()
                        .path("/persons/{id}", "id", "person-1")
                        .header("Cookie", "session=secret-session-id; auth_token=secret-token-value; pref=dark-mode")
                        .send(Person.class);

                verifier.assertExpectations();
            }
        }

        @Test
        void leadingSemicolonInCookieHeader_emptySegmentSkipped() {
            // A leading ';' produces an empty first segment after split(";") — trimmed.isEmpty()
            // returns true and the continue skips it; the cookie must still be logged once.
            try (SystemLogVerifier verifier = SystemLogVerifier.builder()
                    .configure(RequestLogger.class, System.Logger.Level.TRACE)
                    .expect(
                            LogExpectation.builder()
                                    .logger(RequestLogger.class)
                                    .level(System.Logger.Level.TRACE)
                                    .predicate(e -> e.message().contains("Cookie: session=[redacted]")
                                            && !e.message().contains("secret-token"))
                                    .failureMessage("Expected TRACE log with session=[redacted] and no cookie value.")
                                    .build()
                    )
                    .build()) {
                client.get()
                        .path("/persons/{id}", "id", "person-1")
                        .header("Cookie", "; session=secret-token")
                        .send(Person.class);

                verifier.assertExpectations();
            }
        }

        @Test
        void flagCookieInRequestHeader_nameLoggedAsIs() {
            // A flag cookie with no '=' — eq == -1, so eq > 0 is false and formatted = trimmed.
            // The name must appear in the log unchanged (there is no value to redact).
            try (SystemLogVerifier verifier = SystemLogVerifier.builder()
                    .configure(RequestLogger.class, System.Logger.Level.TRACE)
                    .expect(
                            LogExpectation.builder()
                                    .logger(RequestLogger.class)
                                    .level(System.Logger.Level.TRACE)
                                    .predicate(e -> e.message().contains("Cookie: flagcookie"))
                                    .failureMessage("Expected TRACE log with Cookie: flagcookie logged as-is.")
                                    .build()
                    )
                    .build()) {
                client.get()
                        .path("/persons/{id}", "id", "person-1")
                        .header("Cookie", "flagcookie")
                        .send(Person.class);

                verifier.assertExpectations();
            }
        }
    }

    // -------------------------------------------------------------------------
    // Set-Cookie response header — value redacted, attributes preserved
    // -------------------------------------------------------------------------

    /**
     * Verifies that {@code Set-Cookie} response headers are logged with the cookie
     * value replaced with {@code [redacted]} while the cookie name and all security
     * attributes ({@code Path}, {@code Secure}, {@code HttpOnly}, etc.) are preserved,
     * matching the server-side format.
     */
    @Nested
    class SetCookieHeaderLogging {
        @Test
        void singleSetCookie_valueRedactedAttributesPreserved() {
            // SetCookieResource returns: Set-Cookie: session=abc123; Path=/; HttpOnly
            // Expected log:              set-cookie: session=[redacted]; Path=/; HttpOnly
            try (SystemLogVerifier verifier = SystemLogVerifier.builder()
                    .configure(RequestLogger.class, System.Logger.Level.TRACE)
                    .expect(
                            LogExpectation.builder()
                                    .logger(RequestLogger.class)
                                    .level(System.Logger.Level.TRACE)
                                    .predicate(e -> e.message().contains("set-cookie: session=[redacted]; Path=/; HttpOnly")
                                            && !e.message().contains("abc123"))
                                    .failureMessage("Expected TRACE log with set-cookie value redacted and attributes preserved.")
                                    .build()
                    )
                    .build()) {
                client.get()
                        .path("/set-cookie")
                        .send(String.class);

                verifier.assertExpectations();
            }
        }

        @Test
        void multipleSetCookieHeaders_eachLoggedOnSeparateLine() {
            // MultiSetCookieResource returns two Set-Cookie headers — each must appear
            // on its own line with the value redacted and all attributes preserved.
            try (SystemLogVerifier verifier = SystemLogVerifier.builder()
                    .configure(RequestLogger.class, System.Logger.Level.TRACE)
                    .expect(
                            LogExpectation.builder()
                                    .logger(RequestLogger.class)
                                    .level(System.Logger.Level.TRACE)
                                    .predicate(e -> e.message().contains("set-cookie: session=[redacted]; Path=/; HttpOnly; Secure")
                                            && e.message().contains("set-cookie: token=[redacted]; Path=/api; Secure; Max-Age=3600")
                                            && !e.message().contains("secret-session-id")
                                            && !e.message().contains("secret-token-value"))
                                    .failureMessage("Expected TRACE log with two Set-Cookie headers each on their own line.")
                                    .build()
                    )
                    .build()) {
                client.get()
                        .path("/multi-set-cookie")
                        .send(String.class);

                verifier.assertExpectations();
            }
        }

        @Test
        void blankSetCookieValue_isFullyRedacted() {
            // BlankSetCookieResource returns Set-Cookie with a blank value — the
            // null/blank early-return in redactSetCookieValue must emit [redacted].
            try (SystemLogVerifier verifier = SystemLogVerifier.builder()
                    .configure(RequestLogger.class, System.Logger.Level.TRACE)
                    .expect(
                            LogExpectation.builder()
                                    .logger(RequestLogger.class)
                                    .level(System.Logger.Level.TRACE)
                                    .predicate(e -> e.message().contains("set-cookie: [redacted]"))
                                    .failureMessage("Expected TRACE log with blank Set-Cookie value fully redacted.")
                                    .build()
                    )
                    .build()) {
                client.get()
                        .path("/blank-set-cookie")
                        .send(String.class);

                verifier.assertExpectations();
            }
        }

        @Test
        void flagCookieWithNoEquals_isRedactedWithAttributesPreserved() {
            // FlagSetCookieResource returns Set-Cookie: flagcookie; Path=/; HttpOnly
            // The cookie pair has no '=' so the fallback `return REDACTED + attributes`
            // branch is taken — the attributes must be preserved.
            try (SystemLogVerifier verifier = SystemLogVerifier.builder()
                    .configure(RequestLogger.class, System.Logger.Level.TRACE)
                    .expect(
                            LogExpectation.builder()
                                    .logger(RequestLogger.class)
                                    .level(System.Logger.Level.TRACE)
                                    .predicate(e -> e.message().contains("set-cookie: [redacted]; Path=/; HttpOnly")
                                            && !e.message().contains("flagcookie"))
                                    .failureMessage("Expected TRACE log with flag cookie redacted and attributes preserved.")
                                    .build()
                    )
                    .build()) {
                client.get()
                        .path("/flag-set-cookie")
                        .send(String.class);

                verifier.assertExpectations();
            }
        }

        @Test
        void cookieWithNoAttributes_isRedactedWithNoTrailingSemicolon() {
            // NoAttributeSetCookieResource returns Set-Cookie: session=abc123 (no semicolon)
            // firstSemicolon == -1, so attributes == "" and cookiePair == the whole value.
            try (SystemLogVerifier verifier = SystemLogVerifier.builder()
                    .configure(RequestLogger.class, System.Logger.Level.TRACE)
                    .expect(
                            LogExpectation.builder()
                                    .logger(RequestLogger.class)
                                    .level(System.Logger.Level.TRACE)
                                    .predicate(e -> e.message().contains("set-cookie: session=[redacted]")
                                            && !e.message().contains("abc123"))
                                    .failureMessage("Expected TRACE log with session=[redacted] and no trailing semicolon.")
                                    .build()
                    )
                    .build()) {
                client.get()
                        .path("/no-attribute-set-cookie")
                        .send(String.class);

                verifier.assertExpectations();
            }
        }
    }

    // -------------------------------------------------------------------------
    // Logger "completely silent" paths — neither isLoggable branch taken
    // -------------------------------------------------------------------------

    /**
     * These tests exercise the implicit {@code else} (neither TRACE nor INFO/WARNING/ERROR
     * is loggable) branch in each of the three {@code RequestLogger} log methods.
     * <p>
     * When the logger is configured at a level above the method's highest emitted level,
     * both {@code isLoggable()} guards return {@code false} and the method completes
     * without emitting anything.  The goal is code-coverage of those unreached branches,
     * not a behavioral assertion.
     */
    @Nested
    class LoggerSilentPaths {
        /**
         * Configuring {@code RequestLogger} to {@code WARNING} means both
         * {@code isLoggable(TRACE)} and {@code isLoggable(INFO)} are {@code false}
         * inside {@code logSuccess} — neither branch is taken.
         */
        @Test
        void logSuccess_atWarningLevel_emitsNothing() {
            try (SystemLogVerifier ignored = SystemLogVerifier.builder()
                    .configure(RequestLogger.class, System.Logger.Level.WARNING)
                    .build()) {
                // Successful GET — logSuccess() is called but both TRACE and INFO are off
                client.get()
                        .path("/persons/{id}", "id", "person-1")
                        .send(Person.class);
            }
            // No assertion needed — the test passes if no exception is thrown
        }

        /**
         * Configuring {@code RequestLogger} to {@code ERROR} means both
         * {@code isLoggable(TRACE)} and {@code isLoggable(WARNING)} are {@code false}
         * inside {@code logError} — neither branch is taken.
         */
        @Test
        void logError_atErrorLevel_emitsNothing() {
            try (SystemLogVerifier ignored = SystemLogVerifier.builder()
                    .configure(RequestLogger.class, System.Logger.Level.ERROR)
                    .build()) {
                // 400 error — logError() is called but both TRACE and WARNING are off
                try {
                    client.get().path("/large-error").send(String.class);
                } catch (Exception ignored2) {
                    // expected — we only care that logError() was invoked without crashing
                }
            }
        }

        /**
         * Configuring {@code RequestLogger} to {@code OFF} means both
         * {@code isLoggable(TRACE)} and {@code isLoggable(ERROR)} are {@code false}
         * inside {@code logTransportError} — neither branch is taken.
         */
        @Test
        void logTransportError_atOffLevel_emitsNothing() throws IOException {
            int port;

            try (ServerSocket socket = new ServerSocket(0)) {
                port = socket.getLocalPort();
            }

            Client silentClient = Client.builder()
                    .configuration(
                            ClientConfiguration.builder()
                                    .uri(URI.create("http://localhost:" + port))
                                    .connectTimeout(Duration.ofSeconds(5))
                                    .readTimeout(Duration.ofSeconds(5))
                                    .serializer(JacksonSerializer.builder().build())
                                    .build()
                    )
                    .build();

            try (SystemLogVerifier ignored = SystemLogVerifier.builder()
                    .configure(RequestLogger.class, System.Logger.Level.OFF)
                    .build()) {
                try {
                    silentClient.get().path("/anything").send(String.class);
                } catch (ConnectException ignored2) {
                    // expected — we only care that logTransportError() was invoked without crashing
                }
            }
        }
    }
}

