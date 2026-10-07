package software.frisby.web.server;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.EntityTag;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Request;
import jakarta.ws.rs.core.Response;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.http.MimeTypes;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.util.resource.Resources;
import org.glassfish.jersey.process.Inflector;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.Date;
import java.util.Map;
import java.util.Optional;

/**
 * SPA fallback Inflector — serves {@code index.html} for an unmatched extensionless GET.
 * <p>
 * Serves {@code index.html} from the asset root of a {@link StaticAssetsConfiguration}
 * that has {@link StaticAssetsConfiguration#spaFallback()} enabled.
 * <p>
 * Only ever invoked by Jersey for a path that (1) matched no more specific JAX-RS
 * resource and (2) {@link StaticHandler} already confirmed does not correspond to a real
 * static file under this configuration's asset root. Otherwise {@code StaticHandler}
 * would have served it directly, upstream of Jersey entirely (see that class's Javadoc).
 * The extension guard below is defense-in-depth, not the primary guard: the primary guard
 * is {@code StaticHandler.serveMatchedRequest} only returning {@code false} (passing the
 * request through to Jersey) for extensionless misses in the first place.
 * <p>
 * Behaves the same as a direct request for a real static file with respect to every other
 * {@link StaticAssetsConfigurationBuilder} option: {@link StaticAssetsConfiguration#cacheMaxAge()}
 * controls {@code Cache-Control}; conditional requests (weak {@code ETag} / {@code Last-Modified})
 * are honored and answered with {@code 304} when unchanged; {@link StaticAssetsConfiguration#preCompressed()}
 * serves a {@code .br} or {@code .gz} sibling of {@code index.html} when the client advertises
 * support (Brotli preferred); and {@link StaticAssetsConfiguration#responseHeaders()} is applied
 * last, same as every other asset response.
 * <p>
 * Registered once per {@link StaticAssetsConfiguration} with {@code spaFallback()} enabled, by
 * {@code DefaultServer.buildResourceConfig()}, as the handler for a low-priority catch-all
 * {@code "{path:.*}"} JAX-RS resource.
 */
// java:S2143 — jakarta.ws.rs.core.Request.evaluatePreconditions(Date, EntityTag) and
// Response.ResponseBuilder.lastModified(Date) only accept java.util.Date; there is no
// java.time overload, so Date cannot be avoided here.
@SuppressWarnings("java:S2143")
final class SpaFallbackInflector implements Inflector<ContainerRequestContext, Response> {
    private static final String PATH_SEPARATOR = "/";
    private static final String ACCEPT_ENCODING = "Accept-Encoding";
    private static final String CONTENT_ENCODING = "Content-Encoding";
    private static final String VARY = "Vary";
    private static final String BROTLI_ENCODING = "br";
    private static final String GZIP_ENCODING = "gzip";

    private final StaticAssetsConfiguration configuration;
    private final Resource baseResource;
    private final Resource indexResource;

    SpaFallbackInflector(StaticAssetsConfiguration configuration, Resource baseResource) {
        this.configuration = configuration;
        this.baseResource = baseResource;
        this.indexResource = baseResource.resolve(StaticAssetsResourceResolver.INDEX_HTML);
    }

    private static boolean hasFileExtension(String path) {
        int lastSlash = path.lastIndexOf('/');
        String lastSegment = (lastSlash >= 0) ? path.substring(lastSlash + 1) : path;
        int lastDot = lastSegment.lastIndexOf('.');

        return lastDot > 0 && lastDot < lastSegment.length() - 1;
    }

    /**
     * Reads the full contents of {@code resource}.
     *
     * @throws UncheckedIOException if the read fails — a readable-at-startup (or
     *                              readable-a-moment-ago, for the defense-in-depth
     *                              race-condition checks below) file that fails to
     *                              read is a genuine server-side failure, not a
     *                              "not found" — it is deliberately left to propagate
     *                              to Jersey's {@code UnhandledExceptionMapper}
     *                              (always registered — see {@code buildResourceConfig})
     *                              so it surfaces as a real {@code 500} with the
     *                              original exception logged, rather than being
     *                              silently downgraded to a {@code 404}.
     */
    // Package-private (not private) so SpaFallbackInflectorTest can call it directly with a
    // Resource test-double whose newInputStream() throws — exercising the catch block
    // without reflection.
    static byte[] readResourceBytes(Resource resource) {
        try (var in = resource.newInputStream()) {
            return in.readAllBytes();
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to read '" + resource + "'.", ex);
        }
    }

    // Package-private (not private) so SpaFallbackInflectorTest can call both branches
    // directly without reflection.
    static String cacheControlValue(Duration maxAge) {
        return maxAge.isZero()
                ? "max-age=0, no-cache"
                : "max-age=" + maxAge.getSeconds() + ", public";
    }

    /**
     * Builds a weak {@code ETag} from {@code resource}'s last-modified time and length.
     * Weak, to match {@code resourceHandler.setEtags(true)}'s own semantics for real static
     * file responses (see {@code StaticHandler.doStart()}) — this Inflector is a separate
     * code path and cannot produce byte-identical ETags to the real {@code ResourceHandler},
     * but does not need to: this response and a direct {@code GET /index.html} are different
     * request URIs, so distinct cache entries are expected and correct regardless.
     */
    private static EntityTag buildETag(Resource resource) {
        String value = Long.toHexString(resource.lastModified().toEpochMilli())
                + "-" + Long.toHexString(resource.length());

        return new EntityTag(value, true);
    }

