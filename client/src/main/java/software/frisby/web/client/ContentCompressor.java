package software.frisby.web.client;

import software.frisby.core.validation.Strings;
import software.frisby.core.validation.Values;

import java.io.IOException;

/**
 * Bundles a {@code Content-Encoding} token with a corresponding compression
 * implementation for HTTP request bodies.
 * <p>
 * Passed to {@link PostSpec#compress(ContentCompressor)},
 * {@link PutSpec#compress(ContentCompressor)}, and
 * {@link PatchSpec#compress(ContentCompressor)}.  Use the
 * {@linkplain PostSpec#compress() no-arg convenience methods} for the built-in
 * {@code gzip} algorithm.
 * <p>
 * Create instances via the {@link #of(String, BodyCompressor)} factory:
 *
 * <pre>{@code
 * // Anonymous / lambda style (most common):
 * ContentCompressor brotli = ContentCompressor.of("br", bytes -> myBrotliLib.compress(bytes));
 *
 * // Named reusable implementation:
 * public class BrotliCompressor implements ContentCompressor {
 *     public String encoding() { return "br"; }
 *     public byte[] compress(byte[] body) throws IOException { ... }
 * }
 * }</pre>
 *
 * @see PostSpec#compress()
 * @see PostSpec#compress(ContentCompressor)
 */
public interface ContentCompressor {
    /**
     * Creates a {@link ContentCompressor} that binds {@code encoding} to
     * {@code compressor}.
     *
     * @param encoding   The HTTP {@code Content-Encoding} token (e.g. {@code "br"}).
     * @param compressor The compression function.
     * @return A new {@link ContentCompressor}; never {@code null}.
     * @throws software.frisby.core.validation.NullValueException  if {@code encoding} or
     *                                                             {@code compressor} is null.
     * @throws software.frisby.core.validation.BlankValueException if {@code encoding} is blank.
     */
    static ContentCompressor of(String encoding, BodyCompressor compressor) {
        String validEncoding = Strings.notBlank("encoding", encoding);
        BodyCompressor validCompressor = Values.notNull("compressor", compressor);

        return new ContentCompressor() {
            @Override
            public String encoding() {
                return validEncoding;
            }

            @Override
            public byte[] compress(byte[] body) throws IOException {
                return validCompressor.compress(body);
            }
        };
    }

    /**
     * Returns the HTTP {@code Content-Encoding} token for this algorithm
     * (e.g. {@code "gzip"}).
     *
     * @return The encoding token; never blank.
     */
    String encoding();

    /**
     * Compresses {@code body} and returns the compressed bytes.
     *
     * @param body The uncompressed request body bytes.
     * @return The compressed bytes.
     * @throws IOException if compression fails.
     */
    byte[] compress(byte[] body) throws IOException;
}

