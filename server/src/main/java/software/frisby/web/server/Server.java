package software.frisby.web.server;

import java.net.URI;

/**
 * An embedded HTTP server that hosts JAX-RS annotated resource instances.
 * <p>
 * Obtain a builder via {@link #builder()}, configure it, and call
 * {@link ServerBuilder#build()} to create a server instance.  The server does
 * not accept connections until {@link #start()} is called.
 *
 * <pre>{@code
 * Server server = Server.builder()
 *         .configuration(
 *                 ServerConfiguration.builder()
 *                         .port(8080)
 *                         .serializer(new JacksonSerializer())
 *                         .build()
 *         )
 *         .resources(new OrderResource(orderService))
 *         .build();
 *
 * server.start();
 * // ... serve requests ...
 * server.stop();
 * }</pre>
 *
 * <p>Implementations are thread-safe.  Calling {@link #start()} or {@link #stop()}
 * concurrently from multiple threads is safe; only the first caller in each case
 * has any effect.</p>
 *
 * @see ServerBuilder
 * @see ServerConfiguration
 */
public interface Server {
    /**
     * Returns a new {@link ServerBuilder} instance.
     *
     * @return A new builder; never {@code null}.
     */
    static ServerBuilder builder() {
        return new DefaultServerBuilder();
    }

    /**
     * Returns the actual port the server is (or was last) bound to.
     * <p>
     * When an explicit port was configured, this always returns that value.
     * When {@code port(0)} was configured, this returns {@code 0} before
     * {@link #start()} is called and returns the OS-assigned port once the
     * server has started.  After {@link #stop()}, the last-used port is retained.
     *
     * @return The bound port; {@code 0} if port-{@code 0} was configured and
     * the server has not yet been started.
     */
    int port();

    /**
     * Returns the base {@link java.net.URI} the server is (or was last) bound to.
     * <p>
     * The URI is composed of the configured scheme ({@code http} or {@code https}),
     * host, and the value returned by {@link #port()}.  It does <em>not</em> include a
     * trailing slash, making it suitable for direct use as a client base URI:
     *
     * <pre>{@code
     * Client client = Client.builder()
     *         .configuration(
     *                 Configuration.builder()
     *                         .uri(server.uri())
     *                         .serializer(mySerializer)
     *                         .build()
     *         )
     *         .build();
     * }</pre>
     *
     * <p>When {@code port(0)} was configured, this returns a URI with port {@code 0}
     * before {@link #start()} is called and the correct OS-assigned URI once the server
     * has started.  After {@link #stop()}, the last-used URI is retained.
     *
     * @return The base URI; never {@code null}.
     */
    URI uri();

    /**
     * Returns the {@link ServerConfiguration} used to create this server instance.
     *
     * @return The server configuration; never {@code null}.
     */
    ServerConfiguration configuration();

    /**
     * Returns {@code true} if the server is currently running and accepting connections.
     *
     * @return {@code true} if the server is running; {@code false} otherwise.
     */
    boolean isRunning();

    /**
     * Starts the server so that the registered resource components can accept
     * incoming HTTP requests.
     * <p>
     * If the server is already running this method has no effect.
     *
     * @throws java.io.UncheckedIOException if the server cannot bind to the configured port.
     */
    void start();

    /**
     * Stops the server immediately, releasing the bound port and all associated resources.
     * <p>
     * If the server is not running this method has no effect.
     */
    void stop();
}

