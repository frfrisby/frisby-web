package software.frisby.web.server;

import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpHeader;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HttpFieldParserTest {

    // -------------------------------------------------------------------------
    // getLongOrDefault
    // -------------------------------------------------------------------------

    @Nested
    class GetLongOrDefault {
        @Test
        void absentHeader_returnsDefault() {
            HttpFields headers = HttpFields.build();

            assertEquals(42L, HttpFieldParser.getLongOrDefault(headers, HttpHeader.CONTENT_LENGTH, 42L));
        }

        @Test
        void validHeader_returnsParsedValue() {
            HttpFields headers = HttpFields.build().add(HttpHeader.CONTENT_LENGTH, "1024");

            assertEquals(1024L, HttpFieldParser.getLongOrDefault(headers, HttpHeader.CONTENT_LENGTH, 0L));
        }

        @Test
        void malformedHeader_returnsDefault() {
            HttpFields headers = HttpFields.build().add(HttpHeader.CONTENT_LENGTH, "not-a-number");

            assertEquals(-1L, HttpFieldParser.getLongOrDefault(headers, HttpHeader.CONTENT_LENGTH, -1L));
        }
    }
}

