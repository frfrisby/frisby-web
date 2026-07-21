package software.frisby.web.server;

import software.frisby.core.validation.Values;
import software.frisby.web.serial.JsonSerializer;

import javax.net.ssl.SSLContext;
import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.function.UnaryOperator;

/**
 * A builder for creating a {@link ServerConfiguration} instance.
 * <p>
 * Obtain a builder via {@link ServerConfiguration#builder()}.
 *
 * <pre>{@code
 * ServerConfiguration config = ServerConfiguration.builder()
 *         .port(8080)
 *         .serializer(new JacksonSerializer())
 *         .build();
 *
 * // With optional settings
 * ServerConfiguration config = ServerConfiguration.builder()
 *         .port(8443)
 *         .host("0.0.0.0")
 *         .maxRequestSize(10 * 1024 * 1024)   // 10 MB
 *         .serializer(new JacksonSerializer())
 *         .ssl(sslContext)
 *         .build();
 * }</pre>
 *
 * @see ServerConfiguration
 */
public interface ServerConfigurationBuilder {
    /**
     * Sets the network port the server will bind to and listen on.
     * <p>
     * Required.  {@link #build()} throws if no port is provided.
     * <p>
     * Pass {@code 0} to let the OS assign a free ephemeral port — useful in tests to
     * avoid port-conflict races.  Retrieve the actual bound port via {@link Server#port()}
     * after {@link Server#start()} returns.
     *
     * @param port A valid port number ({@code 0} – {@code 65535}).  {@code 0} means
     *             "any free port".
     * @return This builder.
     * @throws software.frisby.core.validation.NumericValueOutsideRangeException if {@code port} is
     *                                                                           outside [0, 65535].
     */
    ServerConfigurationBuilder port(int port);

    /**
     * Sets the hostname or IP address the server will bind to.
     * <p>
     * Optional.  Defaults to {@code "0.0.0.0"} (all interfaces).
     * Use {@code "localhost"} to restrict to the loopback interface only.
     *
     * @param host A hostname or IP address; must not be {@code null} or blank.
     * @return This builder.
     * @throws software.frisby.core.validation.NullValueException  if {@code host} is {@code null}.
     * @throws software.frisby.core.validation.BlankValueException if {@code host} is blank.
     */
    ServerConfigurationBuilder host(String host);

    /**
     * Sets the maximum size in bytes of an incoming request body.
     * <p>
     * Optional.  Defaults to {@code 4194304} (4 MB).
     * Requests whose body exceeds this limit are rejected with HTTP 413.
     *
     * @param maxRequestSize The maximum allowed request body size in bytes; must be positive.
     * @return This builder.
     * @throws software.frisby.core.validation.NumericValueOutsideRangeException if {@code maxRequestSize}
     *                                                                           is not positive.
     */
    ServerConfigurationBuilder maxRequestSize(long maxRequestSize);

    /**
     * Enables full bidirectional gzip support.
     * <p>
     * When set, the server will:
     * <ul>
     *   <li>Transparently decompress incoming request bodies with
     *       {@code Content-Encoding: gzip}.</li>
     *   <li>Compress {@code application/json} response bodies when the client
     *       advertises {@code Accept-Encoding: gzip}.</li>
     * </ul>
     * Optional.  Defaults to {@code false}.
     * <p>
     * For advanced compression control — for example, compressing additional
     * media types or applying custom size thresholds — omit this call and
     * register a custom {@code ContainerResponseFilter} (and {@code GZipEncoder}
     * if needed) via {@link ServerBuilder#components(Object...)}.
     *
     * @return This builder.
     */
    ServerConfigurationBuilder gzip();

