package software.frisby.web.server;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link LogDetail} static helpers.
 * <p>
 * Tests focus on edge cases that cannot be reliably exercised through the
 * full integration test stack (e.g. empty cookie segments, Set-Cookie headers
 * with no {@code =} sign, and redaction methods whose early-return paths are
 * not reachable when the redaction config is always non-empty).
 */
class LogDetailTest {
    // -------------------------------------------------------------------------
    // appendRequestHeader — Cookie edge cases
    // -------------------------------------------------------------------------

    @Nested
    class AppendRequestHeaderCookieEdgeCases {
        private static final Set<String> MASKED = Set.of("cookie");

        @Test
        void cookieHeaderWithBlankSegment_blankSegmentIsSkipped() {
            // A leading semicolon produces a blank first token after split(";").
            // The blank segment must be skipped silently (continue at L56).
            StringBuilder sb = new StringBuilder();

            LogDetail.appendRequestHeader(sb, "Cookie", "; session=abc123", MASKED);

            String result = sb.toString();

            // Only the non-blank cookie must appear.
            assertTrue(result.contains("session=[redacted]"),
                    "Non-blank cookie should be present: " + result);
            assertFalse(result.contains(": =[redacted]"),
                    "Blank cookie name must not produce an output line: " + result);
        }

        @Test
        void cookieHeaderWithNoEqualsSign_cookieNameRenderedWithoutValue() {
            // A cookie token with no '=' (e.g. a bare flag) — eq > 0 is false so
            // the token is rendered as-is rather than name=[redacted].
            StringBuilder sb = new StringBuilder();

            LogDetail.appendRequestHeader(sb, "Cookie", "baretoken", MASKED);

            String result = sb.toString();

            assertTrue(result.contains("Cookie: baretoken"),
                    "Bare token must be rendered as-is: " + result);
        }
    }

    // -------------------------------------------------------------------------
    // redactSetCookieHeader — edge cases
    // -------------------------------------------------------------------------

    @Nested
    class RedactSetCookieHeaderEdgeCases {
        @Test
        void nullValue_returnsRedacted() {
            assertEquals("[redacted]", LogDetail.redactSetCookieHeader(null));
        }

        @Test
        void blankValue_returnsRedacted() {
            assertEquals("[redacted]", LogDetail.redactSetCookieHeader("   "));
        }

        @Test
        void cookiePairWithNoEqualsSign_returnsRedactedPlusAttributes() {
            // A Set-Cookie value whose name=value portion contains no '='.
            // eq <= 0 → return "[redacted]" + attributes.
            String result = LogDetail.redactSetCookieHeader("novalue; Path=/; Secure");

            assertEquals("[redacted]; Path=/; Secure", result);
        }

        @Test
        void cookiePairWithValueAndAttributes_valueIsRedacted() {
            // Normal Set-Cookie: name=value; attributes.
            String result = LogDetail.redactSetCookieHeader("session=abc123; Path=/; HttpOnly");

            assertEquals("session=[redacted]; Path=/; HttpOnly", result);
        }

        @Test
        void cookieWithValueButNoAttributes_valueIsRedacted() {
            // Set-Cookie with no attributes at all — firstSemicolon == -1.
            // cookiePair == full value, attributes == "".
            String result = LogDetail.redactSetCookieHeader("session=abc123");

            assertEquals("session=[redacted]", result);
        }

        @Test
        void cookieWithNoEqualsAndNoAttributes_returnsRedacted() {
            // A bare token with no '=' and no attributes — firstSemicolon == -1,
            // eq <= 0 → fallback "[redacted]" with empty attributes string.
            String result = LogDetail.redactSetCookieHeader("baretoken");

            assertEquals("[redacted]", result);
        }
    }

    // -------------------------------------------------------------------------
    // redactFieldValues — no-match early return
    // -------------------------------------------------------------------------

    @Nested
    class RedactFieldValuesEdgeCases {
        @Test
        void noMatchingFields_returnsOriginalJson() {
            String json = "{\"username\":\"frank\",\"role\":\"admin\"}";
            List<String> fields = List.of("password", "token");

            // None of the field names appear in the JSON → result must be unchanged.
            String result = LogDetail.redactFieldValues(json, fields);

            assertEquals(json, result);
        }

        @Test
        void nullJson_returnsNull() {
            List<String> fields = List.of("password", "token");
            assertNull(LogDetail.redactFieldValues(null, fields));
        }

        @Test
        void emptyJson_returnsEmpty() {
            String json = "";
            List<String> fields = List.of("password", "token");

            // None of the field names appear in the JSON → result must be unchanged.
            String result = LogDetail.redactFieldValues(json, fields);

            assertEquals(json, result);
        }

        @Test
        void emptyFieldsList_returnsOriginalJson() {
            String json = "{\"password\":\"secret\"}";

            String result = LogDetail.redactFieldValues(json, List.of());

            assertEquals(json, result);
        }
    }

    // -------------------------------------------------------------------------
    // redactFormValues — no-match early return
    // -------------------------------------------------------------------------

    @Nested
    class RedactFormValuesEdgeCases {
        @Test
        void noMatchingFields_returnsOriginalForm() {
            String form = "username=frank&role=admin";
            List<String> fields = List.of("password", "token");

            // None of the field names appear in the form → result must be unchanged.
            String result = LogDetail.redactFormValues(form, fields);

            assertEquals(form, result);
        }

        @Test
        void nullForm_returnsNull() {
            List<String> fields = List.of("password", "token");
            assertNull(LogDetail.redactFormValues(null, fields));
        }

        @Test
        void emptyForm_returnsEmpty() {
            String form = "";
            List<String> fields = List.of("password", "token");

            // None of the field names appear in the form → result must be unchanged.
            String result = LogDetail.redactFormValues(form, fields);

            assertEquals(form, result);
        }

        @Test
        void emptyFieldsList_returnsOriginalForm() {
            String form = "password=secret&confirm=secret";

            String result = LogDetail.redactFormValues(form, List.of());

            assertEquals(form, result);
        }
    }
}

