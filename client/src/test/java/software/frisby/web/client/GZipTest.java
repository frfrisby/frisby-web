package software.frisby.web.client;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/**
 * Unit tests for {@link GZip}.
 */
class GZipTest {

    @Nested
    class Compress {
        @Test
        void validData_compressesAndDecompressesCorrectly() throws IOException {
            byte[] original = "Hello, GZIP world!".getBytes(StandardCharsets.UTF_8);
            byte[] compressed = GZip.compress(original);

            assertArrayEquals(original, decompress(compressed));
        }

        @Test
        void emptyData_compressesAndDecompressesCorrectly() throws IOException {
            byte[] original = new byte[0];
            byte[] compressed = GZip.compress(original);

            assertArrayEquals(original, decompress(compressed));
        }

        private static byte[] decompress(byte[] compressed) throws IOException {
            try (GZIPInputStream gz = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
                return gz.readAllBytes();
            }
        }
    }
}