    /**
     * Enables HTTP/2 support.  The transport variant selected depends on whether
     * {@link #ssl(SSLContext)} is also configured:
     * <ul>
     *   <li><strong>With SSL ({@code ssl()} or {@code ssl(SSLContext)} also called)</strong> —
     *       h2 over TLS via ALPN negotiation.  Clients advertising {@code h2} during the TLS
     *       handshake receive HTTP/2; HTTP/1.1 clients fall back automatically.  The protocol
     *       list presented to clients is {@code ["h2", "http/1.1"]} with {@code http/1.1} as
     *       the fallback default.</li>
     *   <li><strong>Without SSL (plain HTTP)</strong> — h2c (HTTP/2 cleartext, RFC 7540 §3.2).
     *       The server accepts the standard {@code Upgrade: h2c} handshake on the same port and
     *       connector as HTTP/1.1; no additional configuration is required.  Use this variant
     *       behind a TLS-terminating load balancer (e.g. AWS ALB with Protocol Version HTTP2)
     *       to gain HTTP/2 multiplexing and HPACK header compression on internal service-to-service
     *       traffic without adding certificate management to each backend service.</li>
     * </ul>
     * <p>
     * Optional.  Defaults to {@code false} (HTTP/1.1 only).
     *
     * <pre>{@code
     * // h2c — plain HTTP with HTTP/2 upgrade (ALB or direct internal traffic)
     * ServerConfiguration.builder()
     *         .port(8080)
     *         .serializer(serializer)
     *         .http2()
     *         .build();
     *
     * // h2 over TLS — direct HTTPS with ALPN negotiation
     * ServerConfiguration.builder()
     *         .port(8443)
     *         .serializer(serializer)
     *         .ssl(sslContext)
     *         .http2()
     *         .build();
     * }</pre>
     *
     * @return This builder.
     */
    ServerConfigurationBuilder http2();

    /**
     * Sets the {@link JsonSerializer} used to (de)serialize request and response
     * entity bodies.
     * <p>
     * Required.  {@link #build()} throws if no serializer is provided.
     *
     * @param serializer The JSON serializer implementation; must not be {@code null}.
     * @return This builder.
     * @throws software.frisby.core.validation.NullValueException if {@code serializer} is {@code null}.
     */
    ServerConfigurationBuilder serializer(JsonSerializer serializer);

    /**
     * Enables HTTPS using the JVM's default {@link SSLContext}.
     * <p>
     * The default context is initialized from the standard JVM SSL system properties:
     * <ul>
     *   <li>{@code javax.net.ssl.keyStore} / {@code javax.net.ssl.keyStorePassword}</li>
     *   <li>{@code javax.net.ssl.trustStore} / {@code javax.net.ssl.trustStorePassword}</li>
     * </ul>
     * Optional.  When not called, the server accepts plain HTTP connections.
     * Prefer {@link #ssl(SSLContext)} when constructing the context programmatically
     * (e.g. loading a keystore from the classpath or a secrets manager).
     * <p>
     * <em>Implementation note:</em> delegates to {@link SSLContext#getDefault()}, which
     * declares a checked {@link java.security.NoSuchAlgorithmException} that is wrapped
     * in an {@link IllegalStateException} here.  In practice this exception is never
     * thrown — every standard JDK ships with a {@code "Default"} SSL algorithm — but
     * the wrap preserves a clean, unchecked call site for callers.
     *
     * @return This builder.
     * @throws IllegalStateException in the theoretically impossible event that the JVM
     *                               has no default SSL algorithm; never thrown in practice.
     */
    default ServerConfigurationBuilder ssl() {
        return ssl(SSLContextHelper.wrapGetDefault(SSLContext::getDefault));
    }

    /**
     * Enables HTTPS using the provided {@link SSLContext}.
     * <p>
     * Optional.  When not called, the server accepts plain HTTP connections.
     *
     * @param sslContext The SSL context to use for HTTPS; must not be {@code null}.
     * @return This builder.
     * @throws software.frisby.core.validation.NullValueException if {@code sslContext} is {@code null}.
     */
    ServerConfigurationBuilder ssl(SSLContext sslContext);

