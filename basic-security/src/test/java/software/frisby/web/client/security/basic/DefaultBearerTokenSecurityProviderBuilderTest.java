package software.frisby.web.client.security.basic;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import software.frisby.core.validation.BlankValueException;
import software.frisby.core.validation.NullValueException;
import software.frisby.web.client.security.RequestContext;

import java.net.HttpCookie;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DefaultBearerTokenSecurityProviderBuilderTest {
    private static final String MISSING_TOKEN_MESSAGE =
            "The 'token' value is invalid.  A bearer token or token supplier must be provided.";

    // -------------------------------------------------------------------------
    // token(String) validation
    // -------------------------------------------------------------------------

    @Nested
    class StaticToken {
        @Test
        void nullToken_throwsNullValueException() {
            assertThrows(
                    NullValueException.class,
                    () -> BearerTokenSecurityProvider.builder().token((String) null)
            );
        }

        @Test
        void blankToken_throwsBlankValueException() {
            assertThrows(
                    BlankValueException.class,
                    () -> BearerTokenSecurityProvider.builder().token("  ")
            );
        }

        @Test
        void emptyToken_throwsBlankValueException() {
            assertThrows(
                    BlankValueException.class,
                    () -> BearerTokenSecurityProvider.builder().token("")
            );
        }

        @Test
        void validToken_buildsProvider() {
            BearerTokenSecurityProvider provider = BearerTokenSecurityProvider.builder()
                    .token("my-static-token")
                    .build();

            CapturingRequestContext ctx = new CapturingRequestContext();
            provider.secure(ctx);

            assertEquals("Bearer my-static-token", ctx.header("Authorization"));
        }
    }

    // -------------------------------------------------------------------------
    // token(Supplier<String>) validation
    // -------------------------------------------------------------------------

    @Nested
    class SupplierToken {
        @Test
        void nullSupplier_throwsNullValueException() {
            assertThrows(
                    NullValueException.class,
                    () -> BearerTokenSecurityProvider.builder().token((Supplier<String>) null)
            );
        }

        @Test
        void validSupplier_buildsProvider() {
            BearerTokenSecurityProvider provider = BearerTokenSecurityProvider.builder()
                    .token(() -> "dynamic-token")
                    .build();

            CapturingRequestContext ctx = new CapturingRequestContext();
            provider.secure(ctx);

            assertEquals("Bearer dynamic-token", ctx.header("Authorization"));
        }
    }

    // -------------------------------------------------------------------------
    // build — required fields
    // -------------------------------------------------------------------------

    @Nested
    class Build {
        @Test
        void missingToken_throwsIllegalStateException() {
            IllegalStateException ex = assertThrows(
                    IllegalStateException.class,
                    () -> BearerTokenSecurityProvider.builder().build()
            );

            assertEquals(MISSING_TOKEN_MESSAGE, ex.getMessage());
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

