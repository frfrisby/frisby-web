package software.frisby.web.server;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Maps {@link MappedException} to a {@code 503 Service Unavailable} response.
 * <p>
 * Used in failure-logging integration tests to verify that a non-{@link jakarta.ws.rs.WebApplicationException}
 * exception that is handled gracefully by an {@link ExceptionMapper} is still logged at
 * {@code ERROR} — because the exception represents a genuine infrastructure failure,
 * regardless of the fact that it was mapped to a clean HTTP response.
 */
@Provider
final class MappedExceptionMapper implements ExceptionMapper<MappedException> {
    @Override
    public Response toResponse(MappedException exception) {
        return Response
                .status(Response.Status.SERVICE_UNAVAILABLE)
                .build();
    }
}

