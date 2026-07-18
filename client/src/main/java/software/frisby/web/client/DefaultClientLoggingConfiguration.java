package software.frisby.web.client;

import software.frisby.core.validation.Numbers;
import software.frisby.core.validation.Sequences;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

final class DefaultClientLoggingConfiguration implements ClientLoggingConfiguration {
    static final int MAX_BODY_SIZE_LIMIT = 100 * 1024 * 1024;

    private static final Set<String> HARD_REDACTED_HEADERS =
            Set.of("authorization", "cookie", "set-cookie");

    private final Set<String> redactedHeaders;
    private final Set<String> redactedBodyFields;
    private final int maxBodySize;

    DefaultClientLoggingConfiguration(Set<String> additionalRedactedHeaders,
                                      Set<String> redactedBodyFields,
                                      int maxBodySize) {
        Set<String> combined = new HashSet<>(HARD_REDACTED_HEADERS);

        for (String header : Sequences.noNullElements("additionalRedactedHeaders", additionalRedactedHeaders)) {
            combined.add(header.toLowerCase(Locale.ROOT));
        }

        this.redactedHeaders = Set.copyOf(combined);
        this.redactedBodyFields = Set.copyOf(Sequences.noNullElements("redactedBodyFields", redactedBodyFields));
        this.maxBodySize = Numbers.range("maxBodySize", maxBodySize, 0, MAX_BODY_SIZE_LIMIT);
    }

    @Override
    public Set<String> redactedHeaders() {
        return redactedHeaders;
    }

    @Override
    public Set<String> redactedBodyFields() {
        return redactedBodyFields;
    }

    @Override
    public int maxBodySize() {
        return maxBodySize;
    }
}

