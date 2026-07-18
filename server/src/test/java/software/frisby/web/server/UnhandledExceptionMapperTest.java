package software.frisby.web.server;

import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class UnhandledExceptionMapperTest {
    private final UnhandledExceptionMapper mapper = new UnhandledExceptionMapper();

    @Test
    void anyException_returns500() {
        Response response = mapper.toResponse(new RuntimeException("boom"));

        assertEquals(500, response.getStatus());
    }

    @Test
    void anyException_bodyIsNull() {
        Response response = mapper.toResponse(new RuntimeException("boom"));

        assertNull(response.getEntity());
    }

    @Test
    void checkedExceptionSubtype_returns500() {
        Response response = mapper.toResponse(new Exception("checked"));

        assertEquals(500, response.getStatus());
    }
}

