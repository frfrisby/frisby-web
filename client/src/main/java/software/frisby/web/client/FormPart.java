package software.frisby.web.client;

import software.frisby.core.validation.Values;

import java.io.InputStream;

/**
 * A single part of a {@code multipart/form-data} request body.
 * <p>
 * Use the static factory methods to create parts, then pass them to
 * {@link FormData#of(FormPart...)} to assemble the complete request body.
 * Part order is preserved — callers control the ordering.
 *
 * <pre>{@code
 * // File only
 * FormData.of(
 *         FormPart.file("file", stream, "report.pdf")
 * );
 *
 * // File with a JSON metadata entity and a plain-text scalar field
 * FormData.of(
 *         FormPart.file("file", stream, "report.pdf", MediaType.of("application/pdf")),
 *         FormPart.json("metadata", documentMetadata),
 *         FormPart.text("category", "invoices")
 * );
 *
 * // File with a pre-serialized XML entity
 * FormData.of(
 *         FormPart.file("file", stream, "report.pdf"),
 *         FormPart.entity("descriptor", xmlString, MediaType.of("application/xml"))
 * );
 * }</pre>
 *
 * @see FormData
 * @see PostSpec#body(FormData)
 * @see PutSpec#body(FormData)
 */
public sealed interface FormPart permits FormFilePart, FormJsonPart, FormContentPart {
    /**
     * Creates a file stream part.
     * <p>
     * The request uses chunked transfer encoding.  The server must support
     * {@code Transfer-Encoding: chunked}.
     * <p>
     * The {@code Content-Type} of the part is guessed from the file name extension.
     * Use {@link #file(String, InputStream, String, MediaType)} to specify it explicitly.
     *
     * @param name     The multipart part name.
     * @param stream   The file contents.  The stream must not have been consumed before
     *                 the request is sent.
     * @param fileName The file name, including any extension (e.g. {@code "report.pdf"}).
     *                 Used to populate the {@code filename} parameter of the part's
     *                 {@code Content-Disposition} header.
     * @return A {@code FormFilePart} instance.
     * @throws software.frisby.core.validation.NullValueException  if {@code name}, {@code stream}, or
     *                                                             {@code fileName} is null.
     * @throws software.frisby.core.validation.BlankValueException if {@code name} or {@code fileName}
     *                                                             is blank.
     */
    static FormPart file(String name, InputStream stream, String fileName) {
        return new FormFilePart(name, stream, fileName, null);
    }

    /**
     * Creates a file stream part with an explicit {@code Content-Type}.
     * <p>
     * The request uses chunked transfer encoding.  The server must support
     * {@code Transfer-Encoding: chunked}.
     *
     * @param name        The multipart part name.
     * @param stream      The file contents.  The stream must not have been consumed before
     *                    the request is sent.
     * @param fileName    The file name, including any extension (e.g. {@code "report.pdf"}).
     * @param contentType The {@code Content-Type} for this part (e.g.
     *                    {@code MediaType.of("application/pdf")}).
     * @return A {@code FormFilePart} instance.
     * @throws software.frisby.core.validation.NullValueException  if {@code name}, {@code stream},
     *                                                             {@code fileName}, or
     *                                                             {@code contentType} is null.
     * @throws software.frisby.core.validation.BlankValueException if {@code name} or {@code fileName}
     *                                                             is blank.
     */
    static FormPart file(String name, InputStream stream, String fileName, MediaType contentType) {
        return new FormFilePart(name, stream, fileName, Values.notNull("contentType", contentType));
    }

    /**
     * Creates a JSON entity part whose body will be serialized by the configured
     * {@link software.frisby.web.serial.JsonSerializer}.
     * <p>
     * The part is transmitted with {@code Content-Type: application/json}.
     *
     * @param name The multipart part name.
     * @param body The object to serialize as JSON.
     * @return A {@code FormJsonPart} instance.
     * @throws software.frisby.core.validation.NullValueException  if {@code name} or {@code body}
     *                                                             is null.
     * @throws software.frisby.core.validation.BlankValueException if {@code name} is blank.
     */
    static FormPart json(String name, Object body) {
        return new FormJsonPart(name, body);
    }

    /**
     * Creates a {@code text/plain} content part with a pre-serialized string value.
     * <p>
     * Useful for including scalar fields (e.g. a file size, a category name) alongside
     * a file in a multipart request.
     *
     * @param name  The multipart part name.
     * @param value The string content of the part.
     * @return A {@code FormContentPart} instance with {@link MediaType#TEXT_PLAIN}.
     * @throws software.frisby.core.validation.NullValueException  if {@code name} or {@code value}
     *                                                             is null.
     * @throws software.frisby.core.validation.BlankValueException if {@code name} is blank.
     */
    static FormPart text(String name, String value) {
        return new FormContentPart(name, value, MediaType.TEXT_PLAIN);
    }

    /**
     * Creates a pre-serialized content part with an explicit media type.
     * <p>
     * The caller is responsible for serializing and encoding the content.  Use
     * {@link #json(String, Object)} when JSON serialization by the library is preferred.
     *
     * @param name      The multipart part name.
     * @param content   The pre-serialized string content of the part.
     * @param mediaType The media type of the content.
     * @return A {@code FormContentPart} instance.
     * @throws software.frisby.core.validation.NullValueException  if {@code name}, {@code content},
     *                                                             or {@code mediaType} is null.
     * @throws software.frisby.core.validation.BlankValueException if {@code name} is blank.
     */
    static FormPart entity(String name, String content, MediaType mediaType) {
        return new FormContentPart(name, content, mediaType);
    }

    /**
     * Returns the multipart body part name used in the {@code Content-Disposition} header
     * (e.g. {@code form-data; name="file"}).
     *
     * @return The part name.
     */
    String name();
}
