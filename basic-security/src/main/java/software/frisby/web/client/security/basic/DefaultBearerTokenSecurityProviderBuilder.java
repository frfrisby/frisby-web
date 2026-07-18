package software.frisby.web.client.security.basic;

import software.frisby.core.validation.Strings;
import software.frisby.core.validation.Values;

import java.util.function.Supplier;

/**
 * Package-private implementation of {@link BearerTokenSecurityProviderBuilder}.
 */
final class DefaultBearerTokenSecurityProviderBuilder implements BearerTokenSecurityProviderBuilder {
    private static final String TOKEN = "token";

    private Supplier<String> tokenSupplier;

    DefaultBearerTokenSecurityProviderBuilder() {
        this.tokenSupplier = null;
    }

    @Override
    public BearerTokenSecurityProviderBuilder token(String bearerToken) {
        Strings.notBlank(TOKEN, bearerToken);

        this.tokenSupplier = () -> bearerToken;
        return this;
    }

    @Override
    public BearerTokenSecurityProviderBuilder token(Supplier<String> tokenSupplier) {
        this.tokenSupplier = Values.notNull(TOKEN, tokenSupplier);
        return this;
    }

    @Override
    public BearerTokenSecurityProvider build() {
        if (null == tokenSupplier) {
            throw new IllegalStateException(
                    "The 'token' value is invalid.  A bearer token or token supplier must be provided."
            );
        }

        return new DefaultBearerTokenSecurityProvider(tokenSupplier);
    }
}

