package software.frisby.web.client;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import software.frisby.core.validation.BlankValueException;
import software.frisby.core.validation.NullValueException;

import static org.junit.jupiter.api.Assertions.*;

class MediaTypeTest {
    private static final String NULL_VALUE_MSG = "The 'value' value is invalid. The value must not be null.";
    private static final String BLANK_VALUE_MSG = "The 'value' value is invalid. The value must not contain only whitespace characters.";

    // -------------------------------------------------------------------------
    // Well-known constants
    // -------------------------------------------------------------------------

    @Nested
    class Constants {
        @Test
        void applicationJson_hasCorrectValue() {
            assertEquals("application/json", MediaType.APPLICATION_JSON.value());
        }

        @Test
        void applicationOctetStream_hasCorrectValue() {
            assertEquals("application/octet-stream", MediaType.APPLICATION_OCTET_STREAM.value());
        }

        @Test
        void multipartFormData_hasCorrectValue() {
            assertEquals("multipart/form-data", MediaType.MULTIPART_FORM_DATA.value());
        }

        @Test
        void textPlain_hasCorrectValue() {
            assertEquals("text/plain", MediaType.TEXT_PLAIN.value());
        }

        @Test
        void formUrlEncoded_hasCorrectValue() {
            assertEquals("application/x-www-form-urlencoded", MediaType.FORM_URL_ENCODED.value());
        }
    }

    // -------------------------------------------------------------------------
    // of() factory
    // -------------------------------------------------------------------------

    @Nested
    class OfFactory {
        @Test
        void validValue_createsMediaType() {
            MediaType mt = MediaType.of("application/xml");

            assertEquals("application/xml", mt.value());
        }

        @Test
        void nullValue_throwsException() {
            NullValueException ex = assertThrows(NullValueException.class, () -> MediaType.of(null));

            assertEquals(NULL_VALUE_MSG, ex.getMessage());
        }

        @Test
        void blankValue_throwsException() {
            BlankValueException ex = assertThrows(BlankValueException.class, () -> MediaType.of("   "));

            assertEquals(BLANK_VALUE_MSG, ex.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // equals / hashCode / toString
    // -------------------------------------------------------------------------

    @Nested
    class EqualityAndToString {
        @Test
        void twoInstancesWithSameValue_areEqual() {
            assertEquals(MediaType.of("text/html"), MediaType.of("text/html"));
        }

        @Test
        void twoInstancesWithDifferentValues_areNotEqual() {
            assertNotEquals(MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN);
        }

        @Test
        void equalInstances_haveEqualHashCodes() {
            assertEquals(
                    MediaType.of("image/png").hashCode(),
                    MediaType.of("image/png").hashCode()
            );
        }

        @Test
        void toString_returnsRawValue() {
            assertEquals("application/json", MediaType.APPLICATION_JSON.toString());
        }

        @Test
        void constantEqualsOfWithSameValue() {
            assertEquals(MediaType.APPLICATION_JSON, MediaType.of("application/json"));
        }

        @Test
        void nonMediaTypeObject_isNotEqual() {
            // exercises the `!(o instanceof MediaType)` false branch in equals()
            assertNotEquals(MediaType.APPLICATION_JSON, "application/json");
        }
    }
}
