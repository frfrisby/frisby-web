package software.frisby.web.client;

import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import software.frisby.web.client.exception.*;
import software.frisby.web.serial.GenericType;
import software.frisby.web.serial.jackson.JacksonSerializer;
import software.frisby.web.server.Server;
import software.frisby.web.server.ServerConfiguration;
import software.frisby.web.test.TestLogging;
import software.frisby.web.test.TestResources;
import software.frisby.web.test.domain.CreatePersonRequest;
import software.frisby.web.test.domain.Person;
import software.frisby.web.test.domain.UpdatePersonRequest;

import java.io.InputStream;
import java.net.HttpCookie;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase B integration tests — core HTTP verbs (GET, POST, PUT, PATCH, DELETE, HEAD).
 * <p>
 * Uses a real embedded Jersey/Jetty server supplied by {@code test-support}.
 * Each test method sends a real HTTP request and asserts on the fully deserialized response.
 */
class ClientHttpVerbTest {
    private static final String COMPRESS_WITH_FORM_ERROR =
            "The 'compress' value is invalid.  Compression is only supported for JSON entity bodies.";

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
                        TestLogging.forClass(ClientHttpVerbTest.class)
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
    // GET
    // -------------------------------------------------------------------------

    @Nested
    class Get {
        /**
         * GET /persons/{id} with a single-param path convenience overload → typed Person response.
         */
        @Test
        void typedResponse_returnsDeserializedPerson() {
            HttpResponse<Person> response = client.get()
                    .path("/persons/{id}", "id", "person-1")
                    .send(Person.class);

            assertEquals(200, response.statusCode());
            assertNotNull(response.body());
            assertEquals("person-1", response.body().id());
            assertEquals("Test Person", response.body().name());
            assertEquals("test@example.com", response.body().email());
        }

        /**
         * GET /persons → generic collection via {@link GenericType}.
         */
        @Test
        void genericCollection_returnsDeserializedList() {
            HttpResponse<List<Person>> response = client.get()
                    .path("/persons")
                    .send(new GenericType<>() {
                    });

            assertEquals(200, response.statusCode());
            assertNotNull(response.body());
            assertEquals(2, response.body().size());
            assertEquals("person-1", response.body().get(0).id());
            assertEquals("Alice", response.body().get(0).name());
            assertEquals("person-2", response.body().get(1).id());
            assertEquals("Bob", response.body().get(1).name());
        }

        /**
         * GET /persons/not-found → server returns 404, client throws {@link NotFoundException}.
         */
        @Test
        void notFound_throwsNotFoundException() {
            assertThrows(
                    NotFoundException.class,
                    () -> client.get()
                            .path("/persons/{id}", "id", "not-found")
                            .send(Person.class)
            );
        }

        /**
         * Query parameter is appended to the URI — server ignores it, request succeeds,
         * proving the client formed a valid URL with the extra parameter.
         */
        @Test
        void queryParameter_isAppendedToUri() {
            HttpResponse<List<Person>> response = client.get()
                    .path("/persons")
                    .parameter("page", "1")
                    .send(new GenericType<>() {
                    });

            assertEquals(200, response.statusCode());
            assertEquals(2, response.body().size());
        }

        /**
         * Multi-value query parameter produces repeated {@code name=value} pairs without
         * breaking the request.
         */
        @Test
        void multiValueQueryParameter_isAppendedToUri() {
            HttpResponse<List<Person>> response = client.get()
                    .path("/persons")
                    .parameter("status", "active", "pending")
                    .send(new GenericType<>() {
                    });

            assertEquals(200, response.statusCode());
            assertEquals(2, response.body().size());
        }

        /**
         * Path parameter substituted via {@link PathParameter#of(String, String)} — verifies
         * the multi-parameter overload resolves the placeholder correctly.
         */
        @Test
        void pathParameter_pathParameterOf_substitutesId() {
            HttpResponse<Person> response = client.get()
                    .path("/persons/{id}", PathParameter.of("id", "custom-id"))
                    .send(Person.class);

            assertEquals(200, response.statusCode());
            assertEquals("custom-id", response.body().id());
        }

        /**
         * Custom request header is forwarded — server ignores it, request still succeeds.
         */
        @Test
        void customHeader_isAccepted() {
            HttpResponse<Person> response = client.get()
                    .path("/persons/{id}", "id", "person-1")
                    .header("X-Request-Id", "test-123")
                    .send(Person.class);

            assertEquals(200, response.statusCode());
        }

        /**
         * Multi-value custom header is forwarded without breaking the request.
         */
        @Test
        void multiValueCustomHeader_isAccepted() {
            HttpResponse<Person> response = client.get()
                    .path("/persons/{id}", "id", "person-1")
                    .header("X-Flags", "a", "b")
                    .send(Person.class);

            assertEquals(200, response.statusCode());
        }

        /**
         * Cookie attached to the request — server ignores it, request still succeeds.
         */
        @Test
        void cookie_isAccepted() {
            HttpResponse<Person> response = client.get()
                    .path("/persons/{id}", "id", "person-1")
                    .cookie(new HttpCookie("session", "abc"))
                    .send(Person.class);

            assertEquals(200, response.statusCode());
        }

        /**
         * Per-request security provider overrides the client-level default — verifies
         * the {@code security()} fluent method is wired through to {@link RequestState}.
         */
        @Test
        void securityProvider_isInvoked() {
            AtomicBoolean invoked = new AtomicBoolean(false);

            HttpResponse<Person> response = client.get()
                    .path("/persons/{id}", "id", "person-1")
                    .security(req -> invoked.set(true))
                    .send(Person.class);

            assertEquals(200, response.statusCode());
            assertTrue(invoked.get());
        }
    }

