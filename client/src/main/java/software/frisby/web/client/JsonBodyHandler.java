package software.frisby.web.client;

import software.frisby.web.client.exception.HttpResponseException;
import software.frisby.web.client.exception.ResponseDeserializationException;
import software.frisby.web.client.exception.UnsupportedContentEncodingException;
import software.frisby.web.serial.GenericType;
import software.frisby.web.serial.JsonSerializer;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * An {@link HttpResponse.BodyHandler} for JSON responses.
 * <p>
 * Handles both success and error paths:
 * <ul>
 *   <li>Success ({@code 2xx / 3xx}): deserializes the body to {@code T}, decompressing
 *       the response via a registered {@link ContentDecompressor} when the server sets
 *       {@code Content-Encoding}.</li>
 *   <li>Error ({@code 4xx / 5xx}): reads the body as a plain string and throws the
 *       appropriate {@link HttpResponseException}
 *       subclass.</li>
 * </ul>
 * <p>
 * If the server responds with a {@code Content-Encoding} value that has no registered
 * decompressor, {@link UnsupportedContentEncodingException} is thrown immediately
 * from {@link #apply(HttpResponse.ResponseInfo)}.
 */
final class JsonBodyHandler<T> implements HttpResponse.BodyHandler<T> {
    private static final String CONTENT_ENCODING = "content-encoding";

    private final Deserializer<T> deserializer;
    private final String method;
    private final URI uri;
    private final List<ContentDecompressor> decompressors;

    /**
     * Set during the success deserialization mapping function; {@code null} for error
     * responses, empty bodies, and {@code Void} return types.
     * <p>
     * Written by the body-subscriber thread before the {@code CompletableFuture} that
     * wraps this handler completes, so it is always visible to the thread that reads it
     * after {@code httpClient.send()} returns or after {@code handle()} executes.
     */
    private byte[] snapshot;

    private JsonBodyHandler(Deserializer<T> deserializer,
                            String method,
                            URI uri,
                            List<ContentDecompressor> decompressors) {
        this.deserializer = deserializer;
        this.method = method;
        this.uri = uri;
        this.decompressors = decompressors;
    }

    static <T> JsonBodyHandler<T> of(JsonSerializer serializer,
                                     Class<T> type,
                                     String method,
                                     URI uri,
                                     List<ContentDecompressor> decompressors) {
        return new JsonBodyHandler<>(
                new ClassDeserializer<>(serializer, type),
                method,
                uri,
                decompressors
        );
    }

    static <T> JsonBodyHandler<T> of(JsonSerializer serializer,
                                     GenericType<T> type,
                                     String method,
                                     URI uri,
                                     List<ContentDecompressor> decompressors) {
        return new JsonBodyHandler<>(
                new GenericDeserializer<>(serializer, type),
                method,
                uri,
                decompressors
        );
    }

    /**
     * Returns the raw decompressed response body bytes captured during deserialization,
     * or {@code null} if the response was an error, had no body, or was a
     * {@code Void}/{@code void} response type.
     */
    byte[] snapshot() {
        return snapshot;
    }

    @Override
    @SuppressWarnings("java:S3776") // cognitive complexity is inherent to the BodySubscribers.mapping() lambda nesting — the logic itself is linear and easy to follow
    public HttpResponse.BodySubscriber<T> apply(HttpResponse.ResponseInfo responseInfo) {
        if (ExceptionFactory.isError(responseInfo.statusCode())) {
            return HttpResponse.BodySubscribers.mapping(
                    HttpResponse.BodySubscribers.ofString(StandardCharsets.UTF_8),
                    body -> {
                        String errorBody = body.isBlank() ? null : body;

                        throw ExceptionFactory.create(
                                errorBody,
                                method,
                                uri,
                                responseInfo.statusCode(),
                                responseInfo.headers()
                        );
                    }
            );
        }

        String contentEncoding = responseInfo.headers()
                .firstValue(CONTENT_ENCODING)
                .orElse(null);

        if (null != contentEncoding && !contentEncoding.isBlank()) {
            ContentDecompressor decompressor = findDecompressor(contentEncoding);
            if (null == decompressor) {
                throw new UnsupportedContentEncodingException(contentEncoding);
            }

            return HttpResponse.BodySubscribers.mapping(
                    HttpResponse.BodySubscribers.ofByteArray(),
                    bytes -> {
                        try {
                            InputStream decompressed = decompressor.decompress(
                                    new ByteArrayInputStream(bytes)
                            );

                            if (null == decompressed) {
                                throw new IllegalStateException(
                                        "The 'ContentDecompressor' value is invalid." +
                                                "  decompress() must not return null."
                                );
                            }

                            byte[] decompressedBytes = decompressed.readAllBytes();

                            if (decompressedBytes.length > 0) {
                                snapshot = decompressedBytes;
                            }

                            return deserializer.deserialize(decompressedBytes);
                        } catch (IOException ex) {
                            throw new UncheckedIOException(ex);
                        }
                    }
            );
        }

        return HttpResponse.BodySubscribers.mapping(
                HttpResponse.BodySubscribers.ofByteArray(),
                content -> {
                    if (content.length > 0) {
                        snapshot = content;
                    }

                    return deserializer.deserialize(content);
                }
        );
    }

    private ContentDecompressor findDecompressor(String contentEncoding) {
        for (ContentDecompressor d : decompressors) {
            if (d.encoding().equalsIgnoreCase(contentEncoding)) {
                return d;
            }
        }

        return null;
    }

    @FunctionalInterface
    interface Deserializer<T> {
        T deserialize(byte[] content);
    }

    private record ClassDeserializer<T>(JsonSerializer serializer, Class<T> type) implements Deserializer<T> {
        @Override
        public T deserialize(byte[] content) {
            if (0 == content.length) {
                return null;
            }

            if (Void.class.equals(type) || void.class.equals(type)) {
                return null;
            }

            if (String.class.equals(type) && content[0] != '"') {
                return type.cast(new String(content, StandardCharsets.UTF_8));
            }

            try {
                return serializer.deserialize(content, type);
            } catch (Exception ex) {
                throw new ResponseDeserializationException(
                        "The 'response body' value is invalid." +
                                "  Failed to deserialize the response to '" + type.getName() + "'.",
                        ex,
                        type.getName(),
                        new String(content, StandardCharsets.UTF_8)
                );
            }
        }
    }

    private record GenericDeserializer<T>(JsonSerializer serializer, GenericType<T> type) implements Deserializer<T> {
        @Override
        public T deserialize(byte[] content) {
            if (0 == content.length) {
                return null;
            }

            try {
                return serializer.deserialize(content, type);
            } catch (Exception ex) {
                throw new ResponseDeserializationException(
                        "The 'response body' value is invalid." +
                                "  Failed to deserialize the response to '" + type.type().getTypeName() + "'.",
                        ex,
                        type.type().getTypeName(),
                        new String(content, StandardCharsets.UTF_8)
                );
            }
        }
    }
}
