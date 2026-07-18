package software.frisby.web.server;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;

/**
 * Routes {@link WebApplicationException}s through their embedded response, preventing
 * {@link UnhandledExceptionMapper} from treating them as unhandled failures.
 * <p>
 * Without this mapper, {@link UnhandledExceptionMapper} — which is bound to the root
 * {@link Exception} type — would catch all {@link WebApplicationException} subclasses
 * (including Jersey's own {@code NotFoundException} for unmatched routes) and return a
 * plain {@code 500}.  Registering a mapper for {@link WebApplicationException} causes
 * Jersey to prefer this more-specific mapper, preserving the embedded status code.
 * <p>
 * Body stripping for security-sensitive status codes ({@code 401}, {@code 403},
 * {@code 500}) is handled downstream by {@link SecurityResponseFilter}, so this mapper
 * passes every response through unchanged regardless of status.
 * <p>
 * Note: per the JAX-RS specification, this mapper is <em>not</em> invoked when the
 * {@link WebApplicationException}'s embedded response already carries an entity.  In
 * that case the runtime uses the embedded response directly — {@link SecurityResponseFilter}
 * still runs afterward and strips the entity for the three sensitive codes.
 * <p>
 * Registered automatically by {@link DefaultServer}.
 */
final class WebApplicationExceptionMapper implements ExceptionMapper<WebApplicationException> {
    WebApplicationExceptionMapper() {
    }

    @Override
    public Response toResponse(WebApplicationException exception) {
        return exception.getResponse();
    }
}

