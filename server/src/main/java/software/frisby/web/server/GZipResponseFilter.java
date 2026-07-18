package software.frisby.web.server;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;

import java.io.InputStream;

/**
 * JAX-RS response filter that enables gzip compression of JSON responses when the
 * client advertises support via {@code Accept-Encoding: gzip}.
 * <p>
 * Registered automatically by {@link DefaultServer} when
 * {@link ServerConfigurationBuilder#gzip()} is set.  Not part of the public API —
 * users who need a custom compression policy register their own
 * {@code ContainerResponseFilter} via {@link ServerBuilder#components(Object...)}.
 * <p>
 * This filter sets {@code Content-Encoding: gzip} when all three conditions are met:
 * <ul>
 *   <li>The response entity is not an {@link InputStream}.</li>
 *   <li>The response media type is {@code application/json}.</li>
 *   <li>The client included {@code gzip} in its {@code Accept-Encoding} header.</li>
 * </ul>
 * Actual byte compression is performed by Jersey's {@code GZipEncoder}, which is
 * always registered alongside this filter by the {@code gzip()} knob.
 */
@Priority(Priorities.ENTITY_CODER)
final class GZipResponseFilter implements ContainerResponseFilter {
    GZipResponseFilter() {
    }

    private static boolean shouldCompress(ContainerRequestContext requestContext,
                                          ContainerResponseContext responseContext) {
        if (responseContext.getEntity() instanceof InputStream) {
            return false;
        }

        if (null == responseContext.getMediaType()) {
            return false;
        }

        if (!MediaType.APPLICATION_JSON_TYPE.isCompatible(responseContext.getMediaType())) {
            return false;
        }

        String acceptEncoding = requestContext.getHeaders().getFirst(HttpHeaders.ACCEPT_ENCODING);

        return null != acceptEncoding && acceptEncoding.contains("gzip");
    }

    @Override
    public void filter(ContainerRequestContext requestContext,
                       ContainerResponseContext responseContext) {
        if (shouldCompress(requestContext, responseContext)) {
            responseContext.getHeaders().add(HttpHeaders.CONTENT_ENCODING, "gzip");
        }
    }
}

