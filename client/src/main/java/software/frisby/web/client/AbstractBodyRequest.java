package software.frisby.web.client;

import software.frisby.core.validation.Values;
import software.frisby.web.client.security.SecurityProvider;
import software.frisby.web.serial.GenericType;

import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

import static software.frisby.web.client.RequestConstants.*;

/**
 * Abstract base for body-bearing request implementations ({@link PostRequest},
 * {@link PutRequest}, and {@link PatchRequest}).
 * <p>
 * Holds all state and behavior common to HTTP methods that carry a request body:
 * path/header/parameter/cookie/security navigation (delegated to {@link RequestState}),
 * body assignment (JSON, form-url-encoded), compression, and the full
 * {@code send}/{@code sendAsync} dispatch pipeline.
 * <p>
 * Concrete subclasses supply the HTTP method string via {@link #method()} and a
 * typed self-reference via {@link #self()} to support the fluent builder chain.
 * Subclasses that additionally support multipart bodies (POST, PUT) override
 * {@link #buildRequest} to handle the multipart path before delegating to this
 * class's form-url-encoded / JSON dispatch.
 *
 * @param <S> The spec interface type returned by the fluent builder methods.
 */
abstract class AbstractBodyRequest<S> {
    private static final ContentCompressor GZIP_COMPRESSOR =
            ContentCompressor.of("gzip", GZip::compress);

    protected final HttpEngine engine;
    protected final RequestState state;

    private Object jsonBody;
    private FormUrlEncoded formUrlEncoded;
    private ContentCompressor compressor;

    AbstractBodyRequest(HttpEngine engine, SecurityProvider defaultSecurity) {
        this.engine = engine;
        this.state = new RequestState(defaultSecurity);
        this.jsonBody = null;
        this.formUrlEncoded = null;
        this.compressor = null;
    }

    /**
     * Returns the HTTP method string for this request (e.g. {@code "POST"}).
     */
    protected abstract String method();

    /**
     * Returns {@code this} typed as the concrete spec interface {@code S}.
     * <p>
     * Used by the fluent builder methods to return the correct type without
     * an unchecked cast at each call site.
     */
    protected abstract S self();

    /**
     * Clears the JSON body and form-url-encoded body fields.
     * <p>
     * Called by subclass {@code body(FormData)} overrides to ensure that
     * base-class body state is reset when a multipart body is set.
     */
    protected void clearBodyFields() {
        this.jsonBody = null;
        this.formUrlEncoded = null;
    }

    /**
     * Throws {@link IllegalStateException} if a {@link ContentCompressor} has been set.
     * <p>
     * Called by subclass multipart and form-url-encoded build paths — compression is
     * not supported for those body types.
     */
    protected void assertNotCompressed() {
        if (null != compressor) {
            throw new IllegalStateException(COMPRESS_WITH_FORM_ERROR);
        }
    }

    public S path(String path) {
        state.path(path);
        return self();
    }

    public S path(String path, String parameterId, String parameterValue) {
        state.path(path, parameterId, parameterValue);
        return self();
    }

    public S path(String path, PathParameter... parameters) {
        state.path(path, parameters);
        return self();
    }

    public S parameter(String name, String value) {
        state.parameter(name, value);
        return self();
    }

    public S parameter(String name, String... values) {
        state.parameter(name, values);
        return self();
    }

    public S header(String name, String value) {
        state.header(name, value);
        return self();
    }

    public S header(String name, String... values) {
        state.header(name, values);
        return self();
    }

    public S cookie(HttpCookie cookie) {
        state.cookie(cookie);
        return self();
    }

    public S security(SecurityProvider provider) {
        state.security(provider);
        return self();
    }

    public S compress() {
        return compress(GZIP_COMPRESSOR);
    }

    public S compress(ContentCompressor compressor) {
        this.compressor = Values.notNull(COMPRESSOR_ARGUMENT_NAME, compressor);
        return self();
    }

