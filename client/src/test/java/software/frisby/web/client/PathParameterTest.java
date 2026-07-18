package software.frisby.web.client;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import software.frisby.core.validation.BlankValueException;
import software.frisby.core.validation.NullValueException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PathParameterTest {
    private static final String NULL_ID_MSG = "The 'id' value is invalid. The value must not be null.";
    private static final String BLANK_ID_MSG = "The 'id' value is invalid. The value must not contain only whitespace characters.";
    private static final String NULL_VALUE_MSG = "The 'value' value is invalid. The value must not be null.";
    private static final String BLANK_VALUE_MSG = "The 'value' value is invalid. The value must not contain only whitespace characters.";

    // -------------------------------------------------------------------------
    // of() — id validation
    // -------------------------------------------------------------------------

    @Nested
    class Id {
        @Test
        void nullId_throwsNullValueException() {
            NullValueException ex = assertThrows(
                    NullValueException.class,
                    () -> PathParameter.of(null, "42")
            );

            assertEquals(NULL_ID_MSG, ex.getMessage());
        }

        @Test
        void blankId_throwsBlankValueException() {
            BlankValueException ex = assertThrows(
                    BlankValueException.class,
                    () -> PathParameter.of("   ", "42")
            );

            assertEquals(BLANK_ID_MSG, ex.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // of() — value validation
    // -------------------------------------------------------------------------

    @Nested
    class Value {
        @Test
        void nullValue_throwsNullValueException() {
            NullValueException ex = assertThrows(
                    NullValueException.class,
                    () -> PathParameter.of("id", null)
            );

            assertEquals(NULL_VALUE_MSG, ex.getMessage());
        }

        @Test
        void blankValue_throwsBlankValueException() {
            BlankValueException ex = assertThrows(
                    BlankValueException.class,
                    () -> PathParameter.of("id", "   ")
            );

            assertEquals(BLANK_VALUE_MSG, ex.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // of() — accessors
    // -------------------------------------------------------------------------

    @Nested
    class Accessors {
        @Test
        void validOf_accessorsReturnCorrectValues() {
            PathParameter param = PathParameter.of("userId", "42");

            assertEquals("userId", param.id());
            assertEquals("42", param.value());
        }
    }
}

