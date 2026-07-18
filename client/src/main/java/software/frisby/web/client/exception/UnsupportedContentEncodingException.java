package software.frisby.web.client.exception;

import software.frisby.web.client.ClientConfigurationBuilder;
import software.frisby.web.client.ContentDecompressor;

import java.io.Serial;

/**
 * Thrown when an HTTP response carries a {@code Content-Encoding} header whose value
 * has no registered {@link ContentDecompressor}.
 * <p>
 * This is a fail-fast exception — attempting to deserialize compressed bytes as
 * plain JSON would produce a cryptic parse failure.  Register a matching decompressor
 * via {@link ClientConfigurationBuilder#decompress(ContentDecompressor)}
 * before making requests to servers that use this encoding.
 *
 * <pre>{@code
 * // If a server responds with Content-Encoding: br and no brotli decompressor is registered:
 * try {
 *     client.get().path("/data").send(Data.class);
 * } catch (UnsupportedContentEncodingException e) {
 *     // e.contentEncoding() == "br"
 * }
 * }</pre>
 */
public final class UnsupportedContentEncodingException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

    private static final String MESSAGE_PREFIX = "The 'Content-Encoding' value of '";
    private static final String MESSAGE_SUFFIX =
            "' is invalid.  No registered decompressor handles this encoding.";

    /**
     * The unrecognised {@code Content-Encoding} token from the server response.
     */
    private final String contentEncoding;

    /**
     * Creates an exception for the given unrecognized encoding token.
     *
     * @param contentEncoding The {@code Content-Encoding} value returned by the server.
     */
    public UnsupportedContentEncodingException(String contentEncoding) {
        super(MESSAGE_PREFIX + contentEncoding + MESSAGE_SUFFIX);

        this.contentEncoding = contentEncoding;
    }

    /**
     * Returns the unrecognised {@code Content-Encoding} token from the server response.
     *
     * @return The encoding token (e.g. {@code "br"}, {@code "zstd"}).
     */
    public String contentEncoding() {
        return contentEncoding;
    }
}

