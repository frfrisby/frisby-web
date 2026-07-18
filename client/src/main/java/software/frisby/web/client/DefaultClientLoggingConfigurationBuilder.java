package software.frisby.web.client;

import software.frisby.core.validation.Numbers;
import software.frisby.core.validation.StringSequences;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class DefaultClientLoggingConfigurationBuilder implements ClientLoggingConfigurationBuilder {
    static final int DEFAULT_MAX_BODY_SIZE = 8192;

    private static final String REDACT_HEADERS = "headers";
    private static final String REDACT_FIELDS = "fields";
    private static final String MAX_BODY_SIZE = "maxBodySize";

    private final Set<String> additionalRedactedHeaders;
    private final Set<String> redactedBodyFields;
    private int maxBodySize;

    DefaultClientLoggingConfigurationBuilder() {
        this.additionalRedactedHeaders = new HashSet<>();
        this.redactedBodyFields = new HashSet<>();
        this.maxBodySize = DEFAULT_MAX_BODY_SIZE;
    }

    @Override
    public ClientLoggingConfigurationBuilder redactHeaders(String... headers) {
        for (String header : StringSequences.notBlank(REDACT_HEADERS, headers)) {
            additionalRedactedHeaders.add(header.toLowerCase(Locale.ROOT));
        }

        return this;
    }

    @Override
    public ClientLoggingConfigurationBuilder redactFields(String... fields) {
        redactedBodyFields.addAll(List.of(StringSequences.notBlank(REDACT_FIELDS, fields)));

        return this;
    }

    @Override
    public ClientLoggingConfigurationBuilder maxBodySize(int maxBodySize) {
        this.maxBodySize = Numbers.range(MAX_BODY_SIZE, maxBodySize, 0, DefaultClientLoggingConfiguration.MAX_BODY_SIZE_LIMIT);
        return this;
    }

    @Override
    public ClientLoggingConfiguration build() {
        return new DefaultClientLoggingConfiguration(
                additionalRedactedHeaders,
                redactedBodyFields,
                maxBodySize
        );
    }
}

