package software.frisby.web.server;

import jakarta.ws.rs.core.SecurityContext;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import software.frisby.core.validation.NullValueException;

import java.security.Principal;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ServerSecurityContextTest {
    private static final Principal PRINCIPAL = () -> "alice";

    // -------------------------------------------------------------------------
    // of(Principal)
    // -------------------------------------------------------------------------

    @Nested
    class SingleArgFactory {
        @Test
        void returnsCorrectPrincipal() {
            SecurityContext ctx = ServerSecurityContext.of(PRINCIPAL);

            assertEquals(PRINCIPAL, ctx.getUserPrincipal());
        }

        @Test
        void isNotSecure() {
            SecurityContext ctx = ServerSecurityContext.of(PRINCIPAL);

            assertFalse(ctx.isSecure());
        }

        @Test
        void schemeIsNull() {
            SecurityContext ctx = ServerSecurityContext.of(PRINCIPAL);

            assertNull(ctx.getAuthenticationScheme());
        }

        @Test
        void noRoles_isUserInRoleReturnsFalse() {
            SecurityContext ctx = ServerSecurityContext.of(PRINCIPAL);

            assertFalse(ctx.isUserInRole("ADMIN"));
        }

        @Test
        void nullPrincipal_throwsNullValueException() {
            assertThrows(
                    NullValueException.class,
                    () -> ServerSecurityContext.of(null)
            );
        }
    }

    // -------------------------------------------------------------------------
    // of(Principal, Set<String>)
    // -------------------------------------------------------------------------

    @Nested
    class TwoArgFactory {
        @Test
        void returnsCorrectPrincipal() {
            SecurityContext ctx = ServerSecurityContext.of(PRINCIPAL, Set.of("USER"));

            assertEquals(PRINCIPAL, ctx.getUserPrincipal());
        }

        @Test
        void matchingRole_isUserInRoleReturnsTrue() {
            SecurityContext ctx = ServerSecurityContext.of(PRINCIPAL, Set.of("ADMIN", "USER"));

            assertTrue(ctx.isUserInRole("ADMIN"));
            assertTrue(ctx.isUserInRole("USER"));
        }

        @Test
        void nonMatchingRole_isUserInRoleReturnsFalse() {
            SecurityContext ctx = ServerSecurityContext.of(PRINCIPAL, Set.of("USER"));

            assertFalse(ctx.isUserInRole("ADMIN"));
        }

        @Test
        void emptyRoles_isUserInRoleReturnsFalse() {
            SecurityContext ctx = ServerSecurityContext.of(PRINCIPAL, Set.of());

            assertFalse(ctx.isUserInRole("USER"));
        }

        @Test
        void isNotSecure() {
            SecurityContext ctx = ServerSecurityContext.of(PRINCIPAL, Set.of("USER"));

            assertFalse(ctx.isSecure());
        }

        @Test
        void schemeIsNull() {
            SecurityContext ctx = ServerSecurityContext.of(PRINCIPAL, Set.of("USER"));

            assertNull(ctx.getAuthenticationScheme());
        }

        @Test
        void nullPrincipal_throwsNullValueException() {
            assertThrows(
                    NullValueException.class,
                    () -> ServerSecurityContext.of(null, Set.of("USER"))
            );
        }

        @Test
        void nullRoles_throwsNullValueException() {
            assertThrows(
                    NullValueException.class,
                    () -> ServerSecurityContext.of(PRINCIPAL, null)
            );
        }
    }

    // -------------------------------------------------------------------------
    // of(Principal, Set<String>, boolean, String)
    // -------------------------------------------------------------------------

    @Nested
    class FullFactory {
        @Test
        void returnsCorrectPrincipal() {
            SecurityContext ctx = ServerSecurityContext.of(PRINCIPAL, Set.of("ADMIN"), true, "BEARER");

            assertEquals(PRINCIPAL, ctx.getUserPrincipal());
        }

        @Test
        void secureTrue_isSecureReturnsTrue() {
            SecurityContext ctx = ServerSecurityContext.of(PRINCIPAL, Set.of(), true, "BASIC");

            assertTrue(ctx.isSecure());
        }

        @Test
        void secureFalse_isSecureReturnsFalse() {
            SecurityContext ctx = ServerSecurityContext.of(PRINCIPAL, Set.of(), false, "BASIC");

            assertFalse(ctx.isSecure());
        }

        @Test
        void schemeIsReturned() {
            SecurityContext ctx = ServerSecurityContext.of(PRINCIPAL, Set.of(), true, "BEARER");

            assertEquals("BEARER", ctx.getAuthenticationScheme());
        }

        @Test
        void nullScheme_isReturned() {
            SecurityContext ctx = ServerSecurityContext.of(PRINCIPAL, Set.of(), false, null);

            assertNull(ctx.getAuthenticationScheme());
        }

        @Test
        void matchingRole_isUserInRoleReturnsTrue() {
            SecurityContext ctx = ServerSecurityContext.of(PRINCIPAL, Set.of("ADMIN"), true, "BEARER");

            assertTrue(ctx.isUserInRole("ADMIN"));
        }

        @Test
        void nonMatchingRole_isUserInRoleReturnsFalse() {
            SecurityContext ctx = ServerSecurityContext.of(PRINCIPAL, Set.of("USER"), true, "BEARER");

            assertFalse(ctx.isUserInRole("ADMIN"));
        }

        @Test
        void nullPrincipal_throwsNullValueException() {
            assertThrows(
                    NullValueException.class,
                    () -> ServerSecurityContext.of(null, Set.of(), true, "BEARER")
            );
        }

        @Test
        void nullRoles_throwsNullValueException() {
            assertThrows(
                    NullValueException.class,
                    () -> ServerSecurityContext.of(PRINCIPAL, null, true, "BEARER")
            );
        }
    }
}

