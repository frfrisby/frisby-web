package software.frisby.web.server;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

final class DefaultStaticAssetsConfiguration implements StaticAssetsConfiguration {
    private final String classpathPath; // non-null for classpath sources
    private final Path filesystemPath;  // non-null for filesystem sources
    private final String urlPrefix;
    private final Duration cacheMaxAge;              // null = not configured
    private final Map<String, String> responseHeaders;
    private final boolean spaFallback;
    private final Map<Integer, String> errorPages;   // empty = not configured
    private final StaticAssetsAuthFilter authFilter; // null = not configured

    DefaultStaticAssetsConfiguration(
            String classpathPath,
            Path filesystemPath,
            String urlPrefix,
            Duration cacheMaxAge,
            Map<String, String> responseHeaders,
            boolean spaFallback,
            Map<Integer, String> errorPages,
            StaticAssetsAuthFilter authFilter) {
        this.classpathPath = classpathPath;
        this.filesystemPath = filesystemPath;
        this.urlPrefix = urlPrefix;
        this.cacheMaxAge = cacheMaxAge;
        this.responseHeaders = responseHeaders;
        this.spaFallback = spaFallback;
        this.errorPages = errorPages;
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
    public Map<Integer, String> errorPages() {
        return errorPages;
    }

    @Override
    public Optional<StaticAssetsAuthFilter> authFilter() {
        return Optional.ofNullable(authFilter);
    }
}
