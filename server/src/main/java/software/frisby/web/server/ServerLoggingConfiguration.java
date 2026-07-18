package software.frisby.web.server;

import java.util.Set;

/**
 * Controls how the server logs details about failed requests (4xx and 5xx responses,
 * and unhandled exceptions).
 * <p>
 * Obtain a builder via the static {@link #builder()} factory method and pass the
 * resulting configuration to {@link ServerConfigurationBuilder#logging(ServerLoggingConfiguration)}:
 *
 * <pre>{@code
 * ServerLoggingConfiguration logging = ServerLoggingConfiguration.builder()
 *         .maxBodySize(4096)
 *         .redactHeaders("X-Amzn-Oidc-Data")
 *         .redactFields("password", "token")
 *         .build();
 *
 * ServerConfiguration config = ServerConfiguration.builder()
 *         .port(8080)
 *         .serializer(new JacksonSerializer())
 *         .logging(logging)
 *         .build();
 * }</pre>
 *
 * <p>When {@link ServerConfigurationBuilder#logging(ServerLoggingConfiguration)} is not called,
 * a default configuration is used: 8 KB body size limit, no custom redacted fields, and
 * only the three hard-coded headers masked.
 *
 * @see ServerLoggingConfigurationBuilder
 * @see ServerConfigurationBuilder#logging(ServerLoggingConfiguration)
 */
public interface ServerLoggingConfiguration {
    /**
     * Returns a new {@link ServerLoggingConfigurationBuilder} instance.
     *
     * @return A new builder; never {@code null}.
     */
    static ServerLoggingConfigurationBuilder builder() {
        return new DefaultServerLoggingConfigurationBuilder();
    }

    /**
     * Returns the set of HTTP header names whose values are replaced with {@code ***}
     * in failure log entries.
     * <p>
     * The set always includes the hard-coded defaults — {@code authorization},
     * {@code cookie}, and {@code set-cookie} — regardless of what was passed to
     * {@link ServerLoggingConfigurationBuilder#redactHeaders(String...)}.  All names are
     * stored in lower-case for case-insensitive matching.
     *
     * @return An unmodifiable set; never {@code null}.
     */
    Set<String> redactedHeaders();

    /**
     * Returns the set of JSON body field names whose string values are replaced with
     * {@code [redacted]} in failure log entries.
     * <p>
     * Matching is case-sensitive and exact.  Only string-typed field values are
     * affected — numeric, boolean, and nested-object values are left unchanged.
     * Redaction applies to both request and response bodies.
     *
     * @return An unmodifiable set; never {@code null}.
     */
    Set<String> redactedBodyFields();

    /**
     * Returns the maximum number of bytes that will be buffered from an incoming request
     * body and included in failure log entries.
     * <p>
     * Bodies larger than this limit are truncated and marked with a note in the log.
     * A value of {@code 0} disables request body logging entirely.
     *
     * @return The maximum body size in bytes; always non-negative.
     */
    int maxBodySize();
}

