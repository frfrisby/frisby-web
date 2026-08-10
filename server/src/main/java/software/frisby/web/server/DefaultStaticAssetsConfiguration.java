package software.frisby.web.server;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

final class DefaultStaticAssetsConfiguration implements StaticAssetsConfiguration {

    enum AssetSourceType {
        CLASSPATH, FILESYSTEM
    }

    private final AssetSourceType sourceType;
    private final String classpathResourcePath; // non-null when sourceType == CLASSPATH
    private final Path filesystemDirectory;     // non-null when sourceType == FILESYSTEM
    private final String urlPrefix;
    private final Duration cacheMaxAge;         // null = not configured
    private final Map<String, String> responseHeaders;
    private final boolean spaFallback;
    private final String notFoundPage;          // null = not configured
    private final StaticAssetsAuthFilter authFilter; // null = not configured

    DefaultStaticAssetsConfiguration(
            AssetSourceType sourceType,
            String classpathResourcePath,
            Path filesystemDirectory,
            String urlPrefix,
            Duration cacheMaxAge,
            Map<String, String> responseHeaders,
            boolean spaFallback,
            String notFoundPage,
            StaticAssetsAuthFilter authFilter) {
        this.sourceType = sourceType;
        this.classpathResourcePath = classpathResourcePath;
        this.filesystemDirectory = filesystemDirectory;
        this.urlPrefix = urlPrefix;
        this.cacheMaxAge = cacheMaxAge;
        this.responseHeaders = responseHeaders;
        this.spaFallback = spaFallback;
        this.notFoundPage = notFoundPage;
        this.authFilter = authFilter;
    }

    AssetSourceType sourceType() {
        return sourceType;
    }

    String classpathResourcePath() {
        return classpathResourcePath;
    }

    Path filesystemDirectory() {
        return filesystemDirectory;
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

