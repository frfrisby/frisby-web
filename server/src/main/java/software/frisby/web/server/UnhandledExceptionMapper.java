package software.frisby.web.server;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;

/**
 * Catches all exceptions not handled by a more specific {@link ExceptionMapper} and
 * returns a plain {@code 500 Internal Server Error} response with no body.
 * <p>
 * Registered automatically by {@link DefaultServer}.  Ensures that unhandled exceptions
 * never inadvertently return stack traces, internal state, or diagnostic detail to
 * callers — regardless of what the throwing code included in the exception message.
 */
final class UnhandledExceptionMapper implements ExceptionMapper<Exception> {
    UnhandledExceptionMapper() {
    }

    @Override
    public Response toResponse(Exception exception) {
        return Response
                .status(Response.Status.INTERNAL_SERVER_ERROR)
                .build();
    }
}

