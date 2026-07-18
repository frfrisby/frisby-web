package software.frisby.web.server;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.*;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;

import java.util.List;

/**
 * JAX-RS filter that enforces the {@link CorsConfiguration} CORS policy.
 * <p>
 * Registered automatically by {@link DefaultServer} when
 * {@link ServerConfigurationBuilder#cors(CorsConfiguration)} is set.
 * Not part of the public API — use the configuration builder to activate CORS.
 * <p>
 * <strong>Preflight handling ({@link ContainerRequestFilter}):</strong>
 * Runs {@link PreMatching} so OPTIONS requests with CORS preflight headers are
 * intercepted before Jersey performs URI matching.  Allowed preflights are aborted
 * with HTTP 200 and the appropriate {@code Access-Control-*} headers.  Disallowed
 * origins receive HTTP 200 with no CORS headers (the browser rejects the actual
 * request when it sees no {@code Access-Control-Allow-Origin} in the preflight
 * response).
 * <p>
 * <strong>Actual request handling ({@link ContainerResponseFilter}):</strong>
 * Adds {@code Access-Control-Allow-Origin} (and optionally
 * {@code Access-Control-Allow-Credentials} and {@code Vary: Origin}) to all
 * non-preflight responses whose {@code Origin} matches the allowed list.  CORS
 * headers already set by the preflight handler are not duplicated.
 */
@PreMatching
@Priority(Priorities.AUTHENTICATION - 100)
final class CorsFilter implements ContainerRequestFilter, ContainerResponseFilter {
    private static final String HEADER_ORIGIN = "Origin";
    private static final String HEADER_REQUEST_METHOD = "Access-Control-Request-Method";
    private static final String HEADER_REQUEST_HEADERS = "Access-Control-Request-Headers";
    private static final String HEADER_ALLOW_ORIGIN = "Access-Control-Allow-Origin";
    private static final String HEADER_ALLOW_METHODS = "Access-Control-Allow-Methods";
    private static final String HEADER_ALLOW_HEADERS = "Access-Control-Allow-Headers";
    private static final String HEADER_ALLOW_CREDENTIALS = "Access-Control-Allow-Credentials";
    private static final String HEADER_MAX_AGE = "Access-Control-Max-Age";
    private static final String HEADER_VARY = "Vary";

    private static final String WILDCARD = "*";
    private static final String CREDENTIALS_TRUE = "true";
    private static final String PREFLIGHT_MAX_AGE = "3600";

    private final CorsConfiguration corsConfig;

    CorsFilter(CorsConfiguration corsConfig) {
        this.corsConfig = corsConfig;
    }

    // -------------------------------------------------------------------------
    // ContainerRequestFilter — preflight interception
    // -------------------------------------------------------------------------

    @Override
    public void filter(ContainerRequestContext request) {
        String origin = request.getHeaderString(HEADER_ORIGIN);

        if (null == origin) {
            return;
        }

        // A preflight request has BOTH Origin AND Access-Control-Request-Method.
        // A simple cross-origin request has only Origin — handled in the response filter.
        boolean isPreflight = null != request.getHeaderString(HEADER_REQUEST_METHOD)
                && "OPTIONS".equalsIgnoreCase(request.getMethod());

        if (!isPreflight) {
            return;
        }

        if (!isAllowedOrigin(origin)) {
            // Disallowed origin — abort with 200 and no CORS headers.
            // The browser rejects the actual request when it finds no Allow-Origin header.
            request.abortWith(Response.ok().build());
            return;
        }

        Response.ResponseBuilder builder = Response.ok();

        applyOriginHeader(builder, origin);
        builder.header(HEADER_ALLOW_METHODS, String.join(", ", corsConfig.allowedMethods()));

        // Allowed headers — use the configured list, or echo what the client requested.
        if (corsConfig.allowedHeaders() instanceof AllowedHeaders.Explicit explicit) {
            builder.header(
                    HEADER_ALLOW_HEADERS,
                    String.join(", ", explicit.headers())
            );
        } else {
            String requestedHeaders = request.getHeaderString(HEADER_REQUEST_HEADERS);

            if (null != requestedHeaders) {
                builder.header(HEADER_ALLOW_HEADERS, requestedHeaders);
            }
        }

        if (corsConfig.allowCredentials()) {
            builder.header(HEADER_ALLOW_CREDENTIALS, CREDENTIALS_TRUE);
        }

        builder.header(HEADER_MAX_AGE, PREFLIGHT_MAX_AGE);

        request.abortWith(builder.build());
    }

    // -------------------------------------------------------------------------
    // ContainerResponseFilter — response header injection
    // -------------------------------------------------------------------------

    @Override
    public void filter(ContainerRequestContext request, ContainerResponseContext response) {
        String origin = request.getHeaderString(HEADER_ORIGIN);

        if (null == origin) {
            return;
        }

        // Preflight responses already carry CORS headers set by the request filter above.
        // Don't duplicate them.
        MultivaluedMap<String, Object> headers = response.getHeaders();

        if (headers.containsKey(HEADER_ALLOW_ORIGIN)) {
            return;
        }

        if (!isAllowedOrigin(origin)) {
            return;
        }

        applyOriginHeader(headers, origin);

        if (corsConfig.allowCredentials()) {
            headers.add(HEADER_ALLOW_CREDENTIALS, CREDENTIALS_TRUE);
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private boolean isAllowedOrigin(String origin) {
        List<String> allowed = corsConfig.allowedOrigins();

        if (allowed.contains(WILDCARD)) {
            return true;
        }

        for (String allowedOrigin : allowed) {
            if (allowedOrigin.equalsIgnoreCase(origin)) {
                return true;
            }
        }

        return false;
    }

    private void applyOriginHeader(Response.ResponseBuilder builder, String origin) {
        if (corsConfig.allowedOrigins().contains(WILDCARD)) {
            builder.header(HEADER_ALLOW_ORIGIN, WILDCARD);
        } else {
            builder.header(HEADER_ALLOW_ORIGIN, origin);
            builder.header(HEADER_VARY, HEADER_ORIGIN);
        }
    }

    private void applyOriginHeader(MultivaluedMap<String, Object> headers, String origin) {
        if (corsConfig.allowedOrigins().contains(WILDCARD)) {
            headers.add(HEADER_ALLOW_ORIGIN, WILDCARD);
        } else {
            headers.add(HEADER_ALLOW_ORIGIN, origin);
            headers.add(HEADER_VARY, HEADER_ORIGIN);
        }
    }
}

