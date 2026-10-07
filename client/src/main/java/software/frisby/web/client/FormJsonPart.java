package software.frisby.web.client;

import software.frisby.core.validation.Strings;
import software.frisby.core.validation.Values;
import software.frisby.web.serial.JsonSerializer;

/**
 * A JSON entity part whose body is serialized by the configured
 * {@link JsonSerializer}.
 *
 * @param name The multipart part name.
 * @param body The object that will be serialized to JSON.
 * @see FormPart#json(String, Object)
 */
record FormJsonPart(String name, Object body) implements FormPart {
    private static final String NAME_ARGUMENT_NAME = "name";
    private static final String BODY_ARGUMENT_NAME = "body";

    FormJsonPart {
        Strings.notBlank(NAME_ARGUMENT_NAME, name);
        Values.notNull(BODY_ARGUMENT_NAME, body);
    }
}

