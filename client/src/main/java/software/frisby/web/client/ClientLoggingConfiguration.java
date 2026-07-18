package software.frisby.web.client;

import java.util.Set;

/**
 * Controls how the client logs HTTP request and response details.
 * <p>
 * Obtain a builder via the static {@link #builder()} factory method and pass the
 * resulting configuration to {@link ClientConfigurationBuilder#logging(ClientLoggingConfiguration)}:
 *
 * <pre>{@code
 * ClientLoggingConfiguration logging = ClientLoggingConfiguration.builder()
 *         .maxBodySize(4096)
 *         .redactHeaders("X-Api-Key", "X-Amzn-Oidc-Data")
 *         .redactFields("password", "token")
 *         .build();
 *
 * ClientConfiguration config = ClientConfiguration.builder()
 *         .uri(URI.create("https://api.example.com"))
 *         .serializer(mySerializer)
 *         .logging(logging)
 *         .build();
 * }</pre>
 *
 * <p>When {@link ClientConfigurationBuilder#logging(ClientLoggingConfiguration)} is not called,
 * a default configuration is used: 8 KB body size limit, no custom redacted fields, and
 * only the three hard-coded headers masked.
 *
 * @see ClientLoggingConfigurationBuilder
 * @see ClientConfigurationBuilder#logging(ClientLoggingConfiguration)
 */
public interface ClientLoggingConfiguration {
    /**
     * Returns a new {@link ClientLoggingConfigurationBuilder} instance.
     *
     * @return A new builder; never {@code null}.
     */
    static ClientLoggingConfigurationBuilder builder() {
        return new DefaultClientLoggingConfigurationBuilder();
    }

    /**
     * Returns the set of HTTP header names whose values are replaced with {@code [redacted]}
     * in log entries.
     * <p>
     * The set always includes the hard-coded defaults — {@code authorization},
     * {@code cookie}, and {@code set-cookie} — regardless of what was passed to
     * {@link ClientLoggingConfigurationBuilder#redactHeaders(String...)}.  All names are
     * stored in lower-case for case-insensitive matching.
     *
     * @return An unmodifiable set; never {@code null}.
     */
    Set<String> redactedHeaders();

    /**
     * Returns the set of JSON body field names whose string values are replaced with
     * {@code [redacted]} in log entries.
     * <p>
     * Matching is case-sensitive and exact.  Only string-typed field values are
     * affected — numeric, boolean, and nested-object values are left unchanged.
     * Redaction applies to both request and response bodies.
     *
     * @return An unmodifiable set; never {@code null}.
     */
    Set<String> redactedBodyFields();

    /**
     * Returns the maximum number of characters of a body that will be included in log
     * entries at {@code WARNING} and {@code TRACE} levels.
     * <p>
     * Bodies larger than this limit are truncated and marked with {@code …(truncated)}.
     * A value of {@code 0} disables body logging entirely.
     *
     * @return The maximum body size in characters; always non-negative.
     */
    int maxBodySize();
}

