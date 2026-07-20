package software.frisby.web.server;

import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpHeader;

final class HttpFieldParser {
    private HttpFieldParser() {}

    /**
     * Returns the numeric value of the named header field, or {@code defaultValue} if
     * the field is absent or its value cannot be parsed as a {@code long}.
     *
     * @param headers      The Jetty {@link HttpFields} to read from.
     * @param header       The header to look up.
     * @param defaultValue The value to return when the field is absent or malformed.
     * @return The parsed field value, or {@code defaultValue}.
     */
    static long getLongOrDefault(HttpFields headers, HttpHeader header, long defaultValue) {
        String value = headers.get(header);

        if (null == value) {
            return defaultValue;
        }

        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }
}
