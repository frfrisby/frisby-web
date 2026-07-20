package software.frisby.web.client;

import software.frisby.core.validation.*;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The request body for an {@code application/x-www-form-urlencoded} HTTP request.
 * <p>
 * Fields are encoded as {@code name=value} pairs separated by {@code &amp;}, with
 * both names and values percent-encoded per RFC 3986.  Insertion order is preserved.
 * <p>
 * Create an instance via {@link #builder()}.
 *
 * <pre>{@code
 * FormUrlEncoded form = FormUrlEncoded.builder()
 *         .field("grant_type", "client_credentials")
 *         .field("client_id", clientId)
 *         .field("client_secret", clientSecret)
 *         .build();
 * }</pre>
 *
 * @see PostSpec#body(FormUrlEncoded)
 * @see PutSpec#body(FormUrlEncoded)
 */
public final class FormUrlEncoded {
    private static final String FIELDS_ARGUMENT_NAME = "fields";

    private final Map<String, String> fields;

    private FormUrlEncoded(Map<String, String> fields) {
        this.fields = Collections.unmodifiableMap(
                Maps.notEmpty(FIELDS_ARGUMENT_NAME, fields)
        );
    }

    /**
     * Returns a new builder that will construct a new instance of {@link FormUrlEncoded}.
     *
     * @return A {@link Builder} instance.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns an unmodifiable view of the form fields in insertion order.
     *
     * @return The form fields as a {@code name → value} map.
     */
    public Map<String, String> fields() {
        return fields;
    }

    /**
     * A builder for creating an instance of {@link FormUrlEncoded}.
     */
    public static final class Builder {
        private static final String FIELD_NAME = "name";
        private static final String FIELD_VALUE = "value";

        private final Map<String, String> fields;

        private Builder() {
            this.fields = new LinkedHashMap<>();
        }

        /**
         * Adds a form field with the specified name and value.
         * <p>
         * If a field with the same name already exists it will be overwritten.
         *
         * @param name  The field name.
         * @param value The field value.  May be blank to represent an empty field.  To omit an
         *              optional field entirely, simply don't call this method for it.
         * @return This builder instance.
         * @throws NullValueException  if {@code name} or {@code value} is null.
         * @throws BlankValueException if {@code name} is blank.
         */
        public Builder field(String name, String value) {
            Strings.notBlank(FIELD_NAME, name);
            Strings.notNull(FIELD_VALUE, value);

            this.fields.put(name, value);
            return this;
        }

        /**
         * Returns a new {@link FormUrlEncoded} instance.
         *
         * @return A new {@link FormUrlEncoded} instance.
         * @throws MissingElementsException if no fields have been added.
         */
        public FormUrlEncoded build() {
            return new FormUrlEncoded(fields);
        }
    }
}

