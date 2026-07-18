package software.frisby.web.server;

import software.frisby.core.validation.StringSequences;

import java.util.*;

final class DefaultCorsConfigurationBuilder implements CorsConfigurationBuilder {
    private static final String ALLOWED_ORIGINS = "allowedOrigins";
    private static final String ALLOWED_METHODS = "allowedMethods";
    private static final String ALLOWED_HEADERS = "allowedHeaders";

    private static final String WILDCARD = "*";

    private static final String CREDENTIALS_WITH_WILDCARD_MESSAGE =
            "allowCredentials() cannot be combined with a wildcard origin ('*') — "
                    + "browsers reject Access-Control-Allow-Credentials: true with Access-Control-Allow-Origin: *.  "
                    + "Specify an explicit allowedOrigins list instead of the wildcard.";

    private final Set<String> allowedOrigins;
    private final Set<String> allowedMethods;
    private Set<String> allowedHeaders;
    private boolean allowCredentials;

    DefaultCorsConfigurationBuilder() {
        this.allowedOrigins = new LinkedHashSet<>();
        this.allowedMethods = new LinkedHashSet<>();
        this.allowedHeaders = null;
        this.allowCredentials = false;
    }

    @Override
    public CorsConfigurationBuilder allowedOrigins(String... origins) {
        allowedOrigins.addAll(List.of(StringSequences.notBlank(ALLOWED_ORIGINS, origins)));
        return this;
    }

    @Override
    public CorsConfigurationBuilder allowedMethods(String... methods) {
        allowedMethods.addAll(List.of(StringSequences.notBlank(ALLOWED_METHODS, methods)));
        return this;
    }

    @Override
    public CorsConfigurationBuilder allowedHeaders(String... headers) {
        StringSequences.noBlankElements(ALLOWED_HEADERS, headers);

        if (null == allowedHeaders) {
            allowedHeaders = new LinkedHashSet<>();
        }

        allowedHeaders.addAll(List.of(headers));

        return this;
    }

    @Override
    public CorsConfigurationBuilder allowCredentials() {
        this.allowCredentials = true;
        return this;
    }

    @Override
    public CorsConfiguration build() {
        StringSequences.notBlank(ALLOWED_ORIGINS, allowedOrigins);
        StringSequences.notBlank(ALLOWED_METHODS, allowedMethods);

        if (allowCredentials && allowedOrigins.contains(WILDCARD)) {
            throw new IllegalStateException(CREDENTIALS_WITH_WILDCARD_MESSAGE);
        }

        return new DefaultCorsConfiguration(
                new ArrayList<>(allowedOrigins),
                new ArrayList<>(allowedMethods),
                null == allowedHeaders ? AllowedHeaders.echo() : AllowedHeaders.explicit(new ArrayList<>(allowedHeaders)),
                allowCredentials
        );
    }
}

