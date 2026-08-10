package software.frisby.web.server;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

/**
 * Default implementation of {@link StaticAssetsConfigurationBuilder}.
 *
 * <p>Obtain instances via {@link StaticAssetsConfiguration#classpath(String)} or
 * {@link StaticAssetsConfiguration#filesystem(Path)}.
 *
 * <p><strong>Implementation note:</strong> This class is a stub created in Chunk 1
 * to allow the module to compile.  Full validation and field storage are implemented
 * in Chunk 2.
 */
final class DefaultStaticAssetsConfigurationBuilder implements StaticAssetsConfigurationBuilder {

    DefaultStaticAssetsConfigurationBuilder(String resourcePath) {
        throw new UnsupportedOperationException("Not yet implemented — see Chunk 2");
    }

    DefaultStaticAssetsConfigurationBuilder(Path directory) {
        throw new UnsupportedOperationException("Not yet implemented — see Chunk 2");
    }

    @Override
    public StaticAssetsConfigurationBuilder urlPrefix(String prefix) {
        throw new UnsupportedOperationException("Not yet implemented — see Chunk 2");
    }

    @Override
    public StaticAssetsConfigurationBuilder cacheMaxAge(Duration maxAge) {
        throw new UnsupportedOperationException("Not yet implemented — see Chunk 2");
    }

    @Override
    public StaticAssetsConfigurationBuilder responseHeaders(Map<String, String> headers) {
        throw new UnsupportedOperationException("Not yet implemented — see Chunk 2");
    }

    @Override
    public StaticAssetsConfigurationBuilder spaFallback(boolean enabled) {
        throw new UnsupportedOperationException("Not yet implemented — see Chunk 2");
    }

    @Override
    public StaticAssetsConfigurationBuilder notFoundPage(String path) {
        throw new UnsupportedOperationException("Not yet implemented — see Chunk 2");
    }

    @Override
    public StaticAssetsConfigurationBuilder authFilter(StaticAssetsAuthFilter filter) {
        throw new UnsupportedOperationException("Not yet implemented — see Chunk 2");
    }

    @Override
    public StaticAssetsConfiguration build() {
        throw new UnsupportedOperationException("Not yet implemented — see Chunk 2");
    }
}

