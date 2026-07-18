package software.frisby.web.server;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.HttpHeaders;

import java.lang.annotation.Annotation;
import java.util.Set;

/**
 * Strips the response entity for security-sensitive status codes before the response
 * is written to the wire, preventing internal state from reaching callers.
 * <p>
 * Entities are stripped for:
 * <ul>
 *   <li>{@code 401 Unauthorized} — authentication challenges must not reveal token
 *       structures, user identities, or credential requirements beyond the
 *       {@code WWW-Authenticate} header.</li>
 *   <li>{@code 403 Forbidden} — authorization failures must not reveal permission
 *       models, role structures, or resource ownership to the requester.</li>
 *   <li>{@code 500 Internal Server Error} — infrastructure failures must not return
 *       internal state, service addresses, or error context to the caller.</li>
 * </ul>
 * All other status codes (e.g. {@code 400}, {@code 404}, {@code 422}) pass through
 * unchanged so that error detail useful to callers is preserved.
 * <p>
 * This filter runs at {@code Priorities.USER} (5000), which is higher than
 * {@code Priorities.ENTITY_CODER} (4000) used by {@link GZipResponseFilter}.
 * Because response filters execute in descending priority order, this filter runs
 * before the gzip filter — ensuring that stripping prevents a spurious
 * {@code Content-Encoding: gzip} header from being added to an empty body.
 * <p>
 * Registered automatically by {@link DefaultServer}.
 */
final class SecurityResponseFilter implements ContainerResponseFilter {
    private static final Set<Integer> STRIPPED_STATUS_CODES = Set.of(401, 403, 500);

    SecurityResponseFilter() {
    }

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
        if (STRIPPED_STATUS_CODES.contains(responseContext.getStatus())) {
            responseContext.setEntity(null, new Annotation[0], null);
            responseContext.getHeaders().remove(HttpHeaders.CONTENT_TYPE);
        }
    }
}

