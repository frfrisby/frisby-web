package software.frisby.web.server;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;

/**
 * A filter invoked before each static asset request is served.
 *
 * <p>Implement to add authentication or authorization to static asset serving.
 * When the filter returns {@code false} the static file is not served; the handler
 * then checks whether a custom error page has been configured (via
 * {@link StaticAssetsConfigurationBuilder#errorPage(int, String)}) for the current
 * response status code.  If a page is configured and the response has not already
 * been committed, the handler serves it automatically.  If no page is configured for
 * that status, or if the filter already committed the response, the response is left
 * as-is and the handler completes normally.
 *
 * <p>Practical patterns:
 * <ul>
 *   <li><strong>Delegate body to error page</strong> — set the status on the response and
 *       return {@code false}; the handler will serve the configured error page for that
 *       status code automatically:
 *       <pre>{@code
 *       (req, res) -> {
 *           if (!isValid(token(req))) {
 *               res.setStatus(401);
 *               return false;
 *           }
 *           return true;
 *       }
 *       }</pre>
 *   <li><strong>Write a full response directly</strong> — write the complete response
 *       (status, headers, and body) and return {@code false}; the handler will not
 *       interfere because the response is already committed.
 * </ul>
 *
 * <p>If the filter throws an exception, the handler catches it, logs it at
 * {@code ERROR} level, and serves the error page configured for status {@code 500}
 * (if any); otherwise it writes a plain {@code 500 Internal Server Error}.
 *
 * <p><strong>Jersey exception mappers do not apply here.</strong>  This filter
 * runs at the Jetty handler layer, ahead of Jersey.
 *
 * <p>Filters must be thread-safe; a single instance is shared across all
 * concurrent requests.
 *
 * @see StaticAssetsConfigurationBuilder#authFilter(StaticAssetsAuthFilter)
 */
@FunctionalInterface
public interface StaticAssetsAuthFilter {
    /**
     * Evaluates whether the request should be allowed to proceed.
     *
     * @param request  the incoming Jetty request; never {@code null}
     * @param response the outgoing Jetty response; set the status code here when
     *                 delegating the body to a configured error page, or write a
     *                 full response before returning {@code false} if you need
     *                 full control of the error body
     * @return {@code true} to allow the request to proceed and serve the file;
     * {@code false} to block it
     * @throws RuntimeException if an error occurs during the authorization check (e.g. an
     *                          {@link java.io.UncheckedIOException} wrapping an
     *                          {@link java.io.IOException} from a remote auth service); the
     *                          handler catches this, logs it, and serves the configured
     *                          {@code 500} error page (if any), or returns a plain
     *                          {@code 500 Internal Server Error}
     */
    boolean authorize(Request request, Response response);
}
