package software.frisby.web.client;

import software.frisby.core.validation.Strings;
import software.frisby.core.validation.Values;

import java.io.IOException;
import java.io.InputStream;

/**
 * Bundles a {@code Content-Encoding} token with a corresponding decompression
 * implementation for HTTP response bodies.
 * <p>
 * Registered on a client via {@link ClientConfigurationBuilder#decompress(ContentDecompressor)}.
 * Calls are additive — multiple decompressors may be registered; the client picks the
 * matching one at response time based on the server's {@code Content-Encoding} header.
 * The {@code Accept-Encoding} request header is derived automatically from the set of
 * registered decompressors.
 * <p>
 * Use the {@linkplain ClientConfigurationBuilder#decompress() no-arg convenience method} for
 * the built-in {@code gzip} algorithm.  Create custom instances via
 * {@link #of(String, ResponseDecompressor)}:
 *
 * <pre>{@code
 * // Anonymous / lambda style (most common):
 * ContentDecompressor brotli = ContentDecompressor.of("br", stream -> new BrotliInputStream(stream));
 *
 * // Named reusable implementation:
 * public class BrotliDecompressor implements ContentDecompressor {
 *     public String encoding() { return "br"; }
 *     public InputStream decompress(InputStream compressed) throws IOException { ... }
 * }
 *
 * // ClientConfiguration example:
 * ClientConfiguration.builder()
 *         .uri(serviceUri)
 *         .serializer(serializer)
 *         .decompress()                                                       // gzip
 *         .decompress(ContentDecompressor.of("br", BrotliInputStream::new))  // brotli
 *         .build();
 * }</pre>
 *
 * @see ClientConfigurationBuilder#decompress()
 * @see ClientConfigurationBuilder#decompress(ContentDecompressor)
 */
public interface ContentDecompressor {
    /**
     * Creates a {@link ContentDecompressor} that binds {@code encoding} to
     * {@code decompressor}.
     *
     * @param encoding     The HTTP {@code Content-Encoding} token (e.g. {@code "br"}).
     * @param decompressor The decompression function.
     * @return A new {@link ContentDecompressor}; never {@code null}.
     * @throws software.frisby.core.validation.NullValueException  if {@code encoding} or
     *                                                             {@code decompressor} is null.
     * @throws software.frisby.core.validation.BlankValueException if {@code encoding} is blank.
     */
    static ContentDecompressor of(String encoding, ResponseDecompressor decompressor) {
        String validEncoding = Strings.notBlank("encoding", encoding);
        ResponseDecompressor validDecompressor = Values.notNull("decompressor", decompressor);

        return new ContentDecompressor() {
            @Override
            public String encoding() {
                return validEncoding;
            }

            @Override
            public InputStream decompress(InputStream compressed) throws IOException {
                return validDecompressor.decompress(compressed);
            }
        };
    }

    /**
     * Returns the HTTP {@code Content-Encoding} token this decompressor handles
     * (e.g. {@code "gzip"}).
     *
     * @return The encoding token; never blank.
     */
    String encoding();

    /**
     * Wraps {@code compressed} in a decompressing stream.
     * <p>
     * The caller is responsible for closing the returned stream.
     *
     * @param compressed The raw compressed response body stream.
     * @return A stream that decompresses bytes as they are read.
     * @throws IOException if the decompressor stream cannot be initialized.
     */
    InputStream decompress(InputStream compressed) throws IOException;
}

