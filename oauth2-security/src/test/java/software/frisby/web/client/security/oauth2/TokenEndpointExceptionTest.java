package software.frisby.web.client.security.oauth2;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenEndpointExceptionTest {
    private static final URI TOKEN_ENDPOINT = URI.create("https://auth.example.com/oauth/token");

    // -------------------------------------------------------------------------
    // accessors
    // -------------------------------------------------------------------------

    @Nested
    class Accessors {
        @Test
        void tokenEndpoint_returnsUri() {
            TokenEndpointException ex = new TokenEndpointException(TOKEN_ENDPOINT, 401, null);

            assertEquals(TOKEN_ENDPOINT, ex.tokenEndpoint());
        }

        @Test
        void statusCode_returnsValue() {
            TokenEndpointException ex = new TokenEndpointException(TOKEN_ENDPOINT, 401, null);

            assertEquals(401, ex.statusCode());
        }

        @Test
        void statusCode_zeroForProtocolError() {
            TokenEndpointException ex = new TokenEndpointException(TOKEN_ENDPOINT, 0, null);

            assertEquals(0, ex.statusCode());
        }

        @Test
        void body_emptyWhenNull() {
            TokenEndpointException ex = new TokenEndpointException(TOKEN_ENDPOINT, 401, null);

            assertFalse(ex.body().isPresent());
        }

        @Test
        void body_presentWhenSet() {
            TokenEndpointException ex = new TokenEndpointException(
                    TOKEN_ENDPOINT,
                    401,
                    "{\"error\":\"unauthorized_client\"}"
            );

            assertTrue(ex.body().isPresent());
            assertEquals("{\"error\":\"unauthorized_client\"}", ex.body().get());
        }
    }

    // -------------------------------------------------------------------------
    // toString
    // -------------------------------------------------------------------------

    @Nested
    class ToString {
        @Test
        void toString_withStatusAndBody() {
            TokenEndpointException ex = new TokenEndpointException(
                    TOKEN_ENDPOINT,
                    401,
                    "{\"error\":\"unauthorized_client\"}"
            );

            String result = ex.toString();

            assertTrue(result.contains("https://auth.example.com/oauth/token"));
            assertTrue(result.contains("401"));
            assertTrue(result.contains("unauthorized_client"));
        }

        @Test
        void toString_withStatusNoBody() {
            TokenEndpointException ex = new TokenEndpointException(TOKEN_ENDPOINT, 503, null);
            String result = ex.toString();

            assertTrue(result.contains("https://auth.example.com/oauth/token"));
            assertTrue(result.contains("503"));
        }

        @Test
        void toString_withStatusZero_omitsArrow() {
            TokenEndpointException ex = new TokenEndpointException(TOKEN_ENDPOINT, 0, null);
            String result = ex.toString();

            assertFalse(result.contains("→ 0"));
        }

        @Test
        void toString_longBody_truncatedTo200Chars() {
            String longBody = "x".repeat(300);
            TokenEndpointException ex = new TokenEndpointException(TOKEN_ENDPOINT, 400, longBody);
            String result = ex.toString();

            // The body preview is limited to 200 characters followed by "…"
            assertTrue(result.contains("…"));
            // The full 300-char body should not appear verbatim
            assertFalse(result.contains(longBody));
        }

        @Test
        void toString_exactlyMaxLengthBody_notTruncated() {
            String body = "x".repeat(200);
            TokenEndpointException ex = new TokenEndpointException(TOKEN_ENDPOINT, 400, body);
            String result = ex.toString();

            // Should not be truncated — no ellipsis
            assertTrue(result.contains(body));
            assertFalse(result.endsWith("…"));
        }

        @Test
        void toString_blankBody_omitsBody() {
            TokenEndpointException ex = new TokenEndpointException(TOKEN_ENDPOINT, 503, "   ");
            String result = ex.toString();

            // blank body is treated as absent
            assertFalse(result.contains("   "));
        }
    }
}

