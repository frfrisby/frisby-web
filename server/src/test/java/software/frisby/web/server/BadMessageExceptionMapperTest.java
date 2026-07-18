package software.frisby.web.server;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.jetty.http.BadMessageException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BadMessageExceptionMapperTest {
    private static final BadMessageExceptionMapper MAPPER = new BadMessageExceptionMapper();

    @Nested
    class KnownStatusCodes {
        @Test
        void code413_usesPayloadTooLargeReasonPhrase() {
            Response response = MAPPER.toResponse(new BadMessageException(413));

            assertEquals(413, response.getStatus());
            assertTrue(
                    ((String) response.getEntity()).contains("Payload Too Large"),
                    "Response entity must include the standard 413 reason phrase"
            );
            assertTrue(
                    response.getMediaType().isCompatible(MediaType.APPLICATION_JSON_TYPE),
                    "Content-Type must be application/json"
            );
        }

        @Test
        void code400_usesBadRequestReasonPhrase() {
            Response response = MAPPER.toResponse(new BadMessageException(400));

            assertEquals(400, response.getStatus());
            assertTrue(
                    ((String) response.getEntity()).contains("\"status\":400"),
                    "Response entity must include the status code"
            );
            assertTrue(
                    ((String) response.getEntity()).contains("Bad Request"),
                    "Response entity must include the standard 400 reason phrase"
            );
        }
    }
}


