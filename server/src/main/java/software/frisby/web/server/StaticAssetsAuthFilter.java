package software.frisby.web.server;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;

/**
 * A filter invoked before each static asset request is served.
 *
 * <p>Implement to add authentication or authorization to static asset serving.
 * If the filter returns {@code false}, the static file will not be served.
 * The filter <strong>must</strong> write a complete error response — status
 * code and any relevant headers such as {@code WWW-Authenticate} — to
 * {@code response} before returning {@code false}.  Failing to write a response
 * before returning {@code false} will leave the client with an empty reply.
 *
 * <p><strong>Jersey exception mappers do not apply here.</strong>  This filter
 * runs at the Jetty handler layer, ahead of Jersey.  Write all rejection
 * responses directly to the provided {@link Response}; do not throw exceptions
 * as a rejection signal.  Implementations that call checked-exception-throwing
 * code (JWT parsers, database lookups, etc.) should catch those exceptions,
 * write an appropriate error response, and return {@code false}.
 *
 * <p>Filters must be thread-safe; a single instance is shared across all
 * concurrent requests.
 *
 * <p>Example — Bearer token guard:
 * <pre>{@code
 * StaticAssetsAuthFilter guard = (request, response) -> {
 *     String token = extractBearerToken(request);
 *     if (null == token || !tokenStore.isValid(token)) {
 *         response.setStatus(401);
 *         return false;
 *     }
 *     return true;
 * };
 * }</pre>
 *
 * @see StaticAssetsConfigurationBuilder#authFilter(StaticAssetsAuthFilter)
 */
@FunctionalInterface
public interface StaticAssetsAuthFilter {

    /**
     * Evaluates whether the request should be allowed to proceed.
     *
     * <p>When this method returns {@code false}, the static file is not served.
     * The filter <strong>must</strong> write a complete error response — status
     * code and any relevant headers such as {@code WWW-Authenticate} — to
     * {@code response} before returning {@code false}.  Failing to write a response
     * before returning {@code false} will leave the client with an empty reply.
     *
     * <p>This filter runs at the Jetty handler layer, ahead of Jersey.  Jersey
     * exception mappers and response filters are <strong>not</strong> applied to
     * responses written here.  Implementations that call checked-exception-throwing
     * code (JWT parsers, database lookups, etc.) should catch those exceptions,
     * write an appropriate error response (e.g. {@code 500 Internal Server Error}),
     * and return {@code false} rather than allowing exceptions to propagate.
     *
     * @param request  the incoming Jetty request; never {@code null}
     * @param response the outgoing Jetty response; write the error response to it
     *                 before returning {@code false}
     * @return {@code true} to allow the request to proceed and serve the file;
     * {@code false} to block it — the filter must have written a response first
     */
    boolean authorize(Request request, Response response);
}
