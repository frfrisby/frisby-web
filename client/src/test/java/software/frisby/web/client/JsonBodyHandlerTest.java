package software.frisby.web.client;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import software.frisby.web.client.exception.HttpResponseException;
import software.frisby.web.client.exception.ResponseDeserializationException;
import software.frisby.web.client.exception.UnsupportedContentEncodingException;
import software.frisby.web.serial.GenericType;
import software.frisby.web.serial.JsonSerializer;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.SubmissionPublisher;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link JsonBodyHandler} and its inner {@code ClassDeserializer},
 * {@code GenericDeserializer}, and error-handler lambda.
 * <p>
 * All tests drive the handler through the standard {@link HttpResponse.BodySubscriber}
 * API using a {@link SubmissionPublisher} to deliver bytes — the same path the JDK
 * {@code HttpClient} uses in production.
 */
class JsonBodyHandlerTest {
    private static final URI TEST_URI = URI.create("http://test.example.com/resource");
    private static final String GET = "GET";

    private static final String DESER_EXCEPTION_MSG =
            "The 'response body' value is invalid.  Failed to deserialize the response to 'java.lang.String'.";
    private static final String DESER_GENERIC_MSG_PREFIX =
            "The 'response body' value is invalid.  Failed to deserialize the response to '";

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static HttpResponse.ResponseInfo responseInfo(int statusCode) {
        return responseInfo(statusCode, Map.of());
    }

    private static HttpResponse.ResponseInfo responseInfo(int statusCode, Map<String, List<String>> headers) {
        return new HttpResponse.ResponseInfo() {
            @Override
            public int statusCode() {
                return statusCode;
            }

            @Override
            public HttpHeaders headers() {
                return HttpHeaders.of(headers, (n, v) -> true);
            }

            @Override
            public HttpClient.Version version() {
                return HttpClient.Version.HTTP_1_1;
            }
        };
    }

    /**
     * Feeds {@code bytes} to a body subscriber and returns the result.
     * Passes an empty publication when {@code bytes} is zero-length so the
     * underlying {@code ofByteArray()} subscriber produces a zero-length array.
     */
    private static <T> T subscribe(HttpResponse.BodySubscriber<T> subscriber, byte[] bytes)
            throws Exception {
        SubmissionPublisher<List<ByteBuffer>> publisher = new SubmissionPublisher<>();

        publisher.subscribe(subscriber);

        if (bytes.length > 0) {
            publisher.submit(List.of(ByteBuffer.wrap(bytes)));
        }

        publisher.close();

        return subscriber.getBody().toCompletableFuture().get();
    }

    /**
     * A {@link JsonSerializer} whose {@code deserialize} methods always throw.
     */
    private static JsonSerializer failingDeserializer() {
        return new JsonSerializer() {
            @Override
            public byte[] serialize(Object value) {
                throw new UnsupportedOperationException();
            }

            @Override
            public <T> T deserialize(byte[] json, Class<T> type) {
                throw new RuntimeException("intentional failure");
            }

            @Override
            public <T> T deserialize(byte[] json, GenericType<T> type) {
                throw new RuntimeException("intentional failure");
            }
        };
    }

    // -------------------------------------------------------------------------
    // ClassDeserializer
    // -------------------------------------------------------------------------

    @Nested
    class ClassDeserializerTests {
        @Test
        void emptyContent_returnsNull() throws Exception {
            JsonBodyHandler<String> handler = JsonBodyHandler.of(
                    new TestJsonSerializer(),
                    String.class,
                    GET,
                    TEST_URI
                    , java.util.List.of()
            );
            HttpResponse.BodySubscriber<String> subscriber = handler.apply(responseInfo(200));

            String result = subscribe(subscriber, new byte[0]);

            assertNull(result);
        }

        @Test
        void voidClass_returnsNull() throws Exception {
            JsonBodyHandler<Void> handler = JsonBodyHandler.of(
                    new TestJsonSerializer(),
                    Void.class,
                    GET,
                    TEST_URI
                    , java.util.List.of()
            );
            HttpResponse.BodySubscriber<Void> subscriber = handler.apply(responseInfo(200));

            Void result = subscribe(subscriber, "any content".getBytes(StandardCharsets.UTF_8));

            assertNull(result);
        }

        @Test
        void voidPrimitiveClass_returnsNull() throws Exception {
            // void.class is distinct from Void.class; exercises the second operand of
            // the `Void.class.equals(type) || void.class.equals(type)` branch
            @SuppressWarnings("unchecked")
            JsonBodyHandler<Void> handler = JsonBodyHandler.of(
                    new TestJsonSerializer(),
                    (Class<Void>) void.class,
                    GET,
                    TEST_URI
                    , java.util.List.of()
            );
            HttpResponse.BodySubscriber<Void> subscriber = handler.apply(responseInfo(200));

            Void result = subscribe(subscriber, "any content".getBytes(StandardCharsets.UTF_8));

            assertNull(result);
        }

