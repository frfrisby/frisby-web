package software.frisby.web.client;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import software.frisby.core.validation.NullValueException;
import software.frisby.web.serial.GenericType;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies null-argument validation on {@code compress()} and {@code body()} for
 * {@link PostSpec}, {@link PutSpec}, and {@link PatchSpec}, and that a
 * {@link BodyCompressor} that throws {@link IOException} is wrapped as
 * {@link UncheckedIOException}.
 * <p>
 * These checks live in the per-verb request classes rather than {@link RequestState},
 * so they are not covered by {@link RequestStateTest}.  No real HTTP server is needed —
 * all exceptions are thrown during request construction, before any network activity.
 */
class BodySpecValidationTest {
    private static final String NULL_BODY_MSG = "The 'body' value is invalid. The value must not be null.";
    private static final String NULL_COMPRESSOR_MSG = "The 'compressor' value is invalid. The value must not be null.";
    private static final String NULL_COMPRESSOR_RETURN_MSG = "The 'ContentCompressor' value is invalid.  compress() must not return null.";
    private static final String NULL_RESPONSE_TYPE_MSG = "The 'responseType' value is invalid. The value must not be null.";

    private static final ContentCompressor THROWING_COMPRESSOR =
            ContentCompressor.of("test", body -> {
                throw new IOException("simulated compression failure");
            });

    private static final ContentCompressor NULL_RETURNING_COMPRESSOR =
            ContentCompressor.of("test", body -> null);

    private static Client client;

    @BeforeAll
    static void setup() {
        client = Client.builder()
                .configuration(
                        ClientConfiguration.builder()
                                .uri(URI.create("https://nowhere.example.com"))
                                .connectTimeout(Duration.ofSeconds(10))
                                .readTimeout(Duration.ofSeconds(30))
                                .serializer(new TestJsonSerializer())
                                .build()
                )
                .build();
    }

    // -------------------------------------------------------------------------
    // GetSpec
    // -------------------------------------------------------------------------

    @Nested
    class Get {
        @Test
        void nullClassResponseType_throwsNullValueException() {
            NullValueException ex = assertThrows(
                    NullValueException.class,
                    () -> client.get().path("/any").send((Class<String>) null)
            );

            assertEquals(NULL_RESPONSE_TYPE_MSG, ex.getMessage());
        }

        @Test
        void nullGenericResponseType_throwsNullValueException() {
            NullValueException ex = assertThrows(
                    NullValueException.class,
                    () -> client.get().path("/any").send((GenericType<String>) null)
            );

            assertEquals(NULL_RESPONSE_TYPE_MSG, ex.getMessage());
        }

        @Test
        void nullClassResponseTypeAsync_throwsNullValueException() {
            NullValueException ex = assertThrows(
                    NullValueException.class,
                    () -> client.get().path("/any").sendAsync((Class<String>) null)
            );

            assertEquals(NULL_RESPONSE_TYPE_MSG, ex.getMessage());
        }

