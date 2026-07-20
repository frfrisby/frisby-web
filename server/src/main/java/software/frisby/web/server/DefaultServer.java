package software.frisby.web.server;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.jetty.alpn.server.ALPNServerConnectionFactory;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.http2.server.HTTP2ServerConnectionFactory;
import org.eclipse.jetty.server.*;
import org.eclipse.jetty.server.handler.SizeLimitHandler;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.glassfish.jersey.internal.inject.Providers;
import org.glassfish.jersey.jetty.JettyHttpContainerFactory;
import org.glassfish.jersey.message.GZipEncoder;
import org.glassfish.jersey.process.Inflector;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.ServerProperties;
import org.glassfish.jersey.server.model.Resource;
import org.glassfish.jersey.server.monitoring.ApplicationEvent;
import org.glassfish.jersey.server.monitoring.ApplicationEventListener;
import org.glassfish.jersey.server.monitoring.RequestEvent;
import org.glassfish.jersey.server.monitoring.RequestEventListener;
import software.frisby.core.util.StopWatch;
import software.frisby.core.validation.Sequences;
import software.frisby.core.validation.Strings;
import software.frisby.core.validation.Values;
import software.frisby.web.server.event.RequestCompletedEvent;
import software.frisby.web.server.event.ServerEventListener;

import javax.net.ssl.SSLContext;
import java.io.*;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/**
 * Jersey + Jetty 12 implementation of {@link Server}.
 * <p>
 * Uses {@link JettyHttpContainerFactory} to create and manage an embedded Jetty HTTP
 * server that hosts Jersey as the JAX-RS runtime.  The Jetty server is created lazily
 * on the first call to {@link #start()} and destroyed on {@link #stop()}.
 * <p>
 * All state mutations are guarded by {@code syncRoot} so that {@link #start()} and
 * {@link #stop()} are safe to call from multiple threads.
 * <p>
 * <strong>GzipHandler note</strong>: Jetty's {@code GzipHandler} is intentionally not
 * registered here — see CVE-2026-1605 (Gzip request memory leak in Jetty 12.0.x).
 * Use {@link ServerConfigurationBuilder#gzip()} to enable full bidirectional gzip support
 * via Jersey's own {@code GZipEncoder} (no CVE risk).
 */
final class DefaultServer implements Server {
    private static final System.Logger LOGGER = System.getLogger(DefaultServer.class.getName());

    private static final String PATH_SEPARATOR = "/";
    private static final String HEALTH_CHECK_BODY = "{\"status\":\"UP\"}";

    /**
     * Number of threads reserved for Jetty's own infrastructure (acceptor, selector,
     * and two reserved-thread slots) on top of the application concurrency budget.
     * <p>
     * This ensures Jetty always has threads available to accept new TCP connections and
     * read incoming HTTP requests even when all application dispatch slots are occupied.
     * Without this headroom Jetty's infrastructure tasks would compete with request-dispatch
     * threads, causing unpredictable behavior.  The concurrency cap itself is enforced by
     * {@link ConcurrencyLimitHandler}'s {@link Semaphore}, not by the pool size.
     */
    private static final int JETTY_INFRASTRUCTURE_THREADS = 4;

    private static final String HTTP_1_1 = "http/1.1";

    private final Object syncRoot = new Object();

    private final ServerConfiguration configuration;
    private final List<Object> resources;
    private final List<Object> components;
    private final List<AuthenticationProvider> authenticationProviders;
    private final ServerEventListener eventListener;
    private final RequestLogger requestLogger;
    private final String healthCheckPath;
    private final AtomicBoolean shuttingDown;
    private final AtomicBoolean running;

    // Guarded by syncRoot
    private org.eclipse.jetty.server.Server jettyServer;

    // Volatile: written inside syncRoot during start(), read by port() without lock.
    private volatile int port;

    // Written inside syncRoot during start() and stop(); only ever accessed under the lock.
    private Semaphore concurrencyLimitSemaphore;

