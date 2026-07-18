package software.frisby.web.client;

import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import software.frisby.web.serial.GenericType;
import software.frisby.web.serial.jackson.JacksonSerializer;
import software.frisby.web.server.Server;
import software.frisby.web.server.ServerConfiguration;
import software.frisby.web.test.TestLogging;
import software.frisby.web.test.TestResources;
import software.frisby.web.test.domain.CreatePersonRequest;
import software.frisby.web.test.domain.MixedUploadResult;
import software.frisby.web.test.domain.Person;
import software.frisby.web.test.domain.UploadResult;

import java.io.ByteArrayInputStream;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase C integration tests — form-urlencoded requests and multipart uploads.
 */
class ClientMultipartTest {
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
                        TestLogging.forClass(ClientMultipartTest.class)
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
    // Form-urlencoded
    // -------------------------------------------------------------------------

    @Nested
    class FormUrlEncodedPost {
        /**
         * POST with a form-urlencoded body — server echoes fields back as a JSON map.
         */
        @Test
        void post_formUrlEncoded_echoesFieldsBack() {
            FormUrlEncoded form = FormUrlEncoded.builder()
                    .field("name", "Alice")
                    .field("email", "alice@example.com")
                    .build();

            HttpResponse<Map<String, String>> response = client.post()
                    .path("/echo/form")
                    .body(form)
                    .send(new GenericType<>() {
                    });

            assertEquals(200, response.statusCode());
            assertNotNull(response.body());
            assertEquals("Alice", response.body().get("name"));
            assertEquals("alice@example.com", response.body().get("email"));
        }

        /**
         * PUT with a form-urlencoded body — verifies PUT sends form-encoded content correctly.
         */
        @Test
        void put_formUrlEncoded_echoesFieldsBack() {
            FormUrlEncoded form = FormUrlEncoded.builder()
                    .field("name", "Bob")
                    .field("email", "bob@example.com")
                    .build();

            HttpResponse<Map<String, String>> response = client.put()
                    .path("/echo/form")
                    .body(form)
                    .send(new GenericType<>() {
                    });

            assertEquals(200, response.statusCode());
            assertEquals("Bob", response.body().get("name"));
            assertEquals("bob@example.com", response.body().get("email"));
        }

        /**
         * Void-send form-urlencoded POST exercises the {@code acceptJson = false} →
         * short-circuit path of {@code acceptJson && acceptGzip()} inside
         * {@code PostRequest.buildFormUrlEncodedRequest()}.
         */
        @Test
        void post_formUrlEncoded_voidSend_acceptJsonIsFalse() {
            FormUrlEncoded form = FormUrlEncoded.builder()
                    .field("name", "Carol")
                    .build();

            HttpResponse<Void> response = client.post()
                    .path("/echo/form")
                    .body(form)
                    .send();

            assertEquals(200, response.statusCode());
        }

        /**
         * Void-send form-urlencoded PUT exercises the same short-circuit path in
         * {@code PutRequest.buildFormUrlEncodedRequest()}.
         */
        @Test
        void put_formUrlEncoded_voidSend_acceptJsonIsFalse() {
            FormUrlEncoded form = FormUrlEncoded.builder()
                    .field("name", "Dave")
                    .build();

            HttpResponse<Void> response = client.put()
                    .path("/echo/form")
                    .body(form)
                    .send();

            assertEquals(200, response.statusCode());
        }
    }

    // -------------------------------------------------------------------------
    // Multipart file upload — POST
    // -------------------------------------------------------------------------

    @Nested
    class MultipartPost {
        /**
         * POST with a single-file multipart body — server returns filename and byte count.
         */
        @Test
        void singleFilePart_returnsFilenameAndSize() {
            byte[] fileContent = "Hello, World!".getBytes();

            HttpResponse<UploadResult> response = client.post()
                    .path("/upload")
                    .body(FormData.of(
                            FormPart.file("file", new ByteArrayInputStream(fileContent), "hello.txt")
                    ))
                    .send(UploadResult.class);

            assertEquals(200, response.statusCode());
            assertNotNull(response.body());
            assertEquals("hello.txt", response.body().fileName());
            assertEquals(fileContent.length, response.body().size());
        }