    @Override
    public Response apply(ContainerRequestContext context) {
        String path = PATH_SEPARATOR + context.getUriInfo().getPath(true);

        if (hasFileExtension(path)) {
            return Response.status(HttpStatus.NOT_FOUND_404).build();
        }

        // Defense-in-depth only: buildResourceConfig() already validated that index.html is
        // a readable file at server startup.  This guards the narrow race where the file is
        // deleted after startup but before this request — the same category of race
        // StaticHandler.writeErrorIfNotServed documents for real static file requests.
        if (!Resources.isReadableFile(indexResource)) {
            return serveMissingIndexResponse();
        }

        CompressedVariant variant = selectCompressedVariant(context);
        Resource contentResource = null != variant ? variant.resource() : indexResource;

        EntityTag eTag = buildETag(contentResource);
        Date lastModified = Date.from(contentResource.lastModified());

        // Jersey always passes its own ContainerRequest to Inflector.apply(), which
        // implements both ContainerRequestContext and jakarta.ws.rs.core.Request.
        Request jaxRsRequest = (Request) context;
        Response.ResponseBuilder builder = jaxRsRequest.evaluatePreconditions(lastModified, eTag);
        boolean notModified = null != builder;

        if (!notModified) {
            byte[] content = readResourceBytes(contentResource);
            builder = Response.ok(content, MediaType.TEXT_HTML_TYPE);

            if (null != variant) {
                builder.header(CONTENT_ENCODING, variant.encoding());
            }
        }

        builder.tag(eTag).lastModified(lastModified);

        if (configuration.preCompressed()) {
            builder.header(VARY, ACCEPT_ENCODING);
        }

        Optional<Duration> cacheMaxAge = configuration.cacheMaxAge();

        if (cacheMaxAge.isPresent()) {
            builder.header(HttpHeaders.CACHE_CONTROL, cacheControlValue(cacheMaxAge.get()));
        }

        for (Map.Entry<String, String> entry : configuration.responseHeaders().entrySet()) {
            builder.header(entry.getKey(), entry.getValue());
        }

        return builder.build();
    }

    /**
     * Resolves the preferred pre-compressed sibling of {@code index.html} ({@code .br} over
     * {@code .gz}, matching {@code CompressedContentFormat.BR, CompressedContentFormat.GZIP}
     * precedence already set on the real {@link org.eclipse.jetty.server.handler.ResourceHandler}
     * in {@code StaticHandler.doStart()}) — or {@code null} if {@link StaticAssetsConfiguration#preCompressed()}
     * is disabled, the client did not advertise support, or no matching sibling file exists.
     */
    private CompressedVariant selectCompressedVariant(ContainerRequestContext context) {
        if (!configuration.preCompressed()) {
            return null;
        }

        String acceptEncoding = context.getHeaderString(ACCEPT_ENCODING);

        if (null == acceptEncoding) {
            return null;
        }

        if (acceptEncoding.contains(BROTLI_ENCODING)) {
            Resource brResource = baseResource.resolve(StaticAssetsResourceResolver.INDEX_HTML + ".br");

            if (Resources.isReadableFile(brResource)) {
                return new CompressedVariant(brResource, BROTLI_ENCODING);
            }
        }

        if (acceptEncoding.contains(GZIP_ENCODING)) {
            Resource gzResource = baseResource.resolve(StaticAssetsResourceResolver.INDEX_HTML + ".gz");

            if (Resources.isReadableFile(gzResource)) {
                return new CompressedVariant(gzResource, GZIP_ENCODING);
            }
        }

        return null;
    }

    private record CompressedVariant(Resource resource, String encoding) {
    }

    /**
     * Mirrors {@code StaticHandler.serveErrorPage} for the case where SPA fallback is
     * enabled but the asset root has no {@code index.html}: serve the configured
     * {@code errorPage(404, ...)} if one exists and is readable, else a plain 404.
     *
     * <p>In normal operation this is unreachable — {@code buildResourceConfig()} validates
     * {@code index.html} exists at startup — and only runs for the narrow delete-after-startup
     * race described on {@link #apply}.
     */
    private Response serveMissingIndexResponse() {
        String errorPagePath = configuration.errorPages().get(HttpStatus.NOT_FOUND_404);

        if (null == errorPagePath) {
            return Response.status(HttpStatus.NOT_FOUND_404).build();
        }

        Resource errorPageResource = baseResource.resolve(errorPagePath);

        if (!Resources.isReadableFile(errorPageResource)) {
            return Response.status(HttpStatus.NOT_FOUND_404).build();
        }

        byte[] content = readResourceBytes(errorPageResource);
        String contentType = MimeTypes.DEFAULTS.getMimeByExtension(errorPagePath);

        return Response.status(HttpStatus.NOT_FOUND_404)
                .type(null != contentType ? contentType : "text/html; charset=utf-8")
                .entity(content)
                .build();
    }
}

