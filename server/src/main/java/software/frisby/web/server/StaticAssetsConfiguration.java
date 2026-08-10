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
 *         .errorPage(404, "404.html")
 *         .errorPage(500, "500.html")
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
 * <p><strong>Note:</strong> This interface is not intended for external implementation.
 * Use the {@link #classpath(String)} or {@link #filesystem(Path)} factory methods
 * to obtain an instance.
 *
 * @see StaticAssetsConfigurationBuilder
 * @see ServerBuilder
 */
public interface StaticAssetsConfiguration {
    /**
     * Returns a builder pre-configured to serve assets from a classpath resource
     * path embedded in any JAR on the classpath.
     *
     * <p>The {@code resourcePath} must start with {@code /}.  Example: {@code "/web"}
     * serves the contents of {@code src/main/resources/web/} from the calling
     * module's JAR.
     *
     * <p><strong>Existence is not validated at configuration time.</strong>  Classpath
     * resource lookup is classloader-relative; checking here would use the library's
     * own classloader, which cannot see resources embedded in the application's JAR.
     * The path is resolved against the runtime classloader at server startup, and the
     * server will fail to start with a clear error if the path does not exist at that
     * point.  Contrast with {@link #filesystem(Path)}, where directory existence is
     * validated eagerly at configuration time.
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
     * Returns a map of HTTP error status codes to the asset-root-relative path of the
     * file to serve as the response body when that status code is produced by this
     * handler.
     *
     * <p>For example, a mapping of {@code 404 → "404.html"} causes the handler to serve
     * the body of {@code {assetRoot}/404.html} (with HTTP status {@code 404}) whenever a
     * requested resource is not found.  A mapping of {@code 500 → "500.html"} causes the
     * handler to serve {@code {assetRoot}/500.html} when the auth filter throws an
     * unexpected exception.
     *
     * <p>The HTTP response status is always the configured status code; only the response
     * body and {@code Content-Type} are taken from the configured file.  If the file itself
     * is not found in the asset root at request time, a plain error response with no custom
     * body is returned.
     *
     * <p>Returns an empty map when no error pages have been configured.
     *
     * @return an unmodifiable map of HTTP status codes to relative file paths; never {@code null}
     */
    Map<Integer, String> errorPages();

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

    /**
     * Returns the classpath resource path if this configuration was created via
     * {@link #classpath(String)}, or {@link Optional#empty()} if it was created via
     * {@link #filesystem(Path)}.
     *
     * @return the classpath resource path, or {@link Optional#empty()} for filesystem sources
     */
    Optional<String> classpathResourcePath();

    /**
     * Returns the filesystem directory if this configuration was created via
     * {@link #filesystem(Path)}, or {@link Optional#empty()} if it was created via
     * {@link #classpath(String)}.
     *
     * @return the filesystem directory, or {@link Optional#empty()} for classpath sources
     */
    Optional<Path> filesystemDirectory();

    /**
     * Returns a human-readable description of the asset source for use in
     * logging and diagnostics, e.g. {@code "classpath:/web"} or
     * {@code "/var/app/static"}.
     *
     * @return a non-null, non-blank source description
     */
    default String describeSource() {
        return classpathResourcePath()
                .map(p -> "classpath:" + p)
                .orElseGet(() -> filesystemDirectory()
                        .map(Path::toString)
                        .orElse("unknown"));
    }
}