    public S body(Object body) {
        this.jsonBody = Values.notNull(BODY_ARGUMENT_NAME, body);
        this.formUrlEncoded = null;
        return self();
    }

    public S body(FormUrlEncoded formUrlEncoded) {
        this.formUrlEncoded = Values.notNull(BODY_ARGUMENT_NAME, formUrlEncoded);
        this.jsonBody = null;
        return self();
    }

    public <T> HttpResponse<T> send(Class<T> responseType) {
        Values.notNull(RESPONSE_TYPE_ARGUMENT_NAME, responseType);

        URI uri = state.resolveUri(engine.configuration().uri());

        return engine.send(
                () -> buildRequest(uri, true),
                JsonBodyHandler.of(
                        engine.configuration().serializer(),
                        responseType,
                        method(),
                        uri,
                        engine.configuration().decompressors()
                )
        );
    }

    public <T> HttpResponse<T> send(GenericType<T> responseType) {
        Values.notNull(RESPONSE_TYPE_ARGUMENT_NAME, responseType);

        URI uri = state.resolveUri(engine.configuration().uri());

        return engine.send(
                () -> buildRequest(uri, true),
                JsonBodyHandler.of(
                        engine.configuration().serializer(),
                        responseType,
                        method(),
                        uri,
                        engine.configuration().decompressors()
                )
        );
    }

    public HttpResponse<Void> send() {
        URI uri = state.resolveUri(engine.configuration().uri());

        return engine.send(
                () -> buildRequest(uri, false),
                RequestState.voidBodyHandler(method(), uri)
        );
    }

    public <T> CompletableFuture<HttpResponse<T>> sendAsync(Class<T> responseType) {
        Values.notNull(RESPONSE_TYPE_ARGUMENT_NAME, responseType);

        URI uri = state.resolveUri(engine.configuration().uri());

        return engine.sendAsync(
                () -> buildRequest(uri, true),
                JsonBodyHandler.of(
                        engine.configuration().serializer(),
                        responseType,
                        method(),
                        uri,
                        engine.configuration().decompressors()
                )
        );
    }

    public <T> CompletableFuture<HttpResponse<T>> sendAsync(GenericType<T> responseType) {
        Values.notNull(RESPONSE_TYPE_ARGUMENT_NAME, responseType);

        URI uri = state.resolveUri(engine.configuration().uri());

        return engine.sendAsync(
                () -> buildRequest(uri, true),
                JsonBodyHandler.of(
                        engine.configuration().serializer(),
                        responseType,
                        method(),
                        uri,
                        engine.configuration().decompressors()
                )
        );
    }

    public CompletableFuture<HttpResponse<Void>> sendAsync() {
        URI uri = state.resolveUri(engine.configuration().uri());

        return engine.sendAsync(
                () -> buildRequest(uri, false),
                RequestState.voidBodyHandler(method(), uri)
        );
    }

    OutboundRequest buildRequest(URI uri, boolean acceptJson) {
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
            byte[] bodyBytes = RequestBodyEncoder.serializeBody(
                    jsonBody,
                    engine.configuration().serializer()
            );
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

        if (null != jsonBody) {
            builder.header(Headers.CONTENT_TYPE, "application/json");
        }

        if (compressed) {
            builder.header(Headers.CONTENT_ENCODING, compressor.encoding());
        }

        return OutboundRequest.of(builder.build(), bodySnap);
    }

    private OutboundRequest buildFormUrlEncodedRequest(URI uri, boolean acceptJson) {
        assertNotCompressed();

        byte[] encodedBytes = RequestBodyEncoder
                .encodeFormFields(formUrlEncoded.fields())
                .getBytes(StandardCharsets.UTF_8);
        HttpRequest.BodyPublisher bodyPublisher = HttpRequest.BodyPublishers.ofByteArray(encodedBytes);

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

        builder.header(Headers.CONTENT_TYPE, "application/x-www-form-urlencoded");

        return OutboundRequest.of(builder.build(), encodedBytes);
    }
}

