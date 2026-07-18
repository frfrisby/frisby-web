package software.frisby.web.serial.jackson;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.UncheckedIOException;
import software.frisby.core.validation.NullValueException;
import software.frisby.web.serial.JsonSerializer;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

/**
 * A Jackson-backed {@link JsonSerializer} with an opinionated default configuration suitable
 * for most REST API use cases.
 *
 * <h2>Default {@code ObjectMapper} configuration</h2>
 * <ul>
 *   <li>{@code FAIL_ON_UNKNOWN_PROPERTIES = false} — tolerant reading; avoids deserialization
 *       errors when APIs add new fields.</li>
 *   <li>{@code WRITE_DATES_AS_TIMESTAMPS = false} / {@code WRITE_DURATIONS_AS_TIMESTAMPS = false}
 *       — dates and durations serialized as ISO-8601 strings for readability and
 *       interoperability.  Bring your own {@link ObjectMapper}
 *       via {@link JacksonSerializerBuilder#mapper(ObjectMapper)}
 *       if epoch timestamps are required.</li>
 *   <li>{@code WRITE_BIGDECIMAL_AS_PLAIN = true} — prevents scientific notation in
 *       {@link BigDecimal} output.</li>
 *   <li>Field visibility {@code ANY} — serializes private fields directly; works with
 *       records and value objects without getter boilerplate.</li>
 *   <li>Inclusion {@code NON_EMPTY} — omits {@code null} and empty collections/strings
 *       from serialized output.</li>
 *   <li>{@link Jdk8Module} registered — supports
 *       {@link Optional} and other JDK 8 types.</li>
 *   <li>{@link JavaTimeModule} registered — supports
 *       {@link LocalDate}, {@link Instant}, and other Java time types.</li>
 * </ul>
 *
 * <p>All method parameters are validated on entry.  Passing {@code null} for any
 * parameter throws {@link NullValueException}.  Jackson processing failures (malformed
 * JSON, unserializable types, circular references, etc.) are wrapped and rethrown as
 * {@link UncheckedIOException}.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Default opinionated configuration
 * JacksonSerializer serializer = JacksonSerializer.builder().build();
 *
 * // Custom ObjectMapper
 * JacksonSerializer serializer = JacksonSerializer.builder()
 *         .mapper(myObjectMapper)
 *         .build();
 * }</pre>
 */
public interface JacksonSerializer extends JsonSerializer {
    /**
     * Returns a new builder for constructing a {@link JacksonSerializer}.
     *
     * @return A new {@link JacksonSerializerBuilder}.
     */
    static JacksonSerializerBuilder builder() {
        return new DefaultJacksonSerializerBuilder();
    }
}

