package software.frisby.web.server;

import org.eclipse.jetty.http.*;
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
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Jetty handler that serves static files from a classpath resource path or
 * filesystem directory, with dotfile protection, optional SPA fallback,
 * custom error pages, auth filtering, and configurable response headers.
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
    private final RequestLogger requestLogger;
    private final Set<String> redactedHeaders;

    /**
     * Set during {@link #doStart()}; used for resource existence checks,
     * SPA fallback resolution, and custom error-page resolution.
     */
    private Resource baseResource;

    StaticHandler(StaticAssetsConfiguration configuration,
                  ServerEventListener eventListener,
                  RequestLogger requestLogger,
                  Set<String> redactedHeaders) {
        super(new ResourceHandler());
        this.configuration = configuration;
        this.eventListener = eventListener;
        this.requestLogger = requestLogger;
        this.redactedHeaders = redactedHeaders;
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
     * @param request  the Jetty request, forwarded to {@link Response#writeError}
     * @param response the Jetty response, forwarded to {@link Response#writeError}
     * @param callback the response-completion callback, forwarded to {@link Response#writeError}
     */
    static void writeErrorIfNotServed(boolean served,
                                      Request request,
                                      Response response,
                                      Callback callback) {
        if (!served) {
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

        if (!matchesPrefix(path, configuration.urlPrefix())) {
            return false;
        }

        StopWatch watch = StopWatch.start();
        String method = request.getMethod();
        EventFiringCallback eventCallback = new EventFiringCallback(
                callback,
                watch,
                method,
                path,
                request,
                response,
                eventListener,
                requestLogger,
                redactedHeaders
        );

        if (isDotfilePath(path)) {
            Response.writeError(request, response, eventCallback, HttpStatus.NOT_FOUND_404);
            return true;
        }

        if (handleAuthFilter(request, response, eventCallback)) {
            return true;
        }

        return serveMatchedRequest(path, request, response, eventCallback);
    }

    /**
     * Invokes the auth filter if one is configured.
     *
     * @return {@code true} if the request was fully handled (blocked or exception path) and
     * {@code handle()} should return immediately; {@code false} if the filter allowed
     * the request or no filter is configured.
     */
    private boolean handleAuthFilter(Request request,
                                     Response response,
                                     EventFiringCallback eventCallback) {
        StaticAssetsAuthFilter authFilter = configuration.authFilter().orElse(null);

        if (null == authFilter) {
            return false;
        }

        boolean allowed;

        try {
            allowed = authFilter.authorize(request, response);
        } catch (RuntimeException ex) {
            eventCallback.setRequestException(ex);

            if (!response.isCommitted()) {
                int status = HttpStatus.INTERNAL_SERVER_ERROR_500;

                if (configuration.errorPages().containsKey(status)) {
                    serveErrorPage(status, request, response, eventCallback);
                } else {
                    Response.writeError(request, response, eventCallback, status);
                }
            } else {
                eventCallback.succeeded();
            }

            return true;
        }

        if (!allowed) {
            if (!response.isCommitted()) {
                int status = response.getStatus();

                if (configuration.errorPages().containsKey(status)) {
                    serveErrorPage(status, request, response, eventCallback);
                    return true;
                }
            }

            eventCallback.succeeded();
            return true;
        }

        return false;
    }

    /**
     * Resolves and serves the resource at {@code path}, applying response headers,
     * SPA fallback, and custom error pages as configured.
     *
     * <p>Called after prefix-matching, dotfile protection, and auth filtering have
     * all passed.
     *
     * @return {@code true} if this handler claimed the request; {@code false} if the
     * resource was not found and no fallback or error page applied (passes the
     * request to the next handler in the chain).
     */
    private boolean serveMatchedRequest(String path,
                                        Request request,
                                        Response response,
                                        EventFiringCallback eventCallback) throws Exception {
        Request strippedRequest = stripUrlPrefix(request, path, configuration.urlPrefix());
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

        for (Map.Entry<String, String> entry : configuration.responseHeaders().entrySet()) {
            response.getHeaders().put(entry.getKey(), entry.getValue());
        }

        if (resourceExists) {
            boolean served = resourceHandler().handle(strippedRequest, response, eventCallback);
            writeErrorIfNotServed(served, request, response, eventCallback);
            return true;
        }

        if (willUseSpaFallback) {
            HttpURI indexUri = HttpURI.build(strippedRequest.getHttpURI()).pathQuery("/index.html");
            Request indexRequest = Request.serveAs(strippedRequest, indexUri);
            boolean served = resourceHandler().handle(indexRequest, response, eventCallback);

            if (served) {
                return true;
            }

            if (!configuration.errorPages().containsKey(HttpStatus.NOT_FOUND_404)) {
                Response.writeError(request, response, eventCallback, HttpStatus.NOT_FOUND_404);
                return true;
            }
        }

        serveErrorPage(HttpStatus.NOT_FOUND_404, request, response, eventCallback);
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
                        Request request,
                        Response response,
                        Callback callback) {
        String errorPagePath = configuration.errorPages().get(statusCode);
        Resource errorPageResource = baseResource.resolve(errorPagePath);

        if (!Resources.isReadableFile(errorPageResource)) {
            Response.writeError(request, response, callback, statusCode);
            return;
        }

        response.setStatus(statusCode);

        String contentType = MimeTypes.DEFAULTS.getMimeByExtension(errorPagePath);
        response.getHeaders().put(
                HttpHeader.CONTENT_TYPE,
                null != contentType ? contentType : "text/html; charset=utf-8"
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
     * Wraps a downstream {@link Callback} to log the completed request through
     * {@link RequestLogger} and to fire a {@link RequestCompletedEvent} on the
     * configured {@link ServerEventListener} when the response completes,
     * regardless of success or failure.
     * <p>
     * Logging follows the same level conventions as the JSON API side:
     * <ul>
     *   <li>{@code TRACE} — full entry with request and response headers for all completed requests.</li>
     *   <li>{@code INFO} — compact one-liner for 2xx/3xx responses.</li>
     *   <li>{@code WARNING} — full entry with request and response headers for 4xx responses
     *       and 5xx responses caused by a {@link jakarta.ws.rs.WebApplicationException}.</li>
     *   <li>{@code ERROR} — full entry with headers and attached stack trace for 5xx responses
     *       caused by an unexpected exception (e.g. auth filter threw).</li>
     * </ul>
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
        private final Request request;
        private final Response response;
        private final ServerEventListener eventListener;
        private final RequestLogger requestLogger;
        private final Set<String> redactedHeaders;

        /**
         * Captured when the auth filter throws — used to attach the exception to the
         * structured log entry so that ERROR-level records include the full stack trace.
         */
        private final AtomicReference<Throwable> requestException;

        @SuppressWarnings("java:S107")
        private EventFiringCallback(Callback delegate,
                                    StopWatch watch,
                                    String method,
                                    String path,
                                    Request request,
                                    Response response,
                                    ServerEventListener eventListener,
                                    RequestLogger requestLogger,
                                    Set<String> redactedHeaders) {
            this.delegate = delegate;
            this.watch = watch;
            this.method = method;
            this.path = path;
            this.request = request;
            this.response = response;
            this.eventListener = eventListener;
            this.requestLogger = requestLogger;
            this.redactedHeaders = redactedHeaders;
            this.requestException = new AtomicReference<>(null);
        }

        /**
         * Builds a multi-line detail string for log entries, including masked request
         * headers and response headers.  Static assets carry no request or response
         * bodies, so only headers are included.
         *
         * @return A detail string (may start with {@code \n}); never {@code null}.
         */
        private static String buildDetail(Request request,
                                          Response response,
                                          Set<String> redactedHeaders) {
            StringBuilder sb = new StringBuilder();

            String reqHeaders = LogDetail.formatJettyRequestHeaders(request.getHeaders(), redactedHeaders);

            sb.append("\n  Request Headers:").append(reqHeaders);

            HttpFields responseFields = response.getHeaders();
            String resHeaders = LogDetail.formatJettyResponseHeaders(responseFields, redactedHeaders);

            if (!resHeaders.isEmpty()) {
                sb.append("\n  Response Headers:").append(resHeaders);
            }

            return sb.toString();
        }

        void setRequestException(Throwable ex) {
            this.requestException.set(ex);
        }

        @Override
        public void succeeded() {
            watch.stop();
            logCompletion();
            fireEvent();
            delegate.succeeded();
        }

        @Override
        public void failed(Throwable t) {
            watch.stop();
            logCompletion();
            fireEvent();
            delegate.failed(t);
        }

        private void logCompletion() {
            int status = response.getStatus();
            long latencyMs = watch.duration().toMillis();
            String detail = buildDetail(request, response, redactedHeaders);

            if (status < 400) {
                requestLogger.logRequest(method, path, status, latencyMs, detail);
            } else {
                requestLogger.logFailureDetail(method, path, status, latencyMs, detail, requestException.get());
            }
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
