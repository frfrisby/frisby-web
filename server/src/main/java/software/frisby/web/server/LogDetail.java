package software.frisby.web.server;

import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Static helpers for building failure-log detail strings.
 * <p>
 * Shared by {@link DefaultServer} (JAX-RS / Jersey pipeline) and
 * {@link JsonErrorHandler} (Jetty pre-Jersey layer) so that header masking,
 * cookie redaction, and body-field redaction behave identically regardless of
 * which layer intercepts the request.
 */
final class LogDetail {
    private static final String REDACTED = "[redacted]";

    private LogDetail() {
    }

    // -------------------------------------------------------------------------
    // Request header formatting
    // -------------------------------------------------------------------------

    /**
     * Appends a single request header field to {@code sb}, applying the shared
     * masking and cookie-name-preserving redaction rules.
     * <p>
     * <ul>
     *   <li>{@code Cookie} headers: splits the value on {@code ;} and emits one
     *       {@code Cookie: name=[redacted]} line per cookie, preserving names.</li>
     *   <li>All other masked headers: emits {@code name: [redacted]}.</li>
     *   <li>All other headers: emits {@code name: value} verbatim.</li>
     * </ul>
     * Callers are responsible for the {@code \n  Request Headers:} label and the
     * {@code (none)} fallback when the header map is empty.
     *
     * @param sb     The builder to append to.
     * @param name   The HTTP header name (original casing preserved in output).
     * @param value  The header value — for multi-value JAX-RS headers the caller
     *               pre-joins the list with {@code ", "}; for Jetty {@code HttpField}
     *               the raw value is passed directly.
     * @param masked The set of lower-cased header names whose values must be redacted.
     */
    static void appendRequestHeader(StringBuilder sb,
                                    String name,
                                    String value,
                                    Set<String> masked) {
        String lowerName = name.toLowerCase(Locale.ROOT);

        if (masked.contains(lowerName) && "cookie".equals(lowerName)) {
            // Preserve cookie names, redact values — one line per cookie.
            for (String pair : value.split(";")) {
                String trimmed = pair.trim();

                if (trimmed.isEmpty()) {
                    continue;
                }

                int eq = trimmed.indexOf('=');
                String formatted = eq > 0
                        ? trimmed.substring(0, eq + 1) + REDACTED
                        : trimmed;

                sb.append("\n    ").append(name).append(": ").append(formatted);
            }
        } else {
            String v = masked.contains(lowerName) ? REDACTED : value;
            sb.append("\n    ").append(name).append(": ").append(v);
        }
    }

    // -------------------------------------------------------------------------
    // Set-Cookie response header redaction
    // -------------------------------------------------------------------------

    /**
     * Redacts the cookie value in a {@code Set-Cookie} response header while
     * preserving the cookie name and all security attributes.
     * <p>
     * Input:  {@code session=abc123; Path=/api; Secure; HttpOnly; Max-Age=3600}<br>
     * Output: {@code session=[redacted]; Path=/api; Secure; HttpOnly; Max-Age=3600}
     * <p>
     * Attributes such as {@code Path}, {@code Domain}, {@code Secure},
     * {@code HttpOnly}, {@code SameSite}, and {@code Max-Age} are protocol metadata
     * — not sensitive — and preserving them is useful for auditing the security
     * posture of each cookie.
     */
    static String redactSetCookieHeader(String value) {
        if (null == value || value.isBlank()) {
            return REDACTED;
        }

        // The first segment (before the first ';') is name=value.
        // Everything after is attributes — safe to keep verbatim.
        int firstSemicolon = value.indexOf(';');
        String cookiePair = firstSemicolon >= 0 ? value.substring(0, firstSemicolon) : value;
        String attributes = firstSemicolon >= 0 ? value.substring(firstSemicolon) : "";

        int eq = cookiePair.indexOf('=');

        if (eq > 0) {
            return cookiePair.substring(0, eq + 1) + REDACTED + attributes;
        }

        return REDACTED + attributes;
    }

    // -------------------------------------------------------------------------
    // Body field redaction
    // -------------------------------------------------------------------------

    /**
     * Replaces the string value of each named JSON field with {@code [redacted]}.
     * <p>
     * Handles both compact ({@code "field":"value"}) and pretty-printed
     * ({@code "field": "value"}) JSON.  Only string-typed values are affected —
     * numeric, boolean, and nested-object values are left unchanged.
     */
    static String redactFieldValues(String json, Collection<String> fields) {
        if (null == json || json.isEmpty() || fields.isEmpty()) {
            return json;
        }

        String result = json;

        for (String field : fields) {
            // "fieldname"\s*:\s*"string-value"
            String regex = "\"" + Pattern.quote(field) + "\"\\s*:\\s*\"(?:[^\"\\\\]|\\\\.)*\"";
            result = result.replaceAll(regex, "\"" + field + "\": \"[redacted]\"");
        }

        return result;
    }

    /**
     * Replaces the value of each named {@code application/x-www-form-urlencoded}
     * field with {@code [redacted]}.
     * <p>
     * Handles standard form bodies of the shape {@code key=value&key2=value2}.  Field
     * name matching is exact — {@code password} does not match {@code confirmPassword}.
     * URL-encoded values (e.g. {@code my%20secret}) are replaced in their entirety.
     */
    static String redactFormValues(String form, Collection<String> fields) {
        if (null == form || form.isEmpty() || fields.isEmpty()) {
            return form;
        }

        String result = form;

        for (String field : fields) {
            // Match the field name preceded by start-of-string or '&', followed by '='
            // and any value up to the next '&' or end-of-string.
            String regex = "(?:^|(?<=&))" + Pattern.quote(field) + "=[^&]*";
            result = result.replaceAll(regex, field + "=[redacted]");
        }

        return result;
    }
}

