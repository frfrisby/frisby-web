package software.frisby.web.server;

import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class WebApplicationExceptionMapperTest {
    private final WebApplicationExceptionMapper mapper = new WebApplicationExceptionMapper();

    @Test
    void anyStatus_embeddedResponseIsReturned() {
        Response embedded = Response.status(404).build();
        WebApplicationException exception = new WebApplicationException(embedded);

        Response response = mapper.toResponse(exception);

        assertSame(embedded, response);
    }

    @Test
    void subtype_embeddedResponseIsReturned() {
        Response embedded = Response.status(404).build();
        WebApplicationException exception = new NotFoundException(embedded);

        Response response = mapper.toResponse(exception);

        assertSame(embedded, response);
    }

    @Test
    void statusIsPreserved() {
        Response response = mapper.toResponse(new WebApplicationException(418));

        assertEquals(418, response.getStatus());
    }
}

