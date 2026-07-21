package software.frisby.web.client;

import software.frisby.web.client.security.SecurityProvider;

/**
 * Package-private implementation of {@link PutSpec}.
 * <p>
 * All state and behavior — including multipart body support — are inherited from
 * {@link AbstractMultipartBodyRequest}. This class contributes only the HTTP
 * method string and the typed self-reference.
 */
final class PutRequest extends AbstractMultipartBodyRequest<PutSpec> implements PutSpec {
    private static final String PUT = "PUT";

    PutRequest(HttpEngine engine, SecurityProvider defaultSecurity) {
        super(engine, defaultSecurity);
    }

    @Override
    protected String method() {
        return PUT;
    }

    @Override
    protected PutSpec self() {
        return this;
    }
}
