package software.frisby.web.client;

import software.frisby.core.validation.Values;
import software.frisby.web.client.security.SecurityProvider;

import java.net.URI;
import java.net.http.HttpRequest;

import static software.frisby.web.client.RequestConstants.BODY_ARGUMENT_NAME;

/**
 * Intermediate abstract base for request implementations that support multipart
 * bodies — {@link PostRequest} and {@link PutRequest}.
 * <p>
 * Extends {@link AbstractBodyRequest} with a {@link FormData} body field,
 * the {@code body(FormData)} overload, and {@code buildMultipartRequest}.
 * Both {@link PostRequest} and {@link PutRequest} inherit this class and
 * contribute only {@link #method()} and {@link #self()}.
 *
 * @param <S> The spec interface type returned by the fluent builder methods.
 */
abstract class AbstractMultipartBodyRequest<S> extends AbstractBodyRequest<S> {
    private FormData formData;

    AbstractMultipartBodyRequest(HttpEngine engine, SecurityProvider defaultSecurity) {
        super(engine, defaultSecurity);
        this.formData = null;
    }

    @Override
    public S body(Object body) {
        this.formData = null;
        return super.body(body);
    }

    @Override
    public S body(FormUrlEncoded formUrlEncoded) {
        this.formData = null;
        return super.body(formUrlEncoded);
    }

    public S body(FormData formData) {
        this.formData = Values.notNull(BODY_ARGUMENT_NAME, formData);
        clearBodyFields();
        return self();
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

