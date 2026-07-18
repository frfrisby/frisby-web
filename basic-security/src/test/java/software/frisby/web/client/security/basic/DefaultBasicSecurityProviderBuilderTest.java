package software.frisby.web.client.security.basic;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import software.frisby.core.validation.BlankValueException;
import software.frisby.core.validation.NullValueException;
import software.frisby.web.client.security.RequestContext;

import java.net.HttpCookie;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DefaultBasicSecurityProviderBuilderTest {
    private static final String MISSING_CREDENTIALS_MESSAGE =
            "The 'credentials' value is invalid.  Credentials must be provided before calling build().";

    // -------------------------------------------------------------------------
    // credentials(Credentials) validation
    // -------------------------------------------------------------------------

    @Nested
    class CredentialsObject {
        @Test
        void nullCredentials_throwsNullValueException() {
            assertThrows(
                    NullValueException.class,
                    () -> BasicSecurityProvider.builder().credentials((Credentials) null)
            );
        }

        @Test
        void validCredentials_buildsProvider() {
            BasicSecurityProvider provider = BasicSecurityProvider.builder()
                    .credentials(Credentials.of("alice", "s3cr3t"))
                    .build();

            CapturingRequestContext ctx = new CapturingRequestContext();
            provider.secure(ctx);

            assertEquals("Basic YWxpY2U6czNjcjN0", ctx.header("Authorization"));
        }
    }

    // -------------------------------------------------------------------------
    // credentials(String, String) validation
    // -------------------------------------------------------------------------

    @Nested
    class CredentialsStrings {
        @Test
        void nullUsername_throwsNullValueException() {
            assertThrows(
                    NullValueException.class,
                    () -> BasicSecurityProvider.builder().credentials(null, "s3cr3t")
            );
        }

        @Test
        void blankUsername_throwsBlankValueException() {
            assertThrows(
                    BlankValueException.class,
                    () -> BasicSecurityProvider.builder().credentials("  ", "s3cr3t")
            );
        }

        @Test
        void nullPassword_throwsNullValueException() {
            assertThrows(
                    NullValueException.class,
                    () -> BasicSecurityProvider.builder().credentials("alice", null)
            );
        }

        @Test
        void blankPassword_throwsBlankValueException() {
            assertThrows(
                    BlankValueException.class,
                    () -> BasicSecurityProvider.builder().credentials("alice", "  ")
            );
        }

        @Test
        void validStrings_buildsProvider() {
            BasicSecurityProvider provider = BasicSecurityProvider.builder()
                    .credentials("alice", "s3cr3t")
                    .build();

            CapturingRequestContext ctx = new CapturingRequestContext();
            provider.secure(ctx);

            assertEquals("Basic YWxpY2U6czNjcjN0", ctx.header("Authorization"));
        }
    }

    // -------------------------------------------------------------------------
    // build — required fields
    // -------------------------------------------------------------------------

    @Nested
    class Build {
        @Test
        void missingCredentials_throwsIllegalStateException() {
            IllegalStateException ex = assertThrows(
                    IllegalStateException.class,
                    () -> BasicSecurityProvider.builder().build()
            );

            assertEquals(MISSING_CREDENTIALS_MESSAGE, ex.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Test helper
    // -------------------------------------------------------------------------

    private static final class CapturingRequestContext implements RequestContext {
        private final Map<String, String> headers = new LinkedHashMap<>();

        @Override
        public void addHeader(String name, String value) {
            headers.put(name, value);
        }

        @Override
        public void addCookie(HttpCookie cookie) {
        }

        String header(String name) {
            return headers.get(name);
        }
    }
}

