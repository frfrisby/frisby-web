package software.frisby.web.client;

import software.frisby.core.validation.Values;
import software.frisby.web.client.event.ClientEventListener;
import software.frisby.web.client.security.SecurityProvider;

import java.util.function.UnaryOperator;

/**
 * A builder for creating an instance of {@link Client}.
 * <p>
 * A {@link ClientConfiguration} is required.  All other options have documented defaults.
 * {@link #build()} throws an {@link IllegalStateException} if no configuration is
 * provided.
 *
 * <pre>{@code
 * Client client = Client.builder()
 *         .configuration(
 *                 ClientConfiguration.builder()
 *                         .uri(URI.create("https://api.example.com"))
 *                         .connectTimeout(Duration.ofSeconds(5))
 *                         .readTimeout(Duration.ofSeconds(30))
 *                         .serializer(new JacksonSerializer())
 *                         .build()
 *         )
 *         .security(oauthProvider)
 *         .eventListener(myMetricsListener)
 *         .build();
 * }</pre>
 *
 * @see Client
 * @see ClientConfiguration
 * @see ClientConfigurationBuilder
 */
public interface ClientBuilder {
    /**
     * Sets the configuration for the client.  Required.
     *
     * @param configuration The client configuration; must not be {@code null}.
     * @return This builder instance.
     */
    ClientBuilder configuration(ClientConfiguration configuration);

    /**
     * Convenience overload — configures the client inline via a lambda instead of
     * constructing a {@link ClientConfiguration} object explicitly.
     * <p>
     * The library creates a fresh {@link ClientConfigurationBuilder}, passes it to
     * {@code configurer}, and delegates the result to
     * {@link #configuration(ClientConfiguration)}.  This is equivalent to:
     *
     * <pre>{@code
     * ClientConfigurationBuilder builder = ClientConfiguration.builder();
     * ClientConfiguration configuration = configurer.apply(builder).build();
     * return configuration(configuration);
     * }</pre>
     * <p>
     * Typical usage:
     *
     * <pre>{@code
     * Client client = Client.builder()
     *         .configuration(c -> c
     *                 .uri(URI.create("https://api.example.com"))
     *                 .connectTimeout(Duration.ofSeconds(5))
     *                 .readTimeout(Duration.ofSeconds(30))
     *                 .serializer(new JacksonSerializer()))
     *         .build();
     * }</pre>
     *
     * @param configurer A function that receives a fresh {@link ClientConfigurationBuilder}
     *                   and returns it after applying the desired settings; must not be
     *                   {@code null}.
     * @return This builder instance.
     * @throws software.frisby.core.validation.NullValueException if {@code configurer} is {@code null}.
     */
    default ClientBuilder configuration(UnaryOperator<ClientConfigurationBuilder> configurer) {
        ClientConfigurationBuilder builder = ClientConfiguration.builder();

        return configuration(
                Values.notNull("configurer", configurer)
                        .apply(builder)
                        .build()
        );
    }

    /**
     * Sets the default security provider applied to every request sent by this client.
     * <p>
     * The per-request {@code security(SecurityProvider)} method on each verb spec
     * overrides this default for individual requests.
     * <p>
     * Optional; defaults to no authentication.
     *
     * @param provider The security provider to apply to all requests; must not be
     *                 {@code null}.
     * @return This builder instance.
     */
    ClientBuilder security(SecurityProvider provider);

    /**
     * Sets the event listener that receives notifications after every completed or
     * failed request.
     * <p>
     * Optional; defaults to a no-op listener.
     *
     * @param listener The event listener; must not be {@code null}.
     * @return This builder instance.
     */
    ClientBuilder eventListener(ClientEventListener listener);

    /**
     * Returns a new {@link Client} instance based on the provided options.
     *
     * @return A new {@link Client} instance.
     * @throws IllegalStateException if no {@link ClientConfiguration} has been provided.
     */
    Client build();
}

