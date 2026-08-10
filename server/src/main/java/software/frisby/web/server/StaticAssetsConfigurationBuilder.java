package software.frisby.web.server;

import java.time.Duration;
import java.util.Map;

/**
 * A builder for creating a {@link StaticAssetsConfiguration}.
 *
 * <p>Obtain a builder via
 * {@link StaticAssetsConfiguration#classpath(String)} or
 * {@link StaticAssetsConfiguration#filesystem(java.nio.file.Path)}.
 * All methods return {@code this} builder for chaining.  Call
 * {@link #build()} when configuration is complete.
 *
 * <pre>{@code
 * StaticAssetsConfiguration assets = StaticAssetsConfiguration.classpath("/web")
 *         .urlPrefix("/ui")
 *         .spaFallback(true)
 *         .errorPage(404, "404.html")
 *         .errorPage(500, "500.html")
 *         .cacheMaxAge(Duration.ofDays(7))
 *         .responseHeaders(Map.of(
 *                 "Content-Security-Policy", "default-src 'self'",
 *                 "X-Frame-Options", "DENY",
 *                 "X-Content-Type-Options", "nosniff"
 *         ))
 *         .build();
 * }</pre>
 *
 * @see StaticAssetsConfiguration
 */
public interface StaticAssetsConfigurationBuilder {

    /**
     * Sets the URL prefix under which assets are served.
     *
     * <p>Defaults to {@code "/"}, which serves any path not claimed by a JAX-RS
     * endpoint.  Use a sub-path such as {@code "/ui"} to scope assets to a
     * dedicated URL namespace while keeping the root available for JAX-RS.
     *
     * @param prefix the URL prefix; must not be {@code null} or blank, and must
     *               start with {@code /}
     * @return this builder
     * @throws software.frisby.core.validation.NullValueException       if {@code prefix} is {@code null}
     * @throws software.frisby.core.validation.BlankValueException      if {@code prefix} is blank
     * @throws software.frisby.core.validation.PatternMismatchException if {@code prefix} does not start
     *                                                                  with {@code /}
     */
    StaticAssetsConfigurationBuilder urlPrefix(String prefix);

    /**
     * Sets the {@code Cache-Control} max-age applied to all asset responses.
     *
     * <p>When set, the response header is
     * {@code Cache-Control: max-age=<seconds>, public}.
     * Pass {@link Duration#ZERO} to emit
     * {@code Cache-Control: max-age=0, no-cache} and force revalidation on every
     * request.  Omitting this call emits no {@code Cache-Control} header and
     * browser defaults apply.
     *
     * @param maxAge the max-age duration; must not be {@code null} and must not be
     *               negative
     * @return this builder
     * @throws software.frisby.core.validation.NullValueException            if {@code maxAge} is {@code null}
     * @throws software.frisby.core.validation.DurationOutsideRangeException if {@code maxAge} is negative
     */
    StaticAssetsConfigurationBuilder cacheMaxAge(Duration maxAge);

    /**
     * Adds HTTP response headers emitted with every asset response from this
     * handler.
     *
     * <p>Intended for security headers such as {@code Content-Security-Policy},
     * {@code X-Frame-Options}, {@code X-Content-Type-Options},
     * {@code Referrer-Policy}, and {@code Permissions-Policy}.  Header names and
     * values are written verbatim; the library performs no semantic validation.
     *
     * <p>Calling this method multiple times merges the maps.  When the same header
     * name appears in more than one call, the value from the later call overwrites
     * the earlier one.
     *
     * <p><strong>One value per header name.</strong>  Headers that require multiple
     * separate field lines — most notably {@code Set-Cookie} — are not supported by
     * this method.  Use {@link #authFilter(StaticAssetsAuthFilter)} to write such
     * headers directly via the raw Jetty {@link org.eclipse.jetty.server.Response}.
     *
     * @param headers a map of header names to values; must not be {@code null};
     *                no key or value in the map may be {@code null}
     * @return this builder
     * @throws software.frisby.core.validation.NullValueException if {@code headers} is {@code null}
     * @throws software.frisby.core.validation.NullValueException if any key or value in
     *                                                            {@code headers} is {@code null}
     */
    StaticAssetsConfigurationBuilder responseHeaders(Map<String, String> headers);

