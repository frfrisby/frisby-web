package software.frisby.web.server.security.oauth2;

import jakarta.ws.rs.ForbiddenException;
import java.security.Principal;
import software.frisby.web.server.AuthenticatedIdentity;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;


import static org.junit.jupiter.api.Assertions.*;

class DefaultBearerTokenAuthenticationProviderTest {
    private static final String VALID_TOKEN = "valid.jwt.token";
    private static final Principal BOB = () -> "bob";

    private static final BearerTokenValidator ACCEPTING_VALIDATOR =
            token -> AuthenticatedIdentity.of(BOB);
    private static final BearerTokenValidator REJECTING_VALIDATOR = token -> {
        throw new NotAuthorizedException(
                Response.status(Response.Status.UNAUTHORIZED).build()
        );
    };
    private static final BearerTokenValidator FORBIDDEN_VALIDATOR = token -> {
        throw new ForbiddenException();
    };

    // -------------------------------------------------------------------------
    // accepts()
    // -------------------------------------------------------------------------

    @Nested
    class Accepts {
        @Test
        void bearerScheme_returnsTrue() {
            var provider = new DefaultBearerTokenAuthenticationProvider(ACCEPTING_VALIDATOR);
            var ctx = MockRequestContext.withHeader("Authorization", "Bearer " + VALID_TOKEN);

            assertTrue(provider.accepts(ctx));
        }

        @Test
        void bearerSchemeUppercase_returnsTrue() {
            var provider = new DefaultBearerTokenAuthenticationProvider(ACCEPTING_VALIDATOR);
            var ctx = MockRequestContext.withHeader("Authorization", "BEARER " + VALID_TOKEN);

            assertTrue(provider.accepts(ctx));
        }

        @Test
        void basicScheme_returnsFalse() {
            var provider = new DefaultBearerTokenAuthenticationProvider(ACCEPTING_VALIDATOR);
            var ctx = MockRequestContext.withHeader("Authorization", "Basic dXNlcjpwYXNz");

            assertFalse(provider.accepts(ctx));
        }

        @Test
        void noAuthorizationHeader_returnsFalse() {
            var provider = new DefaultBearerTokenAuthenticationProvider(ACCEPTING_VALIDATOR);
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
        void validToken_returnsPrincipal() {
            var provider = new DefaultBearerTokenAuthenticationProvider(ACCEPTING_VALIDATOR);
            var ctx = MockRequestContext.withHeader("Authorization", "Bearer " + VALID_TOKEN);

            SecurityContext sc = provider.authenticate(ctx);

            assertEquals("bob", sc.getUserPrincipal().getName());
        }

        @Test
        void authSchemeIsBearer() {
            var provider = new DefaultBearerTokenAuthenticationProvider(ACCEPTING_VALIDATOR);
            var ctx = MockRequestContext.withHeader("Authorization", "Bearer " + VALID_TOKEN);

            SecurityContext sc = provider.authenticate(ctx);

            assertEquals("BEARER", sc.getAuthenticationScheme());
        }

        @Test
        void validatorReceivesRawToken() {
            String[] capturedToken = new String[1];
            BearerTokenValidator capturingValidator = token -> {
                capturedToken[0] = token;
                return AuthenticatedIdentity.of(BOB);
            };

            var provider = new DefaultBearerTokenAuthenticationProvider(capturingValidator);
            var ctx = MockRequestContext.withHeader("Authorization", "Bearer " + VALID_TOKEN);

            provider.authenticate(ctx);

            assertEquals(VALID_TOKEN, capturedToken[0]);
        }
    }

    // -------------------------------------------------------------------------
    // authenticate() — failure paths
    // -------------------------------------------------------------------------

    @Nested
    class AuthenticateFailures {
        @Test
        void invalidToken_throws401() {
            var provider = new DefaultBearerTokenAuthenticationProvider(REJECTING_VALIDATOR);
            var ctx = MockRequestContext.withHeader("Authorization", "Bearer bad-token");

            assertThrows(
                    NotAuthorizedException.class,
                    () -> provider.authenticate(ctx)
            );
        }

        @Test
        void validatorThrowsForbidden_throws403() {
            var provider = new DefaultBearerTokenAuthenticationProvider(FORBIDDEN_VALIDATOR);
            var ctx = MockRequestContext.withHeader("Authorization", "Bearer " + VALID_TOKEN);

            assertThrows(
                    ForbiddenException.class,
                    () -> provider.authenticate(ctx)
            );
        }

        @Test
        void emptyTokenAfterBearer_throws401() {
            var provider = new DefaultBearerTokenAuthenticationProvider(ACCEPTING_VALIDATOR);
            var ctx = MockRequestContext.withHeader("Authorization", "Bearer   ");

            assertThrows(
                    NotAuthorizedException.class,
                    () -> provider.authenticate(ctx)
            );
        }

        @Test
        void extraFieldsAfterToken_throws401() {
            var provider = new DefaultBearerTokenAuthenticationProvider(ACCEPTING_VALIDATOR);
            var ctx = MockRequestContext.withHeader("Authorization", "Bearer valid.jwt.token extra garbage");

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
                    () -> BearerTokenAuthenticationProvider.of(null)
            );
        }

        @Test
        void validValidator_returnsProvider() {
            assertNotNull(BearerTokenAuthenticationProvider.of(ACCEPTING_VALIDATOR));
        }
    }
}

