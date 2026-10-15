package software.frisby.web.client;

import software.frisby.web.client.security.SecurityProvider;

import java.io.InputStream;
import java.net.HttpCookie;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Package-private implementation of {@link SseSpec}.
 * <p>
 * A thin wrapper around a {@link StreamRequestSupport} constructed with {@code
 * defaultAcceptHeaderValue = "text/event-stream"} — every navigation method and both
 * terminals are one-line delegations, identical in shape to {@link DownloadRequest}.
 */
final class SseRequest implements SseSpec {
    private static final String TEXT_EVENT_STREAM = "text/event-stream";

    private final StreamRequestSupport support;

    SseRequest(HttpEngine engine, SecurityProvider defaultSecurity) {
        this.support = new StreamRequestSupport(engine, defaultSecurity, TEXT_EVENT_STREAM);
    }

    @Override
    public SseSpec path(String path) {
        support.path(path);
        return this;
    }

    @Override
    public SseSpec path(String path, String parameterId, String parameterValue) {
        support.path(path, parameterId, parameterValue);
        return this;
    }

    @Override
    public SseSpec path(String path, PathParameter... parameters) {
        support.path(path, parameters);
        return this;
    }

    @Override
    public SseSpec parameter(String name, String value) {
        support.parameter(name, value);
        return this;
    }

    @Override
    public SseSpec parameter(String name, String... values) {
        support.parameter(name, values);
        return this;
    }

    @Override
    public SseSpec header(String name, String value) {
        support.header(name, value);
        return this;
    }

    @Override
    public SseSpec header(String name, String... values) {
        support.header(name, values);
        return this;
    }

    @Override
    public SseSpec cookie(HttpCookie cookie) {
        support.cookie(cookie);
        return this;
    }

    @Override
    public SseSpec security(SecurityProvider provider) {
        support.security(provider);
        return this;
    }

    @Override
    public SseSpec idleTimeout(Duration timeout) {
        support.idleTimeout(timeout);
        return this;
    }

    @Override
    public HttpResponse<InputStream> stream() {
        return support.execute();
    }

    @Override
    public CompletableFuture<HttpResponse<InputStream>> streamAsync() {
        return support.executeAsync();
    }
}
