package software.frisby.web.client;

import software.frisby.core.validation.Strings;
import software.frisby.core.validation.Values;

/**
 * A JSON entity part whose body is serialized by the configured
 * {@link software.frisby.web.serial.JsonSerializer}.
 *
 * @see FormPart#json(String, Object)
 */
final class FormJsonPart implements FormPart {
    private static final String NAME_ARGUMENT_NAME = "name";
    private static final String BODY_ARGUMENT_NAME = "body";

    private final String name;
    private final Object body;

    FormJsonPart(String name, Object body) {
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
    Object body() {
        return body;
    }
}

