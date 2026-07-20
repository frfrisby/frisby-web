package software.frisby.web.client;

import software.frisby.core.validation.Values;
import software.frisby.web.client.event.ClientEventListener;
import software.frisby.web.client.security.SecurityProvider;

/**
 * Package-private implementation of {@link ClientBuilder}.
 */
final class DefaultClientBuilder implements ClientBuilder {
    private static final String CONFIGURATION_ARGUMENT_NAME = "configuration";
    private static final String SECURITY_ARGUMENT_NAME = "security";
    private static final String EVENT_LISTENER_ARGUMENT_NAME = "eventListener";

    private ClientConfiguration configuration;
    private SecurityProvider security;
    private ClientEventListener eventListener;

    DefaultClientBuilder() {
        this.configuration = null;
        this.security = null;
        this.eventListener = NoOpClientEventListener.INSTANCE;
    }

    @Override
    public ClientBuilder configuration(ClientConfiguration configuration) {
        this.configuration = Values.notNull(CONFIGURATION_ARGUMENT_NAME, configuration);
        return this;
    }

    @Override
    public ClientBuilder security(SecurityProvider provider) {
        this.security = Values.notNull(SECURITY_ARGUMENT_NAME, provider);
        return this;
    }

    @Override
    public ClientBuilder eventListener(ClientEventListener listener) {
        this.eventListener = Values.notNull(EVENT_LISTENER_ARGUMENT_NAME, listener);
        return this;
    }

    @Override
    public Client build() {
        return new DefaultClient(new HttpEngine(configuration, eventListener), security);
    }
}

