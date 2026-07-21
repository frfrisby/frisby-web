package software.frisby.web.client;

import software.frisby.web.client.security.SecurityProvider;

/**
 * Package-private implementation of {@link PostSpec}.
 * <p>
 * All state and behavior — including multipart body support — are inherited from
 * {@link AbstractMultipartBodyRequest}. This class contributes only the HTTP
 * method string and the typed self-reference.
 */
final class PostRequest extends AbstractMultipartBodyRequest<PostSpec> implements PostSpec {
    private static final String POST = "POST";

    PostRequest(HttpEngine engine, SecurityProvider defaultSecurity) {
        super(engine, defaultSecurity);
    }

    @Override
    protected String method() {
        return POST;
    }

    @Override
    protected PostSpec self() {
        return this;
    }
}
