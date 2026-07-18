package software.frisby.web.client;

import software.frisby.core.validation.Values;
import software.frisby.web.client.security.SecurityProvider;
import software.frisby.web.serial.GenericType;

import java.net.HttpCookie;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.CompletableFuture;

/**
 * Package-private implementation of {@link PostSpec}.
 * <p>
 * Navigation state is held by a {@link RequestState} instance; this class is
 * responsible for body assembly and dispatching POST-specific requests via the
 * shared {@link HttpEngine}.
 */
final class PostRequest implements PostSpec {
    private static final String POST = "POST";
    private static final String RESPONSE_TYPE = "responseType";
    private static final String BODY = "body";
    private static final String COMPRESSOR = "compressor";
    private static final String COMPRESS_WITH_FORM_ERROR =
            "The 'compress' value is invalid.  Compression is only supported for JSON entity bodies.";

    private static final ContentCompressor GZIP_COMPRESSOR =
            ContentCompressor.of("gzip", GZip::compress);

    private final HttpEngine engine;
    private final RequestState state;

    private Object jsonBody;
    private FormData formData;
    private FormUrlEncoded formUrlEncoded;
    private ContentCompressor compressor;

    PostRequest(HttpEngine engine, SecurityProvider defaultSecurity) {
        this.engine = engine;
        this.state = new RequestState(defaultSecurity);
        this.jsonBody = null;
        this.formData = null;
        this.formUrlEncoded = null;
        this.compressor = null;
    }

    private static String encodeFormFields(java.util.Map<String, String> fields) {
        StringJoiner joiner = new StringJoiner("&");

        for (Map.Entry<String, String> entry : fields.entrySet()) {
            String encodedName = URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8);
            String encodedValue = URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8);