    DefaultServer(ServerConfiguration configuration,
                  List<Object> resources,
                  List<Object> components,
                  List<AuthenticationProvider> authenticationProviders,
                  ServerEventListener eventListener,
                  String healthCheckPath) {
        this.configuration = Values.notNull("configuration", configuration);
        this.resources = Sequences.notEmpty("resources", resources);
        this.components = Sequences.notNull("components", components);
        this.authenticationProviders = Sequences.notNull("authenticationProviders", authenticationProviders);
        this.eventListener = Values.notNull("eventListener", eventListener);
        this.healthCheckPath = Strings.optionalNotBlank("healthCheckPath", healthCheckPath);

        this.requestLogger = new RequestLogger();
        this.shuttingDown = new AtomicBoolean(false);
        this.running = new AtomicBoolean(false);

        this.jettyServer = null;
        this.concurrencyLimitSemaphore = null;
        this.port = configuration.port();
    }

    // -------------------------------------------------------------------------
    // Server interface — port, uri, configuration, lifecycle
    // -------------------------------------------------------------------------

    @Override
    public int port() {
        return port;
    }

    @Override
    public URI uri() {
        String scheme = configuration.ssl().isPresent() ? "https" : "http";

        return URI.create(scheme + "://" + configuration.host() + ":" + port);
    }

    @Override
    public ServerConfiguration configuration() {
        return configuration;
    }


    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public void start() {
        synchronized (syncRoot) {
            if (null != jettyServer) {
                return;
            }

            // Reset state so the server is restartable — a previous stop() will have
            // set shuttingDown to true and running to false.
            shuttingDown.set(false);

            String baseUri = buildBaseUri();

            // JettyHttpContainerFactory only accepts http:// URIs — it creates a plain
            // HTTP connector internally.  When SSL is configured, configureConnectors()
            // replaces that connector with an SslConnectionFactory-backed connector, so
            // the scheme passed here has no bearing on the final protocol.
            String factoryUri = "http://" + configuration.host() + ":" + port + PATH_SEPARATOR;

            try {
                ResourceConfig rc = buildResourceConfig();

                jettyServer = buildJettyServer(factoryUri, rc);

                configureConnectors(jettyServer);

                // Suppress info-leaking response headers on every connector, regardless
                // of which buildJettyServer() branch created them (factory-default or
                // custom-pool).  Applied after configureConnectors() so the SSL connector
                // is included when https is configured.
                suppressInfoHeaders(jettyServer);

                jettyServer.start();

                // Resolve the actual bound port.  When configuration.port() == 0 the OS
                // assigns a free ephemeral port; getLocalPort() returns it in both cases.
                port = ((org.eclipse.jetty.server.ServerConnector) jettyServer.getConnectors()[0]).getLocalPort();

                running.set(true);

                requestLogger.logStarted(buildBaseUri(), buildStartupSummary());
            } catch (Exception ex) {
                requestLogger.logStartFailed(ex);
                jettyServer = null;

                throw new UncheckedIOException(
                        new IOException("Failed to start server at '" + baseUri + "'.", ex)
                );
            }
        }
    }

