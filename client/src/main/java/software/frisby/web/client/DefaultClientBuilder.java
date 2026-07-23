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
    private static final String RETRY_POLICY_ARGUMENT_NAME = "policy";

    private ClientConfiguration configuration;
    private SecurityProvider security;
    private ClientEventListener eventListener;
    private RetryPolicy retryPolicy;

    DefaultClientBuilder() {
        this.configuration = null;
        this.security = null;
        this.eventListener = NoOpClientEventListener.INSTANCE;
        this.retryPolicy = RetryPolicy.none();
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
    public ClientBuilder retryPolicy(RetryPolicy policy) {
        this.retryPolicy = Values.notNull(RETRY_POLICY_ARGUMENT_NAME, policy);
        return this;
    }

    @Override
    public Client build() {
        return new DefaultClient(new HttpEngine(configuration, eventListener, retryPolicy), security);
    }
}

