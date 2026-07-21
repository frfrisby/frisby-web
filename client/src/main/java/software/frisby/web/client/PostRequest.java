package software.frisby.web.client;

import software.frisby.core.validation.Values;
import software.frisby.web.client.security.SecurityProvider;

import java.net.URI;
import java.net.http.HttpRequest;

import static software.frisby.web.client.RequestConstants.*;

/**
 * Package-private implementation of {@link PostSpec}.
 * <p>
 * Navigation state and all body/send logic are handled by {@link AbstractBodyRequest}.
 * This class contributes the HTTP method string, the multipart body path, and the
 * {@code body(FormData)} overload that POST uniquely supports alongside PUT.
 */
final class PostRequest extends AbstractBodyRequest<PostSpec> implements PostSpec {
    private static final String POST = "POST";

    private FormData formData;

    PostRequest(HttpEngine engine, SecurityProvider defaultSecurity) {
        super(engine, defaultSecurity);
        this.formData = null;
    }

    @Override
    protected String method() {
        return POST;
    }

    @Override
    protected PostSpec self() {
        return this;
    }

    @Override
    public PostSpec body(Object body) {
        this.formData = null;
        return super.body(body);
    }

    @Override
    public PostSpec body(FormUrlEncoded formUrlEncoded) {
        this.formData = null;
        return super.body(formUrlEncoded);
    }

    @Override
    public PostSpec body(FormData formData) {
        this.formData = Values.notNull(BODY_ARGUMENT_NAME, formData);
        clearBodyFields();
        return this;
    }

    @Override
    OutboundRequest buildRequest(URI uri, boolean acceptJson) {
        if (null != formData) {
            return buildMultipartRequest(uri, acceptJson);
        }

        return super.buildRequest(uri, acceptJson);
    }

    private OutboundRequest buildMultipartRequest(URI uri, boolean acceptJson) {
        assertNotCompressed();

        String boundary = MultipartBodyBuilder.generateBoundary();
        HttpRequest.BodyPublisher bodyPublisher = MultipartBodyBuilder.build(
                formData.parts(),
                boundary,
                engine.configuration().serializer()
        );

        String acceptEncoding = acceptJson
                ? DefaultClientConfiguration.acceptEncoding(engine.configuration().decompressors())
                : null;

        HttpRequest.Builder builder = state.prepareBuilder(
                uri,
                method(),
                bodyPublisher,
                acceptJson,
                acceptEncoding,
                engine.configuration().readTimeout()
        );

        builder.header(Headers.CONTENT_TYPE, MultipartBodyBuilder.contentType(boundary));

        return OutboundRequest.of(builder.build(), OutboundRequest.MULTIPART_SNAPSHOT);
    }
}
