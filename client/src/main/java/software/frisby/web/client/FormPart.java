package software.frisby.web.client;

import software.frisby.core.validation.Strings;
import software.frisby.core.validation.Values;

import java.io.InputStream;
import java.util.Optional;

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
public sealed interface FormPart permits FormPart.FilePart, FormPart.JsonPart, FormPart.ContentPart {
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
     * @return A {@link FilePart} instance.
     * @throws software.frisby.core.validation.NullValueException  if {@code name}, {@code stream}, or
     *                                                             {@code fileName} is null.
     * @throws software.frisby.core.validation.BlankValueException if {@code name} or {@code fileName}
     *                                                             is blank.
     */
    static FormPart file(String name, InputStream stream, String fileName) {
        return new FilePart(name, stream, fileName, null);
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
     * @return A {@link FilePart} instance.
     * @throws software.frisby.core.validation.NullValueException  if {@code name}, {@code stream},
     *                                                             {@code fileName}, or
     *                                                             {@code contentType} is null.
     * @throws software.frisby.core.validation.BlankValueException if {@code name} or {@code fileName}
     *                                                             is blank.
     */
    static FormPart file(String name, InputStream stream, String fileName, MediaType contentType) {
        return new FilePart(name, stream, fileName, Values.notNull("contentType", contentType));
    }

    /**
     * Creates a JSON entity part whose body will be serialized by the configured
     * {@link software.frisby.web.serial.JsonSerializer}.
     * <p>
     * The part is transmitted with {@code Content-Type: application/json}.
     *
     * @param name The multipart part name.
     * @param body The object to serialize as JSON.
     * @return A {@link JsonPart} instance.
     * @throws software.frisby.core.validation.NullValueException  if {@code name} or {@code body}
     *                                                             is null.
     * @throws software.frisby.core.validation.BlankValueException if {@code name} is blank.
     */
    static FormPart json(String name, Object body) {
        return new JsonPart(name, body);
    }

    /**
     * Creates a {@code text/plain} content part with a pre-serialized string value.
     * <p>
     * Useful for including scalar fields (e.g. a file size, a category name) alongside
     * a file in a multipart request.
     *
     * @param name  The multipart part name.
     * @param value The string content of the part.
     * @return A {@link ContentPart} instance with {@link MediaType#TEXT_PLAIN}.
     * @throws software.frisby.core.validation.NullValueException  if {@code name} or {@code value}
     *                                                             is null.
     * @throws software.frisby.core.validation.BlankValueException if {@code name} is blank.
     */
    static FormPart text(String name, String value) {
        return new ContentPart(name, value, MediaType.TEXT_PLAIN);
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
     * @return A {@link ContentPart} instance.
     * @throws software.frisby.core.validation.NullValueException  if {@code name}, {@code content},
     *                                                             or {@code mediaType} is null.
     * @throws software.frisby.core.validation.BlankValueException if {@code name} is blank.
     */
    static FormPart entity(String name, String content, MediaType mediaType) {
        return new ContentPart(name, content, mediaType);
    }

    /**
     * Returns the multipart body part name used in the {@code Content-Disposition} header
     * (e.g. {@code form-data; name="file"}).
     *
     * @return The part name.
     */
    String name();

    // -------------------------------------------------------------------------
    // Permitted implementations
    // -------------------------------------------------------------------------

    /**
     * A file stream part of a multipart request.
     *
     * @see FormPart#file(String, InputStream, String)
     * @see FormPart#file(String, InputStream, String, MediaType)
     */
    final class FilePart implements FormPart {
        private static final String NAME_ARGUMENT_NAME = "name";
        private static final String STREAM_ARGUMENT_NAME = "stream";
        private static final String FILE_NAME_ARGUMENT_NAME = "fileName";

        private final String name;
        private final InputStream stream;
        private final String fileName;
        private final MediaType contentType;

        private FilePart(String name,
                         InputStream stream,
                         String fileName,
                         MediaType contentType) {
            this.name = Strings.notBlank(NAME_ARGUMENT_NAME, name);
            this.stream = Values.notNull(STREAM_ARGUMENT_NAME, stream);
            this.fileName = Strings.notBlank(FILE_NAME_ARGUMENT_NAME, fileName);
            this.contentType = contentType;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String name() {
            return name;
        }

        /**
         * Returns the file contents stream.
         * <p>
         * The caller is responsible for ensuring the stream has not been consumed before
         * the request is sent.
         *
         * @return The file contents stream.
         */
        InputStream stream() {
            return stream;
        }

        /**
         * Returns the file name, including any extension (e.g. {@code "report.pdf"}).
         *
         * @return The file name.
         */
        String fileName() {
            return fileName;
        }

        /**
         * Returns the explicitly specified {@code Content-Type} for this part, if any.
         * <p>
         * When present, this value is used directly as the part's {@code Content-Type}
         * header.  When empty, the client guesses the content type from the file name
         * extension, falling back to {@code application/octet-stream} for unrecognized
         * extensions.
         *
         * @return The explicit content type, or empty to use the guessed value.
         */
        Optional<MediaType> contentType() {
            return Optional.ofNullable(contentType);
        }
    }

    /**
     * A JSON entity part whose body is serialized by the configured
     * {@link software.frisby.web.serial.JsonSerializer}.
     *
     * @see FormPart#json(String, Object)
     */
    final class JsonPart implements FormPart {
        private static final String NAME_ARGUMENT_NAME = "name";
        private static final String BODY_ARGUMENT_NAME = "body";

        private final String name;
        private final Object body;

        private JsonPart(String name, Object body) {
            this.name = Strings.notBlank(NAME_ARGUMENT_NAME, name);
            this.body = Values.notNull(BODY_ARGUMENT_NAME, body);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String name() {
            return name;
        }

        /**
         * Returns the object that will be serialized to JSON.
         *
         * @return The body object.
         */
        public Object body() {
            return body;
        }
    }

    /**
     * A pre-serialized content part with an explicit media type.
     *
     * @see FormPart#text(String, String)
     * @see FormPart#entity(String, String, MediaType)
     */
    final class ContentPart implements FormPart {
        private static final String NAME_ARGUMENT_NAME = "name";
        private static final String CONTENT_ARGUMENT_NAME = "content";
        private static final String MEDIA_TYPE_ARGUMENT_NAME = "mediaType";

        private final String name;
        private final String content;
        private final MediaType mediaType;

        private ContentPart(String name, String content, MediaType mediaType) {
            this.name = Strings.notBlank(NAME_ARGUMENT_NAME, name);
            this.content = Values.notNull(CONTENT_ARGUMENT_NAME, content);
            this.mediaType = Values.notNull(MEDIA_TYPE_ARGUMENT_NAME, mediaType);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String name() {
            return name;
        }

        /**
         * Returns the pre-serialized string content of this part.
         *
         * @return The content string.
         */
        public String content() {
            return content;
        }

        /**
         * Returns the media type of this part's content.
         *
         * @return The media type.
         */
        public MediaType mediaType() {
            return mediaType;
        }
    }
}