            joiner.add(encodedName + "=" + encodedValue);
        }

        return joiner.toString();
    }

    @Override
    public PostSpec path(String path) {
        state.path(path);
        return this;
    }

    @Override
    public PostSpec path(String path, String parameterId, String parameterValue) {
        state.path(path, parameterId, parameterValue);
        return this;
    }

    @Override
    public PostSpec path(String path, PathParameter... parameters) {
        state.path(path, parameters);
        return this;
    }

    @Override
    public PostSpec parameter(String name, String value) {
        state.parameter(name, value);
        return this;
    }

    @Override
    public PostSpec parameter(String name, String... values) {
        state.parameter(name, values);
        return this;
    }

    @Override
    public PostSpec header(String name, String value) {
        state.header(name, value);
        return this;
    }

    @Override
    public PostSpec header(String name, String... values) {
        state.header(name, values);
        return this;
    }

    @Override
    public PostSpec cookie(HttpCookie cookie) {
        state.cookie(cookie);
        return this;
    }

    @Override
    public PostSpec security(SecurityProvider provider) {
        state.security(provider);
        return this;
    }

    @Override
    public PostSpec compress() {
        return compress(GZIP_COMPRESSOR);
    }

    @Override
    public PostSpec compress(ContentCompressor compressor) {
        this.compressor = Values.notNull(COMPRESSOR, compressor);
        return this;
    }

    @Override
    public PostSpec body(Object body) {
        this.jsonBody = Values.notNull(BODY, body);
        this.formData = null;
        this.formUrlEncoded = null;
        return this;
    }

    @Override
    public PostSpec body(FormData formData) {
        this.formData = Values.notNull(BODY, formData);
        this.jsonBody = null;
        this.formUrlEncoded = null;
        return this;
    }

    @Override
    public PostSpec body(FormUrlEncoded formUrlEncoded) {
        this.formUrlEncoded = Values.notNull(BODY, formUrlEncoded);
        this.jsonBody = null;
        this.formData = null;
        return this;
    }

    @Override
    public <T> HttpResponse<T> send(Class<T> responseType) {
        Values.notNull(RESPONSE_TYPE, responseType);

        URI uri = state.resolveUri(engine.configuration().uri());
        OutboundRequest outbound = buildRequest(uri, true);

        return engine.send(
                outbound,
                JsonBodyHandler.of(engine.configuration().serializer(), responseType, POST, uri,
                        engine.configuration().decompressors())
        );
    }

    @Override
    public <T> HttpResponse<T> send(GenericType<T> responseType) {
        Values.notNull(RESPONSE_TYPE, responseType);

        URI uri = state.resolveUri(engine.configuration().uri());
        OutboundRequest outbound = buildRequest(uri, true);

        return engine.send(
                outbound,
                JsonBodyHandler.of(engine.configuration().serializer(), responseType, POST, uri,
                        engine.configuration().decompressors())
        );
    }

    @Override
    public HttpResponse<Void> send() {
        URI uri = state.resolveUri(engine.configuration().uri());
        OutboundRequest outbound = buildRequest(uri, false);

        return engine.send(outbound, RequestState.voidBodyHandler(POST, uri));
    }

    @Override
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(Class<T> responseType) {
        Values.notNull(RESPONSE_TYPE, responseType);

        URI uri = state.resolveUri(engine.configuration().uri());
        OutboundRequest outbound = buildRequest(uri, true);

        return engine.sendAsync(
                outbound,
                JsonBodyHandler.of(engine.configuration().serializer(), responseType, POST, uri,
                        engine.configuration().decompressors())
        );
    }

    @Override
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(GenericType<T> responseType) {
        Values.notNull(RESPONSE_TYPE, responseType);

        URI uri = state.resolveUri(engine.configuration().uri());
        OutboundRequest outbound = buildRequest(uri, true);

        return engine.sendAsync(
                outbound,
                JsonBodyHandler.of(engine.configuration().serializer(), responseType, POST, uri,
                        engine.configuration().decompressors())
        );
    }

    @Override
    public CompletableFuture<HttpResponse<Void>> sendAsync() {
        URI uri = state.resolveUri(engine.configuration().uri());
        OutboundRequest outbound = buildRequest(uri, false);

        return engine.sendAsync(outbound, RequestState.voidBodyHandler(POST, uri));
    }

    private OutboundRequest buildRequest(URI uri, boolean acceptJson) {
        if (null != formData) {
            return buildMultipartRequest(uri, acceptJson);
        }

        if (null != formUrlEncoded) {
            return buildFormUrlEncodedRequest(uri, acceptJson);
        }

        return buildJsonRequest(uri, acceptJson);
    }

    private OutboundRequest buildJsonRequest(URI uri, boolean acceptJson) {
        HttpRequest.BodyPublisher bodyPublisher;
        boolean compressed = null != compressor;
        byte[] bodySnap = null;

        if (null == jsonBody) {
            bodyPublisher = HttpRequest.BodyPublishers.noBody();
            compressed = false;
        } else {
            byte[] bodyBytes = serializeBody(jsonBody);
            bodySnap = bodyBytes;

            if (compressed) {
                try {
                    byte[] compressedBytes = compressor.compress(bodyBytes);

                    if (null == compressedBytes) {
                        throw new IllegalStateException(
                                "The 'ContentCompressor' value is invalid." +
                                        "  compress() must not return null."
                        );
                    }

                    bodyPublisher = HttpRequest.BodyPublishers.ofByteArray(compressedBytes);
                } catch (java.io.IOException ex) {
                    throw new java.io.UncheckedIOException(ex);
                }
            } else {
                bodyPublisher = HttpRequest.BodyPublishers.ofByteArray(bodyBytes);
            }
        }

        String acceptEncoding = acceptJson ? DefaultClientConfiguration.acceptEncoding(engine.configuration().decompressors()) : null;

        HttpRequest.Builder builder = state.prepareBuilder(
                uri,
                POST,
                bodyPublisher,
                acceptJson,
                acceptEncoding,
                engine.configuration().readTimeout()
        );

        if (null != jsonBody) {
            builder.header(Headers.CONTENT_TYPE, "application/json");
        }

        if (compressed) {
            builder.header(Headers.CONTENT_ENCODING, compressor.encoding());
        }

        return OutboundRequest.of(builder.build(), bodySnap);
    }

    private OutboundRequest buildMultipartRequest(URI uri, boolean acceptJson) {
        if (null != compressor) {
            throw new IllegalStateException(COMPRESS_WITH_FORM_ERROR);
        }

        String boundary = MultipartBodyBuilder.generateBoundary();
        HttpRequest.BodyPublisher bodyPublisher = MultipartBodyBuilder.build(
                formData.parts(),
                boundary,
                engine.configuration().serializer()
        );

        String acceptEncoding = acceptJson ? DefaultClientConfiguration.acceptEncoding(engine.configuration().decompressors()) : null;

        HttpRequest.Builder builder = state.prepareBuilder(
                uri,
                POST,
                bodyPublisher,
                acceptJson,
                acceptEncoding,
                engine.configuration().readTimeout()
        );

        builder.header(Headers.CONTENT_TYPE, MultipartBodyBuilder.contentType(boundary));

        return OutboundRequest.of(builder.build(), OutboundRequest.MULTIPART_SNAPSHOT);
    }

    private OutboundRequest buildFormUrlEncodedRequest(URI uri, boolean acceptJson) {
        if (null != compressor) {
            throw new IllegalStateException(COMPRESS_WITH_FORM_ERROR);
        }

        byte[] encodedBytes = encodeFormFields(formUrlEncoded.fields()).getBytes(StandardCharsets.UTF_8);
        HttpRequest.BodyPublisher bodyPublisher = HttpRequest.BodyPublishers.ofByteArray(encodedBytes);

        String acceptEncoding = acceptJson ? DefaultClientConfiguration.acceptEncoding(engine.configuration().decompressors()) : null;

        HttpRequest.Builder builder = state.prepareBuilder(
                uri,
                POST,
                bodyPublisher,
                acceptJson,
                acceptEncoding,
                engine.configuration().readTimeout()
        );

        builder.header(Headers.CONTENT_TYPE, "application/x-www-form-urlencoded");

        return OutboundRequest.of(builder.build(), encodedBytes);
    }

    private byte[] serializeBody(Object body) {
        if (body instanceof String s) {
            return s.getBytes(StandardCharsets.UTF_8);
        }

        return engine.configuration().serializer().serialize(body);
    }
}

