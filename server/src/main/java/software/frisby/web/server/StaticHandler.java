package software.frisby.web.server;

import org.eclipse.jetty.http.CompressedContentFormat;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.http.HttpURI;
import org.eclipse.jetty.http.MimeTypes;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.util.resource.ResourceFactory;
import org.eclipse.jetty.util.resource.Resources;
import software.frisby.core.util.StopWatch;
import software.frisby.core.validation.FieldGroup;
import software.frisby.core.validation.FieldGroups;
import software.frisby.web.server.event.RequestCompletedEvent;
import software.frisby.web.server.event.ServerEventListener;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

/**
 * Jetty handler that serves static files from a classpath resource path or
 * filesystem directory, with dotfile protection, optional SPA fallback,
 * custom 404 pages, auth filtering, and configurable response headers.
 *
 * <p>One {@code StaticHandler} instance is created per
 * {@link StaticAssetsConfiguration} registered on the server. Each instance
 * handles only requests whose path starts with its configured URL prefix;
 * all other requests are passed to the next handler (typically the JAX-RS
 * servlet) by returning {@code false}.
 */
final class StaticHandler extends Handler.Wrapper {
    private static final System.Logger LOGGER = System.getLogger(StaticHandler.class.getName());

    private static final FieldGroup SOURCE_FIELDS =
            FieldGroup.of("classpathResourcePath", "filesystemDirectory");

    private final StaticAssetsConfiguration configuration;
    private final ServerEventListener eventListener;

    /**
     * Set during {@link #doStart()}; used for resource existence checks,
     * SPA fallback resolution, and custom 404-page resolution.
     */
    private Resource baseResource;