        /**
         * Void-send multipart POST exercises the {@code acceptJson = false} short-circuit
         * path of {@code acceptJson && acceptGzip()} inside
         * {@code PostRequest.buildMultipartRequest()}.
         */
        @Test
        void singleFilePart_voidSend_acceptJsonIsFalse() {
            byte[] fileContent = "void upload".getBytes();

            HttpResponse<Void> response = client.post()
                    .path("/upload")
                    .body(FormData.of(
                            FormPart.file("file", new ByteArrayInputStream(fileContent), "void.txt")
                    ))
                    .send();

            assertEquals(200, response.statusCode());
        }

        /**
         * POST with a mixed multipart body containing a file part, a JSON metadata part,
         * and a plain-text field part — all three parts are received and echoed back.
         */
        @Test
        void mixedParts_allPartsReceivedByServer() {
            byte[] fileContent = "binary data".getBytes();
            CreatePersonRequest metadata = new CreatePersonRequest("Alice", "alice@example.com");

            HttpResponse<MixedUploadResult> response = client.post()
                    .path("/upload/mixed")
                    .body(FormData.of(
                            FormPart.file("file", new ByteArrayInputStream(fileContent), "data.bin"),
                            FormPart.json("metadata", metadata),
                            FormPart.text("category", "documents")
                    ))
                    .send(MixedUploadResult.class);

            assertEquals(200, response.statusCode());
            assertNotNull(response.body());
            assertEquals("data.bin", response.body().fileName());
            assertEquals(fileContent.length, response.body().fileSize());
            assertNotNull(response.body().metadata());
            assertEquals("documents", response.body().category());
        }

        /**
         * POST with a multipart body containing only JSON and text parts — no file stream.
         * <p>
         * Exercises the {@code MultipartBodyBuilder} path where every part is assembled
         * from serialized {@code byte[]} or plain strings, with no {@code InputStream}
         * involved.
         */
        @Test
        void jsonAndTextPartsOnly_noFileStream_partsReceivedByServer() {
            CreatePersonRequest person = new CreatePersonRequest("Alice", "alice@example.com");

            HttpResponse<Map<String, String>> response = client.post()
                    .path("/echo/multipart")
                    .body(FormData.of(
                            FormPart.json("person", person),
                            FormPart.text("category", "documents")
                    ))
                    .send(new GenericType<>() {
                    });

            assertEquals(200, response.statusCode());
            assertNotNull(response.body());
            assertTrue(response.body().get("person").contains("Alice"));
            assertEquals("documents", response.body().get("category"));
        }
    }

    // -------------------------------------------------------------------------
    // Multipart file upload — PUT
    // -------------------------------------------------------------------------

    @Nested
    class MultipartPut {
        /**
         * PUT with a single-file multipart body — verifies PUT sends multipart content
         * using the same assembly pipeline as POST.
         */
        @Test
        void singleFilePart_returnsFilenameAndSize() {
            byte[] fileContent = "replaced content".getBytes();

            HttpResponse<UploadResult> response = client.put()
                    .path("/upload")
                    .body(FormData.of(
                            FormPart.file("file", new ByteArrayInputStream(fileContent), "replaced.txt")
                    ))
                    .send(UploadResult.class);

            assertEquals(200, response.statusCode());
            assertNotNull(response.body());
            assertEquals("replaced.txt", response.body().fileName());
            assertEquals(fileContent.length, response.body().size());
        }

