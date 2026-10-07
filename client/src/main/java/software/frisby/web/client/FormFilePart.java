package software.frisby.web.client;

import software.frisby.core.validation.Strings;
import software.frisby.core.validation.Values;

import java.io.InputStream;
import java.util.Optional;

/**
 * A file stream part of a multipart request.
 *
 * @see FormPart#file(String, InputStream, String)
 * @see FormPart#file(String, InputStream, String, MediaType)
 */
final class FormFilePart implements FormPart {
    private static final String NAME_ARGUMENT_NAME = "name";
    private static final String STREAM_ARGUMENT_NAME = "stream";
    private static final String FILE_NAME_ARGUMENT_NAME = "fileName";

    private final String name;
    private final InputStream stream;
    private final String fileName;
    private final MediaType contentType;

    FormFilePart(String name,
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

