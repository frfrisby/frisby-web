package software.frisby.web.client;

import software.frisby.core.validation.Values;
import software.frisby.web.client.event.ClientEventListener;
import software.frisby.web.client.security.SecurityProvider;

/**
 * Package-private implementation of {@link ClientBuilder}.
 */
final class DefaultClientBuilder implements ClientBuilder {
    private static final String CONFIGURATION = "configuration";
    private static final String SECURITY = "security";
    private static final String EVENT_LISTENER = "eventListener";

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
        this.configuration = Values.notNull(CONFIGURATION, configuration);
        return this;
    }

    @Override
    public ClientBuilder security(SecurityProvider provider) {
        this.security = Values.notNull(SECURITY, provider);
        return this;
    }

    @Override
    public ClientBuilder eventListener(ClientEventListener listener) {
        this.eventListener = Values.notNull(EVENT_LISTENER, listener);
        return this;
    }

    @Override
    public Client build() {
        return new DefaultClient(new HttpEngine(configuration, eventListener), security);
    }
}

