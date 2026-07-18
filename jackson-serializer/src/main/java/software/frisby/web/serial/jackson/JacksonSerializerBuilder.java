package software.frisby.web.serial.jackson;

import com.fasterxml.jackson.databind.ObjectMapper;
import software.frisby.core.validation.NullValueException;

/**
 * Builder for constructing a {@link JacksonSerializer}.
 *
 * <p>Obtain an instance via {@link JacksonSerializer#builder()}.
 *
 * <p>All setter methods return this builder, enabling fluent chaining.  Calling
 * {@link #build()} may be called multiple times; each call produces a new
 * {@link JacksonSerializer} instance backed by the same {@link ObjectMapper}.
 */
public interface JacksonSerializerBuilder {
    /**
     * Replaces the default {@link ObjectMapper} with the provided one.
     *
     * <p>Use this when the default opinionated configuration does not match your needs —
     * for example, if you need epoch timestamps instead of ISO-8601 strings, or if you
     * have existing serialization behavior that must be preserved.
     *
     * <p>The supplied {@code mapper} is used as-is; no additional modules or features are
     * registered on it by this builder.
     *
     * @param mapper The object mapper to use; must not be {@code null}.
     * @return This builder.
     * @throws NullValueException if {@code mapper} is {@code null}.
     */
    JacksonSerializerBuilder mapper(ObjectMapper mapper);

    /**
     * Builds and returns a new {@link JacksonSerializer}.
     *
     * @return A new {@link JacksonSerializer} backed by the configured {@link ObjectMapper}.
     */
    JacksonSerializer build();
}

