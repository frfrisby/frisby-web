package software.frisby.web.client.security.basic;

import software.frisby.core.validation.Values;
import software.frisby.web.client.security.RequestContext;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Package-private implementation of {@link BasicSecurityProvider}.
 * <p>
 * On each call to {@link #secure(RequestContext)}, encodes the configured credentials
 * and sets the {@code Authorization: Basic <token>} request header.
 */
final class DefaultBasicSecurityProvider implements BasicSecurityProvider {
    private static final String AUTHORIZATION = "Authorization";
    private static final String BASIC_PREFIX = "Basic ";

    private final String encodedToken;

    DefaultBasicSecurityProvider(Credentials credentials) {
        Values.notNull("credentials", credentials);

        String combined = credentials.username() + ":" + credentials.password();
        this.encodedToken = Base64.getEncoder().encodeToString(combined.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void secure(RequestContext request) {
        request.addHeader(AUTHORIZATION, BASIC_PREFIX + encodedToken);
    }
}

