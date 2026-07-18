package software.frisby.web.client.security.oauth2;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import software.frisby.core.validation.BlankValueException;
import software.frisby.core.validation.NullValueException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ClientCredentialsTest {
    private static final String EXPECTED_TOSTRING =
            "ClientCredentials{clientId=my-client-id, clientSecret=***}";

    // -------------------------------------------------------------------------
    // clientId validation
    // -------------------------------------------------------------------------

    @Nested
    class ClientId {
        @Test
        void nullClientId_throwsNullValueException() {
            assertThrows(
                    NullValueException.class,
                    () -> ClientCredentials.of(null, "my-secret")
            );
        }

        @Test
        void blankClientId_throwsBlankValueException() {
            assertThrows(
                    BlankValueException.class,
                    () -> ClientCredentials.of("  ", "my-secret")
            );
        }

        @Test
        void emptyClientId_throwsBlankValueException() {
            assertThrows(
                    BlankValueException.class,
                    () -> ClientCredentials.of("", "my-secret")
            );
        }
    }

    // -------------------------------------------------------------------------
    // clientSecret validation
    // -------------------------------------------------------------------------

    @Nested
    class ClientSecret {
        @Test
        void nullClientSecret_throwsNullValueException() {
            assertThrows(
                    NullValueException.class,
                    () -> ClientCredentials.of("my-client-id", null)
            );
        }

        @Test
        void blankClientSecret_throwsBlankValueException() {
            assertThrows(
                    BlankValueException.class,
                    () -> ClientCredentials.of("my-client-id", "  ")
            );
        }

        @Test
        void emptyClientSecret_throwsBlankValueException() {
            assertThrows(
                    BlankValueException.class,
                    () -> ClientCredentials.of("my-client-id", "")
            );
        }
    }

    // -------------------------------------------------------------------------
    // accessors
    // -------------------------------------------------------------------------

    @Nested
    class Accessors {
        @Test
        void clientId_returnsValue() {
            assertEquals("my-client-id", ClientCredentials.of("my-client-id", "my-secret").clientId());
        }

        @Test
        void clientSecret_returnsValue() {
            assertEquals("my-secret", ClientCredentials.of("my-client-id", "my-secret").clientSecret());
        }
    }

    // -------------------------------------------------------------------------
    // toString — clientSecret must be redacted
    // -------------------------------------------------------------------------

    @Nested
    class ToString {
        @Test
        void toString_redactsClientSecret() {
            assertEquals(EXPECTED_TOSTRING, ClientCredentials.of("my-client-id", "my-secret").toString());
        }

        @Test
        void toString_doesNotContainSecret() {
            String result = ClientCredentials.of("my-client-id", "my-secret").toString();

            assertEquals(-1, result.indexOf("my-secret"));
        }
    }

    // -------------------------------------------------------------------------
    // of — factory method
    // -------------------------------------------------------------------------

    @Nested
    class Of {
        @Test
        void of_returnsEqualInstance() {
            ClientCredentials a = ClientCredentials.of("my-client-id", "my-secret");
            ClientCredentials b = ClientCredentials.of("my-client-id", "my-secret");

            assertEquals(a, b);
        }
    }
}