        /**
         * Void-send multipart PUT exercises the {@code acceptJson = false} short-circuit
         * path of {@code acceptJson && acceptGzip()} inside
         * {@code PutRequest.buildMultipartRequest()}.
         */
        @Test
        void singleFilePart_voidSend_acceptJsonIsFalse() {
            byte[] fileContent = "void put upload".getBytes();

            HttpResponse<Void> response = client.put()
                    .path("/upload")
                    .body(FormData.of(
                            FormPart.file("file", new ByteArrayInputStream(fileContent), "void-put.txt")
                    ))
                    .send();

            assertEquals(200, response.statusCode());
        }

        /**
         * PUT with a mixed multipart body containing a file part, a JSON metadata part,
         * and a plain-text field part — verifies the full mixed-parts path is exercised
         * independently for PUT (separate code path from POST).
         */
        @Test
        void mixedParts_allPartsReceivedByServer() {
            byte[] fileContent = "replaced binary data".getBytes();
            CreatePersonRequest metadata = new CreatePersonRequest("Bob", "bob@example.com");

            HttpResponse<MixedUploadResult> response = client.put()
                    .path("/upload/mixed")
                    .body(FormData.of(
                            FormPart.file("file", new ByteArrayInputStream(fileContent), "replaced.bin"),
                            FormPart.json("metadata", metadata),
                            FormPart.text("category", "replacements")
                    ))
                    .send(MixedUploadResult.class);

            assertEquals(200, response.statusCode());
            assertNotNull(response.body());
            assertEquals("replaced.bin", response.body().fileName());
            assertEquals(fileContent.length, response.body().fileSize());
            assertNotNull(response.body().metadata());
            assertEquals("replacements", response.body().category());
        }

        /**
         * PUT with a multipart body containing only JSON and text parts — no file stream.
         * <p>
         * Exercises the {@code MultipartBodyBuilder} path independently for PUT, confirming
         * the file-free assembly path works identically to POST.
         */
        @Test
        void jsonAndTextPartsOnly_noFileStream_partsReceivedByServer() {
            CreatePersonRequest person = new CreatePersonRequest("Bob", "bob@example.com");

            HttpResponse<Map<String, String>> response = client.put()
                    .path("/echo/multipart")
                    .body(FormData.of(
                            FormPart.json("person", person),
                            FormPart.text("category", "replacements")
                    ))
                    .send(new GenericType<>() {
                    });

            assertEquals(200, response.statusCode());
            assertNotNull(response.body());
            assertTrue(response.body().get("person").contains("Bob"));
            assertEquals("replacements", response.body().get("category"));
        }
    }

    // -------------------------------------------------------------------------
    // JSON body — baseline to confirm the shared server is healthy
    // -------------------------------------------------------------------------

    @Nested
    class JsonBodyPost {
        @Test
        void post_jsonBody_returnsCreatedPerson() {
            CreatePersonRequest request = new CreatePersonRequest("Charlie", "charlie@example.com");

            HttpResponse<Person> response = client.post()
                    .path("/persons")
                    .body(request)
                    .send(Person.class);

            assertEquals(201, response.statusCode());
            assertEquals("Charlie", response.body().name());
        }
    }

    // -------------------------------------------------------------------------
    // MultipartBodyBuilder.serializeBody() String branch
    // -------------------------------------------------------------------------

    /**
     * When a {@link FormPart#json(String, Object)} part carries a plain {@link String}
     * body, {@code MultipartBodyBuilder.serializeBody()} encodes it directly as raw UTF-8
     * bytes instead of delegating to the {@link software.frisby.web.serial.JsonSerializer}.
     * This test exercises that branch.
     */
    @Nested
    class MultipartStringBodyPart {
        @Test
        void jsonPart_stringBody_isEncodedAsRawUtf8() {
            // The metadata part is a plain String — serializeBody(String) returns raw bytes.
            // The server returns the metadata field as-is in MixedUploadResult.
            HttpResponse<MixedUploadResult> response = client.post()
                    .path("/upload/mixed")
                    .body(FormData.of(
                            FormPart.file(
                                    "file",
                                    new ByteArrayInputStream("content".getBytes()),
                                    "test.txt"
                            ),
                            FormPart.json("metadata", "plain string metadata"),
                            FormPart.text("category", "testing")
                    ))
                    .send(MixedUploadResult.class);

            assertEquals(200, response.statusCode());
        }

