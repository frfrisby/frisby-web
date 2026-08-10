package software.frisby.web.server;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 * Configuration for serving static files alongside JAX-RS endpoints.
 *
 * <p>Obtain a builder via the static {@link #classpath(String)} or
 * {@link #filesystem(Path)} factory methods:
 *
 * <pre>{@code
 * // Serve embedded classpath resources at /
 * StaticAssetsConfiguration assets = StaticAssetsConfiguration.classpath("/web")
 *         .spaFallback(true)
 *         .notFoundPage("404.html")
 *         .responseHeaders(Map.of(
 *                 "Content-Security-Policy", "default-src 'self'",
 *                 "X-Frame-Options", "DENY",
 *                 "X-Content-Type-Options", "nosniff"
 *         ))
 *         .build();
 *
 * // Serve files from a local directory at /docs
 * StaticAssetsConfiguration docs = StaticAssetsConfiguration
 *         .filesystem(Path.of("/var/app/docs"))
 *         .urlPrefix("/docs")
 *         .cacheMaxAge(Duration.ofHours(1))
 *         .build();
 * }</pre>
 *
 * <p>Pass the built configuration to {@code ServerBuilder.staticAssets()}.  That
 * method may be called multiple times to register independent handlers at different
 * URL prefixes.
 *
 * <p><strong>Dotfile protection:</strong> Files whose name starts with {@code .}
 * (such as {@code .env} or {@code .htpasswd}) are always blocked with
 * {@code 404} — this behavior cannot be disabled.
 *
 * <p><strong>JAX-RS priority:</strong> JAX-RS endpoints always take priority over
 * static files.  Static handlers only receive requests that were not matched by
 * any registered JAX-RS resource.
 *
 * @see StaticAssetsConfigurationBuilder
 * @see ServerBuilder
 */
public interface StaticAssetsConfiguration {

    /**
     * Returns a builder pre-configured to serve assets from a classpath resource
     * path embedded in any JAR on the classpath.
     *
     * <p>The {@code resourcePath} must start with {@code /} and the path must exist
     * on the classpath at server startup time.  Example: {@code "/web"} serves the
     * contents of {@code src/main/resources/web/} from the calling module's JAR.
     *
     * @param resourcePath the classpath resource path to serve from; must not be
     *                     {@code null} or blank, and must start with {@code /}
     * @return a new {@link StaticAssetsConfigurationBuilder}; never {@code null}
     * @throws software.frisby.core.validation.NullValueException       if {@code resourcePath} is {@code null}
     * @throws software.frisby.core.validation.BlankValueException      if {@code resourcePath} is blank
     * @throws software.frisby.core.validation.PatternMismatchException if {@code resourcePath} does not
     *                                                                  start with {@code /}
     */
    static StaticAssetsConfigurationBuilder classpath(String resourcePath) {
        return new DefaultStaticAssetsConfigurationBuilder(resourcePath);
    }

    /**
     * Returns a builder pre-configured to serve assets from a directory on the
     * local file system.
     *
     * <p>The {@code directory} must exist and be a readable directory at server
     * startup time.  Suitable for containerized deployments where static assets
     * are mounted into the container's file system at a known path.
     *
     * @param directory the file system directory to serve from; must not be
     *                  {@code null}, must exist, and must be a directory
     * @return a new {@link StaticAssetsConfigurationBuilder}; never {@code null}
     * @throws software.frisby.core.validation.NullValueException       if {@code directory} is {@code null}
     * @throws software.frisby.core.validation.DisallowedValueException if {@code directory} does not exist
     *                                                                  or is not a directory
     */
    static StaticAssetsConfigurationBuilder filesystem(Path directory) {
        return new DefaultStaticAssetsConfigurationBuilder(directory);
    }

    /**
     * Returns the URL path prefix under which assets are served.
     *
     * <p>Defaults to {@code "/"}, which serves any path not claimed by a
     * JAX-RS endpoint.  Use a sub-path such as {@code "/ui"} to scope assets
     * to a dedicated URL namespace.
     *
     * @return the URL prefix; never {@code null} or blank; always starts with {@code /}
     */
    String urlPrefix();

    /**
     * Returns the {@code Cache-Control} max-age applied to all asset responses,
     * or empty if no {@code Cache-Control} header should be emitted.
     *
     * <p>When present, the response header is
     * {@code Cache-Control: max-age=<seconds>, public}.  When the duration is
     * {@link Duration#ZERO}, the header is {@code Cache-Control: max-age=0, no-cache}.
     *
     * @return the configured max-age, or {@link Optional#empty()} if not set
     */
    Optional<Duration> cacheMaxAge();

    /**
     * Returns additional HTTP response headers emitted with every asset response.
     *
     * <p>Intended for security headers such as {@code Content-Security-Policy},
     * {@code X-Frame-Options}, {@code X-Content-Type-Options},
     * {@code Referrer-Policy}, and {@code Permissions-Policy}.  Header names and
     * values are written verbatim; the library performs no semantic validation.
     *
     * <p>Returns an empty map when no extra headers have been configured.
     *
     * @return an unmodifiable map of header names to values; never {@code null}
     */
    Map<String, String> responseHeaders();

    /**
     * Returns {@code true} if the SPA index fallback is enabled.
     *
     * <p>When {@code true}, a {@code 404} from the static handler for a path with
     * no file extension is converted to a {@code 200} response serving
     * {@code index.html} from the asset root.  This enables single-page application
     * client-side routing (e.g. React Router, Vue Router): deep links and browser
     * refreshes on client-side routes receive the application shell rather than a
     * {@code 404}.
     *
     * <p>Paths with a file extension that resolve to a missing file still return
     * {@code 404} — the extension guard prevents silently serving HTML in place of
     * a missing image, script, or stylesheet.
     *
     * <p>Defaults to {@code false}.
     *
     * @return {@code true} if SPA fallback is enabled; {@code false} otherwise
     */
    boolean spaFallback();

    /**
     * Returns the path, relative to the asset root, of the file to serve as the
     * body of {@code 404} responses, or empty if no custom 404 page is configured.
     *
     * <p>The HTTP response status is always {@code 404}; only the response body
     * and {@code Content-Type} are taken from the configured file.  If the file
     * itself is not found in the asset root, a plain {@code 404} with no body is
     * returned.
     *
     * <p>Example: {@code "404.html"} resolves to {@code {assetRoot}/404.html}.
     *
     * @return the relative path of the custom 404 page, or {@link Optional#empty()}
     * if not set
     */
    Optional<String> notFoundPage();

    /**
     * Returns the auth filter to invoke before serving each asset, or empty if no
     * filter has been configured.
     *
     * <p>When empty, all requests are served without any authentication or
     * authorisation check.
     *
     * @return the configured {@link StaticAssetsAuthFilter}, or
     * {@link Optional#empty()} if not set
     */
    Optional<StaticAssetsAuthFilter> authFilter();
}