    /**
     * Enables or disables the SPA index fallback.
     *
     * <p>When enabled, a {@code 404} response from the static handler for a path
     * with no file extension is replaced by a {@code 200} response serving
     * {@code index.html} from the asset root.  This enables single-page application
     * client-side routing (React Router, Vue Router, etc.) so that deep links and
     * browser refreshes work correctly.
     *
     * <p>Paths with a file extension (e.g. {@code /logo.png}) that resolve to a
     * missing file still return {@code 404} — the extension guard prevents silently
     * serving HTML in place of a missing image, script, or stylesheet.
     *
     * <p>Defaults to {@code false}.
     *
     * @param enabled {@code true} to enable SPA fallback; {@code false} to disable it
     * @return this builder
     */
    StaticAssetsConfigurationBuilder spaFallback(boolean enabled);

    /**
     * Maps an HTTP error status code to a custom response page served from the asset root.
     *
     * <p>{@code path} is relative to the asset root.  For example,
     * {@code errorPage(404, "404.html")} serves {@code {assetRoot}/404.html} whenever a
     * requested resource is not found.  {@code errorPage(500, "500.html")} serves
     * {@code {assetRoot}/500.html} when the auth filter throws an unexpected exception.
     * The HTTP response status is always the supplied {@code statusCode}; only the body
     * and {@code Content-Type} are taken from the file.
     *
     * <p>Calling this method multiple times with the same {@code statusCode} overwrites
     * the earlier mapping — last call wins, consistent with
     * {@link #responseHeaders(java.util.Map)} merging behavior.
     *
     * <p>Each configured path is validated for readability against the asset root at
     * server startup.  If the file does not exist at that point the server will refuse
     * to start with a clear error message.
     *
     * @param statusCode the HTTP error status code to handle; must be between 400 and 599
     *                   (inclusive)
     * @param path       the path to the error page, relative to the asset root; must not
     *                   be {@code null} or blank
     * @return this builder
     * @throws software.frisby.core.validation.NumericValueOutsideRangeException if {@code statusCode}
     *                                                                           is outside the range
     *                                                                           400–599
     * @throws software.frisby.core.validation.NullValueException                if {@code path} is
     *                                                                           {@code null}
     * @throws software.frisby.core.validation.BlankValueException               if {@code path} is blank
     */
    StaticAssetsConfigurationBuilder errorPage(int statusCode, String path);

    /**
     * Registers an auth filter that is invoked before each asset request is served.
     *
     * <p>The filter receives the raw Jetty request and response.  If the filter
     * returns {@code false}, the asset is not served.  The handler then checks whether
     * a custom error page has been configured for the current response status code via
     * {@link #errorPage(int, String)}: if one is found and the response has not already
     * been committed, the handler serves it automatically.  If no error page is configured
     * for that status, or if the filter already committed the response, the response is
     * left as-is and the handler completes normally.
     *
     * <p>If the filter throws an exception, the handler catches it, logs it, and serves
     * the error page configured for status {@code 500} (if any); otherwise it returns a
     * plain {@code 500 Internal Server Error}.
     *
     * <p>Use this to add authentication or authorization to static asset serving
     * without coupling the configuration to a specific security module.
     *
     * @param filter the auth filter to register; must not be {@code null}
     * @return this builder
     * @throws software.frisby.core.validation.NullValueException if {@code filter} is {@code null}
     */
    StaticAssetsConfigurationBuilder authFilter(StaticAssetsAuthFilter filter);

    /**
     * Builds and returns the {@link StaticAssetsConfiguration}.
     *
     * @return a new {@link StaticAssetsConfiguration}; never {@code null}
     */
    StaticAssetsConfiguration build();
}



