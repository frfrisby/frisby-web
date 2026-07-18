package software.frisby.web.server;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import org.eclipse.jetty.http.BadMessageException;
import org.eclipse.jetty.http.HttpStatus;

/**
 * Maps Jetty's {@link BadMessageException} to a well-formed JSON error response.
 * <p>
 * Jetty's {@link org.eclipse.jetty.server.handler.SizeLimitHandler} throws
 * {@code BadMessageException(413)} when the request body exceeds the configured
 * limit while it is being read — specifically for chunked-transfer requests
 * (no {@code Content-Length} header).  For requests with a known
 * {@code Content-Length}, {@code SizeLimitHandler} rejects the request before
 * it reaches Jersey, so {@link JsonErrorHandler} handles those instead.
 * <p>
 * Without this mapper, {@link UnhandledExceptionMapper} would catch the exception
 * and return a plain 500, even though the HTTP status embedded in the
 * {@code BadMessageException} is 413 (Payload Too Large).  This mapper ensures
 * the correct status and a JSON body consistent with {@link JsonErrorHandler}.
 * <p>
 * Response body format:
 * <pre>{@code {"status":413,"message":"Payload Too Large"}}</pre>
 */
final class BadMessageExceptionMapper implements ExceptionMapper<BadMessageException> {
    BadMessageExceptionMapper() {
    }

    @Override
    public Response toResponse(BadMessageException exception) {
        int status = exception.getCode();
        String reasonPhrase = HttpStatus.getMessage(status);
        String json = "{\"status\":" + status + ",\"message\":\"" + reasonPhrase + "\"}";

        return Response
                .status(status)
                .type(MediaType.APPLICATION_JSON)
                .entity(json)
                .build();
    }
}


