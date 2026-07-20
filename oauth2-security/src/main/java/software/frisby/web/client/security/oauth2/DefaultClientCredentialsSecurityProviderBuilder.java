package software.frisby.web.client.security.oauth2;

import software.frisby.core.validation.Durations;
import software.frisby.core.validation.StringSequences;
import software.frisby.core.validation.Values;
import software.frisby.web.serial.JsonSerializer;

import javax.net.ssl.SSLContext;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Package-private implementation of {@link ClientCredentialsSecurityProviderBuilder}.
 */
final class DefaultClientCredentialsSecurityProviderBuilder
        implements ClientCredentialsSecurityProviderBuilder {
    private static final String TOKEN_ENDPOINT_ARGUMENT_NAME = "tokenEndpoint";
    private static final String CREDENTIALS_ARGUMENT_NAME = "credentials";
    private static final String SERIALIZER_ARGUMENT_NAME = "serializer";
    private static final String SCOPES_ARGUMENT_NAME = "scopes";
    private static final String CONNECT_TIMEOUT_ARGUMENT_NAME = "connectTimeout";
    private static final String REQUEST_TIMEOUT_ARGUMENT_NAME = "requestTimeout";
    private static final String SSL_CONTEXT_ARGUMENT_NAME = "sslContext";
    private static final String EVENT_LISTENER_ARGUMENT_NAME = "eventListener";
    private static final String EXPIRY_BUFFER_ARGUMENT_NAME = "expiryBuffer";

    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration DEFAULT_EXPIRY_BUFFER = Duration.ofSeconds(30);

    private final List<String> scopes;
    private URI tokenEndpoint;
    private ClientCredentials credentials;
    private JsonSerializer serializer;
    private Duration connectTimeout;
    private Duration requestTimeout;
    private SSLContext sslContext;
    private TokenEventListener eventListener;
    private boolean basicAuth;
    private Duration expiryBuffer;

    DefaultClientCredentialsSecurityProviderBuilder() {
        this.tokenEndpoint = null;
        this.credentials = null;
        this.serializer = null;
        this.scopes = new ArrayList<>();
        this.connectTimeout = DEFAULT_CONNECT_TIMEOUT;
        this.requestTimeout = DEFAULT_REQUEST_TIMEOUT;
        this.sslContext = null;
        this.eventListener = NoOpTokenEventListener.INSTANCE;
        this.basicAuth = false;
        this.expiryBuffer = DEFAULT_EXPIRY_BUFFER;
    }

    @Override
    public ClientCredentialsSecurityProviderBuilder tokenEndpoint(URI uri) {
        this.tokenEndpoint = Values.notNull(TOKEN_ENDPOINT_ARGUMENT_NAME, uri);
        return this;
    }

    @Override
    public ClientCredentialsSecurityProviderBuilder credentials(ClientCredentials credentials) {
        this.credentials = Values.notNull(CREDENTIALS_ARGUMENT_NAME, credentials);
        return this;
    }

    @Override
    public ClientCredentialsSecurityProviderBuilder credentials(String clientId, String clientSecret) {
        this.credentials = ClientCredentials.of(clientId, clientSecret);
        return this;
    }

    @Override
    public ClientCredentialsSecurityProviderBuilder serializer(JsonSerializer serializer) {
        this.serializer = Values.notNull(SERIALIZER_ARGUMENT_NAME, serializer);
        return this;
    }

    @Override
    public ClientCredentialsSecurityProviderBuilder scope(String... scopes) {
        StringSequences.notBlank(SCOPES_ARGUMENT_NAME, scopes);

        Collections.addAll(this.scopes, scopes);
        return this;
    }

    @Override
    public ClientCredentialsSecurityProviderBuilder connectTimeout(Duration timeout) {
        this.connectTimeout = Durations.positive(CONNECT_TIMEOUT_ARGUMENT_NAME, timeout);
        return this;
    }

    @Override
    public ClientCredentialsSecurityProviderBuilder requestTimeout(Duration timeout) {
        this.requestTimeout = Durations.positive(REQUEST_TIMEOUT_ARGUMENT_NAME, timeout);
        return this;
    }

    @Override
    public ClientCredentialsSecurityProviderBuilder sslContext(SSLContext sslContext) {
        this.sslContext = Values.notNull(SSL_CONTEXT_ARGUMENT_NAME, sslContext);
        return this;
    }

    @Override
    public ClientCredentialsSecurityProviderBuilder eventListener(TokenEventListener listener) {
        this.eventListener = Values.notNull(EVENT_LISTENER_ARGUMENT_NAME, listener);
        return this;
    }

    @Override
    public ClientCredentialsSecurityProviderBuilder basicAuth() {
        this.basicAuth = true;
        return this;
    }

    @Override
    public ClientCredentialsSecurityProviderBuilder expiryBuffer(Duration buffer) {
        this.expiryBuffer = Durations.positive(EXPIRY_BUFFER_ARGUMENT_NAME, buffer);
        return this;
    }

    @Override
    public ClientCredentialsSecurityProvider build() {
        return new DefaultClientCredentialsSecurityProvider(
                tokenEndpoint,
                credentials,
                serializer,
                List.copyOf(scopes),
                connectTimeout,
                requestTimeout,
                sslContext,
                eventListener,
                basicAuth,
                expiryBuffer
        );
    }
}