    StaticHandler(StaticAssetsConfiguration configuration,
                  ServerEventListener eventListener) {
        super(new ResourceHandler());
        this.configuration = configuration;
        this.eventListener = eventListener;
        this.baseResource = null;
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /**
     * Writes a {@code 404 Not Found} error response if the {@link ResourceHandler} did not
     * serve the request.
     *
     * <p>The {@code served = false} path handles a narrow race condition: the file existed
     * when {@link #resourceExists} checked above, but was deleted before
     * {@link ResourceHandler#handle} ran.  Returning {@code false} at that point is not safe
     * because response headers (e.g. CSP / security headers) were already written in step 5 —
     * passing a header-decorated response to Jersey would be worse than a plain 404.
     *
     * @param served   {@code true} if {@link ResourceHandler} claimed the request; {@code false}
     *                 if it returned without writing a response (file-disappeared race condition)
     * @param path     the original request path, used for the warning log entry
     * @param request  the Jetty request, forwarded to {@link Response#writeError}
     * @param response the Jetty response, forwarded to {@link Response#writeError}
     * @param callback the response-completion callback, forwarded to {@link Response#writeError}
     */
    static void writeErrorIfNotServed(boolean served,
                                      String path,
                                      Request request,
                                      Response response,
                                      Callback callback) {
        if (!served) {
            LOGGER.log(System.Logger.Level.WARNING, "→ GET {0} 404", path);
            Response.writeError(request, response, callback, HttpStatus.NOT_FOUND_404);
        }
    }

    /**
     * Returns {@code true} if {@code path} starts with {@code urlPrefix}.
     *
     * <p>The root prefix {@code "/"} matches all paths.  Any other prefix
     * requires the path to be either exactly equal to the prefix or to start
     * with {@code prefix + "/"} — preventing {@code /admin} from matching
     * {@code /administrator}.
     */
    private static boolean matchesPrefix(String path, String urlPrefix) {
        if (urlPrefix.equals("/")) {
            return true;
        }

        return path.equals(urlPrefix) || path.startsWith(urlPrefix + "/");
    }

    /**
     * Returns {@code true} if any path segment in {@code path} starts with {@code .}.
     *
     * <p>Only the final segment (after the last {@code /}) is checked, matching
     * the typical dotfile case ({@code /.env}, {@code /.git/config}).  Directory
     * components that start with a dot (e.g. {@code /.well-known/}) do not trigger
     * this guard — only the actual file name does.
     */
    private static boolean isDotfilePath(String path) {
        int lastSlash = path.lastIndexOf('/');
        String lastSegment = (lastSlash >= 0) ? path.substring(lastSlash + 1) : path;
        return lastSegment.startsWith(".");
    }

    /**
     * Returns a request whose URI path has the URL prefix stripped, so that the
     * {@link ResourceHandler} looks up files relative to the asset root rather than
     * the full request path.
     *
     * <p>For the root prefix {@code "/"} no stripping is needed.
     */
    private static Request stripUrlPrefix(Request request, String path, String urlPrefix) {
        if (urlPrefix.equals("/")) {
            return request;
        }

        String stripped = path.substring(urlPrefix.length());

        if (stripped.isEmpty()) {
            stripped = "/";
        }

        HttpURI strippedUri = HttpURI.build(request.getHttpURI()).pathQuery(stripped);

        return Request.serveAs(request, strippedUri);
    }

    // -------------------------------------------------------------------------
    // Request handling
    // -------------------------------------------------------------------------

    /**
     * Returns {@code true} if {@code path}'s final segment contains a non-empty
     * file extension — i.e. a {@code .} that is not the first character and not
     * the last character of the segment.
     *
     * <p>Used by SPA fallback to protect against silently serving {@code index.html}
     * in place of a genuinely missing image, script, or stylesheet.
     */
    private static boolean hasFileExtension(String path) {
        int lastSlash = path.lastIndexOf('/');
        String lastSegment = (lastSlash >= 0) ? path.substring(lastSlash + 1) : path;
        int lastDot = lastSegment.lastIndexOf('.');

        return lastDot > 0 && lastDot < lastSegment.length() - 1;
    }

    @Override
    protected void doStart() throws Exception {
        ResourceHandler resourceHandler = resourceHandler();

        baseResource = createBaseResource();

        if (null == baseResource || !baseResource.isDirectory()) {
            throw new IllegalStateException(
                    "The '" + sourceArgumentName() + "' value of '" + describeSource()
                            + "' is invalid.  The resource does not exist or is not a directory."
            );
        }

        resourceHandler.setBaseResource(baseResource);
        resourceHandler.setDirAllowed(false);
        resourceHandler.setEtags(true);
        resourceHandler.setWelcomeFiles("index.html");

        if (configuration.preCompressed()) {
            resourceHandler.setPrecompressedFormats(CompressedContentFormat.BR, CompressedContentFormat.GZIP);
        }

        for (Map.Entry<Integer, String> entry : configuration.errorPages().entrySet()) {
            String errorPagePath = entry.getValue();
            Resource errorPageResource = baseResource.resolve(errorPagePath);

            if (!Resources.isReadableFile(errorPageResource)) {
                throw new IllegalStateException(
                        "The 'errorPage[" + entry.getKey() + "]' value of '" + errorPagePath
                                + "' is invalid.  The file does not exist in the configured asset root."
                );
            }
        }

        configuration.cacheMaxAge().ifPresent(duration -> {
            String cacheControl = duration.isZero()
                    ? "max-age=0, no-cache"
                    : "max-age=" + duration.getSeconds() + ", public";
            resourceHandler.setCacheControl(cacheControl);
        });

        super.doStart();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Resource createBaseResource() {
        String classpathPath = configuration.classpathResourcePath().orElse(null);
        Path filesystemPath = configuration.filesystemDirectory().orElse(null);

        FieldGroups.onlyOne(
                SOURCE_FIELDS,
                classpathPath,
                filesystemPath
        );

        if (null != classpathPath) {
            return ResourceFactory.of(this).newClassLoaderResource(classpathPath);
        }

        return ResourceFactory.of(this).newResource(filesystemPath);
    }

    private String describeSource() {
        return configuration.describeSource();
    }

    private String sourceArgumentName() {
        return configuration.classpathResourcePath().isPresent() ? "resourcePath" : "directory";
    }

    @Override
    public boolean handle(Request request, Response response, Callback callback) throws Exception {
        String path = request.getHttpURI().getPath();
        String urlPrefix = configuration.urlPrefix();

        // 1. Prefix check — only handle requests under our URL prefix.
        if (!matchesPrefix(path, urlPrefix)) {
            return false;
        }

        // We are committed to handling this request.  Start timing and wrap the
        // callback so we fire a RequestCompletedEvent when the response completes.
        StopWatch watch = StopWatch.start();
        String method = request.getMethod();
        Callback eventCallback = new EventFiringCallback(callback, watch, method, path, response, eventListener);

        // 2. Dotfile protection — unconditional 404 for any path segment starting with period (".").
        if (isDotfilePath(path)) {
            LOGGER.log(System.Logger.Level.WARNING, "→ GET {0} blocked (dotfile)", path);
            Response.writeError(request, response, eventCallback, HttpStatus.NOT_FOUND_404);
            return true;
        }

        // 3. Auth filter — if present, invoke it.  When the filter returns false and has
        //    not committed a response, check whether a configured error page applies to the
        //    current response status and serve it automatically.  When the filter throws,
        //    catch the exception and serve the configured 500 error page (if any).
        if (configuration.authFilter().isPresent()) {
            boolean allowed;

            try {
                allowed = configuration.authFilter().get().authorize(request, response);
            } catch (Exception ex) {
                LOGGER.log(
                        System.Logger.Level.ERROR,
                        "Auth filter threw an exception for path: {0}",
                        path,
                        ex
                );

                if (!response.isCommitted()) {
                    int status = HttpStatus.INTERNAL_SERVER_ERROR_500;

                    if (configuration.errorPages().containsKey(status)) {
                        serveErrorPage(status, path, request, response, eventCallback);
                    } else {
                        Response.writeError(request, response, eventCallback, status);
                    }
                } else {
                    eventCallback.succeeded();
                }

                return true;
            }

            if (!allowed) {
                LOGGER.log(
                        System.Logger.Level.WARNING,
                        "→ GET {0} rejected by auth filter",
                        path
                );

                if (!response.isCommitted()) {
                    int status = response.getStatus();

                    if (configuration.errorPages().containsKey(status)) {
                        serveErrorPage(status, path, request, response, eventCallback);
                        return true;
                    }
                }

                eventCallback.succeeded();
                return true;
            }
        }

        // 4. Determine what we will serve before adding response headers.
        //    This prevents our CSP / security headers from leaking onto JAX-RS responses
        //    when we return false (resource not found, no fallback applies).
        Request strippedRequest = stripUrlPrefix(request, path, urlPrefix);
        String strippedPath = strippedRequest.getHttpURI().getPath();

        boolean resourceExists = resourceExists(strippedPath);
        boolean willUseSpaFallback = !resourceExists
                && configuration.spaFallback()
                && !hasFileExtension(path);
        boolean willUseErrorPage = !resourceExists
                && !willUseSpaFallback
                && configuration.errorPages().containsKey(HttpStatus.NOT_FOUND_404);

        if (!resourceExists && !willUseSpaFallback && !willUseErrorPage) {
            return false;
        }

        // 5. Add custom response headers now that we know we are handling this request.
        if (!configuration.responseHeaders().isEmpty()) {
            for (Map.Entry<String, String> entry : configuration.responseHeaders().entrySet()) {
                response.getHeaders().put(entry.getKey(), entry.getValue());
            }
        }

        // 6. Serve the resource.
        if (resourceExists) {
            boolean served = resourceHandler().handle(strippedRequest, response, eventCallback);
            writeErrorIfNotServed(served, path, request, response, eventCallback);

            return true;
        }

        // 7. SPA fallback — serve index.html for extensionless paths.
        if (willUseSpaFallback) {
            HttpURI indexUri = HttpURI.build(strippedRequest.getHttpURI()).pathQuery("/index.html");
            Request indexRequest = Request.serveAs(strippedRequest, indexUri);
            boolean served = resourceHandler().handle(indexRequest, response, eventCallback);

            if (served) {
                LOGGER.log(
                        System.Logger.Level.TRACE,
                        "→ GET {0} → index.html",
                        path
                );
                return true;
            }

            // index.html itself is missing — fall through to custom error page or plain 404.
            if (!configuration.errorPages().containsKey(HttpStatus.NOT_FOUND_404)) {
                LOGGER.log(System.Logger.Level.WARNING, "→ GET {0} 404 (index.html missing)", path);
                Response.writeError(request, response, eventCallback, HttpStatus.NOT_FOUND_404);
                return true;
            }
        }

        // 8. Custom error page.
        serveErrorPage(HttpStatus.NOT_FOUND_404, path, request, response, eventCallback);
        return true;
    }

    private ResourceHandler resourceHandler() {
        return (ResourceHandler) getHandler();
    }

    /**
     * Returns {@code true} if a readable file (or a directory containing
     * {@code index.html}) exists at {@code strippedPath} within the base resource.
     */
    private boolean resourceExists(String strippedPath) {
        if (null == baseResource) {
            return false;
        }

        Resource resource = baseResource.resolve(strippedPath);

        if (null == resource || !resource.exists()) {
            return false;
        }

        if (Resources.isReadableFile(resource)) {
            return true;
        }

        if (resource.isDirectory()) {
            Resource welcomeFile = resource.resolve("index.html");

            return Resources.isReadableFile(welcomeFile);
        }

        return false;
    }

    /**
     * Serves the configured error page for {@code statusCode} with that HTTP status.
     * If the custom page file cannot be found in the asset root at request time, falls
     * back to a plain error response with no body.
     */
    void serveErrorPage(int statusCode,
                        String originalPath,
                        Request request,
                        Response response,
                        Callback callback) {
        String errorPagePath = configuration.errorPages().get(statusCode);
        Resource errorPageResource = baseResource.resolve(errorPagePath);

        if (!Resources.isReadableFile(errorPageResource)) {
            LOGGER.log(System.Logger.Level.WARNING, "→ GET {0} {1}", originalPath, statusCode);
            Response.writeError(request, response, callback, statusCode);
            return;
        }

        response.setStatus(statusCode);

        String contentType = MimeTypes.DEFAULTS.getMimeByExtension(errorPagePath);
        response.getHeaders().put(
                HttpHeader.CONTENT_TYPE,
                null != contentType ? contentType : "text/html; charset=utf-8"
        );

        LOGGER.log(
                System.Logger.Level.WARNING,
                "→ GET {0} {1} (custom page)",
                originalPath,
                statusCode
        );

        try (var in = errorPageResource.newInputStream()) {
            byte[] content = in.readAllBytes();
            response.write(true, ByteBuffer.wrap(content), callback);
        } catch (Exception e) {
            callback.failed(e);
        }
    }

    // -------------------------------------------------------------------------
    // Event-firing callback
    // -------------------------------------------------------------------------

    /**
     * Wraps a downstream {@link Callback} to fire a {@link RequestCompletedEvent}
     * on the configured {@link ServerEventListener} when the response completes,
     * regardless of success or failure.
     * <p>
     * The event is fired with {@code staticAsset = true} and
     * {@code endpoint = Optional.empty()}, reflecting that static asset requests
     * have no associated JAX-RS resource.
     */
    private static final class EventFiringCallback implements Callback {
        private final Callback delegate;
        private final StopWatch watch;
        private final String method;
        private final String path;
        private final Response response;
        private final ServerEventListener eventListener;

        private EventFiringCallback(Callback delegate,
                                    StopWatch watch,
                                    String method,
                                    String path,
                                    Response response,
                                    ServerEventListener eventListener) {
            this.delegate = delegate;
            this.watch = watch;
            this.method = method;
            this.path = path;
            this.response = response;
            this.eventListener = eventListener;
        }

        @Override
        public void succeeded() {
            watch.stop();
            fireEvent();
            delegate.succeeded();
        }

        @Override
        public void failed(Throwable t) {
            watch.stop();
            fireEvent();
            delegate.failed(t);
        }

        private void fireEvent() {
            try {
                eventListener.onRequestCompleted(new RequestCompletedEvent(
                        method,
                        path,
                        response.getStatus(),
                        watch.duration(),
                        0L,
                        0L,
                        Optional.empty(),
                        true
                ));
            } catch (Exception ex) {
                LOGGER.log(
                        System.Logger.Level.WARNING,
                        "ServerEventListener.onRequestCompleted threw an exception.",
                        ex
                );
            }
        }
    }
}
