package software.frisby.web.client;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPOutputStream;

/**
 * Utility for compressing data using the GZIP format.
 */
final class GZip {
    private GZip() {
    }

    /**
     * Compresses {@code data} using GZIP and returns the compressed bytes.
     *
     * @param data The bytes to compress.
     * @return The GZIP-compressed bytes.
     * @throws IOException if compression fails.
     */
    static byte[] compress(byte[] data) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(data.length);

        try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
            gzip.write(data);
        }

        return out.toByteArray();
    }
}
