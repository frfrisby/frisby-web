package software.frisby.web.client;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import software.frisby.core.validation.BlankValueException;
import software.frisby.core.validation.MissingElementsException;
import software.frisby.core.validation.NullValueException;
import software.frisby.core.validation.NumericValueOutsideRangeException;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class DefaultClientLoggingConfigurationBuilderTest {
    private static final String NULL_HEADERS_MSG =
            "The 'headers' value is invalid. The value must not be null.";
    private static final String EMPTY_HEADERS_MSG =
            "The 'headers' value is invalid. The value must not be empty.";
    private static final String BLANK_HEADER_MSG =
            "The 'headers' value is invalid. The value must not contain blank elements. Element at index '0' is blank.";
    private static final String NULL_FIELDS_MSG =
            "The 'fields' value is invalid. The value must not be null.";
    private static final String EMPTY_FIELDS_MSG =
            "The 'fields' value is invalid. The value must not be empty.";
    private static final String BLANK_FIELD_MSG =
            "The 'fields' value is invalid. The value must not contain blank elements. Element at index '0' is blank.";

    // -------------------------------------------------------------------------
    // Default values
    // -------------------------------------------------------------------------

    @Nested
    class Defaults {
        @Test
        void maxBodySize_defaultsTo8Kb() {
            assertEquals(
                    DefaultClientLoggingConfigurationBuilder.DEFAULT_MAX_BODY_SIZE,
                    ClientLoggingConfiguration.builder().build().maxBodySize()
            );
        }

        @Test
        void redactedBodyFields_defaultsToEmpty() {
            assertTrue(ClientLoggingConfiguration.builder().build().redactedBodyFields().isEmpty());
        }

        @Test
        void redactedHeaders_defaultsToHardMaskedSet() {
            ClientLoggingConfiguration config = ClientLoggingConfiguration.builder().build();

            assertTrue(config.redactedHeaders().contains("authorization"));
            assertTrue(config.redactedHeaders().contains("cookie"));
            assertTrue(config.redactedHeaders().contains("set-cookie"));
            assertEquals(3, config.redactedHeaders().size());
        }
    }

    // -------------------------------------------------------------------------
    // redactHeaders
    // -------------------------------------------------------------------------

    @Nested
    class RedactHeaders {
        @Test
        void nullArray_throwsNullValueException() {
            NullValueException ex = assertThrows(
                    NullValueException.class,
                    () -> ClientLoggingConfiguration.builder().redactHeaders((String[]) null)
            );

            assertEquals(NULL_HEADERS_MSG, ex.getMessage());
        }

        @Test
        void emptyArray_throwsMissingElementsException() {
            MissingElementsException ex = assertThrows(
                    MissingElementsException.class,
                    () -> ClientLoggingConfiguration.builder().redactHeaders()
            );

            assertEquals(EMPTY_HEADERS_MSG, ex.getMessage());
        }

        @Test
        void blankElement_throwsBlankValueException() {
            BlankValueException ex = assertThrows(
                    BlankValueException.class,
                    () -> ClientLoggingConfiguration.builder().redactHeaders("   ")
            );

            assertEquals(BLANK_HEADER_MSG, ex.getMessage());
        }

        @Test
        void validHeader_isAddedToRedactedSet() {
            ClientLoggingConfiguration config = ClientLoggingConfiguration.builder()
                    .redactHeaders("X-Api-Key")
                    .build();

            assertTrue(config.redactedHeaders().contains("x-api-key"));
        }

        @Test
        void headers_areStoredLowerCase() {
            ClientLoggingConfiguration config = ClientLoggingConfiguration.builder()
                    .redactHeaders("X-AMZN-OIDC-DATA")
                    .build();

            assertTrue(config.redactedHeaders().contains("x-amzn-oidc-data"));
            assertFalse(config.redactedHeaders().contains("X-AMZN-OIDC-DATA"));
        }

        @Test
        void multipleHeaders_allAdded() {
            ClientLoggingConfiguration config = ClientLoggingConfiguration.builder()
                    .redactHeaders("X-Api-Key", "X-Secret-Token")
                    .build();

            assertTrue(config.redactedHeaders().contains("x-api-key"));
            assertTrue(config.redactedHeaders().contains("x-secret-token"));
        }

        @Test
        void callsAreCumulative() {
            ClientLoggingConfiguration config = ClientLoggingConfiguration.builder()
                    .redactHeaders("X-Api-Key")
                    .redactHeaders("X-Secret-Token")
                    .build();

            assertTrue(config.redactedHeaders().contains("x-api-key"));
            assertTrue(config.redactedHeaders().contains("x-secret-token"));
        }

        @Test
        void hardMaskedHeaders_alwaysPresentRegardlessOfAdditional() {
            ClientLoggingConfiguration config = ClientLoggingConfiguration.builder()
                    .redactHeaders("X-Api-Key")
                    .build();

            assertTrue(config.redactedHeaders().contains("authorization"));
            assertTrue(config.redactedHeaders().contains("cookie"));
            assertTrue(config.redactedHeaders().contains("set-cookie"));
        }

        @Test
        void redactedHeaders_isUnmodifiable() {
            ClientLoggingConfiguration config = ClientLoggingConfiguration.builder().build();

            assertThrows(UnsupportedOperationException.class,
                    () -> config.redactedHeaders().add("x-extra"));
        }
    }

    // -------------------------------------------------------------------------
    // redactFields
    // -------------------------------------------------------------------------

    @Nested
    class RedactFields {
        @Test
        void nullArray_throwsNullValueException() {
            NullValueException ex = assertThrows(
                    NullValueException.class,
                    () -> ClientLoggingConfiguration.builder().redactFields((String[]) null)
            );

            assertEquals(NULL_FIELDS_MSG, ex.getMessage());
        }

        @Test
        void emptyArray_throwsMissingElementsException() {
            MissingElementsException ex = assertThrows(
                    MissingElementsException.class,
                    () -> ClientLoggingConfiguration.builder().redactFields()
            );

            assertEquals(EMPTY_FIELDS_MSG, ex.getMessage());
        }

        @Test
        void blankElement_throwsBlankValueException() {
            BlankValueException ex = assertThrows(
                    BlankValueException.class,
                    () -> ClientLoggingConfiguration.builder().redactFields("  ")
            );

            assertEquals(BLANK_FIELD_MSG, ex.getMessage());
        }

        @Test
        void validField_isAdded() {
            ClientLoggingConfiguration config = ClientLoggingConfiguration.builder()
                    .redactFields("password")
                    .build();

            assertEquals(Set.of("password"), config.redactedBodyFields());
        }

        @Test
        void multipleFields_allAdded() {
            ClientLoggingConfiguration config = ClientLoggingConfiguration.builder()
                    .redactFields("password", "token")
                    .build();

            assertEquals(Set.of("password", "token"), config.redactedBodyFields());
        }

        @Test
        void callsAreCumulative() {
            ClientLoggingConfiguration config = ClientLoggingConfiguration.builder()
                    .redactFields("password")
                    .redactFields("token")
                    .build();

            assertEquals(Set.of("password", "token"), config.redactedBodyFields());
        }

        @Test
        void fieldNamesAreCaseSensitive() {
            ClientLoggingConfiguration config = ClientLoggingConfiguration.builder()
                    .redactFields("Password")
                    .build();

            assertTrue(config.redactedBodyFields().contains("Password"));
            assertFalse(config.redactedBodyFields().contains("password"));
        }

        @Test
        void redactedBodyFields_isUnmodifiable() {
            ClientLoggingConfiguration config = ClientLoggingConfiguration.builder()
                    .redactFields("password")
                    .build();

            assertThrows(UnsupportedOperationException.class,
                    () -> config.redactedBodyFields().add("extra"));
        }
    }

    // -------------------------------------------------------------------------
    // maxBodySize
    // -------------------------------------------------------------------------

    @Nested
    class MaxBodySize {
        @Test
        void negativeValue_throwsNumericValueOutsideRangeException() {
            assertThrows(
                    NumericValueOutsideRangeException.class,
                    () -> ClientLoggingConfiguration.builder().maxBodySize(-1)
            );
        }

        @Test
        void aboveLimit_throwsNumericValueOutsideRangeException() {
            assertThrows(
                    NumericValueOutsideRangeException.class,
                    () -> ClientLoggingConfiguration.builder()
                            .maxBodySize(DefaultClientLoggingConfiguration.MAX_BODY_SIZE_LIMIT + 1)
            );
        }

        @Test
        void zero_isValid() {
            ClientLoggingConfiguration config = ClientLoggingConfiguration.builder()
                    .maxBodySize(0)
                    .build();

            assertEquals(0, config.maxBodySize());
        }

        @Test
        void atLimit_isValid() {
            ClientLoggingConfiguration config = ClientLoggingConfiguration.builder()
                    .maxBodySize(DefaultClientLoggingConfiguration.MAX_BODY_SIZE_LIMIT)
                    .build();

            assertEquals(DefaultClientLoggingConfiguration.MAX_BODY_SIZE_LIMIT, config.maxBodySize());
        }

        @Test
        void customValue_isApplied() {
            ClientLoggingConfiguration config = ClientLoggingConfiguration.builder()
                    .maxBodySize(4096)
                    .build();

            assertEquals(4096, config.maxBodySize());
        }
    }
}

