package software.frisby.web.server;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import software.frisby.core.validation.NullValueException;

import java.security.Principal;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AuthenticatedIdentityTest {
    private static final Principal ALICE = () -> "alice";

    // -------------------------------------------------------------------------
    // of(Principal)
    // -------------------------------------------------------------------------

    @Nested
    class SingleArgFactory {
        @Test
        void returnsPrincipal() {
            AuthenticatedIdentity identity = AuthenticatedIdentity.of(ALICE);

            assertEquals(ALICE, identity.principal());
        }

        @Test
        void rolesAreEmpty() {
            AuthenticatedIdentity identity = AuthenticatedIdentity.of(ALICE);

            assertTrue(identity.roles().isEmpty());
        }

        @Test
        void nullPrincipal_throwsNullValueException() {
            assertThrows(
                    NullValueException.class,
                    () -> AuthenticatedIdentity.of(null)
            );
        }
    }

    // -------------------------------------------------------------------------
    // of(Principal, Set<String>)
    // -------------------------------------------------------------------------

    @Nested
    class TwoArgFactory {
        @Test
        void returnsPrincipal() {
            AuthenticatedIdentity identity = AuthenticatedIdentity.of(ALICE, Set.of("ADMIN"));

            assertEquals(ALICE, identity.principal());
        }

        @Test
        void returnsRoles() {
            AuthenticatedIdentity identity = AuthenticatedIdentity.of(ALICE, Set.of("ADMIN", "USER"));

            assertEquals(Set.of("ADMIN", "USER"), identity.roles());
        }

        @Test
        void emptyRoles_isAccepted() {
            AuthenticatedIdentity identity = AuthenticatedIdentity.of(ALICE, Set.of());

            assertTrue(identity.roles().isEmpty());
        }

        @Test
        void rolesAreImmutable() {
            AuthenticatedIdentity identity = AuthenticatedIdentity.of(ALICE, Set.of("ADMIN"));

            assertThrows(
                    UnsupportedOperationException.class,
                    () -> identity.roles().add("EXTRA")
            );
        }

        @Test
        void nullPrincipal_throwsNullValueException() {
            assertThrows(
                    NullValueException.class,
                    () -> AuthenticatedIdentity.of(null, Set.of("ADMIN"))
            );
        }

        @Test
        void nullRoles_throwsNullValueException() {
            assertThrows(
                    NullValueException.class,
                    () -> AuthenticatedIdentity.of(ALICE, null)
            );
        }
    }

    // -------------------------------------------------------------------------
    // Record equality
    // -------------------------------------------------------------------------

    @Nested
    class Equality {
        @Test
        void sameComponents_areEqual() {
            AuthenticatedIdentity a = AuthenticatedIdentity.of(ALICE, Set.of("ADMIN"));
            AuthenticatedIdentity b = AuthenticatedIdentity.of(ALICE, Set.of("ADMIN"));

            assertEquals(a, b);
        }

        @Test
        void differentRoles_areNotEqual() {
            AuthenticatedIdentity a = AuthenticatedIdentity.of(ALICE, Set.of("ADMIN"));
            AuthenticatedIdentity b = AuthenticatedIdentity.of(ALICE, Set.of("USER"));

            assertNotEquals(a, b);
        }
    }
}

