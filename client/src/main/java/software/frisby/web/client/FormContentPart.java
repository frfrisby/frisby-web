package software.frisby.web.client;

import software.frisby.core.validation.Strings;
import software.frisby.core.validation.Values;

/**
 * A pre-serialized content part with an explicit media type.
 *
 * @param name      The multipart part name.
 * @param content   The pre-serialized string content of this part.
 * @param mediaType The media type of this part's content.
 * @see FormPart#text(String, String)
 * @see FormPart#entity(String, String, MediaType)
 */
record FormContentPart(String name, String content, MediaType mediaType) implements FormPart {
    private static final String NAME_ARGUMENT_NAME = "name";
    private static final String CONTENT_ARGUMENT_NAME = "content";
    private static final String MEDIA_TYPE_ARGUMENT_NAME = "mediaType";

    FormContentPart {
        Strings.notBlank(NAME_ARGUMENT_NAME, name);
        Values.notNull(CONTENT_ARGUMENT_NAME, content);
        Values.notNull(MEDIA_TYPE_ARGUMENT_NAME, mediaType);
    }
}