        /**
         * A file part with an unrecognized extension causes
         * {@code URLConnection.guessContentTypeFromName()} to return {@code null}.
         * This exercises the {@code null != contentType ? contentType : DEFAULT_CONTENT_TYPE}
         * false branch in {@code MultipartBodyBuilder.guessContentType()}, and the server
         * confirms the part actually arrived with {@code Content-Type: application/octet-stream}.
         */
        @Test
        void filePart_unknownExtension_fallsBackToOctetStream() {
            HttpResponse<UploadResult> response = client.post()
                    .path("/upload")
                    .body(FormData.of(
                            FormPart.file(
                                    "file",
                                    new ByteArrayInputStream("data".getBytes()),
                                    "archive.zzz123unknown"   // unrecognized extension
                            )
                    ))
                    .send(UploadResult.class);

            assertEquals(200, response.statusCode());
            assertEquals("application/octet-stream", response.body().contentType());
        }

        /**
         * A file part with a recognized extension causes
         * {@code URLConnection.guessContentTypeFromName()} to return a non-null value.
         * This exercises the {@code null != contentType ? contentType : DEFAULT_CONTENT_TYPE}
         * true branch in {@code MultipartBodyBuilder.guessContentType()}, and the server
         * confirms the inferred {@code Content-Type} actually arrived on the wire.
         */
        @Test
        void filePart_knownExtension_contentTypeIsInferredFromFileName() {
            HttpResponse<UploadResult> response = client.post()
                    .path("/upload")
                    .body(FormData.of(
                            FormPart.file(
                                    "file",
                                    new ByteArrayInputStream("hello".getBytes()),
                                    "readme.txt"   // .txt → text/plain
                            )
                    ))
                    .send(UploadResult.class);

            assertEquals(200, response.statusCode());
            assertEquals("text/plain", response.body().contentType());
        }
    }

    // -------------------------------------------------------------------------
    // PATCH with form-urlencoded body
    // -------------------------------------------------------------------------

    /**
     * Tests that PATCH supports {@link FormUrlEncoded} bodies and exercises the
     * {@code buildFormUrlEncodedRequest} path in {@code PatchRequest}, including
     * the {@code acceptJson = false} short-circuit branch.
     * <p>
     * {@code PersonResource} expects JSON for PATCH and returns 415; the tests catch
     * the resulting {@link exception.HttpResponseException}
     * — the goal is to exercise the code path, not the server response.
     */
    @Nested
    class PatchFormUrlEncoded {
        @Test
        void patch_formUrlEncoded_typedSend_acceptJsonIsTrue() {
            // acceptJson=true: evaluates acceptJson && acceptGzip() normally
            assertThrows(
                    software.frisby.web.client.exception.HttpResponseException.class,
                    () -> client.patch()
                            .path("/persons/{id}", "id", "person-1")
                            .body(FormUrlEncoded.builder()
                                    .field("name", "Patched Form")
                                    .build())
                            .send(software.frisby.web.test.domain.Person.class)
            );
        }

        @Test
        void patch_formUrlEncoded_voidSend_acceptJsonIsFalse() {
            // acceptJson=false: short-circuits acceptJson && acceptGzip() immediately
            assertThrows(
                    software.frisby.web.client.exception.HttpResponseException.class,
                    () -> client.patch()
                            .path("/persons/{id}", "id", "person-1")
                            .body(FormUrlEncoded.builder()
                                    .field("name", "Void Patch Form")
                                    .build())
                            .send()
            );
        }
    }
}