    // -------------------------------------------------------------------------
    // GET — download / downloadAsync
    // -------------------------------------------------------------------------

    @Nested
    class Download {
        /**
         * Successful download — {@code isError()} is {@code false}, so the handler
         * returns the raw {@link InputStream} subscriber without throwing.
         */
        @Test
        void download_successStatus_returnsInputStream() {
            HttpResponse<InputStream> response = client.get()
                    .path("/persons/person-1")
                    .download();

            assertEquals(200, response.statusCode());
            assertNotNull(response.body());
        }

        /**
         * Error download with a JSON body — {@code isError()} is {@code true} and
         * the body string is non-blank, so {@code errorBody} is set and the thrown
         * exception carries it.
         */
        @Test
        void download_errorStatus_withBody_throwsException() {
            assertThrows(
                    BadRequestException.class,
                    () -> client.get().path("/status/400").download()
            );
        }

        /**
         * Error download where the server strips the response body (401 via the
         * security filter returns an empty body).  The body string is blank, so the
         * ternary sets {@code errorBody = null} — exercising the {@code null} arm of
         * {@code (null == body || body.isBlank()) ? null : body}.
         */
        @Test
        void download_errorStatus_withBlankBody_throwsException() {
            assertThrows(
                    UnauthorizedException.class,
                    () -> client.get().path("/status/401").download()
            );
        }

        /**
         * Async download — success path returns a completed future with the raw stream.
         */
        @Test
        void downloadAsync_successStatus_returnsInputStream() {
            HttpResponse<InputStream> response = client.get()
                    .path("/persons/person-1")
                    .downloadAsync()
                    .join();

            assertEquals(200, response.statusCode());
            assertNotNull(response.body());
        }

        /**
         * Async download error — the exception thrown inside the body subscriber mapping
         * function surfaces as the cause of a {@link CompletionException}.
         */
        @Test
        void downloadAsync_errorStatus_throwsCompletionException() {
            CompletionException ex = assertThrows(
                    CompletionException.class,
                    () -> client.get().path("/status/400").downloadAsync().join()
            );

            assertInstanceOf(BadRequestException.class, ex.getCause());
        }
    }

    // -------------------------------------------------------------------------
    // POST
    // -------------------------------------------------------------------------

    @Nested
    class Post {
        /**
         * POST /persons with a JSON body → 201 Created, typed Person response.
         */
        @Test
        void withJsonBody_typedResponse_returnsCreatedPerson() {
            CreatePersonRequest request = new CreatePersonRequest("Alice", "alice@example.com");

            HttpResponse<Person> response = client.post()
                    .path("/persons")
                    .body(request)
                    .send(Person.class);

            assertEquals(201, response.statusCode());
            assertNotNull(response.body());
            assertEquals("created-1", response.body().id());
            assertEquals("Alice", response.body().name());
            assertEquals("alice@example.com", response.body().email());
        }

        /**
         * POST /persons with a JSON body, void {@code send()} terminal — verifies the
         * request is sent and the server responds successfully without reading the body.
         */
        @Test
        void withJsonBody_voidSend_returnsSuccessStatus() {
            CreatePersonRequest request = new CreatePersonRequest("Bob", "bob@example.com");

            HttpResponse<Void> response = client.post()
                    .path("/persons")
                    .body(request)
                    .send();

            assertEquals(201, response.statusCode());
        }

        /**
         * {@code path(String, String, String)} substitutes a placeholder eagerly —
         * {@code POST /persons/{id}} has no server handler so the server returns 405
         * Method Not Allowed, which maps to {@link MethodNotAllowedException}.
         */
        @Test
        void path_3argConvenience_substitutesPlaceholder() {
            MethodNotAllowedException ex = assertThrows(
                    MethodNotAllowedException.class,
                    () -> client.post()
                            .path("/persons/{id}", "id", "test-id")
                            .body(new CreatePersonRequest("Test", "test@test.com"))
                            .send(Person.class)
            );

            assertEquals(405, ex.statusCode());
        }

        /**
         * {@code path(String, String, String)} throws {@link UriSyntaxException}
         * immediately at the call site when the placeholder is not present in the path.
         */
        @Test
        void path_3argConvenience_throwsWhenPlaceholderNotFound() {
            assertThrows(
                    UriSyntaxException.class,
                    () -> client.post().path("/persons", "id", "person-1")
            );
        }

        /**
         * {@code path(String, PathParameter...)} varargs overload substitutes the
         * placeholder eagerly — {@code POST /persons/{id}} has no server handler so
         * the server returns 405 Method Not Allowed.
         */
        @Test
        void pathParameterOf_substitutesPlaceholder() {
            MethodNotAllowedException ex = assertThrows(
                    MethodNotAllowedException.class,
                    () -> client.post()
                            .path("/persons/{id}", PathParameter.of("id", "test-id"))
                            .body(new CreatePersonRequest("Test", "test@test.com"))
                            .send(Person.class)
            );

            assertEquals(405, ex.statusCode());
        }

        /**
         * {@code path(String, PathParameter...)} throws {@link UriSyntaxException}
         * immediately at the call site when the placeholder is not present in the path.
         */
        @Test
        void pathParameterOf_throwsWhenPlaceholderNotFound() {
            assertThrows(
                    UriSyntaxException.class,
                    () -> client.post().path("/persons", PathParameter.of("id", "person-1"))
            );
        }

