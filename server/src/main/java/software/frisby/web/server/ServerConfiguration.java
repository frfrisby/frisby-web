package software.frisby.web.server;

import software.frisby.web.serial.JsonSerializer;

import javax.net.ssl.SSLContext;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Executor;

/**
 * Runtime configuration options for a {@link Server} instance.
 * <p>
 * Obtain a builder via the static {@link #builder()} factory method, configure the
 * options you need, and pass the resulting configuration to
 * {@link ServerBuilder#configuration(ServerConfiguration)}.
 *
 * <pre>{@code
 * ServerConfiguration config = ServerConfiguration.builder()
 *         .port(8080)
 *         .serializer(new JacksonSerializer())
 *         .build();
 * }</pre>
 *
 * @see ServerConfigurationBuilder
 * @see Server
 */
public interface ServerConfiguration {
    /**
     * Returns a new {@link ServerConfigurationBuilder} instance.
     *
     * @return A new builder; never {@code null}.
     */
    static ServerConfigurationBuilder builder() {
        return new DefaultServerConfigurationBuilder();
    }

    /**
     * Returns the network port the server will bind to and listen on.
     *
     * @return The configured port number.
     */
    int port();

    /**
     * Returns the hostname or IP address the server will bind to.
     * <p>
     * Defaults to {@code "0.0.0.0"}, which binds to all available network interfaces.
     * Use {@code "localhost"} or {@code "127.0.0.1"} to restrict to the loopback
     * interface only.
     *
     * @return The bind address; never {@code null}.
     */
    String host();

    /**
     * Returns the maximum size in bytes of an incoming request body.
     * <p>
     * Requests whose body exceeds this limit will be rejected with an HTTP 413
     * (Payload Too Large) response.
     *
     * @return The maximum request body size in bytes.
     */
    long maxRequestSize();

    /**
     * Returns {@code true} if full gzip support is enabled for this server.
     * <p>
     * When {@code true}, the server will:
     * <ul>
     *   <li>Transparently decompress incoming requests whose body carries
     *       {@code Content-Encoding: gzip}.</li>
     *   <li>Compress {@code application/json} responses when the client advertises
     *       {@code Accept-Encoding: gzip}.</li>
     * </ul>
     * Controlled by {@link ServerConfigurationBuilder#gzip()}.
     *
     * @return {@code true} if gzip support is enabled; {@code false} otherwise.
     */
    boolean gzip();

    /**
     * Returns {@code true} if HTTP/2 support is enabled for this server.
     * <p>
     * When {@code true} and an SSL context is configured, the server uses ALPN negotiation
     * (h2 over TLS).  When {@code true} and no SSL context is configured, the server
     * accepts HTTP/2 cleartext upgrades (h2c).
     * <p>
     * Controlled by {@link ServerConfigurationBuilder#http2()}.
     *
     * @return {@code true} if HTTP/2 support is enabled; {@code false} otherwise.
     */
    boolean http2();

    /**
     * Returns the {@link JsonSerializer} used to (de)serialize request and response
     * entity bodies.
     *
     * @return The serializer; never {@code null}.
     */
    JsonSerializer serializer();

    /**
     * Returns the {@link SSLContext} to use for HTTPS connections, if SSL is enabled.
     * <p>
     * When present, the server accepts only HTTPS connections on the configured port.
     * When empty, the server accepts plain HTTP connections.
     *
     * @return An {@link Optional} containing the {@link SSLContext}, or
     * {@link Optional#empty()} if SSL is not configured.
     */
    Optional<SSLContext> ssl();

    /**
     * Returns the CORS policy applied to all responses, if one was configured.
     * <p>
     * When present, a {@link CorsFilter} is registered that handles preflight
     * {@code OPTIONS} requests and injects {@code Access-Control-*} headers into
     * actual responses for permitted origins.
     * <p>
     * Controlled by {@link ServerConfigurationBuilder#cors(CorsConfiguration)}.
     *
     * @return An {@link Optional} containing the {@link CorsConfiguration}, or
     * {@link Optional#empty()} if CORS is not configured.
     */
    Optional<CorsConfiguration> cors();

    /**
     * Returns the logging configuration that controls failure-log detail: header
     * masking, body field redaction, and maximum body size.
     * <p>
     * When {@link ServerConfigurationBuilder#logging(ServerLoggingConfiguration)} is not
     * called, a default configuration is returned: 8 KB body size limit, no custom
     * redacted fields, and only the three hard-coded headers
     * ({@code Authorization}, {@code Cookie}, {@code Set-Cookie}) masked.
     *
     * @return The logging configuration; never {@code null}.
     */
    ServerLoggingConfiguration logging();

    /**
     * Returns the maximum number of requests allowed to be in-flight simultaneously.
     * <p>
     * When this limit is reached, additional requests receive an immediate HTTP 503
     * response with a {@code Retry-After: 1} header.  The limit is enforced at the
     * Jetty handler layer so that all in-flight requests — including ones that would
     * later be rejected with a 413 — count against the concurrency budget.
     * <p>
     * Defaults to {@code Runtime.getRuntime().availableProcessors() * 20} when not
     * explicitly configured via {@link ServerConfigurationBuilder#maxConcurrentRequests(int)}.
     *
     * @return The effective maximum concurrent requests; always positive.
     */
    int maxConcurrentRequests();

    /**
     * Returns the executor used to run individual request-handling tasks, if one was
     * configured.
     * <p>
     * When present, the executor is wired into Jetty's {@code QueuedThreadPool} via
     * {@code setVirtualThreadsExecutor()}.  The canonical use is
     * {@code Executors.newVirtualThreadPerTaskExecutor()} (Java 21+), which routes
     * each request onto its own virtual thread.
     * <p>
     * When empty, Jetty uses standard platform threads.
     * <p>
     * <em>Resource ownership note:</em> if an executor is configured, the caller is
     * responsible for shutting it down after the server is stopped.  Retrieve the
     * executor via {@code configuration().executor()} to access it after
     * {@link ServerConfiguration} is built.
     *
     * @return An {@link Optional} containing the executor, or
     * {@link Optional#empty()} if not set (platform threads apply).
     */
    Optional<Executor> executor();

    /**
     * Returns the graceful-shutdown timeout, if one was configured.
     * <p>
     * When present, {@link Server#stop()} performs an application-layer drain: new
     * requests are rejected immediately with HTTP 503 while the server waits up to
     * this duration for all in-flight requests to complete.  If in-flight requests
     * do not complete within the timeout, Jetty forcibly closes their connections.
     * When empty, {@code stop()} terminates connections immediately without a drain.
     *
     * @return An {@link Optional} containing the stop timeout, or
     * {@link Optional#empty()} if immediate shutdown is configured.
     */
    Optional<Duration> stopTimeout();
}
