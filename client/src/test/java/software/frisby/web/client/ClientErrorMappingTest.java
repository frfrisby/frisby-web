package software.frisby.web.client;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import software.frisby.web.client.exception.*;
import software.frisby.web.serial.jackson.JacksonSerializer;
import software.frisby.web.server.Server;
import software.frisby.web.server.ServerConfiguration;
import software.frisby.web.test.TestLogging;
import software.frisby.web.test.TestResources;
import software.frisby.web.test.domain.Person;

import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase C integration tests — HTTP error-status mapping and redirect behavior.
 * <p>
 * Uses {@code StatusResource} ({@code GET /status/{code}}) which returns any requested
 * HTTP status code with a small JSON body, allowing every entry in
 * {@link ExceptionFactory} to be exercised in a real round-trip.
 */
class ClientErrorMappingTest {
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
                .resources(new EmptyErrorResource())
                .resources(new VoidErrorResource())
                .components(
                        new MultiPartFeature(),
                        TestLogging.forClass(ClientErrorMappingTest.class)
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

    /**
     * Asserts all key fields of an {@link HttpResponseException} for a {@code GET /status/{code}}
     * round-trip against {@code StatusResource}, expecting a body to be present.
     * <p>
     * Validates: {@code statusCode()}, {@code status()}, {@code method()}, {@code uri()},
     * {@code headers()} (Content-Type), and {@code body()} ({@code {"status":<code>}}).
     */
    private static void assertCommonFields(HttpResponseException ex,
                                           int expectedCode,
                                           ResponseStatus expectedStatus) {
        assertCommonFields(ex, expectedCode, expectedStatus, true);
    }

    // -------------------------------------------------------------------------
    // Known 4xx status codes
    // -------------------------------------------------------------------------

    /**
     * Asserts all key fields of an {@link HttpResponseException} for a {@code GET /status/{code}}
     * round-trip against {@code StatusResource}.
     * <p>
     * When {@code expectBody} is {@code true}, asserts that {@code Content-Type: application/json}
     * is present and the body is {@code {"status":<code>}}.
     * <p>
     * When {@code expectBody} is {@code false}, asserts that both {@code Content-Type} and the
     * body are absent — confirming that {@code SecurityResponseFilter} has stripped the entity
     * for security-sensitive status codes ({@code 401}, {@code 403}, {@code 500}).
     */
    private static void assertCommonFields(HttpResponseException ex,
                                           int expectedCode,
                                           ResponseStatus expectedStatus,
                                           boolean expectBody) {
        assertEquals(expectedCode, ex.statusCode());
        assertEquals(expectedStatus, ex.status());
        assertEquals("GET", ex.method().orElse(null));

        assertTrue(ex.uri().isPresent());
        assertEquals("/status/" + expectedCode, ex.uri().get().getPath());

        if (expectBody) {
            assertTrue(
                    ex.headers().firstValue("Content-Type").orElse("").contains("application/json"),
                    "Expected Content-Type: application/json in response headers"
            );

            assertTrue(ex.body().isPresent());
            assertEquals("{\"status\":" + expectedCode + "}", ex.body().get());
        } else {
            assertTrue(
                    ex.headers().firstValue("Content-Type").isEmpty(),
                    "Expected Content-Type to be absent for security-stripped response"
            );

            assertTrue(
                    ex.body().isEmpty(),
                    "Expected body to be absent for security-stripped response"
            );
        }
    }

    // -------------------------------------------------------------------------
    // Known 5xx status codes
    // -------------------------------------------------------------------------

    /**
     * Returns a 404 with no response entity — used to exercise the {@code body.isBlank()}
     * branch in {@code GetRequest.downloadBodyHandler} (and {@code voidBodyHandler}).
     */
    @Path("/empty-error")
    public static class EmptyErrorResource {
        @GET
        public Response get() {
            return Response.status(404).build();
        }
    }

    /**
     * Returns a 400 with a non-blank plain-text body — used to exercise the
     * {@code body.isBlank() = false} branch in {@code RequestState.voidBodyHandler}.
     */
    @Path("/void-error")
    public static class VoidErrorResource {
        @jakarta.ws.rs.DELETE
        public Response delete() {
            return Response.status(400).entity("bad request").type("text/plain").build();
        }
    }

    // -------------------------------------------------------------------------
    // Unknown / catch-all status codes
    // -------------------------------------------------------------------------

    @Nested
    class KnownClientErrors {
        @Test
        void status400_throwsBadRequestException() {
            BadRequestException ex = assertThrows(
                    BadRequestException.class,
                    () -> client.get().path("/status/400").send(Person.class)
            );

            assertCommonFields(ex, 400, ResponseStatus.BAD_REQUEST);
        }

        @Test
        void status401_throwsUnauthorizedException() {
            UnauthorizedException ex = assertThrows(
                    UnauthorizedException.class,
                    () -> client.get().path("/status/401").send(Person.class)
            );

            assertCommonFields(ex, 401, ResponseStatus.UNAUTHORIZED, false);
        }

        @Test
        void status403_throwsForbiddenException() {
            ForbiddenException ex = assertThrows(
                    ForbiddenException.class,
                    () -> client.get().path("/status/403").send(Person.class)
            );

            assertCommonFields(ex, 403, ResponseStatus.FORBIDDEN, false);
        }

        @Test
        void status404_throwsNotFoundException() {
            NotFoundException ex = assertThrows(
                    NotFoundException.class,
                    () -> client.get().path("/status/404").send(Person.class)
            );

            assertCommonFields(ex, 404, ResponseStatus.NOT_FOUND);
        }

        @Test
        void status405_throwsMethodNotAllowedException() {
            MethodNotAllowedException ex = assertThrows(
                    MethodNotAllowedException.class,
                    () -> client.get().path("/status/405").send(Person.class)
            );

            assertCommonFields(ex, 405, ResponseStatus.METHOD_NOT_ALLOWED);
        }

        @Test
        void status409_throwsConflictException() {
            ConflictException ex = assertThrows(
                    ConflictException.class,
                    () -> client.get().path("/status/409").send(Person.class)
            );

            assertCommonFields(ex, 409, ResponseStatus.CONFLICT);
        }

        @Test
        void status413_throwsPayloadTooLargeException() {
            PayloadTooLargeException ex = assertThrows(
                    PayloadTooLargeException.class,
                    () -> client.get().path("/status/413").send(Person.class)
            );

            assertCommonFields(ex, 413, ResponseStatus.CONTENT_TOO_LARGE);
        }

        @Test
        void status422_throwsUnprocessableEntityException() {
            UnprocessableEntityException ex = assertThrows(
                    UnprocessableEntityException.class,
                    () -> client.get().path("/status/422").send(Person.class)
            );

            assertCommonFields(ex, 422, ResponseStatus.UNPROCESSABLE_CONTENT);
        }

        @Test
        void status429_throwsTooManyRequestsException() {
            TooManyRequestsException ex = assertThrows(
                    TooManyRequestsException.class,
                    () -> client.get().path("/status/429").send(Person.class)
            );

            assertCommonFields(ex, 429, ResponseStatus.TOO_MANY_REQUESTS);
        }
    }

    // -------------------------------------------------------------------------
    // Exception body
    // -------------------------------------------------------------------------

    @Nested
    class KnownServerErrors {
        @Test
        void status500_throwsInternalServerErrorException() {
            InternalServerErrorException ex = assertThrows(
                    InternalServerErrorException.class,
                    () -> client.get().path("/status/500").send(Person.class)
            );

            assertCommonFields(ex, 500, ResponseStatus.INTERNAL_SERVER_ERROR, false);
        }

        @Test
        void status501_throwsNotImplementedException() {
            NotImplementedException ex = assertThrows(
                    NotImplementedException.class,
                    () -> client.get().path("/status/501").send(Person.class)
            );

            assertCommonFields(ex, 501, ResponseStatus.NOT_IMPLEMENTED);
        }

        @Test
        void status503_throwsServiceUnavailableException() {
            ServiceUnavailableException ex = assertThrows(
                    ServiceUnavailableException.class,
                    () -> client.get().path("/status/503").send(Person.class)
            );

            assertCommonFields(ex, 503, ResponseStatus.SERVICE_UNAVAILABLE);
        }
    }

    // -------------------------------------------------------------------------
    // Void-response error paths — voidBodyHandler in DELETE / HEAD
    // -------------------------------------------------------------------------

    @Nested
    class UnknownStatusCodes {
        /**
         * An unrecognized 4xx code falls back to {@link ClientException} with the
         * correct status code preserved.
         */
        @Test
        void unknownClientError_throwsClientException() {
            ClientException ex = assertThrows(
                    ClientException.class,
                    () -> client.get().path("/status/418").send(Person.class)
            );

            assertCommonFields(ex, 418, ResponseStatus.UNKNOWN);
        }

        /**
         * An unrecognized 5xx code falls back to {@link ServerException} with the
         * correct status code preserved.
         */
        @Test
        void unknownServerError_throwsServerException() {
            ServerException ex = assertThrows(
                    ServerException.class,
                    () -> client.get().path("/status/599").send(Person.class)
            );

            assertCommonFields(ex, 599, ResponseStatus.UNKNOWN);
        }
    }

    // -------------------------------------------------------------------------
    // Redirect — NEVER policy
    // -------------------------------------------------------------------------

    @Nested
    class ExceptionBody {
        /**
         * The raw response body is preserved in the thrown exception so callers can
         * inspect server-provided error details.
         */
        @Test
        void errorBody_isAvailableOnException() {
            HttpResponseException ex = assertThrows(
                    HttpResponseException.class,
                    () -> client.get().path("/status/400").send(Person.class)
            );

            assertCommonFields(ex, 400, ResponseStatus.BAD_REQUEST);
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * These tests exercise the error path inside {@code RequestState.voidBodyHandler}
     * and {@code GetRequest.downloadBodyHandler} which are never triggered by the typed
     * GET tests above (those use {@link JsonBodyHandler}).
     */
    @Nested
    class VoidAndDownloadErrorPaths {
        /**
         * A DELETE to {@code /void-error} returns 400 with a non-blank plain-text body.
         * This exercises both the {@code voidBodyHandler} error branch and the
         * {@code body.isBlank() = false} path within it, confirming that
         * {@code errorBody} is populated on the thrown exception.
         */
        @Test
        void delete_4xxResponse_throwsHttpResponseException() {
            BadRequestException ex = assertThrows(
                    BadRequestException.class,
                    () -> client.delete().path("/void-error").send()
            );

            assertEquals(400, ex.statusCode());
            assertEquals("DELETE", ex.method().orElse(null));
            assertTrue(ex.body().isPresent());
            assertEquals("bad request", ex.body().get());
        }

        /**
         * HEAD requests are processed by the same GET handler but HTTP strips the body.
         * The client receives a 404 with an empty response body — exercising both the
         * {@code voidBodyHandler} error path and the {@code body.isBlank() → errorBody = null}
         * branch within it.
         */
        @Test
        void head_4xxResponse_throwsHttpResponseException() {
            NotFoundException ex = assertThrows(
                    NotFoundException.class,
                    () -> client.head().path("/status/404").send()
            );

            assertEquals(404, ex.statusCode());
            assertEquals("HEAD", ex.method().orElse(null));
            // HEAD responses carry no body — errorBody is null inside voidBodyHandler
            assertTrue(ex.body().isEmpty());
        }

        /**
         * {@code download()} receives a 4xx error response.
         * Exercises the error branch inside {@code GetRequest.downloadBodyHandler}
         * which the successful-download tests never reach.
         */
        @Test
        void download_4xxResponseWithBody_throwsHttpResponseException() {
            NotFoundException ex = assertThrows(
                    NotFoundException.class,
                    () -> client.get().path("/status/404").download()
            );

            assertEquals(404, ex.statusCode());
            assertEquals("GET", ex.method().orElse(null));
        }

        /**
         * {@code download()} error response with no entity body.
         * The empty body string exercises the {@code body.isBlank()} → {@code errorBody = null}
         * branch inside {@code downloadBodyHandler}.
         */
        @Test
        void download_4xxResponseWithEmptyBody_exceptionBodyIsEmpty() {
            NotFoundException ex = assertThrows(
                    NotFoundException.class,
                    () -> client.get().path("/empty-error").download()
            );

            assertEquals(404, ex.statusCode());
            assertTrue(ex.body().isEmpty());
        }
    }

    @Nested
    class Redirect {
        /**
         * When the redirect policy is {@link HttpClient.Redirect#NEVER} and the server
         * returns a 3xx status, the client returns the response as-is: the status code is
         * preserved and the body stream is non-null (the raw bytes are available for
         * download via {@link GetSpec#download()}).
         */
        @Test
        void neverPolicy_302Response_returnsRedirectStatusCode() {
            Client neverClient = Client.builder()
                    .configuration(
                            ClientConfiguration.builder()
                                    .uri(server.uri())
                                    .connectTimeout(Duration.ofSeconds(5))
                                    .readTimeout(Duration.ofSeconds(30))
                                    .serializer(JacksonSerializer.builder().build())
                                    .redirectPolicy(HttpClient.Redirect.NEVER)
                                    .build()
                    )
                    .build();

            HttpResponse<InputStream> response = neverClient.get()
                    .path("/status/302")
                    .download();

            assertEquals(302, response.statusCode());
        }

        /**
         * The default redirect policy ({@link HttpClient.Redirect#NORMAL}) follows
         * redirects transparently.  When the server returns a 302 with a {@code Location}
         * header pointing back to itself and no redirect loop occurs, the client follows it.
         * Here, {@code StatusResource} returns a bare 302 with no {@code Location}, which
         * causes the JDK to raise an {@link java.io.IOException} — confirming that the
         * NEVER policy is required when raw 3xx responses must be inspected.
         */
        @Test
        void normalPolicy_302WithNoLocation_raisesIoException() {
            // With NORMAL redirect policy and a 302 that has no Location header,
            // the JDK throws an IOException ("Invalid redirection").
            // Use download() which wraps it as TransportException.
            org.junit.jupiter.api.Assertions.assertThrows(
                    TransportException.class,
                    () -> client.get()
                            .path("/status/302")
                            .download()
            );
        }
    }
}
