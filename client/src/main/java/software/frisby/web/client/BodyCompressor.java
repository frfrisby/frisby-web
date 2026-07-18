package software.frisby.web.client;

import java.io.IOException;

/**
 * Compresses a request body byte array using a caller-supplied algorithm.
 * <p>
 * Used with {@link ContentCompressor#of(String, BodyCompressor)} to create a
 * named compressor for a custom encoding:
 *
 * <pre>{@code
 * ContentCompressor brotli = ContentCompressor.of("br", bytes -> myBrotliLib.compress(bytes));
 * }</pre>
 *
 * @see ContentCompressor
 */
@FunctionalInterface
public interface BodyCompressor {
    /**
     * Compresses {@code body} and returns the compressed bytes.
     *
     * @param body The uncompressed request body bytes.
     * @return The compressed bytes.
     * @throws IOException if compression fails.
     */
    byte[] compress(byte[] body) throws IOException;
}

