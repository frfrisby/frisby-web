package software.frisby.web.client;

import software.frisby.core.validation.Values;
import software.frisby.web.client.security.SecurityProvider;
import software.frisby.web.serial.GenericType;

import java.io.InputStream;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Package-private implementation of {@link GetSpec}.
 * <p>
 * Navigation state is held by a {@link RequestState} instance; this class is
 * responsible only for assembling and dispatching GET-specific requests via
 * the shared {@link HttpEngine}.
 */
final class GetRequest implements GetSpec {
    private static final String GET = "GET";
    private static final String RESPONSE_TYPE = "responseType";

    private final HttpEngine engine;
    private final RequestState state;

    GetRequest(HttpEngine engine, SecurityProvider defaultSecurity) {
        this.engine = engine;
        this.state = new RequestState(defaultSecurity);
    }

    @Override
    public GetSpec path(String path) {
        state.path(path);
        return this;
    }

    @Override
    public GetSpec path(String path, String parameterId, String parameterValue) {
        state.path(path, parameterId, parameterValue);
        return this;
    }

    @Override
    public GetSpec path(String path, PathParameter... parameters) {
        state.path(path, parameters);
        return this;
    }

    @Override
    public GetSpec parameter(String name, String value) {
        state.parameter(name, value);
        return this;
    }

    @Override
    public GetSpec parameter(String name, String... values) {
        state.parameter(name, values);
        return this;
    }

    @Override
    public GetSpec header(String name, String value) {
        state.header(name, value);
        return this;
    }

    @Override
    public GetSpec header(String name, String... values) {
        state.header(name, values);
        return this;
    }

    @Override
    public GetSpec cookie(HttpCookie cookie) {
        state.cookie(cookie);
        return this;
    }

    @Override
    public GetSpec security(SecurityProvider provider) {
        state.security(provider);
        return this;
    }

    @Override
    public <T> HttpResponse<T> send(Class<T> responseType) {
        Values.notNull(RESPONSE_TYPE, responseType);

        URI uri = state.resolveUri(engine.configuration().uri());
        OutboundRequest outbound = OutboundRequest.of(state.buildRequest(
                uri, GET, HttpRequest.BodyPublishers.noBody(),
                true, DefaultClientConfiguration.acceptEncoding(engine.configuration().decompressors()), engine.configuration().readTimeout()
        ));

        return engine.send(
                outbound,
                JsonBodyHandler.of(engine.configuration().serializer(), responseType, GET, uri,
                        engine.configuration().decompressors())
        );
    }

    @Override
    public <T> HttpResponse<T> send(GenericType<T> responseType) {
        Values.notNull(RESPONSE_TYPE, responseType);

        URI uri = state.resolveUri(engine.configuration().uri());
        OutboundRequest outbound = OutboundRequest.of(state.buildRequest(
                uri, GET, HttpRequest.BodyPublishers.noBody(),
                true, DefaultClientConfiguration.acceptEncoding(engine.configuration().decompressors()), engine.configuration().readTimeout()
        ));

        return engine.send(
                outbound,
                JsonBodyHandler.of(engine.configuration().serializer(), responseType, GET, uri,
                        engine.configuration().decompressors())
        );
    }

    @Override
    public HttpResponse<InputStream> download() {
        URI uri = state.resolveUri(engine.configuration().uri());
        OutboundRequest outbound = OutboundRequest.of(state.buildRequest(
                uri, GET, HttpRequest.BodyPublishers.noBody(),
                false, null, engine.configuration().readTimeout()
        ));

        return engine.send(outbound, downloadBodyHandler(uri));
    }

    @Override
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(Class<T> responseType) {
        Values.notNull(RESPONSE_TYPE, responseType);

        URI uri = state.resolveUri(engine.configuration().uri());
        OutboundRequest outbound = OutboundRequest.of(state.buildRequest(
                uri, GET, HttpRequest.BodyPublishers.noBody(),
                true, DefaultClientConfiguration.acceptEncoding(engine.configuration().decompressors()), engine.configuration().readTimeout()
        ));

        return engine.sendAsync(
                outbound,
                JsonBodyHandler.of(engine.configuration().serializer(), responseType, GET, uri,
                        engine.configuration().decompressors())
        );
    }

    @Override
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(GenericType<T> responseType) {
        Values.notNull(RESPONSE_TYPE, responseType);

        URI uri = state.resolveUri(engine.configuration().uri());
        OutboundRequest outbound = OutboundRequest.of(state.buildRequest(
                uri, GET, HttpRequest.BodyPublishers.noBody(),
                true, DefaultClientConfiguration.acceptEncoding(engine.configuration().decompressors()), engine.configuration().readTimeout()
        ));

        return engine.sendAsync(
                outbound,
                JsonBodyHandler.of(engine.configuration().serializer(), responseType, GET, uri,
                        engine.configuration().decompressors())
        );
    }

    @Override
    public CompletableFuture<HttpResponse<InputStream>> downloadAsync() {
        URI uri = state.resolveUri(engine.configuration().uri());
        OutboundRequest outbound = OutboundRequest.of(state.buildRequest(
                uri, GET, HttpRequest.BodyPublishers.noBody(),
                false, null, engine.configuration().readTimeout()
        ));

        return engine.sendAsync(outbound, downloadBodyHandler(uri));
    }

    private HttpResponse.BodyHandler<InputStream> downloadBodyHandler(URI uri) {
        return responseInfo -> {
            if (ExceptionFactory.isError(responseInfo.statusCode())) {
                return HttpResponse.BodySubscribers.mapping(
                        HttpResponse.BodySubscribers.ofString(StandardCharsets.UTF_8),
                        (Function<String, InputStream>) body -> {
                            String errorBody = body.isBlank() ? null : body;

                            throw ExceptionFactory.create(
                                    errorBody,
                                    GET,
                                    uri,
                                    responseInfo.statusCode(),
                                    responseInfo.headers()
                            );
                        }
                );
            }

            return HttpResponse.BodySubscribers.ofInputStream();
        };
    }
}
