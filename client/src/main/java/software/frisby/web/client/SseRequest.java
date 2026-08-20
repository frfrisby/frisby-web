package software.frisby.web.client;

import software.frisby.web.client.security.SecurityProvider;

import java.io.InputStream;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Package-private implementation of {@link SseSpec}.
 * <p>
 * Navigation state is held by a {@link RequestState} instance; this class is
 * responsible only for assembling and dispatching the SSE stream request via the
 * shared {@link HttpEngine}.  Structurally identical to {@link GetRequest}, except the
 * terminal methods return the raw response stream rather than a deserialized body.
 */
final class SseRequest implements SseSpec {
    private static final String GET = "GET";
    private static final String TEXT_EVENT_STREAM = "text/event-stream";

    private final HttpEngine engine;
    private final RequestState state;

    SseRequest(HttpEngine engine, SecurityProvider defaultSecurity) {
        this.engine = engine;
        this.state = new RequestState(defaultSecurity);
    }

    @Override
    public SseSpec path(String path) {
        state.path(path);
        return this;
    }

    @Override
    public SseSpec path(String path, String parameterId, String parameterValue) {
        state.path(path, parameterId, parameterValue);
        return this;
    }

    @Override
    public SseSpec path(String path, PathParameter... parameters) {
        state.path(path, parameters);
        return this;
    }

    @Override
    public SseSpec parameter(String name, String value) {
        state.parameter(name, value);
        return this;
    }

    @Override
    public SseSpec parameter(String name, String... values) {
        state.parameter(name, values);
        return this;
    }

    @Override
    public SseSpec header(String name, String value) {
        state.header(name, value);
        return this;
    }

    @Override
    public SseSpec header(String name, String... values) {
        state.header(name, values);
        return this;
    }

    @Override
    public SseSpec cookie(HttpCookie cookie) {
        state.cookie(cookie);
        return this;
    }

    @Override
    public SseSpec security(SecurityProvider provider) {
        state.security(provider);
        return this;
    }

    @Override
    public HttpResponse<InputStream> stream() {
        URI uri = state.resolveUri(engine.configuration().uri());

        return engine.send(
                () -> OutboundRequest.of(buildStreamRequest(uri)),
                streamBodyHandler(uri)
        );
    }

    @Override
    public CompletableFuture<HttpResponse<InputStream>> streamAsync() {
        URI uri = state.resolveUri(engine.configuration().uri());

        return engine.sendAsync(
                () -> OutboundRequest.of(buildStreamRequest(uri)),
                streamBodyHandler(uri)
        );
    }

    private HttpRequest buildStreamRequest(URI uri) {
        HttpRequest.Builder builder = state.prepareBuilder(
                uri, GET, HttpRequest.BodyPublishers.noBody(),
                false, null, engine.configuration().readTimeout()
        );

        if (!state.hasHeader(Headers.ACCEPT)) {
            builder.header(Headers.ACCEPT, TEXT_EVENT_STREAM);
        }

        return builder.build();
    }

    private HttpResponse.BodyHandler<InputStream> streamBodyHandler(URI uri) {
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

