package software.frisby.web.client;

import software.frisby.core.validation.Strings;
import software.frisby.core.validation.Values;

/**
 * A pre-serialized content part with an explicit media type.
 *
 * @see FormPart#text(String, String)
 * @see FormPart#entity(String, String, MediaType)
 */
final class FormContentPart implements FormPart {
    private static final String NAME_ARGUMENT_NAME = "name";
    private static final String CONTENT_ARGUMENT_NAME = "content";
    private static final String MEDIA_TYPE_ARGUMENT_NAME = "mediaType";

    private final String name;
    private final String content;
    private final MediaType mediaType;

    FormContentPart(String name, String content, MediaType mediaType) {
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
    String content() {
        return content;
    }

    /**
     * Returns the media type of this part's content.
     *
     * @return The media type.
     */
    MediaType mediaType() {
        return mediaType;
    }
}

