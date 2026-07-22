package software.frisby.web.client;

import software.frisby.web.client.security.SecurityProvider;

import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

/**
 * Package-private implementation of {@link HeadSpec}.
 * <p>
 * Navigation state is held by a {@link RequestState} instance; this class is
 * responsible only for assembling and dispatching HEAD-specific requests via
 * the shared {@link HttpEngine}.
 */
final class HeadRequest implements HeadSpec {
    private static final String HEAD = "HEAD";

    private final HttpEngine engine;
    private final RequestState state;

    HeadRequest(HttpEngine engine, SecurityProvider defaultSecurity) {
        this.engine = engine;
        this.state = new RequestState(defaultSecurity);
    }

    @Override
    public HeadSpec path(String path) {
        state.path(path);
        return this;
    }

    @Override
    public HeadSpec path(String path, String parameterId, String parameterValue) {
        state.path(path, parameterId, parameterValue);
        return this;
    }

    @Override
    public HeadSpec path(String path, PathParameter... parameters) {
        state.path(path, parameters);
        return this;
    }

    @Override
    public HeadSpec parameter(String name, String value) {
        state.parameter(name, value);
        return this;
    }

    @Override
    public HeadSpec parameter(String name, String... values) {
        state.parameter(name, values);
        return this;
    }

    @Override
    public HeadSpec header(String name, String value) {
        state.header(name, value);
        return this;
    }

    @Override
    public HeadSpec header(String name, String... values) {
        state.header(name, values);
        return this;
    }

    @Override
    public HeadSpec cookie(HttpCookie cookie) {
        state.cookie(cookie);
        return this;
    }

    @Override
    public HeadSpec security(SecurityProvider provider) {
        state.security(provider);
        return this;
    }

    @Override
    public HttpResponse<Void> send() {
        URI uri = state.resolveUri(engine.configuration().uri());

        return engine.send(
                () -> OutboundRequest.of(state.buildRequest(
                        uri, HEAD, HttpRequest.BodyPublishers.noBody(),
                        false, null, engine.configuration().readTimeout()
                )),
                RequestState.voidBodyHandler(HEAD, uri)
        );
    }

    @Override
    public CompletableFuture<HttpResponse<Void>> sendAsync() {
        URI uri = state.resolveUri(engine.configuration().uri());

        return engine.sendAsync(
                () -> OutboundRequest.of(state.buildRequest(
                        uri, HEAD, HttpRequest.BodyPublishers.noBody(),
                        false, null, engine.configuration().readTimeout()
                )),
                RequestState.voidBodyHandler(HEAD, uri)
        );
    }
}
