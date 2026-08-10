package software.frisby.web.server;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

final class DefaultStaticAssetsConfiguration implements StaticAssetsConfiguration {
    private final String classpathPath; // non-null for classpath sources
    private final Path filesystemPath;  // non-null for filesystem sources
    private final String urlPrefix;
    private final Duration cacheMaxAge;         // null = not configured
    private final Map<String, String> responseHeaders;
    private final boolean spaFallback;
    private final String notFoundPage;          // null = not configured
    private final StaticAssetsAuthFilter authFilter; // null = not configured

    DefaultStaticAssetsConfiguration(
            String classpathPath,
            Path filesystemPath,
            String urlPrefix,
            Duration cacheMaxAge,
            Map<String, String> responseHeaders,
            boolean spaFallback,
            String notFoundPage,
            StaticAssetsAuthFilter authFilter) {
        this.classpathPath = classpathPath;
        this.filesystemPath = filesystemPath;
        this.urlPrefix = urlPrefix;
        this.cacheMaxAge = cacheMaxAge;
        this.responseHeaders = responseHeaders;
        this.spaFallback = spaFallback;
        this.notFoundPage = notFoundPage;
        this.authFilter = authFilter;
    }

    @Override
    public Optional<String> classpathResourcePath() {
        return Optional.ofNullable(classpathPath);
    }

    @Override
    public Optional<Path> filesystemDirectory() {
        return Optional.ofNullable(filesystemPath);
    }

    @Override
    public String urlPrefix() {
        return urlPrefix;
    }

    @Override
    public Optional<Duration> cacheMaxAge() {
        return Optional.ofNullable(cacheMaxAge);
    }

    @Override
    public Map<String, String> responseHeaders() {
        return responseHeaders;
    }

    @Override
    public boolean spaFallback() {
        return spaFallback;
    }

    @Override
    public Optional<String> notFoundPage() {
        return Optional.ofNullable(notFoundPage);
    }

    @Override
    public Optional<StaticAssetsAuthFilter> authFilter() {
        return Optional.ofNullable(authFilter);
    }
}