    /**
     * Configures the CORS (Cross-Origin Resource Sharing) policy for this server.
     * <p>
     * When set, a filter is registered that:
     * <ul>
     *   <li>Intercepts preflight {@code OPTIONS} requests and responds with the
     *       appropriate {@code Access-Control-*} headers before URI matching or
     *       authentication filters run.</li>
     *   <li>Adds {@code Access-Control-Allow-Origin} (and optionally
     *       {@code Access-Control-Allow-Credentials} and {@code Vary: Origin}) to
     *       all actual responses whose {@code Origin} header matches the allowed list.</li>
     * </ul>
     * <p>
     * Optional.  When not called, no CORS headers are added and preflight requests
     * are handled by normal URI routing (typically returning 405 Method Not Allowed).
     *
     * <pre>{@code
     * CorsConfiguration cors = CorsConfiguration.builder()
     *         .allowedOrigins("https://app.example.com")
     *         .allowedMethods("GET", "POST", "PUT", "DELETE")
     *         .allowedHeaders("Authorization", "Content-Type")
     *         .allowCredentials()
     *         .build();
     *
     * ServerConfiguration.builder()
     *         .port(8080)
     *         .serializer(serializer)
     *         .cors(cors)
     *         .build();
     * }</pre>
     *
     * @param cors The CORS configuration; must not be {@code null}.
     * @return This builder.
     * @throws software.frisby.core.validation.NullValueException if {@code cors} is {@code null}.
     */
    ServerConfigurationBuilder cors(CorsConfiguration cors);

    /**
     * Sets the failure-log configuration controlling header masking, body field
     * redaction, and maximum logged body size.
     * <p>
     * Optional.  When not called, a default {@link ServerLoggingConfiguration} is used:
     * 8 KB body size limit, no redacted fields, and only the three hard-coded headers
     * ({@code Authorization}, {@code Cookie}, {@code Set-Cookie}) masked.
     *
     * <pre>{@code
     * ServerLoggingConfiguration logging = ServerLoggingConfiguration.builder()
     *         .maxBodySize(4096)
     *         .redactHeaders("X-Amzn-Oidc-Data")
     *         .redactFields("password", "token")
     *         .build();
     *
     * ServerConfiguration.builder()
     *         .port(8080)
     *         .serializer(serializer)
     *         .logging(logging)
     *         .build();
     * }</pre>
     *
     * @param logging The logging configuration; must not be {@code null}.
     * @return This builder.
     * @throws software.frisby.core.validation.NullValueException if {@code logging} is {@code null}.
     */
    ServerConfigurationBuilder logging(ServerLoggingConfiguration logging);

    /**
     * Convenience overload — configures failure-log settings via a lambda instead of
     * constructing a {@link ServerLoggingConfiguration} object explicitly.
     * <p>
     * The library creates a fresh {@link ServerLoggingConfigurationBuilder}, passes it to
     * {@code configurer}, and delegates the result to
     * {@link #logging(ServerLoggingConfiguration)}.  This is equivalent to:
     *
     * <pre>{@code
     * ServerLoggingConfigurationBuilder builder = ServerLoggingConfiguration.builder();
     * ServerLoggingConfiguration logging = configurer.apply(builder).build();
     * return logging(logging);
     * }</pre>
     * <p>
     * Typical usage:
     *
     * <pre>{@code
     * ServerConfiguration.builder()
     *         .port(8080)
     *         .serializer(serializer)
     *         .logging(l -> l.maxBodySize(4096)
     *                        .redactHeaders("X-Amzn-Oidc-Data")
     *                        .redactFields("password", "token"))
     *         .build();
     * }</pre>
     *
     * @param configurer A function that receives a fresh {@link ServerLoggingConfigurationBuilder}
     *                   and returns it after applying the desired settings; must not be
     *                   {@code null}.
     * @return This builder.
     * @throws software.frisby.core.validation.NullValueException if {@code configurer} is {@code null}.
     */
    default ServerConfigurationBuilder logging(UnaryOperator<ServerLoggingConfigurationBuilder> configurer) {
        ServerLoggingConfigurationBuilder builder = ServerLoggingConfiguration.builder();

        return logging(
                Values.notNull("configurer", configurer)
                        .apply(builder)
                        .build()
        );
    }