    @Override
    @SuppressWarnings("java:S899") // best-effort drain — server stops regardless of whether all permits were returned
    public void stop() {
        synchronized (syncRoot) {
            if (null == jettyServer) {
                return;
            }

            String baseUri = buildBaseUri();

            // Signal the concurrency gate to reject all new requests with 503 immediately.
            shuttingDown.set(true);

            // Application-layer drain — blocks until all in-flight requests release their
            // semaphore permits or the timeout expires.  Holding syncRoot is safe here
            // because request-handling threads (which release permits) never touch syncRoot.
            // isRunning() is lock-free (running.get()), so it remains non-blocking and
            // returns true throughout the drain window.
            // If stopTimeout is absent the drain is skipped and connections are closed immediately.
            configuration.stopTimeout().ifPresent(timeout -> {
                try {
                    concurrencyLimitSemaphore.tryAcquire(
                            configuration.maxConcurrentRequests(),
                            timeout.toMillis(),
                            TimeUnit.MILLISECONDS
                    );
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });

            try {
                jettyServer.stop();
            } catch (Exception ex) {
                if (LOGGER.isLoggable(System.Logger.Level.WARNING)) {
                    LOGGER.log(
                            System.Logger.Level.WARNING,
                            "Exception while stopping server at '" + baseUri + "'.",
                            ex
                    );
                }
            } finally {
                jettyServer = null;
            }

            running.set(false);

            requestLogger.logStopped(baseUri);
        }
    }

    private String buildBaseUri() {
        String scheme = configuration.ssl().isPresent() ? "https" : "http";

        return scheme + "://" + configuration.host() + ":" + port + PATH_SEPARATOR;
    }

    private String buildStartupSummary() {
        StringBuilder sb = new StringBuilder();

        if (null != healthCheckPath) {
            sb.append("\n  livenessProbePath=").append(healthCheckPath);
        }

        sb.append("\n  maxConcurrentRequests=").append(configuration.maxConcurrentRequests());

        sb.append("\n  maxRequestSize=").append(configuration.maxRequestSize()).append(" bytes");

        configuration.stopTimeout().ifPresent(t ->
                sb.append("\n  stopTimeout=").append(t.toMillis()).append(" ms")
        );

        String executorType = configuration.executor()
                .map(e -> e.getClass().getSimpleName())
                .orElse("platform threads");
        sb.append("\n  executor=").append(executorType);

        sb.append("\n  ssl=").append(configuration.ssl().isPresent());
        sb.append("\n  gzip=").append(configuration.gzip());

        return sb.toString();
    }

    /**
     * Creates the embedded Jetty {@link org.eclipse.jetty.server.Server} with a custom
     * {@link QueuedThreadPool} sized from {@link ServerConfiguration#maxConcurrentRequests()}.
     * <p>
     * The pool's max-threads is set to {@code maxConcurrentRequests + }{@link #JETTY_INFRASTRUCTURE_THREADS}
     * so that Jetty's acceptor and selector threads are never starved by request-dispatch threads.
     * The min-threads warm the pool for spike handling, computed as
     * {@code Math.max(8, maxConcurrentRequests / 10)}.
     * The actual concurrency cap — and the HTTP 503 response on overflow — is enforced by
     * {@link ConcurrencyLimitHandler}, which is installed in
     * {@link #configureConnectors} at the same time.
     * <p>
     * <strong>MR-JAR note</strong>: {@code jersey-container-jetty-http} is a Multi-Release
     * JAR.  {@code JettyHttpContainer} implements {@code org.eclipse.jetty.server.Handler}
     * only in the {@code META-INF/versions/17} variant — the base class (loaded by some IDE
     * indexers) implements only {@code Container}.  To avoid a type mismatch that prevents
     * IDE compilation, this method always uses {@link JettyHttpContainerFactory#createServer}
     * to obtain a properly wired {@link Handler}; the handler is then extracted from a
     * never-started temporary server and transferred to a new
     * {@link org.eclipse.jetty.server.Server} instance built with the custom pool.
     */
    private org.eclipse.jetty.server.Server buildJettyServer(String factoryUri, ResourceConfig rc) {
        URI uri = URI.create(factoryUri);

        // Let JettyHttpContainerFactory handle the MR-JAR Handler wiring, then transfer
        // the resulting handler to a new Server built with our custom thread pool.
        // The temporary server is never started so the handler has no lifecycle state to
        // carry over.
        org.eclipse.jetty.server.Server temp =
                JettyHttpContainerFactory.createServer(uri, rc, false);

        Handler jerseyHandler = temp.getHandler();

        int maxConcurrent = configuration.maxConcurrentRequests();
        int maxPoolThreads = maxConcurrent + JETTY_INFRASTRUCTURE_THREADS;
        int minPoolThreads = Math.min(maxPoolThreads, Math.max(8, maxConcurrent / 10));

        QueuedThreadPool pool = new QueuedThreadPool(maxPoolThreads, minPoolThreads);

        configuration.executor().ifPresent(pool::setVirtualThreadsExecutor);

        org.eclipse.jetty.server.Server server = new org.eclipse.jetty.server.Server(pool);

        HttpConfiguration httpConfig = new HttpConfiguration();

        ServerConnector connector = new ServerConnector(server, new HttpConnectionFactory(httpConfig));
        connector.setHost(uri.getHost());
        connector.setPort(uri.getPort());

        server.addConnector(connector);
        server.setHandler(jerseyHandler);

        return server;
    }

    private ResourceConfig buildResourceConfig() {
        ResourceConfig rc = new ResourceConfig();

        // Bridge our JsonSerializer into Jersey's message body pipeline.
        rc.registerInstances(new JsonMessageBodyProvider(configuration.serializer()));

        // Gzip support — only registered when explicitly requested via ServerConfigurationBuilder.gzip().
        // GZipEncoder handles both request decompression (Content-Encoding: gzip) and the response
        // encoding engine; GZipResponseFilter provides the response compression policy.
        // This is Jersey's own encoder — not Jetty's GzipHandler — and carries no CVE risk.
        if (configuration.gzip()) {
            rc.register(GZipEncoder.class);
            rc.registerInstances(new GZipResponseFilter());
        }

        // CORS support — only registered when explicitly configured via ServerConfigurationBuilder.cors().
        // CorsFilter is @PreMatching so preflight OPTIONS requests are handled before URI matching
        // and before authentication filters, which is the correct per-spec order.
        configuration.cors().ifPresent(corsConfiguration -> rc.registerInstances(new CorsFilter(corsConfiguration)));

        // Buffers up to maxBodySize bytes of the request body so the failure-logging
        // code in ServerRequestEventListener can include body context for 4xx/5xx responses.
        // The filter is always registered (runs as a no-op when maxBodySize == 0) and
        // re-wraps the entity stream so downstream filters and resource methods see the
        // full body unchanged.
        rc.registerInstances(new RequestBodyBufferingFilter(configuration.logging().maxBodySize()));

        // Exception mappers — always registered; not opt-in.
        // WebApplicationExceptionMapper: routes WebApplicationExceptions through their
        //   embedded response unchanged.  Without it, UnhandledExceptionMapper (bound to
        //   the root Exception type) would catch Jersey's own NotFoundException,
        //   ForbiddenException, etc. and return a plain 500.  This mapper acts as a
        //   firewall so that only genuinely unhandled, non-WAE exceptions reach
        //   UnhandledExceptionMapper.
        // BadMessageExceptionMapper: maps Jetty's BadMessageException to the correct
        //   HTTP status (e.g. 413 for chunked-transfer bodies that exceed the
        //   size limit).  SizeLimitHandler throws this exception during body reads when
        //   no Content-Length is known; without this mapper UnhandledExceptionMapper
        //   would swallow the 413 code and return a plain 500 instead.
        // UnhandledExceptionMapper: ensures every remaining unhandled exception
        //   produces a plain 500 with no body, so stack traces and internal state never
        //   reach callers regardless of what the throwing code put in the message.
        rc.registerInstances(new WebApplicationExceptionMapper());
        rc.registerInstances(new BadMessageExceptionMapper());
        rc.registerInstances(new UnhandledExceptionMapper());

        // Security response filter — always registered; not opt-in.
        //
        // Strips the response entity for 401, 403, and 500 before the response reaches
        // the wire.
        //
        // A ContainerResponseFilter is used rather than an ExceptionMapper because the JAX-RS spec
        // prohibits ExceptionMapper invocation for a WebApplicationException whose embedded response
        // already carries an entity.  This is the common case when Jersey or application code throws
        // a NotAuthorizedException or ForbiddenException with a descriptive body.
        //
        // The filter intercepts all responses regardless of how they were produced.
        rc.registerInstances(new SecurityResponseFilter());

        // Security request filter — registered only when authentication providers are configured.
        // The filter runs at AUTHENTICATION priority (post-matching) and implements a
        // first-accepts-wins provider chain.  The health check path (if configured) always
        // bypasses authentication so liveness probes never require credentials.
        if (!authenticationProviders.isEmpty()) {
            rc.registerInstances(new SecurityRequestFilter(authenticationProviders, healthCheckPath));
        }

        // Hook into Jersey's request lifecycle to fire ServerEventListener callbacks.
        rc.registerInstances(new ServerApplicationEventListener(
                configuration,
                eventListener,
                requestLogger,
                healthCheckPath
        ));

        // Disable WADL generation — we do not expose it.
        rc.property(ServerProperties.WADL_FEATURE_DISABLE, Boolean.TRUE);
        rc.property(ServerProperties.LOCATION_HEADER_RELATIVE_URI_RESOLUTION_DISABLED, Boolean.TRUE);

        // Jersey logs a WARNING for every registered resource instance that does not
        // implement a provider interface.  This is expected behavior and safe to suppress.
        java.util.logging.Logger
                .getLogger(Providers.class.getName())
                .setLevel(Level.SEVERE);

        for (Object resource : resources) {
            rc.registerInstances(resource);
        }

        for (Object component : components) {
            rc.registerInstances(component);
        }

        // Built-in health check — mounted only when healthCheck() was called on the builder.
        // Uses Jersey's programmatic resource model so the path is runtime-configurable.
        // Requests to this path are logged at TRACE (not INFO) and do not fire
        // ServerEventListener callbacks, so load-balancer polling does not inflate metrics.
        if (null != healthCheckPath) {
            Resource.Builder healthResource = Resource.builder(healthCheckPath);

            healthResource
                    .addMethod("GET")
                    .produces(MediaType.APPLICATION_JSON)
                    .handledBy(HealthCheckHandler.class);

            rc.registerResources(healthResource.build());
        }

        return rc;
    }

    /**
     * Wraps the Jersey handler in a {@link SizeLimitHandler} to enforce
     * {@link ServerConfiguration#maxRequestSize()}, wraps that in a
     * {@link ConcurrencyLimitHandler} to enforce
     * {@link ServerConfiguration#maxConcurrentRequests()}, and if SSL is configured,
     * replaces the plain-HTTP connector with an SSL connector built from the
     * {@link SSLContext}.
     */
    private void configureConnectors(org.eclipse.jetty.server.Server server) {
        // Jetty 12 removed setMaxRequestSize() from HttpConfiguration.  Enforce the
        // inbound limit by wrapping the Jersey handler in a SizeLimitHandler instead.
        // SizeLimitHandler(requestLimit, responseLimit):
        //   requestLimit  — enforced; Jetty returns HTTP 413 when exceeded.
        //   responseLimit — intentionally unlimited (-1).  The server controls its own
        //                   responses; oversized responses are an application concern
        //                   (pagination, streaming) not a container-level policy.  A
        //                   response limit would close the connection mid-stream, leaving
        //                   clients with a partial body and no actionable status code.
        Handler existingHandler = server.getHandler();

        SizeLimitHandler sizeLimitHandler = new SizeLimitHandler(
                configuration.maxRequestSize(),
                -1L
        );
        sizeLimitHandler.setHandler(existingHandler);

        // Always install a concurrency gate in front of the size limit handler.
        // The gate sends an HTTP 503 with Retry-After when the limit is reached, rather
        // than dropping the TCP connection, giving callers an actionable signal.  During
        // graceful shutdown, the gate also rejects new requests immediately once the
        // shuttingDown flag is set.
        int maxConcurrent = configuration.maxConcurrentRequests();

        Semaphore semaphore = new Semaphore(maxConcurrent);

        concurrencyLimitSemaphore = semaphore;

        ConcurrencyLimitHandler limitHandler = new ConcurrencyLimitHandler(
                semaphore,
                shuttingDown,
                requestLogger,
                eventListener,
                healthCheckPath
        );
        limitHandler.setHandler(sizeLimitHandler);

        server.setHandler(limitHandler);

        // Replace Jetty's default HTML error page with our JSON handler so that
        // pre-Jersey errors (primarily 413 from SizeLimitHandler) are consistent
        // with the rest of the API and are properly logged and tracked.
        server.setErrorHandler(new JsonErrorHandler(configuration, requestLogger, eventListener));

        configuration.ssl().ifPresent(sslContext -> configureSslConnector(server, sslContext));
    }

    private void suppressInfoHeaders(org.eclipse.jetty.server.Server server) {
        for (org.eclipse.jetty.server.Connector connector : server.getConnectors()) {
            // Every connector we create has an HttpConnectionFactory somewhere in its chain
            // (plain HTTP, SSL/HTTP 1.1, and SSL/ALPN/HTTP 2 all include one).
            // getConnectionFactory(Class) searches the full factory chain, so this is
            // always non-null for the connectors DefaultServer produces.
            connector.getConnectionFactory(HttpConnectionFactory.class)
                    .getHttpConfiguration()
                    .setSendServerVersion(false);

            connector.getConnectionFactory(HttpConnectionFactory.class)
                    .getHttpConfiguration()
                    .setSendDateHeader(false);
        }
    }

    @SuppressWarnings("java:S2095")
    // lifecycle is owned by the Jetty Server — closing ServerConnector would destroy it before start()
    private void configureSslConnector(org.eclipse.jetty.server.Server server, SSLContext sslContext) {
        SslContextFactory.Server sslFactory = new SslContextFactory.Server();
        sslFactory.setSslContext(sslContext);

        HttpConfiguration httpsConfig = new HttpConfiguration();
        httpsConfig.addCustomizer(new SecureRequestCustomizer());

        ServerConnector httpsConnector;

        if (configuration.http2()) {
            // ALPN negotiation — clients that advertise "h2" get HTTP/2;
            // all others fall back to HTTP/1.1 transparently.
            HTTP2ServerConnectionFactory h2Factory = new HTTP2ServerConnectionFactory(httpsConfig);

            ALPNServerConnectionFactory alpn = new ALPNServerConnectionFactory("h2", HTTP_1_1);
            alpn.setDefaultProtocol(HTTP_1_1);

            httpsConnector = new ServerConnector(
                    server,
                    new SslConnectionFactory(sslFactory, "alpn"),
                    alpn,
                    h2Factory,
                    new HttpConnectionFactory(httpsConfig)
            );
        } else {
            httpsConnector = new ServerConnector(
                    server,
                    new SslConnectionFactory(sslFactory, HTTP_1_1),
                    new HttpConnectionFactory(httpsConfig)
            );
        }

        httpsConnector.setHost(configuration.host());
        httpsConnector.setPort(port);

        server.setConnectors(new Connector[]{httpsConnector});
    }


    // -------------------------------------------------------------------------
    // Concurrency-limit handler — 503 gate when maxThreads is configured
    // -------------------------------------------------------------------------

    /**
     * Limits the number of requests concurrently in-flight to the size of the supplied
     * {@link Semaphore}.
     * <p>
     * On every inbound request a permit is acquired from the internal {@link Semaphore}
     * via {@link Semaphore#tryAcquire()} (non-blocking).  When a permit is available the
     * request proceeds to the downstream handler chain; the permit is released when the
     * response callback fires ({@code succeeded} or {@code failed}), so the permit is held
     * for exactly the lifetime of the in-flight request.
     * <p>
     * When no permit is available — i.e. the concurrency limit has been reached — or when
     * the server is in the process of shutting down ({@code shuttingDown} flag set), a
     * {@code 503 Service Unavailable} response is written immediately with a
     * {@code Retry-After: 1} header and a JSON body consistent with the rest of the
     * API's error format.  The TCP connection is fully accepted and HTTP is spoken, so
     * callers receive an actionable response rather than a connection drop.  The 503 is
     * also logged at {@code WARNING} level and fires the {@link ServerEventListener} so
     * operators and monitoring systems see the activity.
     * <p>
     * During graceful shutdown, {@code shuttingDown} is set to {@code true} before the
     * semaphore drain begins.  This handler checks the flag first so that new requests
     * are rejected immediately throughout the entire drain window — not just when capacity
     * is exhausted.
     */
    private static final class ConcurrencyLimitHandler extends Handler.Wrapper {
        private static final String CONTENT_TYPE_JSON = "application/json";
        private static final String RETRY_AFTER_SECONDS = "1";
        private static final byte[] BODY_503 = (
                "{\"message\":\"Service temporarily unavailable." +
                        "  The server has reached its maximum concurrent request limit.\"}"
        ).getBytes(StandardCharsets.UTF_8);

        private final Semaphore semaphore;
        private final AtomicBoolean shuttingDown;
        private final String healthCheckPath;
        private final RequestLogger requestLogger;
        private final ServerEventListener eventListener;

        private ConcurrencyLimitHandler(Semaphore semaphore,
                                        AtomicBoolean shuttingDown,
                                        RequestLogger requestLogger,
                                        ServerEventListener eventListener,
                                        String healthCheckPath) {
            this.semaphore = semaphore;
            this.shuttingDown = shuttingDown;
            this.healthCheckPath = healthCheckPath;
            this.requestLogger = requestLogger;
            this.eventListener = eventListener;
        }

        @Override
        public boolean handle(org.eclipse.jetty.server.Request request,
                              org.eclipse.jetty.server.Response response,
                              Callback callback) throws Exception {
            // During graceful shutdown: reject ALL requests with 503, including the health
            // check.  This is the correct signal — the LB sees "unhealthy" and stops routing
            // traffic to this instance while in-flight requests drain.
            if (shuttingDown.get()) {
                String path = request.getHttpURI().getPath();
                write503(request.getMethod(), path, response, callback);

                return true;
            }

            if (!semaphore.tryAcquire()) {
                String path = request.getHttpURI().getPath();

                // At capacity but still running.  Health check requests bypass the gate:
                // the server IS healthy, just busy.  Returning 503 here would trick the LB
                // into recycling a live server under high load — the opposite of what we want.
                if (path.equals(healthCheckPath)) {
                    return super.handle(request, response, callback);
                }

                write503(request.getMethod(), path, response, callback);

                return true;
            }

            // Permit acquired — wrap the callback so the permit is released when the
            // response is complete, regardless of success or failure.
            Callback releasing = new Callback() {
                @Override
                public void succeeded() {
                    semaphore.release();
                    callback.succeeded();
                }

                @Override
                public void failed(Throwable t) {
                    semaphore.release();
                    callback.failed(t);
                }
            };

            boolean handled;

            try {
                handled = super.handle(request, response, releasing);
            } catch (Exception ex) {
                // super.handle threw before the callback could take ownership —
                // release the permit immediately so capacity is not permanently lost.
                semaphore.release();
                throw ex;
            }

            if (!handled) {
                // No handler claimed the request (404 path) — the framework will use
                // the original callback, not our releasing wrapper, so release now.
                semaphore.release();
            }

            return handled;
        }

        private void write503(String method,
                              String path,
                              org.eclipse.jetty.server.Response response,
                              Callback callback) {
            StopWatch watch = StopWatch.start();

            response.setStatus(HttpStatus.SERVICE_UNAVAILABLE_503);
            response.getHeaders().put(HttpHeader.CONTENT_TYPE, CONTENT_TYPE_JSON);
            response.getHeaders().put(HttpHeader.RETRY_AFTER, RETRY_AFTER_SECONDS);
            response.getHeaders().put(HttpHeader.CONTENT_LENGTH, BODY_503.length);

            response.write(true, ByteBuffer.wrap(BODY_503), new Callback() {
                @Override
                public void succeeded() {
                    logAndFire(method, path, watch);
                    callback.succeeded();
                }

                @Override
                public void failed(Throwable t) {
                    logAndFire(method, path, watch);
                    callback.failed(t);
                }
            });
        }

        private void logAndFire(String method, String path, StopWatch watch) {
            watch.stop();

            Duration latency = watch.duration();

            requestLogger.logCapacityRejection(method, path, latency.toMillis());

            try {
                eventListener.onRequestCompleted(new software.frisby.web.server.event.RequestCompletedEvent(
                        method,
                        path,
                        HttpStatus.SERVICE_UNAVAILABLE_503,
                        latency,
                        0L,
                        BODY_503.length
                ));
            } catch (Exception ex) {
                if (LOGGER.isLoggable(System.Logger.Level.WARNING)) {
                    LOGGER.log(
                            System.Logger.Level.WARNING,
                            "ServerEventListener.onRequestCompleted threw an exception.",
                            ex
                    );
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Jersey ApplicationEventListener — hooks request lifecycle events
    // -------------------------------------------------------------------------

    private static final class ServerApplicationEventListener implements ApplicationEventListener {
        private final ServerConfiguration configuration;
        private final ServerEventListener eventListener;
        private final RequestLogger requestLogger;
        private final String healthCheckPath;

        private ServerApplicationEventListener(ServerConfiguration configuration,
                                               ServerEventListener eventListener,
                                               RequestLogger requestLogger,
                                               String healthCheckPath) {
            this.configuration = configuration;
            this.eventListener = eventListener;
            this.requestLogger = requestLogger;
            this.healthCheckPath = healthCheckPath;
        }

        @Override
        public void onEvent(ApplicationEvent event) {
            // Application-level events are not forwarded — lifecycle events
            // (start/stop) are handled directly in DefaultServer.
        }

        @Override
        public RequestEventListener onRequest(RequestEvent requestEvent) {
            return new ServerRequestEventListener(
                    configuration,
                    eventListener,
                    requestLogger,
                    healthCheckPath
            );
        }
    }


    // -------------------------------------------------------------------------
    // Request body buffering filter — always registered; re-wraps entity stream
    // -------------------------------------------------------------------------

    /**
     * Reads up to {@code maxLogBodySize} bytes from the incoming request entity stream
     * and stores them as a {@code byte[]} under {@link ServerRequestEventListener#BUFFERED_BODY_KEY} in the request
     * context.  The entity stream is re-wrapped with a {@link SequenceInputStream} so that
     * downstream filters and resource methods receive the complete, unmodified body.
     * <p>
     * Only text-based content types are buffered — {@code text/*}, {@code application/json},
     * {@code application/*+json}, {@code application/xml}, {@code application/*+xml}, and
     * {@code application/x-www-form-urlencoded}.  Multipart and binary bodies
     * ({@code application/octet-stream}, {@code image/*}, {@code audio/*}, {@code video/*},
     * etc.) are skipped; {@code buildDetail} in {@link ServerRequestEventListener} renders a placeholder for them.
     * Requests with no {@code Content-Type} header are buffered optimistically since they
     * typically carry no body.
     * <p>
     * Registered by {@link DefaultServer#buildResourceConfig()} at priority
     * {@link Priorities#USER}{@code  - 1} so it runs before user-registered filters.
     * When {@code maxLogBodySize == 0} the filter is a no-op.
     */
    @PreMatching
    @Priority(Priorities.USER - 1)
    private static final class RequestBodyBufferingFilter implements ContainerRequestFilter {
        private final int maxLogBodySize;

        private RequestBodyBufferingFilter(int maxLogBodySize) {
            this.maxLogBodySize = maxLogBodySize;
        }

        @Override
        public void filter(ContainerRequestContext requestContext) throws IOException {
            if (maxLogBodySize <= 0) {
                return;
            }

            // Multipart bodies are binary — skip buffering; buildDetail renders a placeholder.
            MediaType mediaType = requestContext.getMediaType();

            if (null != mediaType && "multipart".equals(mediaType.getType())) {
                return;
            }

            // Non-text binary bodies (octet-stream, image/*, audio/*, video/*, etc.) —
            // skip buffering; buildDetail renders a placeholder.  isTextBody treats null
            // as text-safe so that requests without a Content-Type are still buffered.
            if (!ServerRequestEventListener.isTextBody(mediaType)) {
                return;
            }

            InputStream entityStream = requestContext.getEntityStream();
            byte[] buffer = new byte[maxLogBodySize];
            int bytesRead = 0;
            int n;

            while (bytesRead < maxLogBodySize) {
                n = entityStream.read(buffer, bytesRead, maxLogBodySize - bytesRead);

                if (-1 == n) {
                    break;
                }

                bytesRead += n;
            }

            if (0 < bytesRead) {
                byte[] captured = Arrays.copyOf(buffer, bytesRead);

                requestContext.setProperty(ServerRequestEventListener.BUFFERED_BODY_KEY, captured);

                requestContext.setEntityStream(
                        new SequenceInputStream(new ByteArrayInputStream(captured), entityStream)
                );
            }
        }
    }

    // -------------------------------------------------------------------------
    // Health Check Inflector — responds to GET requests on '/health' or a custom path
    // -------------------------------------------------------------------------

    private static final class HealthCheckHandler implements Inflector<ContainerRequestContext, Response> {
        @Override
        public Response apply(ContainerRequestContext context) {
            return Response
                    .ok(HEALTH_CHECK_BODY)
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        }
    }
}

