package software.frisby.web.server;

import software.frisby.core.validation.StringSequences;
import software.frisby.core.validation.Values;

import java.util.List;

final class DefaultCorsConfiguration implements CorsConfiguration {
    private final List<String> allowedOrigins;
    private final List<String> allowedMethods;
    private final AllowedHeaders allowedHeaders;
    private final boolean allowCredentials;

    DefaultCorsConfiguration(List<String> allowedOrigins,
                             List<String> allowedMethods,
                             AllowedHeaders allowedHeaders,
                             boolean allowCredentials) {
        this.allowedOrigins = List.copyOf(StringSequences.notBlank("allowedOrigins", allowedOrigins));
        this.allowedMethods = List.copyOf(StringSequences.notBlank("allowedMethods", allowedMethods));
        this.allowedHeaders = Values.notNull("allowedHeaders", allowedHeaders);
        this.allowCredentials = allowCredentials;
    }

    @Override
    public List<String> allowedOrigins() {
        return allowedOrigins;
    }

    @Override
    public List<String> allowedMethods() {
        return allowedMethods;
    }

    @Override
    public AllowedHeaders allowedHeaders() {
        return allowedHeaders;
    }

    @Override
    public boolean allowCredentials() {
        return allowCredentials;
    }
}

