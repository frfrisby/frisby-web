package software.frisby.web.client;

import software.frisby.web.client.security.SecurityProvider;

import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

/**
 * Package-private implementation of {@link DeleteSpec}.
 * <p>
 * Navigation state is held by a {@link RequestState} instance; this class is
 * responsible only for assembling and dispatching DELETE-specific requests via
 * the shared {@link HttpEngine}.
 */
final class DeleteRequest implements DeleteSpec {
    private static final String DELETE = "DELETE";

    private final HttpEngine engine;
    private final RequestState state;

    DeleteRequest(HttpEngine engine, SecurityProvider defaultSecurity) {
        this.engine = engine;
        this.state = new RequestState(defaultSecurity);
    }

    @Override
    public DeleteSpec path(String path) {
        state.path(path);
        return this;
    }

    @Override
    public DeleteSpec path(String path, String parameterId, String parameterValue) {
        state.path(path, parameterId, parameterValue);
        return this;
    }

    @Override
    public DeleteSpec path(String path, PathParameter... parameters) {
        state.path(path, parameters);
        return this;
    }

    @Override
    public DeleteSpec parameter(String name, String value) {
        state.parameter(name, value);
        return this;
    }

    @Override
    public DeleteSpec parameter(String name, String... values) {
        state.parameter(name, values);
        return this;
    }

    @Override
    public DeleteSpec header(String name, String value) {
        state.header(name, value);
        return this;
    }

    @Override
    public DeleteSpec header(String name, String... values) {
        state.header(name, values);
        return this;
    }

    @Override
    public DeleteSpec cookie(HttpCookie cookie) {
        state.cookie(cookie);
        return this;
    }

    @Override
    public DeleteSpec security(SecurityProvider provider) {
        state.security(provider);
        return this;
    }

    @Override
    public HttpResponse<Void> send() {
        URI uri = state.resolveUri(engine.configuration().uri());

        return engine.send(
                () -> OutboundRequest.of(state.buildRequest(
                        uri, DELETE, HttpRequest.BodyPublishers.noBody(),
                        false, null, engine.configuration().readTimeout()
                )),
                RequestState.voidBodyHandler(DELETE, uri)
        );
    }

    @Override
    public CompletableFuture<HttpResponse<Void>> sendAsync() {
        URI uri = state.resolveUri(engine.configuration().uri());

        return engine.sendAsync(
                () -> OutboundRequest.of(state.buildRequest(
                        uri, DELETE, HttpRequest.BodyPublishers.noBody(),
                        false, null, engine.configuration().readTimeout()
                )),
                RequestState.voidBodyHandler(DELETE, uri)
        );
    }
}