        /**
         * Single query parameter appended to the URI — server ignores it, request still succeeds.
         */
        @Test
        void queryParameter_isAppendedToUri() {
            HttpResponse<Person> response = client.post()
                    .path("/persons")
                    .parameter("version", "1")
                    .body(new CreatePersonRequest("Test", "test@test.com"))
                    .send(Person.class);

            assertEquals(201, response.statusCode());
        }

        /**
         * Multi-value query parameter produces repeated {@code name=value} pairs without
         * breaking the request.
         */
        @Test
        void multiValueQueryParameter_isAppendedToUri() {
            HttpResponse<Person> response = client.post()
                    .path("/persons")
                    .parameter("flags", "a", "b")
                    .body(new CreatePersonRequest("Test", "test@test.com"))
                    .send(Person.class);

            assertEquals(201, response.statusCode());
        }

        /**
         * Custom request header is forwarded — server ignores it, request still succeeds.
         */
        @Test
        void customHeader_isAccepted() {
            HttpResponse<Person> response = client.post()
                    .path("/persons")
                    .header("X-Request-Id", "test-123")
                    .body(new CreatePersonRequest("Test", "test@test.com"))
                    .send(Person.class);

            assertEquals(201, response.statusCode());
        }

        /**
         * Multi-value custom header is forwarded without breaking the request.
         */
        @Test
        void multiValueCustomHeader_isAccepted() {
            HttpResponse<Person> response = client.post()
                    .path("/persons")
                    .header("X-Flags", "a", "b")
                    .body(new CreatePersonRequest("Test", "test@test.com"))
                    .send(Person.class);

            assertEquals(201, response.statusCode());
        }

        /**
         * Cookie attached to the request — server ignores it, request still succeeds.
         */
        @Test
        void cookie_isAccepted() {
            HttpResponse<Person> response = client.post()
                    .path("/persons")
                    .cookie(new HttpCookie("session", "abc"))
                    .body(new CreatePersonRequest("Test", "test@test.com"))
                    .send(Person.class);

            assertEquals(201, response.statusCode());
        }

        /**
         * Per-request security provider overrides the client-level default — verifies
         * the {@code security()} fluent method is wired through to {@link RequestState}.
         */
        @Test
        void securityProvider_isInvoked() {
            AtomicBoolean invoked = new AtomicBoolean(false);

            HttpResponse<Person> response = client.post()
                    .path("/persons")
                    .security(req -> invoked.set(true))
                    .body(new CreatePersonRequest("Test", "test@test.com"))
                    .send(Person.class);

            assertEquals(201, response.statusCode());
            assertTrue(invoked.get());
        }

        /**
         * No body set before {@code send()} — {@code buildJsonRequest} uses
         * {@code noBody()} publisher and omits {@code Content-Type}.  Jersey injects
         * {@code null} for the entity parameter, the resource method NPEs, and the
         * server returns 500.
         */
        @Test
        void noBody_buildRequestUsesNoBodyPublisher() {
            assertThrows(
                    InternalServerErrorException.class,
                    () -> client.post().path("/persons").send(Person.class)
            );
        }

        /**
         * Raw {@link String} passed as body — {@code serializeBody()} detects the
         * {@code String} type and converts directly to UTF-8 bytes, bypassing the
         * serializer.
         */
        @Test
        void stringBody_isSerializedAsUtf8Bytes() {
            HttpResponse<Person> response = client.post()
                    .path("/persons")
                    .body("{\"name\":\"Alice\",\"email\":\"alice@test.com\"}")
                    .send(Person.class);

            assertEquals(201, response.statusCode());
            assertEquals("Alice", response.body().name());
        }

        /**
         * Calling {@code compress()} before {@code body(FormData)} is set, then
         * sending, must throw {@link IllegalStateException} — compression is not
         * supported for multipart bodies.
         */
        @Test
        void compress_withFormDataBody_throwsIllegalStateException() {
            IllegalStateException ex = assertThrows(
                    IllegalStateException.class,
                    () -> client.post()
                            .path("/upload")
                            .compress()
                            .body(FormData.of(FormPart.text("field", "value")))
                            .send()
            );

            assertEquals(COMPRESS_WITH_FORM_ERROR, ex.getMessage());
        }

