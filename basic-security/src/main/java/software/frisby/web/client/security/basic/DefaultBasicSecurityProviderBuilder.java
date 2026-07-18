package software.frisby.web.client.security.basic;

import software.frisby.core.validation.Values;

/**
 * Package-private implementation of {@link BasicSecurityProviderBuilder}.
 */
final class DefaultBasicSecurityProviderBuilder implements BasicSecurityProviderBuilder {
    private static final String CREDENTIALS = "credentials";

    private Credentials credentials;

    DefaultBasicSecurityProviderBuilder() {
        this.credentials = null;
    }

    @Override
    public BasicSecurityProviderBuilder credentials(Credentials credentials) {
        this.credentials = Values.notNull(CREDENTIALS, credentials);
        return this;
    }

    @Override
    public BasicSecurityProviderBuilder credentials(String username, String password) {
        this.credentials = Credentials.of(username, password);
        return this;
    }

    @Override
    public BasicSecurityProvider build() {
        if (null == credentials) {
            throw new IllegalStateException(
                    "The 'credentials' value is invalid.  Credentials must be provided before calling build()."
            );
        }

        return new DefaultBasicSecurityProvider(credentials);
    }
}

