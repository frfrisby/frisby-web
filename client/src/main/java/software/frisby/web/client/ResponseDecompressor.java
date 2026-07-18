package software.frisby.web.client;

import java.io.IOException;
import java.io.InputStream;

/**
 * Wraps a compressed response body stream in a decompressing stream for a
 * caller-supplied algorithm.
 * <p>
 * Used with {@link ContentDecompressor#of(String, ResponseDecompressor)} to create a
 * named decompressor for a custom encoding:
 *
 * <pre>{@code
 * ContentDecompressor brotli = ContentDecompressor.of("br", stream -> new BrotliInputStream(stream));
 * }</pre>
 *
 * @see ContentDecompressor
 */
@FunctionalInterface
public interface ResponseDecompressor {
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