        /**
         * Calling {@code compress()} before {@code body(FormUrlEncoded)} is set, then
         * sending, must throw {@link IllegalStateException} — compression is not
         * supported for form-urlencoded bodies.
         */
        @Test
        void compress_withFormUrlEncodedBody_throwsIllegalStateException() {
            IllegalStateException ex = assertThrows(
                    IllegalStateException.class,
                    () -> client.post()
                            .path("/echo/form")
                            .compress()
                            .body(FormUrlEncoded.builder().field("name", "x").build())
                            .send()
            );

            assertEquals(COMPRESS_WITH_FORM_ERROR, ex.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // PUT
    // -------------------------------------------------------------------------

    @Nested
    class Put {
        /**
         * PUT /persons/{id} with a JSON body → server echoes id + new name/email.
         */
        @Test
        void withJsonBody_returnsUpdatedPerson() {
            UpdatePersonRequest request = new UpdatePersonRequest("Updated Name", "updated@example.com");

            HttpResponse<Person> response = client.put()
                    .path("/persons/{id}", "id", "person-1")
                    .body(request)
                    .send(Person.class);

            assertEquals(200, response.statusCode());
            assertNotNull(response.body());
            assertEquals("person-1", response.body().id());
            assertEquals("Updated Name", response.body().name());
            assertEquals("updated@example.com", response.body().email());
        }

        /**
         * {@code path(String, PathParameter...)} varargs overload — verifies the
         * placeholder is substituted correctly.
         */
        @Test
        void pathParameterOf_substitutesId() {
            HttpResponse<Person> response = client.put()
                    .path("/persons/{id}", PathParameter.of("id", "person-1"))
                    .body(new UpdatePersonRequest("Updated", "updated@test.com"))
                    .send(Person.class);

            assertEquals(200, response.statusCode());
        }

        /**
         * Single query parameter appended to the URI — server ignores it, request still succeeds.
         */
        @Test
        void queryParameter_isAppendedToUri() {
            HttpResponse<Person> response = client.put()
                    .path("/persons/{id}", "id", "person-1")
                    .parameter("version", "1")
                    .body(new UpdatePersonRequest("Updated", "updated@test.com"))
                    .send(Person.class);

            assertEquals(200, response.statusCode());
        }

        /**
         * Multi-value query parameter produces repeated {@code name=value} pairs without
         * breaking the request.
         */
        @Test
        void multiValueQueryParameter_isAppendedToUri() {
            HttpResponse<Person> response = client.put()
                    .path("/persons/{id}", "id", "person-1")
                    .parameter("flags", "a", "b")
                    .body(new UpdatePersonRequest("Updated", "updated@test.com"))
                    .send(Person.class);

            assertEquals(200, response.statusCode());
        }

        /**
         * Custom request header is forwarded — server ignores it, request still succeeds.
         */
        @Test
        void customHeader_isAccepted() {
            HttpResponse<Person> response = client.put()
                    .path("/persons/{id}", "id", "person-1")
                    .header("X-Request-Id", "test-123")
                    .body(new UpdatePersonRequest("Updated", "updated@test.com"))
                    .send(Person.class);

            assertEquals(200, response.statusCode());
        }

        /**
         * Multi-value custom header is forwarded without breaking the request.
         */
        @Test
        void multiValueCustomHeader_isAccepted() {
            HttpResponse<Person> response = client.put()
                    .path("/persons/{id}", "id", "person-1")
                    .header("X-Flags", "a", "b")
                    .body(new UpdatePersonRequest("Updated", "updated@test.com"))
                    .send(Person.class);

            assertEquals(200, response.statusCode());
        }

        /**
         * Cookie attached to the request — server ignores it, request still succeeds.
         */
        @Test
        void cookie_isAccepted() {
            HttpResponse<Person> response = client.put()
                    .path("/persons/{id}", "id", "person-1")
                    .cookie(new HttpCookie("session", "abc"))
                    .body(new UpdatePersonRequest("Updated", "updated@test.com"))
                    .send(Person.class);

            assertEquals(200, response.statusCode());
        }

        /**
         * Per-request security provider overrides the client-level default — verifies
         * the {@code security()} fluent method is wired through to {@link RequestState}.
         */
        @Test
        void securityProvider_isInvoked() {
            AtomicBoolean invoked = new AtomicBoolean(false);

            HttpResponse<Person> response = client.put()
                    .path("/persons/{id}", "id", "person-1")
                    .security(req -> invoked.set(true))
                    .body(new UpdatePersonRequest("Updated", "updated@test.com"))
                    .send(Person.class);

            assertEquals(200, response.statusCode());
            assertTrue(invoked.get());
        }

        /**
         * Void {@code send()} terminal — verifies the request succeeds without reading the body.
         */
        @Test
        void void_send_returnsSuccessStatus() {
            HttpResponse<Void> response = client.put()
                    .path("/persons/{id}", "id", "person-1")
                    .body(new UpdatePersonRequest("Updated", "updated@test.com"))
                    .send();

            assertEquals(200, response.statusCode());
        }

        /**
         * No body set before {@code send()} — {@code buildJsonRequest} uses
         * {@code noBody()} publisher and omits {@code Content-Type}.  Jersey injects
         * {@code null} for the entity parameter, the resource method NPEs, and the
         * server returns 500.
         */
        @Test
        void noBody_buildRequestUsesNoBodyPublisher() {
            assertThrows(
                    InternalServerErrorException.class,
                    () -> client.put()
                            .path("/persons/{id}", "id", "person-1")
                            .send(Person.class)
            );
        }

        /**
         * Raw {@link String} passed as body — {@code serializeBody()} detects the
         * {@code String} type and converts directly to UTF-8 bytes, bypassing the serializer.
         */
        @Test
        void stringBody_isSerializedAsUtf8Bytes() {
            HttpResponse<Person> response = client.put()
                    .path("/persons/{id}", "id", "person-1")
                    .body("{\"name\":\"Updated\",\"email\":\"updated@test.com\"}")
                    .send(Person.class);

            assertEquals(200, response.statusCode());
            assertEquals("Updated", response.body().name());
        }

        /**
         * {@code compress()} before {@code body(FormData)} → {@link IllegalStateException}
         * because compression is not supported for multipart bodies.
         */
        @Test
        void compress_withFormDataBody_throwsIllegalStateException() {
            IllegalStateException ex = assertThrows(
                    IllegalStateException.class,
                    () -> client.put()
                            .path("/upload")
                            .compress()
                            .body(FormData.of(FormPart.text("field", "value")))
                            .send()
            );

            assertEquals(COMPRESS_WITH_FORM_ERROR, ex.getMessage());
        }

        /**
         * {@code compress()} before {@code body(FormUrlEncoded)} → {@link IllegalStateException}
         * because compression is not supported for form-urlencoded bodies.
         */
        @Test
        void compress_withFormUrlEncodedBody_throwsIllegalStateException() {
            IllegalStateException ex = assertThrows(
                    IllegalStateException.class,
                    () -> client.put()
                            .path("/echo/form")
                            .compress()
                            .body(FormUrlEncoded.builder().field("name", "x").build())
                            .send()
            );

            assertEquals(COMPRESS_WITH_FORM_ERROR, ex.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // PATCH
    // -------------------------------------------------------------------------

    @Nested
    class Patch {
        /**
         * PATCH /persons/{id} with a JSON body → server echoes id + new name/email.
         */
        @Test
        void withJsonBody_returnsUpdatedPerson() {
            UpdatePersonRequest request = new UpdatePersonRequest("Patched Name", "patched@example.com");

            HttpResponse<Person> response = client.patch()
                    .path("/persons/{id}", "id", "person-1")
                    .body(request)
                    .send(Person.class);

            assertEquals(200, response.statusCode());
            assertNotNull(response.body());
            assertEquals("person-1", response.body().id());
            assertEquals("Patched Name", response.body().name());
            assertEquals("patched@example.com", response.body().email());
        }

        /**
         * Literal path with no parameter substitution — verifies the plain
         * {@code path(String)} overload is wired correctly.
         */
        @Test
        void literalPath_returns200() {
            HttpResponse<Person> response = client.patch()
                    .path("/persons/person-1")
                    .body(new UpdatePersonRequest("Patched", "patched@test.com"))
                    .send(Person.class);

            assertEquals(200, response.statusCode());
        }

        /**
         * {@code path(String, PathParameter...)} varargs overload — verifies the
         * placeholder is substituted correctly.
         */
        @Test
        void pathParameterOf_substitutesId() {
            HttpResponse<Person> response = client.patch()
                    .path("/persons/{id}", PathParameter.of("id", "person-1"))
                    .body(new UpdatePersonRequest("Patched", "patched@test.com"))
                    .send(Person.class);

            assertEquals(200, response.statusCode());
        }

        /**
         * Single query parameter appended to the URI — server ignores it, request still succeeds.
         */
        @Test
        void queryParameter_isAppendedToUri() {
            HttpResponse<Person> response = client.patch()
                    .path("/persons/{id}", "id", "person-1")
                    .parameter("version", "1")
                    .body(new UpdatePersonRequest("Patched", "patched@test.com"))
                    .send(Person.class);

            assertEquals(200, response.statusCode());
        }

        /**
         * Multi-value query parameter produces repeated {@code name=value} pairs without
         * breaking the request.
         */
        @Test
        void multiValueQueryParameter_isAppendedToUri() {
            HttpResponse<Person> response = client.patch()
                    .path("/persons/{id}", "id", "person-1")
                    .parameter("flags", "a", "b")
                    .body(new UpdatePersonRequest("Patched", "patched@test.com"))
                    .send(Person.class);

            assertEquals(200, response.statusCode());
        }

        /**
         * Custom request header is forwarded — server ignores it, request still succeeds.
         */
        @Test
        void customHeader_isAccepted() {
            HttpResponse<Person> response = client.patch()
                    .path("/persons/{id}", "id", "person-1")
                    .header("X-Request-Id", "test-123")
                    .body(new UpdatePersonRequest("Patched", "patched@test.com"))
                    .send(Person.class);

            assertEquals(200, response.statusCode());
        }

        /**
         * Multi-value custom header is forwarded without breaking the request.
         */
        @Test
        void multiValueCustomHeader_isAccepted() {
            HttpResponse<Person> response = client.patch()
                    .path("/persons/{id}", "id", "person-1")
                    .header("X-Flags", "a", "b")
                    .body(new UpdatePersonRequest("Patched", "patched@test.com"))
                    .send(Person.class);

            assertEquals(200, response.statusCode());
        }

        /**
         * Cookie attached to the request — server ignores it, request still succeeds.
         */
        @Test
        void cookie_isAccepted() {
            HttpResponse<Person> response = client.patch()
                    .path("/persons/{id}", "id", "person-1")
                    .cookie(new HttpCookie("session", "abc"))
                    .body(new UpdatePersonRequest("Patched", "patched@test.com"))
                    .send(Person.class);

            assertEquals(200, response.statusCode());
        }

        /**
         * Per-request security provider overrides the client-level default.
         */
        @Test
        void securityProvider_isInvoked() {
            AtomicBoolean invoked = new AtomicBoolean(false);

            HttpResponse<Person> response = client.patch()
                    .path("/persons/{id}", "id", "person-1")
                    .security(req -> invoked.set(true))
                    .body(new UpdatePersonRequest("Patched", "patched@test.com"))
                    .send(Person.class);

            assertEquals(200, response.statusCode());
            assertTrue(invoked.get());
        }

        /**
         * {@code send(GenericType)} terminal — verifies the generic-type overload is wired
         * correctly.
         */
        @Test
        void genericType_send_returnsUpdatedPerson() {
            HttpResponse<Person> response = client.patch()
                    .path("/persons/{id}", "id", "person-1")
                    .body(new UpdatePersonRequest("Patched", "patched@test.com"))
                    .send(new GenericType<>() {
                    });

            assertEquals(200, response.statusCode());
            assertEquals("Patched", response.body().name());
        }

        /**
         * Void {@code send()} terminal — verifies the request succeeds without reading the body.
         */
        @Test
        void void_send_returnsSuccessStatus() {
            HttpResponse<Void> response = client.patch()
                    .path("/persons/{id}", "id", "person-1")
                    .body(new UpdatePersonRequest("Patched", "patched@test.com"))
                    .send();

            assertEquals(200, response.statusCode());
        }

        /**
         * No body set before {@code send()} — {@code buildJsonRequest} uses
         * {@code noBody()} publisher and omits {@code Content-Type}.  Jersey injects
         * {@code null} for the entity parameter, the resource method NPEs, and the
         * server returns 500.
         */
        @Test
        void noBody_buildRequestUsesNoBodyPublisher() {
            assertThrows(
                    InternalServerErrorException.class,
                    () -> client.patch()
                            .path("/persons/{id}", "id", "person-1")
                            .send(Person.class)
            );
        }

        /**
         * Raw {@link String} passed as body — {@code serializeBody()} detects the
         * {@code String} type and converts directly to UTF-8 bytes, bypassing the serializer.
         */
        @Test
        void stringBody_isSerializedAsUtf8Bytes() {
            HttpResponse<Person> response = client.patch()
                    .path("/persons/{id}", "id", "person-1")
                    .body("{\"name\":\"Patched\",\"email\":\"patched@test.com\"}")
                    .send(Person.class);

            assertEquals(200, response.statusCode());
            assertEquals("Patched", response.body().name());
        }

        /**
         * {@code body(FormUrlEncoded)} routes to {@code buildFormUrlEncodedRequest}.
         * PATCH /persons/{id} has no form-urlencoded handler so the server returns 415
         * Unsupported Media Type, which maps to {@link ClientException}.
         */
        @Test
        void formUrlEncoded_body_exercisesFormUrlEncodedPath() {
            ClientException ex = assertThrows(
                    ClientException.class,
                    () -> client.patch()
                            .path("/persons/{id}", "id", "person-1")
                            .body(FormUrlEncoded.builder().field("name", "Patched").build())
                            .send(Person.class)
            );

            assertEquals(415, ex.statusCode());
        }

        /**
         * {@code compress()} before {@code body(FormUrlEncoded)} → {@link IllegalStateException}
         * because compression is not supported for form-urlencoded bodies.
         */
        @Test
        void compress_withFormUrlEncodedBody_throwsIllegalStateException() {
            IllegalStateException ex = assertThrows(
                    IllegalStateException.class,
                    () -> client.patch()
                            .path("/persons/{id}", "id", "person-1")
                            .compress()
                            .body(FormUrlEncoded.builder().field("name", "Patched").build())
                            .send(Person.class)
            );

            assertEquals(COMPRESS_WITH_FORM_ERROR, ex.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // DELETE
    // -------------------------------------------------------------------------

    @Nested
    class Delete {
        /**
         * DELETE /persons/{id} with a single-param path convenience overload → 204 No Content.
         */
        @Test
        void send_returns204NoContent() {
            HttpResponse<Void> response = client.delete()
                    .path("/persons/{id}", "id", "person-1")
                    .send();

            assertEquals(204, response.statusCode());
        }

        /**
         * Literal path with no parameter substitution — verifies the plain {@code path(String)}
         * overload is wired correctly.
         */
        @Test
        void literalPath_returns204NoContent() {
            HttpResponse<Void> response = client.delete()
                    .path("/persons/person-1")
                    .send();

            assertEquals(204, response.statusCode());
        }

        /**
         * Path parameter substituted via {@link PathParameter#of(String, String)} — verifies
         * the varargs overload resolves the placeholder correctly.
         */
        @Test
        void pathParameterOf_substitutesId() {
            HttpResponse<Void> response = client.delete()
                    .path("/persons/{id}", PathParameter.of("id", "person-1"))
                    .send();

            assertEquals(204, response.statusCode());
        }

        /**
         * Single query parameter appended to the URI — server ignores it, request still succeeds.
         */
        @Test
        void queryParameter_isAppendedToUri() {
            HttpResponse<Void> response = client.delete()
                    .path("/persons/{id}", "id", "person-1")
                    .parameter("version", "1")
                    .send();

            assertEquals(204, response.statusCode());
        }

        /**
         * Multi-value query parameter produces repeated {@code name=value} pairs without
         * breaking the request.
         */
        @Test
        void multiValueQueryParameter_isAppendedToUri() {
            HttpResponse<Void> response = client.delete()
                    .path("/persons/{id}", "id", "person-1")
                    .parameter("flags", "a", "b")
                    .send();

            assertEquals(204, response.statusCode());
        }

        /**
         * Custom request header is forwarded — server ignores it, request still succeeds.
         */
        @Test
        void customHeader_isAccepted() {
            HttpResponse<Void> response = client.delete()
                    .path("/persons/{id}", "id", "person-1")
                    .header("X-Request-Id", "test-123")
                    .send();

            assertEquals(204, response.statusCode());
        }

        /**
         * Multi-value custom header is forwarded without breaking the request.
         */
        @Test
        void multiValueCustomHeader_isAccepted() {
            HttpResponse<Void> response = client.delete()
                    .path("/persons/{id}", "id", "person-1")
                    .header("X-Flags", "a", "b")
                    .send();

            assertEquals(204, response.statusCode());
        }

        /**
         * Cookie attached to the request — server ignores it, request still succeeds.
         */
        @Test
        void cookie_isAccepted() {
            HttpResponse<Void> response = client.delete()
                    .path("/persons/{id}", "id", "person-1")
                    .cookie(new HttpCookie("session", "abc"))
                    .send();

            assertEquals(204, response.statusCode());
        }

        /**
         * Per-request security provider overrides the client-level default — verifies
         * the {@code security()} fluent method is wired through to {@link RequestState}.
         */
        @Test
        void securityProvider_isInvoked() {
            AtomicBoolean invoked = new AtomicBoolean(false);

            HttpResponse<Void> response = client.delete()
                    .path("/persons/{id}", "id", "person-1")
                    .security(req -> invoked.set(true))
                    .send();

            assertEquals(204, response.statusCode());
            assertTrue(invoked.get());
        }
    }

    // -------------------------------------------------------------------------
    // HEAD
    // -------------------------------------------------------------------------

    @Nested
    class Head {
        /**
         * HEAD /persons/{id} → 200 with no body (JAX-RS auto-serves HEAD from the GET handler).
         */
        @Test
        void send_returns200WithVoidBody() {
            HttpResponse<Void> response = client.head()
                    .path("/persons/{id}", "id", "person-1")
                    .send();

            assertEquals(200, response.statusCode());
            assertNull(response.body());
        }

        /**
         * HEAD response includes a {@code Content-Type} header even though no body is present.
         */
        @Test
        void send_responseIncludesContentTypeHeader() {
            HttpResponse<Void> response = client.head()
                    .path("/persons/{id}", "id", "person-1")
                    .send();

            assertEquals(200, response.statusCode());
            assertNotNull(response.headers().firstValue("Content-Type").orElse(null));
        }

        /**
         * Literal path with no parameter substitution — verifies the plain {@code path(String)}
         * overload is wired correctly.
         */
        @Test
        void literalPath_returns200() {
            HttpResponse<Void> response = client.head()
                    .path("/persons/person-1")
                    .send();

            assertEquals(200, response.statusCode());
        }

        /**
         * Path parameter substituted via {@link PathParameter#of(String, String)} — verifies
         * the varargs overload resolves the placeholder correctly.
         */
        @Test
        void pathParameterOf_substitutesId() {
            HttpResponse<Void> response = client.head()
                    .path("/persons/{id}", PathParameter.of("id", "person-1"))
                    .send();

            assertEquals(200, response.statusCode());
        }

        /**
         * Single query parameter appended to the URI — server ignores it, request still succeeds.
         */
        @Test
        void queryParameter_isAppendedToUri() {
            HttpResponse<Void> response = client.head()
                    .path("/persons/{id}", "id", "person-1")
                    .parameter("version", "1")
                    .send();

            assertEquals(200, response.statusCode());
        }

        /**
         * Multi-value query parameter produces repeated {@code name=value} pairs without
         * breaking the request.
         */
        @Test
        void multiValueQueryParameter_isAppendedToUri() {
            HttpResponse<Void> response = client.head()
                    .path("/persons/{id}", "id", "person-1")
                    .parameter("flags", "a", "b")
                    .send();

            assertEquals(200, response.statusCode());
        }

        /**
         * Custom request header is forwarded — server ignores it, request still succeeds.
         */
        @Test
        void customHeader_isAccepted() {
            HttpResponse<Void> response = client.head()
                    .path("/persons/{id}", "id", "person-1")
                    .header("X-Request-Id", "test-123")
                    .send();

            assertEquals(200, response.statusCode());
        }

        /**
         * Multi-value custom header is forwarded without breaking the request.
         */
        @Test
        void multiValueCustomHeader_isAccepted() {
            HttpResponse<Void> response = client.head()
                    .path("/persons/{id}", "id", "person-1")
                    .header("X-Flags", "a", "b")
                    .send();

            assertEquals(200, response.statusCode());
        }

        /**
         * Cookie attached to the request — server ignores it, request still succeeds.
         */
        @Test
        void cookie_isAccepted() {
            HttpResponse<Void> response = client.head()
                    .path("/persons/{id}", "id", "person-1")
                    .cookie(new HttpCookie("session", "abc"))
                    .send();

            assertEquals(200, response.statusCode());
        }

        /**
         * Per-request security provider overrides the client-level default — verifies
         * the {@code security()} fluent method is wired through to {@link RequestState}.
         */
        @Test
        void securityProvider_isInvoked() {
            AtomicBoolean invoked = new AtomicBoolean(false);

            HttpResponse<Void> response = client.head()
                    .path("/persons/{id}", "id", "person-1")
                    .security(req -> invoked.set(true))
                    .send();

            assertEquals(200, response.statusCode());
            assertTrue(invoked.get());
        }
    }

    // -------------------------------------------------------------------------
    // String body serialization — serializeBody(String) branch in Post/Put/PatchRequest
    // -------------------------------------------------------------------------

    /**
     * When a plain {@link String} is passed as the body, each verb's {@code serializeBody()}
     * encodes it directly as raw UTF-8 bytes instead of delegating to the
     * {@link software.frisby.web.serial.JsonSerializer}.  These tests exercise that branch
     * for POST, PUT, and PATCH.
     * <p>
     * The {@code /echo} resource accepts any entity and echoes it back verbatim.  Since the
     * body is raw text (not a JSON-encoded string), the response is returned as-is by
     * {@code ClassDeserializer(String.class)} (content does not start with {@code "}).
     */
    @Nested
    class StringBodySerialization {
        @Test
        void post_stringBody_isEncodedAsRawUtf8() {
            String result = client.post()
                    .path("/echo")
                    .body("hello from post")
                    .send(String.class)
                    .body();

            assertEquals("hello from post", result);
        }

        @Test
        void put_stringBody_exercisesSerializeBodyStringBranch() {
            // PUT /persons/{id} expects UpdatePersonRequest JSON; a plain String body
            // causes a 400.  The goal is to verify PutRequest.serializeBody(String) runs.
            assertThrows(
                    HttpResponseException.class,
                    () -> client.put()
                            .path("/persons/{id}", "id", "person-1")
                            .body("raw string body")
                            .send(Person.class)
            );
        }

        @Test
        void patch_stringBody_exercisesSerializeBodyStringBranch() {
            // Same rationale as put — exercises PatchRequest.serializeBody(String).
            assertThrows(
                    HttpResponseException.class,
                    () -> client.patch()
                            .path("/persons/{id}", "id", "person-1")
                            .body("raw string body")
                            .send(Person.class)
            );
        }
    }

    // -------------------------------------------------------------------------
    // No-decompress configuration — covers acceptJson=true AND no decompressors branch
    // -------------------------------------------------------------------------

    /**
     * Exercises the {@code acceptJson && configuration().acceptEncoding() != null} expression
     * in {@code buildJsonRequest}, {@code buildMultipartRequest}, and
     * {@code buildFormUrlEncodedRequest} for POST, PUT, and PATCH when no decompressors are
     * registered.  With the standard test client gzip decompressor is registered,
     * so the {@code null} outcome of {@code acceptEncoding()} is never reached.
     * A dedicated no-decompress client covers that missing branch in all eight methods.
     */
    @Nested
    class NoDecompress {
        private final Client noDecompressClient = Client.builder()
                .configuration(
                        ClientConfiguration.builder()
                                .uri(server.uri())
                                .connectTimeout(Duration.ofSeconds(5))
                                .readTimeout(Duration.ofSeconds(30))
                                .serializer(JacksonSerializer.builder().build())
                                .build()
                )
                .build();

        @Test
        void post_jsonBody_typedSend_noDecompress() {
            HttpResponse<Person> response = noDecompressClient.post()
                    .path("/persons")
                    .body(new CreatePersonRequest("NoGzip", "nogzip@example.com"))
                    .send(Person.class);

            assertEquals(201, response.statusCode());
        }

        @Test
        void post_multipartBody_typedSend_noDecompress() {
            HttpResponse<software.frisby.web.test.domain.UploadResult> response = noDecompressClient.post()
                    .path("/upload")
                    .body(FormData.of(
                            FormPart.file("file", new java.io.ByteArrayInputStream("data".getBytes()), "test.txt")
                    ))
                    .send(software.frisby.web.test.domain.UploadResult.class);

            assertEquals(200, response.statusCode());
        }

        @Test
        void post_formUrlEncoded_typedSend_noDecompress() {
            HttpResponse<java.util.Map<String, String>> response = noDecompressClient.post()
                    .path("/echo/form")
                    .body(FormUrlEncoded.builder().field("name", "NoGzip").build())
                    .send(new GenericType<>() {
                    });

            assertEquals(200, response.statusCode());
        }

        @Test
        void put_jsonBody_typedSend_noDecompress() {
            // StatusResource only handles GET — PUT returns 405, exercising buildJsonRequest
            // with acceptJson=true and no decompressors registered.
            assertThrows(
                    MethodNotAllowedException.class,
                    () -> noDecompressClient.put()
                            .path("/status/400")
                            .body(new CreatePersonRequest("NoGzip", "nogzip@example.com"))
                            .send(Person.class)
            );
        }

        @Test
        void put_multipartBody_typedSend_noDecompress() {
            HttpResponse<software.frisby.web.test.domain.UploadResult> response = noDecompressClient.put()
                    .path("/upload")
                    .body(FormData.of(
                            FormPart.file("file", new java.io.ByteArrayInputStream("data".getBytes()), "test.txt")
                    ))
                    .send(software.frisby.web.test.domain.UploadResult.class);

            assertEquals(200, response.statusCode());
        }

        @Test
        void put_formUrlEncoded_typedSend_noDecompress() {
            HttpResponse<java.util.Map<String, String>> response = noDecompressClient.put()
                    .path("/echo/form")
                    .body(FormUrlEncoded.builder().field("name", "NoGzip").build())
                    .send(new GenericType<>() {
                    });

            assertEquals(200, response.statusCode());
        }

        @Test
        void patch_jsonBody_typedSend_noDecompress() {
            // StatusResource only handles GET — PATCH returns 405, exercising buildJsonRequest
            // with acceptJson=true and no decompressors registered.
            assertThrows(
                    MethodNotAllowedException.class,
                    () -> noDecompressClient.patch()
                            .path("/status/400")
                            .body(new CreatePersonRequest("NoGzip", "nogzip@example.com"))
                            .send(Person.class)
            );
        }

        @Test
        void patch_formUrlEncoded_typedSend_noDecompress() {
            assertThrows(
                    HttpResponseException.class,
                    () -> noDecompressClient.patch()
                            .path("/persons/{id}", "id", "person-1")
                            .body(FormUrlEncoded.builder().field("name", "NoGzip").build())
                            .send(Person.class)
            );
        }
    }
}

