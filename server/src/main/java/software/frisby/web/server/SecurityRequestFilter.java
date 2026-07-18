package software.frisby.web.server;

import jakarta.annotation.Priority;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

import java.util.List;

/**
 * JAX-RS {@code ContainerRequestFilter} that enforces authentication via a list of
 * {@link AuthenticationProvider} instances.
 * <p>
 * Runs at {@link Priorities#AUTHENTICATION} priority (post-matching).  On each request:
 * <ol>
 *   <li>The health check path (if configured) is bypassed — liveness probes must never
 *       require credentials.</li>
 *   <li>Providers are tried in insertion order.  The first provider whose
 *       {@link AuthenticationProvider#accepts accepts()} method returns {@code true} is
 *       asked to {@link AuthenticationProvider#authenticate authenticate()} the request.</li>
 *   <li>If {@code authenticate()} succeeds, the resulting {@link jakarta.ws.rs.core.SecurityContext}
 *       is installed on the request context and processing continues.</li>
 *   <li>If {@code authenticate()} throws {@link NotAuthorizedException} or
 *       {@link ForbiddenException} the exception propagates unchanged.</li>
 *   <li>Any other exception thrown by {@code authenticate()} is wrapped in a
 *       {@code 500 Internal Server Error} WebApplicationException so that stack traces
 *       and internal state never reach callers.</li>
 *   <li>If no provider's {@code accepts()} method returns {@code true} a
 *       {@code 401 Unauthorized} response is returned immediately.</li>
 * </ol>
 * <p>
 * Registered by {@code DefaultServer.buildResourceConfig()} only when the authentication
 * provider list is non-empty.
 */
@Priority(Priorities.AUTHENTICATION)
final class SecurityRequestFilter implements ContainerRequestFilter {
    private final List<AuthenticationProvider> providers;
    private final String healthCheckPath;

    SecurityRequestFilter(List<AuthenticationProvider> providers, String healthCheckPath) {
        this.providers = providers;
        this.healthCheckPath = healthCheckPath;
    }

    @Override
    public void filter(ContainerRequestContext context) {
        // Health check bypass — liveness probes must never require credentials.
        // getAbsolutePath().getPath() returns the full path with leading '/', matching
        // the /health (or custom) path registered on the builder.
        if (null != healthCheckPath) {
            String requestPath = context.getUriInfo().getAbsolutePath().getPath();

            if (healthCheckPath.equals(requestPath)) {
                return;
            }
        }

        for (AuthenticationProvider provider : providers) {
            if (provider.accepts(context)) {
                SecurityContext sc;

                try {
                    sc = provider.authenticate(context);
                } catch (NotAuthorizedException | ForbiddenException ex) {
                    throw ex;
                } catch (Exception ex) {
                    throw new jakarta.ws.rs.InternalServerErrorException(ex);
                }

                if (null == sc) {
                    throw new jakarta.ws.rs.InternalServerErrorException(
                            "The 'SecurityContext' value is invalid." +
                                    "  AuthenticationProvider.authenticate() must not return null."
                    );
                }

                context.setSecurityContext(sc);
                return;
            }
        }

        // No provider accepted the request.
        throw new NotAuthorizedException(
                Response.status(Response.Status.UNAUTHORIZED).build()
        );
    }
}