    /**
     * Sets the maximum number of requests allowed to be in-flight simultaneously.
     * <p>
     * Optional.  Defaults to {@code Runtime.getRuntime().availableProcessors() * 20},
     * which is a reasonable starting point for CPU-bound workloads on most hardware
     * (e.g. 80 on a 4-core machine, 320 on a 16-core machine).
     * <p>
     * When this limit is reached, additional requests receive an immediate HTTP 503
     * response with a {@code Retry-After: 1} header rather than being queued or
     * dropped at the TCP level — callers receive an actionable signal.
     *
     * <pre>{@code
     * ServerConfiguration.builder()
     *         .port(8080)
     *         .serializer(serializer)
     *         .maxConcurrentRequests(200)
     *         .build();
     * }</pre>
     *
     * @param maxConcurrentRequests The maximum simultaneous in-flight requests; must be positive.
     * @return This builder.
     * @throws software.frisby.core.validation.NumericValueOutsideRangeException if the value is not positive.
     */
    ServerConfigurationBuilder maxConcurrentRequests(int maxConcurrentRequests);

    /**
     * Sets the executor used to run individual request-handling tasks.
     * <p>
     * Optional.  When not called, Jetty uses standard platform threads.
     * <p>
     * Pass {@code Executors.newVirtualThreadPerTaskExecutor()} (Java 21+) to have
     * each request handled on its own virtual thread, eliminating per-thread stack
     * overhead and making blocking I/O safe at high concurrency.
     * <p>
     * Combining this with {@link #maxConcurrentRequests(int)} is the recommended
     * production configuration: virtual threads deliver I/O efficiency while
     * {@code maxConcurrentRequests} caps in-flight work and protects heap.
     * <p>
     * <em>Resource ownership note:</em> the caller is responsible for shutting down
     * the executor after the server is stopped.  Retrieve it via
     * {@link ServerConfiguration#executor()} on the built configuration.
     *
     * <pre>{@code
     * ServerConfiguration.builder()
     *         .port(8080)
     *         .serializer(serializer)
     *         .maxConcurrentRequests(200)
     *         .executor(Executors.newVirtualThreadPerTaskExecutor())
     *         .build();
     * }</pre>
     *
     * @param executor The executor to use for request handling; must not be {@code null}.
     * @return This builder.
     * @throws software.frisby.core.validation.NullValueException if {@code executor} is {@code null}.
     */
    ServerConfigurationBuilder executor(Executor executor);

    /**
     * Sets the maximum time the server will wait for in-flight requests to complete
     * before forcibly closing connections during {@link Server#stop()}.
     * <p>
     * Optional.  When not set, {@code stop()} terminates connections immediately.
     * <p>
     * Setting a stop timeout enables graceful shutdown: once {@code stop()} is called,
     * the server stops accepting new connections, then waits up to {@code timeout} for
     * any in-flight requests to complete before closing remaining connections.  This
     * prevents requests from being abruptly terminated during rolling deployments.
     *
     * <pre>{@code
     * // Allow 30 seconds for in-flight requests to drain
     * ServerConfiguration.builder()
     *         .port(8080)
     *         .serializer(serializer)
     *         .stopTimeout(Duration.ofSeconds(30))
     *         .build();
     * }</pre>
     *
     * @param timeout The maximum time to wait for in-flight requests; must not be
     *                {@code null} and must be positive.
     * @return This builder.
     * @throws software.frisby.core.validation.NullValueException            if {@code timeout} is {@code null}.
     * @throws software.frisby.core.validation.DurationOutsideRangeException if {@code timeout} is zero or negative.
     */
    ServerConfigurationBuilder stopTimeout(Duration timeout);

    /**
     * Returns a new {@link ServerConfiguration} instance based on the options
     * configured by calling the setter methods on this builder.
     *
     * @return A new {@link ServerConfiguration}; never {@code null}.
     * @throws IllegalStateException                               if {@link #http2()} was called
     *                                                             without also configuring SSL.
     * @throws software.frisby.core.validation.NullValueException  if a required value is {@code null}.
     * @throws software.frisby.core.validation.BlankValueException if a required string value is blank.
     */
    ServerConfiguration build();
}

