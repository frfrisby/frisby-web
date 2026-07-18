package software.frisby.web.client.security.basic;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import software.frisby.web.client.security.RequestContext;

import java.net.HttpCookie;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DefaultBearerTokenSecurityProviderTest {
    private static final String AUTHORIZATION = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private static CapturingRequestContext secure(BearerTokenSecurityProvider provider) {
        CapturingRequestContext ctx = new CapturingRequestContext();

        provider.secure(ctx);
        return ctx;
    }

    // -------------------------------------------------------------------------
    // Static token
    // -------------------------------------------------------------------------

    @Nested
    class StaticToken {
        @Test
        void secure_setsBearerHeader() {
            BearerTokenSecurityProvider provider = BearerTokenSecurityProvider.builder()
                    .token("my-static-token")
                    .build();

            CapturingRequestContext ctx = secure(provider);

            assertEquals(BEARER_PREFIX + "my-static-token", ctx.header(AUTHORIZATION));
        }

        @Test
        void secure_sameTokenOnEveryCall() {
            BearerTokenSecurityProvider provider = BearerTokenSecurityProvider.builder()
                    .token("stable-token")
                    .build();

            assertEquals(
                    secure(provider).header(AUTHORIZATION),
                    secure(provider).header(AUTHORIZATION)
            );
        }
    }

    // -------------------------------------------------------------------------
    // Dynamic supplier
    // -------------------------------------------------------------------------

    @Nested
    class DynamicSupplier {
        @Test
        void secure_supplierInvokedOnEachCall() {
            AtomicInteger callCount = new AtomicInteger(0);

            BearerTokenSecurityProvider provider = BearerTokenSecurityProvider.builder()
                    .token(() -> "token-" + callCount.incrementAndGet())
                    .build();

            secure(provider);
            secure(provider);
            secure(provider);

            assertEquals(3, callCount.get());
        }

        @Test
        void secure_headerReflectsCurrentSupplierValue() {
            AtomicInteger counter = new AtomicInteger(0);

            BearerTokenSecurityProvider provider = BearerTokenSecurityProvider.builder()
                    .token(() -> "token-" + counter.incrementAndGet())
                    .build();

            assertEquals(BEARER_PREFIX + "token-1", secure(provider).header(AUTHORIZATION));
            assertEquals(BEARER_PREFIX + "token-2", secure(provider).header(AUTHORIZATION));
        }

        @Test
        void secure_onlyAuthorizationHeaderIsSet() {
            BearerTokenSecurityProvider provider = BearerTokenSecurityProvider.builder()
                    .token("my-token")
                    .build();

            CapturingRequestContext ctx = secure(provider);

            assertEquals(1, ctx.headers.size());
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

