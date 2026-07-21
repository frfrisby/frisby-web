package software.frisby.web.client;

import software.frisby.web.client.security.SecurityProvider;

/**
 * Package-private implementation of {@link PatchSpec}.
 * <p>
 * All state and behavior are inherited from {@link AbstractBodyRequest}.
 * PATCH does not support multipart bodies, so no additional overrides are needed.
 */
final class PatchRequest extends AbstractBodyRequest<PatchSpec> implements PatchSpec {
    private static final String PATCH = "PATCH";

    PatchRequest(HttpEngine engine, SecurityProvider defaultSecurity) {
        super(engine, defaultSecurity);
    }

    @Override
    protected String method() {
        return PATCH;
    }

    @Override
    protected PatchSpec self() {
        return this;
    }
}