        @Test
        void nullGenericResponseTypeAsync_throwsNullValueException() {
            NullValueException ex = assertThrows(
                    NullValueException.class,
                    () -> client.get().path("/any").sendAsync((GenericType<String>) null)
            );

            assertEquals(NULL_RESPONSE_TYPE_MSG, ex.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // PostSpec
    // -------------------------------------------------------------------------

    @Nested
    class Post {
        @Test
        void nullJsonBody_throwsNullValueException() {
            NullValueException ex = assertThrows(
                    NullValueException.class,
                    () -> client.post().body((Object) null)
            );

            assertEquals(NULL_BODY_MSG, ex.getMessage());
        }

        @Test
        void nullFormData_throwsNullValueException() {
            NullValueException ex = assertThrows(
                    NullValueException.class,
                    () -> client.post().body((FormData) null)
            );

            assertEquals(NULL_BODY_MSG, ex.getMessage());
        }

        @Test
        void nullFormUrlEncoded_throwsNullValueException() {
            NullValueException ex = assertThrows(
                    NullValueException.class,
                    () -> client.post().body((FormUrlEncoded) null)
            );

            assertEquals(NULL_BODY_MSG, ex.getMessage());
        }

        @Test
        void nullCompressor_throwsNullValueException() {
            NullValueException ex = assertThrows(
                    NullValueException.class,
                    () -> client.post().compress((ContentCompressor) null)
            );

            assertEquals(NULL_COMPRESSOR_MSG, ex.getMessage());
        }

        @Test
        void nullClassResponseType_throwsNullValueException() {
            NullValueException ex = assertThrows(
                    NullValueException.class,
                    () -> client.post().path("/any").send((Class<String>) null)
            );

            assertEquals(NULL_RESPONSE_TYPE_MSG, ex.getMessage());
        }

        @Test
        void nullGenericResponseType_throwsNullValueException() {
            NullValueException ex = assertThrows(
                    NullValueException.class,
                    () -> client.post().path("/any").send((GenericType<String>) null)
            );

            assertEquals(NULL_RESPONSE_TYPE_MSG, ex.getMessage());
        }

        @Test
        void nullClassResponseTypeAsync_throwsNullValueException() {
            NullValueException ex = assertThrows(
                    NullValueException.class,
                    () -> client.post().path("/any").sendAsync((Class<String>) null)
            );

            assertEquals(NULL_RESPONSE_TYPE_MSG, ex.getMessage());
        }

        @Test
        void nullGenericResponseTypeAsync_throwsNullValueException() {
            NullValueException ex = assertThrows(
                    NullValueException.class,
                    () -> client.post().path("/any").sendAsync((GenericType<String>) null)
            );

            assertEquals(NULL_RESPONSE_TYPE_MSG, ex.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // PutSpec
    // -------------------------------------------------------------------------

    @Nested
    class Put {
        @Test
        void nullJsonBody_throwsNullValueException() {
            NullValueException ex = assertThrows(
                    NullValueException.class,
                    () -> client.put().body((Object) null)
            );

            assertEquals(NULL_BODY_MSG, ex.getMessage());
        }

        @Test
        void nullFormData_throwsNullValueException() {
            NullValueException ex = assertThrows(
                    NullValueException.class,
                    () -> client.put().body((FormData) null)
            );

            assertEquals(NULL_BODY_MSG, ex.getMessage());
        }

        @Test
        void nullFormUrlEncoded_throwsNullValueException() {
            NullValueException ex = assertThrows(
                    NullValueException.class,
                    () -> client.put().body((FormUrlEncoded) null)
            );

            assertEquals(NULL_BODY_MSG, ex.getMessage());
        }

        @Test
        void nullCompressor_throwsNullValueException() {
            NullValueException ex = assertThrows(
                    NullValueException.class,
                    () -> client.put().compress((ContentCompressor) null)
            );

            assertEquals(NULL_COMPRESSOR_MSG, ex.getMessage());
        }

        @Test
        void nullClassResponseType_throwsNullValueException() {
            NullValueException ex = assertThrows(
                    NullValueException.class,
                    () -> client.put().path("/any").send((Class<String>) null)
            );

            assertEquals(NULL_RESPONSE_TYPE_MSG, ex.getMessage());
        }

        @Test
        void nullGenericResponseType_throwsNullValueException() {
            NullValueException ex = assertThrows(
                    NullValueException.class,
                    () -> client.put().path("/any").send((GenericType<String>) null)
            );

            assertEquals(NULL_RESPONSE_TYPE_MSG, ex.getMessage());
        }

        @Test
        void nullClassResponseTypeAsync_throwsNullValueException() {
            NullValueException ex = assertThrows(
                    NullValueException.class,
                    () -> client.put().path("/any").sendAsync((Class<String>) null)
            );

            assertEquals(NULL_RESPONSE_TYPE_MSG, ex.getMessage());
        }

        @Test
        void nullGenericResponseTypeAsync_throwsNullValueException() {
            NullValueException ex = assertThrows(
                    NullValueException.class,
                    () -> client.put().path("/any").sendAsync((GenericType<String>) null)
            );

            assertEquals(NULL_RESPONSE_TYPE_MSG, ex.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // PatchSpec
    // -------------------------------------------------------------------------

    @Nested
    class Patch {
        @Test
        void nullJsonBody_throwsNullValueException() {
            NullValueException ex = assertThrows(
                    NullValueException.class,
                    () -> client.patch().body((Object) null)
            );

            assertEquals(NULL_BODY_MSG, ex.getMessage());
        }

        @Test
        void nullFormUrlEncoded_throwsNullValueException() {
            NullValueException ex = assertThrows(
                    NullValueException.class,
                    () -> client.patch().body(null)
            );

            assertEquals(NULL_BODY_MSG, ex.getMessage());
        }

        @Test
        void nullCompressor_throwsNullValueException() {
            NullValueException ex = assertThrows(
                    NullValueException.class,
                    () -> client.patch().compress((ContentCompressor) null)
            );

            assertEquals(NULL_COMPRESSOR_MSG, ex.getMessage());
        }

        @Test
        void nullClassResponseType_throwsNullValueException() {
            NullValueException ex = assertThrows(
                    NullValueException.class,
                    () -> client.patch().path("/any").send((Class<String>) null)
            );

            assertEquals(NULL_RESPONSE_TYPE_MSG, ex.getMessage());
        }

        @Test
        void nullGenericResponseType_throwsNullValueException() {
            NullValueException ex = assertThrows(
                    NullValueException.class,
                    () -> client.patch().path("/any").send((GenericType<String>) null)
            );

            assertEquals(NULL_RESPONSE_TYPE_MSG, ex.getMessage());
        }

        @Test
        void nullClassResponseTypeAsync_throwsNullValueException() {
            NullValueException ex = assertThrows(
                    NullValueException.class,
                    () -> client.patch().path("/any").sendAsync((Class<String>) null)
            );

            assertEquals(NULL_RESPONSE_TYPE_MSG, ex.getMessage());
        }

        @Test
        void nullGenericResponseTypeAsync_throwsNullValueException() {
            NullValueException ex = assertThrows(
                    NullValueException.class,
                    () -> client.patch().path("/any").sendAsync((GenericType<String>) null)
            );

            assertEquals(NULL_RESPONSE_TYPE_MSG, ex.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Compressor IOException wrapping — POST / PUT / PATCH
    // -------------------------------------------------------------------------

    /**
     * Verifies that a {@link BodyCompressor} throwing checked {@link IOException}
     * is wrapped as {@link UncheckedIOException} in {@code buildJsonRequest()} for
     * each body-bearing verb.  The exception is thrown during request construction
     * before any network activity, so no real server is needed.
     */
    @Nested
    class CompressorIOException {
        @Test
        void post_compressorThrowsIOException_wrapsAsUncheckedIOException() {
            assertThrows(
                    UncheckedIOException.class,
                    () -> client.post()
                            .path("/any")
                            .compress(THROWING_COMPRESSOR)
                            .body("payload")
                            .send()
            );
        }

        @Test
        void put_compressorThrowsIOException_wrapsAsUncheckedIOException() {
            assertThrows(
                    UncheckedIOException.class,
                    () -> client.put()
                            .path("/any")
                            .compress(THROWING_COMPRESSOR)
                            .body("payload")
                            .send()
            );
        }

        @Test
        void patch_compressorThrowsIOException_wrapsAsUncheckedIOException() {
            assertThrows(
                    UncheckedIOException.class,
                    () -> client.patch()
                            .path("/any")
                            .compress(THROWING_COMPRESSOR)
                            .body("payload")
                            .send()
            );
        }
    }

    // -------------------------------------------------------------------------
    // Compressor null return — POST / PUT / PATCH
    // -------------------------------------------------------------------------

    /**
     * Verifies that a {@link ContentCompressor} returning {@code null} from
     * {@code compress()} throws {@link IllegalStateException} with a clear message
     * rather than a silent {@link NullPointerException} inside
     * {@code HttpRequest.BodyPublishers.ofByteArray()}.
     */
    @Nested
    class CompressorReturnsNull {
        @Test
        void post_compressorReturnsNull_throwsIllegalStateException() {
            IllegalStateException ex = assertThrows(
                    IllegalStateException.class,
                    () -> client.post()
                            .path("/any")
                            .compress(NULL_RETURNING_COMPRESSOR)
                            .body("payload")
                            .send()
            );

            assertEquals(NULL_COMPRESSOR_RETURN_MSG, ex.getMessage());
        }

        @Test
        void put_compressorReturnsNull_throwsIllegalStateException() {
            IllegalStateException ex = assertThrows(
                    IllegalStateException.class,
                    () -> client.put()
                            .path("/any")
                            .compress(NULL_RETURNING_COMPRESSOR)
                            .body("payload")
                            .send()
            );

            assertEquals(NULL_COMPRESSOR_RETURN_MSG, ex.getMessage());
        }

        @Test
        void patch_compressorReturnsNull_throwsIllegalStateException() {
            IllegalStateException ex = assertThrows(
                    IllegalStateException.class,
                    () -> client.patch()
                            .path("/any")
                            .compress(NULL_RETURNING_COMPRESSOR)
                            .body("payload")
                            .send()
            );

            assertEquals(NULL_COMPRESSOR_RETURN_MSG, ex.getMessage());
        }
    }
}
