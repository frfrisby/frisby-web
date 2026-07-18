package software.frisby.web.client.security.basic;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import software.frisby.core.validation.BlankValueException;
import software.frisby.core.validation.NullValueException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CredentialsTest {
    private static final String EXPECTED_TOSTRING = "Credentials{username=alice, password=****}";

    // -------------------------------------------------------------------------
    // username validation
    // -------------------------------------------------------------------------

    @Nested
    class Username {
        @Test
        void nullUsername_throwsNullValueException() {
            assertThrows(
                    NullValueException.class,
                    () -> Credentials.of(null, "s3cr3t")
            );
        }

        @Test
        void blankUsername_throwsBlankValueException() {
            assertThrows(
                    BlankValueException.class,
                    () -> Credentials.of("  ", "s3cr3t")
            );
        }

        @Test
        void emptyUsername_throwsBlankValueException() {
            assertThrows(
                    BlankValueException.class,
                    () -> Credentials.of("", "s3cr3t")
            );
        }
    }

    // -------------------------------------------------------------------------
    // password validation
    // -------------------------------------------------------------------------

    @Nested
    class Password {
        @Test
        void nullPassword_throwsNullValueException() {
            assertThrows(
                    NullValueException.class,
                    () -> Credentials.of("alice", null)
            );
        }

        @Test
        void blankPassword_throwsBlankValueException() {
            assertThrows(
                    BlankValueException.class,
                    () -> Credentials.of("alice", "  ")
            );
        }

        @Test
        void emptyPassword_throwsBlankValueException() {
            assertThrows(
                    BlankValueException.class,
                    () -> Credentials.of("alice", "")
            );
        }
    }

    // -------------------------------------------------------------------------
    // accessors
    // -------------------------------------------------------------------------

    @Nested
    class Accessors {
        @Test
        void username_returnsValue() {
            assertEquals("alice", Credentials.of("alice", "s3cr3t").username());
        }

        @Test
        void password_returnsValue() {
            assertEquals("s3cr3t", Credentials.of("alice", "s3cr3t").password());
        }
    }

    // -------------------------------------------------------------------------
    // toString — password must be redacted
    // -------------------------------------------------------------------------

    @Nested
    class ToString {
        @Test
        void toString_redactsPassword() {
            assertEquals(EXPECTED_TOSTRING, Credentials.of("alice", "s3cr3t").toString());
        }

        @Test
        void toString_doesNotContainPassword() {
            String result = Credentials.of("alice", "s3cr3t").toString();

            assertEquals(-1, result.indexOf("s3cr3t"));
        }
    }

    // -------------------------------------------------------------------------
    // of — factory method
    // -------------------------------------------------------------------------

    @Nested
    class Of {
        @Test
        void of_returnsNewInstance() {
            Credentials a = Credentials.of("alice", "s3cr3t");
            Credentials b = Credentials.of("alice", "s3cr3t");

            assertEquals(a, b);
        }
    }
}

