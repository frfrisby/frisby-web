package software.frisby.web.client.security.basic;

import software.frisby.web.client.security.RequestContext;

import java.util.function.Supplier;

/**
 * Package-private implementation of {@link BearerTokenSecurityProvider}.
 */
final class DefaultBearerTokenSecurityProvider implements BearerTokenSecurityProvider {
    private static final String AUTHORIZATION = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final Supplier<String> tokenSupplier;

    DefaultBearerTokenSecurityProvider(Supplier<String> tokenSupplier) {
        this.tokenSupplier = tokenSupplier;
    }

    @Override
    public void secure(RequestContext request) {
        request.addHeader(AUTHORIZATION, BEARER_PREFIX + tokenSupplier.get());
    }
}

