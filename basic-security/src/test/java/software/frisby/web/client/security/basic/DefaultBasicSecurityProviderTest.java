package software.frisby.web.client.security.basic;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import software.frisby.web.client.security.RequestContext;

import java.net.HttpCookie;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultBasicSecurityProviderTest {
    private static final String AUTHORIZATION = "Authorization";
    private static final String BASIC_PREFIX = "Basic ";

    private static BasicSecurityProvider provider(String username, String password) {
        return BasicSecurityProvider.builder()
                .credentials(username, password)
                .build();
    }

    private static CapturingRequestContext secure(BasicSecurityProvider provider) {
        CapturingRequestContext ctx = new CapturingRequestContext();

        provider.secure(ctx);
        return ctx;
    }

    // -------------------------------------------------------------------------
    // Authorization header
    // -------------------------------------------------------------------------

    @Nested
    class AuthorizationHeader {
        @Test
        void secure_setsAuthorizationHeader() {
            CapturingRequestContext ctx = secure(provider("alice", "s3cr3t"));

            assertTrue(ctx.header(AUTHORIZATION) != null);
        }

        @Test
        void secure_headerStartsWithBasicPrefix() {
            CapturingRequestContext ctx = secure(provider("alice", "s3cr3t"));
            String header = ctx.header(AUTHORIZATION);

            assertTrue(header.startsWith(BASIC_PREFIX));
        }

        @Test
        void secure_headerContainsCorrectBase64Token() {
            String expected = BASIC_PREFIX + Base64.getEncoder()
                    .encodeToString("alice:s3cr3t".getBytes(StandardCharsets.UTF_8));

            CapturingRequestContext ctx = secure(provider("alice", "s3cr3t"));

            assertEquals(expected, ctx.header(AUTHORIZATION));
        }
    }

    // -------------------------------------------------------------------------
    // Base64 encoding correctness
    // -------------------------------------------------------------------------

    @Nested
    class Base64Encoding {
        @Test
        void encoding_colonSeparatesUsernameAndPassword() {
            CapturingRequestContext ctx = secure(provider("alice", "s3cr3t"));
            String token = ctx.header(AUTHORIZATION).substring(BASIC_PREFIX.length());
            String decoded = new String(Base64.getDecoder().decode(token), StandardCharsets.UTF_8);

            assertEquals("alice:s3cr3t", decoded);
        }

        @Test
        void encoding_usesUtf8() {
            // Username with non-ASCII characters — UTF-8 encoding must be used
            String username = "üser";
            String password = "pässwørd";
            String expectedDecoded = username + ":" + password;
            String expectedToken = BASIC_PREFIX + Base64.getEncoder()
                    .encodeToString(expectedDecoded.getBytes(StandardCharsets.UTF_8));

            CapturingRequestContext ctx = secure(provider(username, password));

            assertEquals(expectedToken, ctx.header(AUTHORIZATION));
        }

        @Test
        void encoding_passwordWithColonIsPreservedVerbatim() {
            // RFC 7617 §2: password may contain colons; only the first colon is the separator
            String password = "p:a:s:s";
            String expectedDecoded = "alice:p:a:s:s";
            String expectedToken = BASIC_PREFIX + Base64.getEncoder()
                    .encodeToString(expectedDecoded.getBytes(StandardCharsets.UTF_8));

            CapturingRequestContext ctx = secure(provider("alice", password));

            assertEquals(expectedToken, ctx.header(AUTHORIZATION));
        }

        @Test
        void knownCredentials_matchesExpectedToken() {
            // "alice:s3cr3t" in Base64 → YWxpY2U6czNjcjN0
            CapturingRequestContext ctx = secure(provider("alice", "s3cr3t"));

            assertEquals("Basic YWxpY2U6czNjcjN0", ctx.header(AUTHORIZATION));
        }
    }

    // -------------------------------------------------------------------------
    // Each call to secure() sets the header (token is pre-computed and stable)
    // -------------------------------------------------------------------------

    @Nested
    class RepeatedCalls {
        @Test
        void secure_calledTwice_sameHeaderBothTimes() {
            BasicSecurityProvider provider = provider("alice", "s3cr3t");
            CapturingRequestContext ctx1 = secure(provider);
            CapturingRequestContext ctx2 = secure(provider);

            assertEquals(ctx1.header(AUTHORIZATION), ctx2.header(AUTHORIZATION));
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