        @Test
        void stringClass_rawTextContent_returnsStringDirectly() throws Exception {
            // Content does not start with '"' → ClassDeserializer returns the raw
            // UTF-8 string without invoking the serializer.
            JsonBodyHandler<String> handler = JsonBodyHandler.of(
                    new TestJsonSerializer(),   // would throw if called
                    String.class,
                    GET,
                    TEST_URI
                    , java.util.List.of()
            );
            HttpResponse.BodySubscriber<String> subscriber = handler.apply(responseInfo(200));

            String result = subscribe(subscriber, "plain text".getBytes(StandardCharsets.UTF_8));

            assertEquals("plain text", result);
        }

        @Test
        void serializerThrows_throwsResponseDeserializationException() {
            JsonBodyHandler<String> handler = JsonBodyHandler.of(
                    failingDeserializer(),
                    String.class,
                    GET,
                    TEST_URI
                    , java.util.List.of()
            );
            HttpResponse.BodySubscriber<String> subscriber = handler.apply(responseInfo(200));

            ExecutionException ex = assertThrows(
                    ExecutionException.class,
                    () -> subscribe(subscriber, "\"hello\"".getBytes(StandardCharsets.UTF_8))
            );

            assertInstanceOf(ResponseDeserializationException.class, ex.getCause());

            ResponseDeserializationException rde = (ResponseDeserializationException) ex.getCause();

            assertEquals(DESER_EXCEPTION_MSG, rde.getMessage());
            assertEquals(String.class.getName(), rde.targetType());
            assertTrue(rde.rawBody().isPresent());
        }
    }

    // -------------------------------------------------------------------------
    // GenericDeserializer
    // -------------------------------------------------------------------------

    @Nested
    class GenericDeserializerTests {
        @Test
        void emptyContent_returnsNull() throws Exception {
            JsonBodyHandler<List<String>> handler = JsonBodyHandler.of(
                    new TestJsonSerializer(),
                    new GenericType<>() {
                    },
                    GET,
                    TEST_URI
                    , java.util.List.of()
            );
            HttpResponse.BodySubscriber<List<String>> subscriber = handler.apply(responseInfo(200));

            List<String> result = subscribe(subscriber, new byte[0]);

            assertNull(result);
        }

        @Test
        void serializerThrows_throwsResponseDeserializationException() {
            GenericType<List<String>> genericType = new GenericType<>() {
            };

            JsonBodyHandler<List<String>> handler = JsonBodyHandler.of(
                    failingDeserializer(),
                    genericType,
                    GET,
                    TEST_URI
                    , java.util.List.of()
            );
            HttpResponse.BodySubscriber<List<String>> subscriber = handler.apply(responseInfo(200));

            ExecutionException ex = assertThrows(
                    ExecutionException.class,
                    () -> subscribe(subscriber, "[\"a\",\"b\"]".getBytes(StandardCharsets.UTF_8))
            );

            assertInstanceOf(ResponseDeserializationException.class, ex.getCause());

            ResponseDeserializationException rde = (ResponseDeserializationException) ex.getCause();

            assertTrue(rde.getMessage().startsWith(DESER_GENERIC_MSG_PREFIX));
            assertEquals(genericType.type().getTypeName(), rde.targetType());
            assertTrue(rde.rawBody().isPresent());
        }
    }

    // -------------------------------------------------------------------------
    // Error-handler lambda (4xx / 5xx path in apply())
    // -------------------------------------------------------------------------

    @Nested
    class ErrorHandlerLambda {
        @Test
        void blankErrorBody_exceptionBodyIsEmpty() {
            JsonBodyHandler<String> handler = JsonBodyHandler.of(
                    new TestJsonSerializer(),
                    String.class,
                    GET,
                    TEST_URI
                    , java.util.List.of()
            );
            HttpResponse.BodySubscriber<String> subscriber = handler.apply(responseInfo(400));

            // Blank body string → errorBody = null → body() is empty on the exception
            ExecutionException ex = assertThrows(
                    ExecutionException.class,
                    () -> subscribe(subscriber, "   ".getBytes(StandardCharsets.UTF_8))
            );

            assertInstanceOf(HttpResponseException.class, ex.getCause());

            HttpResponseException hre = (HttpResponseException) ex.getCause();

            assertEquals(400, hre.statusCode());
            assertTrue(hre.body().isEmpty());
        }
    }

    // -------------------------------------------------------------------------
    // Content-encoding path in apply()
    // -------------------------------------------------------------------------

    @Nested
    class ContentEncodingTests {
        private static final String UNSUPPORTED_ENCODING_MSG =
                "The 'Content-Encoding' value of 'br' is invalid.  No registered decompressor handles this encoding.";

        private static final String NULL_DECOMPRESSOR_MSG =
                "The 'ContentDecompressor' value is invalid.  decompress() must not return null.";

