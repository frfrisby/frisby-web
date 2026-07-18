package software.frisby.web.test.resource;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Arrays;

/**
 * JAX-RS resource that returns a fixed-size binary response, enabling client streaming download
 * tests.
 *
 * <ul>
 *   <li>{@code GET /stream} — returns 64 KB of repeating bytes as {@code application/octet-stream};
 *       tests use this to verify that {@code download()} returns a readable {@link InputStream}
 *       and that the full payload is received</li>
 * </ul>
 */
@Path("/stream")
@Produces(MediaType.APPLICATION_OCTET_STREAM)
public final class StreamResource {
    private static final int STREAM_SIZE_BYTES = 64 * 1024;
    private static final byte FILL_BYTE = 0x42;

    /**
     * Returns a 64 KB binary payload as an input stream.
     *
     * @return An {@link InputStream} containing {@value STREAM_SIZE_BYTES} bytes.
     */
    @GET
    public InputStream download() {
        byte[] data = new byte[STREAM_SIZE_BYTES];
        Arrays.fill(data, FILL_BYTE);
        return new ByteArrayInputStream(data);
    }
}

