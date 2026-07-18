package software.frisby.web.test.resource;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.glassfish.jersey.media.multipart.FormDataBodyPart;
import org.glassfish.jersey.media.multipart.FormDataMultiPart;
import software.frisby.web.test.domain.MixedUploadResult;
import software.frisby.web.test.domain.UploadResult;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * JAX-RS resource that accepts multipart form-data uploads, enabling client
 * {@code software.frisby.web.client.FormData} / {@code software.frisby.web.client.FormPart}
 * integration tests.
 *
 * <p><strong>Note:</strong> The server must be started with {@code MultiPartFeature} registered
 * as a component:
 * <pre>{@code
 * .components(new MultiPartFeature())
 * }</pre>
 *
 * <ul>
 *   <li>{@code POST /upload} — accepts a single {@code "file"} part; returns
 *       {@link UploadResult} with the filename and byte count</li>
 *   <li>{@code PUT /upload} — same as POST (tests PUT with multipart body)</li>
 *   <li>{@code POST /upload/mixed} — accepts a {@code "file"} part, a {@code "metadata"}
 *       part (raw JSON string), and a {@code "category"} text part; returns
 *       {@link MixedUploadResult}</li>
 *   <li>{@code PUT /upload/mixed} — same as POST /upload/mixed (tests PUT with mixed
 *       multipart body)</li>
 * </ul>
 */
@Path("/upload")
@Produces(MediaType.APPLICATION_JSON)
public final class UploadResource {
    /**
     * Accepts a single-file multipart upload.
     *
     * @param form The multipart form containing a {@code "file"} part.
     * @return An {@link UploadResult} with the filename and byte count.
     */
    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public UploadResult upload(FormDataMultiPart form) {
        FormDataBodyPart filePart = form.getField("file");

        if (null == filePart) {
            throw new jakarta.ws.rs.BadRequestException("Missing 'file' part.");
        }

        String fileName = filePart.getFormDataContentDisposition().getFileName();
        long size = readAllBytes(filePart).length;
        String contentType = filePart.getMediaType().toString();

        return new UploadResult(fileName, size, contentType);
    }

    /**
     * Accepts a single-file multipart upload via PUT (tests PUT with multipart body).
     *
     * @param form The multipart form containing a {@code "file"} part.
     * @return An {@link UploadResult} with the filename and byte count.
     */
    @PUT
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public UploadResult replace(FormDataMultiPart form) {
        return upload(form);
    }

    /**
     * Accepts a mixed multipart upload containing a file, a JSON metadata part, and a text field.
     *
     * @param form The multipart form containing {@code "file"}, {@code "metadata"}, and
     *             {@code "category"} parts.
     * @return A {@link MixedUploadResult} confirming all parts were received.
     */
    @POST
    @Path("/mixed")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public MixedUploadResult uploadMixed(FormDataMultiPart form) {
        FormDataBodyPart filePart = form.getField("file");
        FormDataBodyPart metadataPart = form.getField("metadata");
        FormDataBodyPart categoryPart = form.getField("category");

        if (null == filePart) {
            throw new jakarta.ws.rs.BadRequestException("Missing 'file' part.");
        }

        String fileName = filePart.getFormDataContentDisposition().getFileName();
        long fileSize = readAllBytes(filePart).length;

        String metadata = null != metadataPart
                ? new String(readAllBytes(metadataPart), StandardCharsets.UTF_8)
                : null;
        String category = null != categoryPart
                ? new String(readAllBytes(categoryPart), StandardCharsets.UTF_8)
                : null;

        return new MixedUploadResult(fileName, fileSize, metadata, category);
    }

    /**
     * Accepts a mixed multipart upload via PUT (tests PUT with mixed multipart body).
     *
     * @param form The multipart form containing {@code "file"}, {@code "metadata"}, and
     *             {@code "category"} parts.
     * @return A {@link MixedUploadResult} confirming all parts were received.
     */
    @PUT
    @Path("/mixed")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public MixedUploadResult replaceMixed(FormDataMultiPart form) {
        return uploadMixed(form);
    }

    private static byte[] readAllBytes(FormDataBodyPart part) {
        try (InputStream stream = part.getEntityAs(InputStream.class)) {
            return stream.readAllBytes();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