        private static final ContentDecompressor THROWING_DECOMPRESSOR =
                ContentDecompressor.of("gzip", stream -> {
                    throw new IOException("simulated decompression failure");
                });

        private static final ContentDecompressor NULL_RETURNING_DECOMPRESSOR =
                ContentDecompressor.of("gzip", stream -> null);

        private static final ContentDecompressor EMPTY_DECOMPRESSOR =
                ContentDecompressor.of("gzip", stream -> InputStream.nullInputStream());

        private static final ContentDecompressor NON_MATCHING_DECOMPRESSOR =
                ContentDecompressor.of("br", stream -> InputStream.nullInputStream());

        @Test
        void unregisteredContentEncoding_throwsUnsupportedContentEncodingException() {
            JsonBodyHandler<String> handler = JsonBodyHandler.of(
                    new TestJsonSerializer(),
                    String.class,
                    GET,
                    TEST_URI,
                    List.of()
            );

            UnsupportedContentEncodingException ex = assertThrows(
                    UnsupportedContentEncodingException.class,
                    () -> handler.apply(responseInfo(200, Map.of("content-encoding", List.of("br"))))
            );

            assertEquals("br", ex.contentEncoding());
            assertEquals(UNSUPPORTED_ENCODING_MSG, ex.getMessage());
        }

        @Test
        void blankContentEncoding_treatedAsNoEncoding() throws Exception {
            JsonBodyHandler<String> handler = JsonBodyHandler.of(
                    new TestJsonSerializer(),
                    String.class,
                    GET,
                    TEST_URI,
                    List.of()
            );

            HttpResponse.BodySubscriber<String> subscriber = handler.apply(
                    responseInfo(200, Map.of("content-encoding", List.of("  ")))
            );

            String result = subscribe(subscriber, "plain text".getBytes(StandardCharsets.UTF_8));

            assertEquals("plain text", result);
        }

        @Test
        void decompressorThrowsIOException_throwsUncheckedIOException() {
            JsonBodyHandler<String> handler = JsonBodyHandler.of(
                    new TestJsonSerializer(),
                    String.class,
                    GET,
                    TEST_URI,
                    List.of(THROWING_DECOMPRESSOR)
            );

            HttpResponse.BodySubscriber<String> subscriber = handler.apply(
                    responseInfo(200, Map.of("content-encoding", List.of("gzip")))
            );

            ExecutionException ex = assertThrows(
                    ExecutionException.class,
                    () -> subscribe(subscriber, new byte[]{1, 2, 3})
            );

            assertInstanceOf(UncheckedIOException.class, ex.getCause());
            assertEquals("simulated decompression failure", ex.getCause().getCause().getMessage());
        }

        @Test
        void decompressorReturnsNull_throwsIllegalStateException() {
            JsonBodyHandler<String> handler = JsonBodyHandler.of(
                    new TestJsonSerializer(),
                    String.class,
                    GET,
                    TEST_URI,
                    List.of(NULL_RETURNING_DECOMPRESSOR)
            );

            HttpResponse.BodySubscriber<String> subscriber = handler.apply(
                    responseInfo(200, Map.of("content-encoding", List.of("gzip")))
            );

            ExecutionException ex = assertThrows(
                    ExecutionException.class,
                    () -> subscribe(subscriber, new byte[]{1, 2, 3})
            );

            assertInstanceOf(IllegalStateException.class, ex.getCause());
            assertEquals(NULL_DECOMPRESSOR_MSG, ex.getCause().getMessage());
        }

        @Test
        void decompressedEmptyBody_returnsNullAndSnapshotIsNull() throws Exception {
            JsonBodyHandler<String> handler = JsonBodyHandler.of(
                    new TestJsonSerializer(),
                    String.class,
                    GET,
                    TEST_URI,
                    List.of(EMPTY_DECOMPRESSOR)
            );

            HttpResponse.BodySubscriber<String> subscriber = handler.apply(
                    responseInfo(200, Map.of("content-encoding", List.of("gzip")))
            );

            String result = subscribe(subscriber, new byte[]{1, 2, 3});

            assertNull(result);
            assertNull(handler.snapshot());
        }

        @Test
        void nonMatchingDecompressorSkipped_matchingDecompressorUsed() throws Exception {
            JsonBodyHandler<String> handler = JsonBodyHandler.of(
                    new TestJsonSerializer(),
                    String.class,
                    GET,
                    TEST_URI,
                    List.of(NON_MATCHING_DECOMPRESSOR, EMPTY_DECOMPRESSOR)
            );

            HttpResponse.BodySubscriber<String> subscriber = handler.apply(
                    responseInfo(200, Map.of("content-encoding", List.of("gzip")))
            );

            String result = subscribe(subscriber, new byte[]{1, 2, 3});

            assertNull(result);
        }
    }
}

