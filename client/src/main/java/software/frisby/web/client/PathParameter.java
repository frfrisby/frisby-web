package software.frisby.web.client;

import software.frisby.core.validation.Strings;

/**
 * Represents a named placeholder and its substitution value for use in URI path templates.
 * <p>
 * Placeholders are denoted by their name surrounded by braces within the path template.
 * For example, given the path {@code /users/{id}}, the placeholder name is {@code id}.
 *
 * <pre>{@code
 * // Single parameter — convenience overload
 * client.get().path("/users/{id}", "id", userId).send(User.class);
 *
 * // Multiple parameters — PathParameter instances
 * client.get()
 *         .path(
 *                 "/teams/{teamId}/members/{memberId}",
 *                 PathParameter.of("teamId", teamId),
 *                 PathParameter.of("memberId", memberId)
 *         )
 *         .send(Member.class);
 * }</pre>
 *
 * @see GetSpec#path(String, PathParameter...)
 */
public final class PathParameter {
    private static final String ID_ARGUMENT_NAME = "id";
    private static final String VALUE_ARGUMENT_NAME = "value";

    private final String id;
    private final String value;

    private PathParameter(String id, String value) {
        this.id = Strings.notBlank(ID_ARGUMENT_NAME, id);
        this.value = Strings.notBlank(VALUE_ARGUMENT_NAME, value);
    }

    /**
     * Creates a new {@link PathParameter} with the specified placeholder name and substitution value.
     *
     * @param id    The placeholder name to replace within the URI path template, without
     *              surrounding braces (e.g. {@code "id"} matches the placeholder {@code {id}}).
     * @param value The value that will replace the placeholder in the URI path template.
     * @return A new {@link PathParameter} instance.
     * @throws software.frisby.core.validation.NullValueException  if {@code id} or {@code value} is null.
     * @throws software.frisby.core.validation.BlankValueException if {@code id} or {@code value} is blank.
     */
    public static PathParameter of(String id, String value) {
        return new PathParameter(id, value);
    }

    /**
     * Returns the placeholder name that this parameter replaces within the URI path template.
     *
     * @return The placeholder name, without surrounding braces.
     */
    public String id() {
        return id;
    }

    /**
     * Returns the value that will replace the placeholder in the URI path template.
     *
     * @return The substitution value.
     */
    public String value() {
        return value;
    }
}

