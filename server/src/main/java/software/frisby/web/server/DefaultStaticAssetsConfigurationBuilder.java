package software.frisby.web.server;

import software.frisby.core.validation.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

final class DefaultStaticAssetsConfigurationBuilder implements StaticAssetsConfigurationBuilder {
    private static final String RESOURCE_PATH_ARGUMENT_NAME = "resourcePath";
    private static final String DIRECTORY_ARGUMENT_NAME = "directory";
    private static final String URL_PREFIX_ARGUMENT_NAME = "urlPrefix";
    private static final String CACHE_MAX_AGE_ARGUMENT_NAME = "cacheMaxAge";
    private static final String RESPONSE_HEADERS_ARGUMENT_NAME = "responseHeaders";
    private static final String NOT_FOUND_PAGE_ARGUMENT_NAME = "notFoundPage";
    private static final String AUTH_FILTER_ARGUMENT_NAME = "authFilter";

    private static final String DEFAULT_URL_PREFIX = "/";
    private static final Pattern STARTS_WITH_SLASH = Pattern.compile("^/.*");

    private final DefaultStaticAssetsConfiguration.AssetSourceType sourceType;
    private final String classpathResourcePath;
    private final Path filesystemDirectory;
    private final Map<String, String> responseHeaders;
    private String urlPrefix;
    private Duration cacheMaxAge;
    private boolean spaFallback;
    private String notFoundPage;
    private StaticAssetsAuthFilter authFilter;

    DefaultStaticAssetsConfigurationBuilder(String resourcePath) {
        this.sourceType = DefaultStaticAssetsConfiguration.AssetSourceType.CLASSPATH;
        this.classpathResourcePath = Strings.notBlankWithMatches(RESOURCE_PATH_ARGUMENT_NAME, resourcePath, STARTS_WITH_SLASH);
        this.filesystemDirectory = null;
        this.urlPrefix = DEFAULT_URL_PREFIX;
        this.cacheMaxAge = null;
        this.responseHeaders = new HashMap<>();
        this.spaFallback = false;
        this.notFoundPage = null;
        this.authFilter = null;
    }

    DefaultStaticAssetsConfigurationBuilder(Path directory) {
        Values.notNull(DIRECTORY_ARGUMENT_NAME, directory);

        if (!Files.isDirectory(directory)) {
            throw new DisallowedValueException(
                    "The '" + DIRECTORY_ARGUMENT_NAME + "' value is invalid.  The path must refer to an existing directory."
            );
        }

        this.sourceType = DefaultStaticAssetsConfiguration.AssetSourceType.FILESYSTEM;
        this.classpathResourcePath = null;
        this.filesystemDirectory = directory;
        this.urlPrefix = DEFAULT_URL_PREFIX;
        this.cacheMaxAge = null;
        this.responseHeaders = new HashMap<>();
        this.spaFallback = false;
        this.notFoundPage = null;
        this.authFilter = null;
    }

    @Override
    public StaticAssetsConfigurationBuilder urlPrefix(String prefix) {
        this.urlPrefix = Strings.notBlankWithMatches(URL_PREFIX_ARGUMENT_NAME, prefix, STARTS_WITH_SLASH);
        return this;
    }

    @Override
    public StaticAssetsConfigurationBuilder cacheMaxAge(Duration maxAge) {
        this.cacheMaxAge = Durations.notNegative(CACHE_MAX_AGE_ARGUMENT_NAME, maxAge);
        return this;
    }

    @Override
    public StaticAssetsConfigurationBuilder responseHeaders(Map<String, String> headers) {
        Maps.notNull(RESPONSE_HEADERS_ARGUMENT_NAME, headers);

        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (null == entry.getKey()) {
                throw new NullMapKeyException(
                        "The '" + RESPONSE_HEADERS_ARGUMENT_NAME + "' value is invalid.  The map must not contain null keys."
                );
            }

            if (null == entry.getValue()) {
                throw new NullMapValueException(
                        "The '" + RESPONSE_HEADERS_ARGUMENT_NAME + "' value is invalid.  The map must not contain null values."
                );
            }
        }

        this.responseHeaders.putAll(headers);
        return this;
    }

    @Override
    public StaticAssetsConfigurationBuilder spaFallback(boolean enabled) {
        this.spaFallback = enabled;
        return this;
    }

    @Override
    public StaticAssetsConfigurationBuilder notFoundPage(String path) {
        this.notFoundPage = Strings.notBlank(NOT_FOUND_PAGE_ARGUMENT_NAME, path);
        return this;
    }

    @Override
    public StaticAssetsConfigurationBuilder authFilter(StaticAssetsAuthFilter filter) {
        this.authFilter = Values.notNull(AUTH_FILTER_ARGUMENT_NAME, filter);
        return this;
    }

    @Override
    public StaticAssetsConfiguration build() {
        return new DefaultStaticAssetsConfiguration(
                sourceType,
                classpathResourcePath,
                filesystemDirectory,
                urlPrefix,
                cacheMaxAge,
                Map.copyOf(responseHeaders),
                spaFallback,
                notFoundPage,
                authFilter
        );
    }
}

