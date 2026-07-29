package software.frisby.web.server;

import software.frisby.core.validation.Sequences;
import software.frisby.core.validation.StringSequences;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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
    private final Set<HttpVerb> allowedMethods;
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
    public CorsConfigurationBuilder allowedMethods(HttpVerb... methods) {
        allowedMethods.addAll(Arrays.asList(Sequences.notEmpty(ALLOWED_METHODS, methods)));
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
        Sequences.notEmpty(ALLOWED_METHODS, allowedMethods);

        if (allowCredentials && allowedOrigins.contains(WILDCARD)) {
            throw new IllegalStateException(CREDENTIALS_WITH_WILDCARD_MESSAGE);
        }

        return new DefaultCorsConfiguration(
                new ArrayList<>(allowedOrigins),
                allowedMethods.stream().map(HttpVerb::name).collect(Collectors.toList()),
                null == allowedHeaders ? AllowedHeaders.echo() : AllowedHeaders.explicit(new ArrayList<>(allowedHeaders)),
                allowCredentials
        );
    }
}
