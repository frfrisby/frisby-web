package software.frisby.web.client;

import software.frisby.web.client.security.SecurityProvider;

import java.io.InputStream;
import java.net.HttpCookie;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Package-private implementation of {@link DownloadSpec}.
 * <p>
 * A thin wrapper around a {@link StreamRequestSupport} constructed with {@code
 * defaultAcceptHeaderValue = null} (no default {@code Accept} header for a raw
 * download, unchanged from the original {@code GetSpec#download()}'s behavior) — every
 * navigation method and both terminals are one-line delegations.
 */
final class DownloadRequest implements DownloadSpec {
    private final StreamRequestSupport support;

    DownloadRequest(HttpEngine engine, SecurityProvider defaultSecurity) {
        this.support = new StreamRequestSupport(engine, defaultSecurity, null);
    }

    @Override
    public DownloadSpec path(String path) {
        support.path(path);
        return this;
    }

    @Override
    public DownloadSpec path(String path, String parameterId, String parameterValue) {
        support.path(path, parameterId, parameterValue);
        return this;
    }

    @Override
    public DownloadSpec path(String path, PathParameter... parameters) {
        support.path(path, parameters);
        return this;
    }

    @Override
    public DownloadSpec parameter(String name, String value) {
        support.parameter(name, value);
        return this;
    }

    @Override
    public DownloadSpec parameter(String name, String... values) {
        support.parameter(name, values);
        return this;
    }

    @Override
    public DownloadSpec header(String name, String value) {
        support.header(name, value);
        return this;
    }

    @Override
    public DownloadSpec header(String name, String... values) {
        support.header(name, values);
        return this;
    }

    @Override
    public DownloadSpec cookie(HttpCookie cookie) {
        support.cookie(cookie);
        return this;
    }

    @Override
    public DownloadSpec security(SecurityProvider provider) {
        support.security(provider);
        return this;
    }

    @Override
    public DownloadSpec idleTimeout(Duration timeout) {
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
