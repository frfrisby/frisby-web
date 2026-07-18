package software.frisby.web.server.security.basic;

import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.core.SecurityContext;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import software.frisby.web.server.AuthenticatedIdentity;

import java.security.Principal;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class DefaultBasicAuthAuthenticationProviderTest {
    private static final String VALID_USERNAME = "alice";
    private static final String VALID_PASSWORD = "s3cr3t";
    private static final Principal ALICE = () -> "alice";

    private static final CredentialsValidator ACCEPTING_VALIDATOR =
            (username, password) -> AuthenticatedIdentity.of(ALICE);
    private static final CredentialsValidator REJECTING_VALIDATOR = (username, password) -> {
        throw new NotAuthorizedException(
                jakarta.ws.rs.core.Response.status(jakarta.ws.rs.core.Response.Status.UNAUTHORIZED).build()
        );
    };
    private static final CredentialsValidator FORBIDDEN_VALIDATOR = (username, password) -> {
        throw new ForbiddenException();
    };

    // -------------------------------------------------------------------------
    // accepts()
    // -------------------------------------------------------------------------

    @Nested
    class Accepts {
        @Test
        void basicScheme_returnsTrue() {
            var provider = new DefaultBasicAuthAuthenticationProvider(ACCEPTING_VALIDATOR);
            var ctx = MockRequestContext.withHeader("Authorization", "Basic " + encode(VALID_USERNAME, VALID_PASSWORD));

            assertTrue(provider.accepts(ctx));
        }

        @Test
        void basicSchemeUppercase_returnsTrue() {
            var provider = new DefaultBasicAuthAuthenticationProvider(ACCEPTING_VALIDATOR);
            var ctx = MockRequestContext.withHeader("Authorization", "BASIC " + encode(VALID_USERNAME, VALID_PASSWORD));

            assertTrue(provider.accepts(ctx));
        }

        @Test
        void bearerScheme_returnsFalse() {
            var provider = new DefaultBasicAuthAuthenticationProvider(ACCEPTING_VALIDATOR);
            var ctx = MockRequestContext.withHeader("Authorization", "Bearer some-token");

            assertFalse(provider.accepts(ctx));
        }

        @Test
        void noAuthorizationHeader_returnsFalse() {
            var provider = new DefaultBasicAuthAuthenticationProvider(ACCEPTING_VALIDATOR);
            var ctx = MockRequestContext.withHeader("X-Other-Header", "value");

            assertFalse(provider.accepts(ctx));
        }

        @Test
        void nullAuthorizationHeader_returnsFalse() {
            var provider = new DefaultBasicAuthAuthenticationProvider(ACCEPTING_VALIDATOR);
            var ctx = MockRequestContext.noHeaders();

            assertFalse(provider.accepts(ctx));
        }
    }

    // -------------------------------------------------------------------------
    // authenticate() — success paths
    // -------------------------------------------------------------------------

    @Nested
    class Authenticate {
        @Test
        void validCredentials_returnsPrincipal() {
            var provider = new DefaultBasicAuthAuthenticationProvider(ACCEPTING_VALIDATOR);
            var ctx = MockRequestContext.withHeader("Authorization", "Basic " + encode(VALID_USERNAME, VALID_PASSWORD));

            SecurityContext sc = provider.authenticate(ctx);

            assertEquals("alice", sc.getUserPrincipal().getName());
        }

        @Test
        void authSchemeIsBasic() {
            var provider = new DefaultBasicAuthAuthenticationProvider(ACCEPTING_VALIDATOR);
            var ctx = MockRequestContext.withHeader("Authorization", "Basic " + encode(VALID_USERNAME, VALID_PASSWORD));

            SecurityContext sc = provider.authenticate(ctx);

            assertEquals(SecurityContext.BASIC_AUTH, sc.getAuthenticationScheme());
        }

        @Test
        void passwordWithColons_fullPasswordDeliveredToValidator() {
            // password is "a:b:c" — only first colon is the separator
            String[] capturedPassword = new String[1];
            CredentialsValidator capturingValidator = (username, password) -> {
                capturedPassword[0] = new String(password);
                return AuthenticatedIdentity.of(ALICE);
            };

            var provider = new DefaultBasicAuthAuthenticationProvider(capturingValidator);
            var ctx = MockRequestContext.withHeader("Authorization", "Basic " + encode("user", "a:b:c"));

            provider.authenticate(ctx);

            assertEquals("a:b:c", capturedPassword[0]);
        }

        @Test
        void validatorReceivesCorrectUsername() {
            String[] capturedUsername = new String[1];
            CredentialsValidator capturingValidator = (username, password) -> {
                capturedUsername[0] = username;
                return AuthenticatedIdentity.of(ALICE);
            };

            var provider = new DefaultBasicAuthAuthenticationProvider(capturingValidator);
            var ctx = MockRequestContext.withHeader("Authorization", "Basic " + encode(VALID_USERNAME, VALID_PASSWORD));

            provider.authenticate(ctx);

            assertEquals(VALID_USERNAME, capturedUsername[0]);
        }
    }

    // -------------------------------------------------------------------------
    // authenticate() — failure paths
    // -------------------------------------------------------------------------

    @Nested
    class AuthenticateFailures {
        @Test
        void invalidCredentials_throws401() {
            var provider = new DefaultBasicAuthAuthenticationProvider(REJECTING_VALIDATOR);
            var ctx = MockRequestContext.withHeader("Authorization", "Basic " + encode("wrong", "wrong"));

            assertThrows(
                    NotAuthorizedException.class,
                    () -> provider.authenticate(ctx)
            );
        }

        @Test
        void validatorThrowsForbidden_throws403() {
            var provider = new DefaultBasicAuthAuthenticationProvider(FORBIDDEN_VALIDATOR);
            var ctx = MockRequestContext.withHeader("Authorization", "Basic " + encode(VALID_USERNAME, VALID_PASSWORD));

            assertThrows(
                    ForbiddenException.class,
                    () -> provider.authenticate(ctx)
            );
        }

        @Test
        void invalidBase64_throws401() {
            var provider = new DefaultBasicAuthAuthenticationProvider(ACCEPTING_VALIDATOR);
            var ctx = MockRequestContext.withHeader("Authorization", "Basic not-valid-base64!!!");

            assertThrows(
                    NotAuthorizedException.class,
                    () -> provider.authenticate(ctx)
            );
        }

        @Test
        void noColonInCredentials_throws401() {
            // Base64 encoding of "usernameonly" — no colon
            String encoded = Base64.getEncoder().encodeToString("usernameonly".getBytes());
            var provider = new DefaultBasicAuthAuthenticationProvider(ACCEPTING_VALIDATOR);
            var ctx = MockRequestContext.withHeader("Authorization", "Basic " + encoded);

            assertThrows(
                    NotAuthorizedException.class,
                    () -> provider.authenticate(ctx)
            );
        }
    }

    // -------------------------------------------------------------------------
    // Factory
    // -------------------------------------------------------------------------

    @Nested
    class Factory {
        @Test
        void nullValidator_throwsNullValueException() {
            assertThrows(
                    software.frisby.core.validation.NullValueException.class,
                    () -> BasicAuthAuthenticationProvider.of(null)
            );
        }

        @Test
        void validValidator_returnsProvider() {
            assertNotNull(BasicAuthAuthenticationProvider.of(ACCEPTING_VALIDATOR));
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static String encode(String username, String password) {
        return Base64.getEncoder().encodeToString(
                (username + ":" + password).getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );
    }
}




