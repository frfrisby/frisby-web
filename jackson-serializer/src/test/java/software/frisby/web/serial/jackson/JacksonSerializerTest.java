package software.frisby.web.serial.jackson;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import software.frisby.core.validation.NullValueException;
import software.frisby.web.serial.GenericType;

import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JacksonSerializerTest {

    private static final String NULL_MAPPER_MSG =
            "The 'mapper' value is invalid. The value must not be null.";

    private static final String NULL_VALUE_MSG =
            "The 'value' value is invalid. The value must not be null.";

    private static final String NULL_CONTENT_MSG =
            "The 'content' value is invalid. The value must not be null.";

    private static final String NULL_TYPE_MSG =
            "The 'type' value is invalid. The value must not be null.";

    private static final String NULL_GENERIC_TYPE_MSG =
            "The 'genericType' value is invalid. The value must not be null.";

    private static final String SERIALIZATION_FAILED_MSG = "Serialization failed.";

    private static final String DESERIALIZATION_FAILED_MSG = "Deserialization failed.";

    private record Person(String name, int age) {}

    /** Circular self-reference — Jackson cannot serialize this without a custom strategy. */
    private static final class SelfRef {
        @SuppressWarnings("unused")
        SelfRef self;

        SelfRef() {
            this.self = this;
        }
    }

    // ---------------------------------------------------------------------------
    // Builder
    // ---------------------------------------------------------------------------

    @Nested
    class Builder {

        @Test
        void nullMapper_throwsNullValueException() {
            NullValueException ex = assertThrows(
                    NullValueException.class,
                    () -> JacksonSerializer.builder().mapper(null)
            );

            assertEquals(NULL_MAPPER_MSG, ex.getMessage());
        }

        @Test
        void build_returnsNonNullSerializer() {
            JacksonSerializer serializer = JacksonSerializer.builder().build();

            assertNotNull(serializer);
        }

        @Test
        void build_returnsJacksonSerializer() {
            Object serializer = JacksonSerializer.builder().build();

            assertInstanceOf(JacksonSerializer.class, serializer);
        }
    }

    // ---------------------------------------------------------------------------
    // Null parameter validation
    // ---------------------------------------------------------------------------

    @Nested
    class NullParameters {

        private final JacksonSerializer serializer = JacksonSerializer.builder().build();

        @Test
        void nullValue_throwsNullValueException() {
            NullValueException ex = assertThrows(
                    NullValueException.class,
                    () -> serializer.serialize(null)
            );

            assertEquals(NULL_VALUE_MSG, ex.getMessage());
        }

        @Test
        void nullContent_onDeserializeByClass_throwsNullValueException() {
            NullValueException ex = assertThrows(
                    NullValueException.class,
                    () -> serializer.deserialize(null, Person.class)
            );

            assertEquals(NULL_CONTENT_MSG, ex.getMessage());
        }

        @Test
        void nullType_onDeserializeByClass_throwsNullValueException() {
            NullValueException ex = assertThrows(
                    NullValueException.class,
                    () -> serializer.deserialize(new byte[0], (Class<Person>) null)
            );

            assertEquals(NULL_TYPE_MSG, ex.getMessage());
        }

        @Test
        void nullContent_onDeserializeByGenericType_throwsNullValueException() {
            NullValueException ex = assertThrows(
                    NullValueException.class,
                    () -> serializer.deserialize(null, new GenericType<Person>() {})
            );

            assertEquals(NULL_CONTENT_MSG, ex.getMessage());
        }

        @Test
        void nullGenericType_onDeserialize_throwsNullValueException() {
            NullValueException ex = assertThrows(
                    NullValueException.class,
                    () -> serializer.deserialize(new byte[0], (GenericType<Person>) null)
            );

            assertEquals(NULL_GENERIC_TYPE_MSG, ex.getMessage());
        }
    }

    // ---------------------------------------------------------------------------
    // Plain POJO / record round-trip
    // ---------------------------------------------------------------------------

    @Nested
    class PlainRoundTrip {

        private final JacksonSerializer serializer = JacksonSerializer.builder().build();

        @Test
        void pojo_roundTrip() {
            record OrderItem(String sku, int qty) {}
            OrderItem item = new OrderItem("SKU-001", 3);

            byte[] json = serializer.serialize(item);
            OrderItem result = serializer.deserialize(json, OrderItem.class);

            assertEquals(item, result);
        }

        @Test
        void record_roundTrip() {
            Person person = new Person("Alice", 30);

            byte[] json = serializer.serialize(person);
            Person result = serializer.deserialize(json, Person.class);

            assertEquals(person, result);
        }
    }

    // ---------------------------------------------------------------------------
    // GenericType (List<T>) round-trip
    // ---------------------------------------------------------------------------

    @Nested
    class GenericTypeRoundTrip {

        private final JacksonSerializer serializer = JacksonSerializer.builder().build();

        @Test
        void listViaGenericType_roundTrip() {
            List<Person> people = List.of(
                    new Person("Alice", 30),
                    new Person("Bob", 25)
            );

            byte[] json = serializer.serialize(people);
            List<Person> result = serializer.deserialize(
                    json,
                    new GenericType<List<Person>>() {}
            );

            assertEquals(people, result);
        }
    }

    // ---------------------------------------------------------------------------
    // Optional (Jdk8Module)
    // ---------------------------------------------------------------------------

    @Nested
    class OptionalSupport {

        private final JacksonSerializer serializer = JacksonSerializer.builder().build();

        @Test
        void optionalPresent_roundTrip() {
            record WithOptional(Optional<String> name) {}
            WithOptional obj = new WithOptional(Optional.of("hello"));

            byte[] json = serializer.serialize(obj);
            WithOptional result = serializer.deserialize(json, WithOptional.class);

            assertEquals(Optional.of("hello"), result.name());
        }

        @Test
        void optionalEmpty_omittedFromJson() {
            record WithOptional(Optional<String> name, int code) {}
            byte[] json = serializer.serialize(new WithOptional(Optional.empty(), 42));
            String jsonStr = new String(json, StandardCharsets.UTF_8);

            assertFalse(jsonStr.contains("\"name\""));
            assertTrue(jsonStr.contains("42"));
        }
    }

    // ---------------------------------------------------------------------------
    // Temporal types (JavaTimeModule) — ISO-8601 output
    // ---------------------------------------------------------------------------

    @Nested
    class TemporalTypes {

        private final JacksonSerializer serializer = JacksonSerializer.builder().build();

        @Test
        void localDate_serializedAsIso8601() {
            record WithDate(LocalDate date) {}
            byte[] json = serializer.serialize(new WithDate(LocalDate.of(2024, 6, 15)));
            String jsonStr = new String(json, StandardCharsets.UTF_8);

            assertTrue(jsonStr.contains("2024-06-15"));
        }

        @Test
        void localDate_roundTrip() {
            record WithDate(LocalDate date) {}
            WithDate obj = new WithDate(LocalDate.of(2024, 6, 15));

            byte[] json = serializer.serialize(obj);
            WithDate result = serializer.deserialize(json, WithDate.class);

            assertEquals(obj, result);
        }

        @Test
        void instant_serializedAsIso8601() {
            record WithTimestamp(Instant timestamp) {}
            byte[] json = serializer.serialize(new WithTimestamp(Instant.ofEpochSecond(1_000)));
            String jsonStr = new String(json, StandardCharsets.UTF_8);

            // ISO-8601 representation — must not be a bare number
            assertFalse(jsonStr.contains("\"1000\""));
            assertTrue(jsonStr.contains("1970-01-01"));
        }

        @Test
        void instant_roundTrip() {
            record WithTimestamp(Instant timestamp) {}
            WithTimestamp obj = new WithTimestamp(Instant.ofEpochSecond(1_000));

            byte[] json = serializer.serialize(obj);
            WithTimestamp result = serializer.deserialize(json, WithTimestamp.class);

            assertEquals(obj, result);
        }
    }

    // ---------------------------------------------------------------------------
    // BigDecimal — plain format, no scientific notation
    // ---------------------------------------------------------------------------

    @Nested
    class BigDecimalFormat {

        private final JacksonSerializer serializer = JacksonSerializer.builder().build();

        @Test
        void bigDecimal_writtenAsPlain() {
            record WithDecimal(BigDecimal amount) {}
            byte[] json = serializer.serialize(new WithDecimal(new BigDecimal("1E+3")));
            String jsonStr = new String(json, StandardCharsets.UTF_8);

            assertTrue(jsonStr.contains("1000"));
            assertFalse(jsonStr.contains("E"));
            assertFalse(jsonStr.contains("e"));
        }

        @Test
        void bigDecimal_roundTrip() {
            record WithDecimal(BigDecimal amount) {}
            WithDecimal obj = new WithDecimal(new BigDecimal("12345.6789"));

            byte[] json = serializer.serialize(obj);
            WithDecimal result = serializer.deserialize(json, WithDecimal.class);

            assertEquals(0, obj.amount().compareTo(result.amount()));
        }
    }

    // ---------------------------------------------------------------------------
    // NON_EMPTY — null fields omitted from serialized output
    // ---------------------------------------------------------------------------

    @Nested
    class NullFieldOmission {

        private final JacksonSerializer serializer = JacksonSerializer.builder().build();

        @Test
        void nullField_omittedFromJson() {
            record WithNullable(String name, int age) {}
            byte[] json = serializer.serialize(new WithNullable(null, 30));
            String jsonStr = new String(json, StandardCharsets.UTF_8);

            assertFalse(jsonStr.contains("\"name\""));
            assertTrue(jsonStr.contains("30"));
        }
    }

    // ---------------------------------------------------------------------------
    // Unknown field tolerance — FAIL_ON_UNKNOWN_PROPERTIES = false
    // ---------------------------------------------------------------------------

    @Nested
    class UnknownFieldTolerance {

        private final JacksonSerializer serializer = JacksonSerializer.builder().build();

        @Test
        void unknownField_toleratedOnDeserialize() {
            byte[] json = "{\"name\":\"Alice\",\"age\":30,\"unknown\":\"value\"}"
                    .getBytes(StandardCharsets.UTF_8);

            Person result = serializer.deserialize(json, Person.class);

            assertEquals("Alice", result.name());
            assertEquals(30, result.age());
        }
    }

    // ---------------------------------------------------------------------------
    // IOException wrapping
    // ---------------------------------------------------------------------------

    @Nested
    class IoFailures {

        private final JacksonSerializer serializer = JacksonSerializer.builder().build();

        @Test
        void unserializableValue_throwsUncheckedIOException() {
            // Circular self-reference causes Jackson to throw JsonMappingException
            UncheckedIOException ex = assertThrows(
                    UncheckedIOException.class,
                    () -> serializer.serialize(new SelfRef())
            );

            assertEquals(SERIALIZATION_FAILED_MSG, ex.getMessage());
        }

        @Test
        void invalidJson_onDeserializeByClass_throwsUncheckedIOException() {
            byte[] garbage = "not-valid-json".getBytes(StandardCharsets.UTF_8);

            UncheckedIOException ex = assertThrows(
                    UncheckedIOException.class,
                    () -> serializer.deserialize(garbage, Person.class)
            );

            assertEquals(DESERIALIZATION_FAILED_MSG, ex.getMessage());
        }

        @Test
        void invalidJson_onDeserializeByGenericType_throwsUncheckedIOException() {
            byte[] garbage = "not-valid-json".getBytes(StandardCharsets.UTF_8);

            UncheckedIOException ex = assertThrows(
                    UncheckedIOException.class,
                    () -> serializer.deserialize(garbage, new GenericType<List<Person>>() {})
            );

            assertEquals(DESERIALIZATION_FAILED_MSG, ex.getMessage());
        }
    }

    // ---------------------------------------------------------------------------
    // Custom mapper override
    // ---------------------------------------------------------------------------

    @Nested
    class CustomMapper {

        @Test
        void mapperOverride_usedInsteadOfDefault() {
            // A mapper that writes dates as epoch timestamps — opposite of default
            ObjectMapper customMapper = new ObjectMapper();
            customMapper.registerModule(new JavaTimeModule());
            customMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, true);

            JacksonSerializer serializer = JacksonSerializer.builder()
                    .mapper(customMapper)
                    .build();

            record WithTimestamp(Instant timestamp) {}
            byte[] json = serializer.serialize(new WithTimestamp(Instant.ofEpochSecond(1_000)));
            String jsonStr = new String(json, StandardCharsets.UTF_8);

            // Custom mapper writes timestamps as numbers, not ISO strings
            assertTrue(jsonStr.contains("1000"));
            assertFalse(jsonStr.contains("1970-01-01"));
        }
    }
}







