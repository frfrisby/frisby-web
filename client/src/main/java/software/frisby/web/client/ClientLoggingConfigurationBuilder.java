package software.frisby.web.client;

/**
 * A builder for creating a {@link ClientLoggingConfiguration} instance.
 * <p>
 * Obtain a builder via {@link ClientLoggingConfiguration#builder()}.
 *
 * <pre>{@code
 * ClientLoggingConfiguration logging = ClientLoggingConfiguration.builder()
 *         .maxBodySize(4096)
 *         .redactHeaders("X-Api-Key", "X-Amzn-Oidc-Data")
 *         .redactFields("password", "token")
 *         .build();
 * }</pre>
 *
 * @see ClientLoggingConfiguration
 */
public interface ClientLoggingConfigurationBuilder {
    /**
     * Adds HTTP header names to the masking set used in log entries.
     * <p>
     * The headers {@code Authorization}, {@code Cookie}, and {@code Set-Cookie} are
     * always masked regardless of this setting (matched case-insensitively).
     * Use this method to add headers that must also be masked — for example:
     *
     * <pre>{@code
     * .redactHeaders("X-Api-Key", "X-Amzn-Oidc-Data")
     * }</pre>
     *
     * <p>Optional.  By default, only the three built-in headers are masked.
     * Calls are cumulative — each invocation adds to the previously registered set.
     *
     * @param headers The header names to mask; must not be {@code null} or empty.
     * @return This builder.
     * @throws software.frisby.core.validation.NullValueException       if {@code headers} is {@code null}.
     * @throws software.frisby.core.validation.MissingElementsException if {@code headers} is empty.
     * @throws software.frisby.core.validation.NullElementException     if any element is {@code null}.
     * @throws software.frisby.core.validation.BlankValueException      if any element is blank.
     */
    ClientLoggingConfigurationBuilder redactHeaders(String... headers);

    /**
     * Specifies body field names whose string values will be replaced with
     * {@code [redacted]} in log entries.
     * <p>
     * Redaction applies to both request and response bodies, across two content types:
     * <ul>
     *   <li><b>JSON</b> ({@code application/json}) — string-valued fields matching by
     *       name are replaced.  Fields containing objects, arrays, numbers, or booleans
     *       are not affected.</li>
     *   <li><b>Form-encoded</b> ({@code application/x-www-form-urlencoded}) — fields
     *       matching by name have their value replaced.</li>
     * </ul>
     * <p>
     * Matching is <b>case-sensitive</b> for both content types.  Field names in JSON and
     * form-encoded bodies are arbitrary application-defined strings with no
     * case-insensitivity convention, so {@code password} and {@code Password} are treated
     * as distinct names.  Configure the exact name as it appears in the payload.
     * <p>
     * Optional.  By default, no fields are redacted.
     * Calls are cumulative — each invocation adds to the previously registered list.
     *
     * @param fields The field names to redact; must not be {@code null} or empty.
     * @return This builder.
     * @throws software.frisby.core.validation.NullValueException       if {@code fields} is {@code null}.
     * @throws software.frisby.core.validation.MissingElementsException if {@code fields} is empty.
     * @throws software.frisby.core.validation.NullElementException     if any element is {@code null}.
     * @throws software.frisby.core.validation.BlankValueException      if any element is blank.
     */
    ClientLoggingConfigurationBuilder redactFields(String... fields);

    /**
     * Sets the maximum number of characters of a body that will be included in log
     * entries at {@code WARNING} and {@code TRACE} levels.
     * <p>
     * Bodies larger than this limit are truncated and marked in the log.
     * Pass {@code 0} to suppress body logging entirely.
     * <p>
     * Optional.  Defaults to {@code 8192} (8 KB).
     *
     * @param maxBodySize The maximum body size in characters; must be between {@code 0}
     *                    and {@code 104857600} (100 MB) inclusive.
     * @return This builder.
     * @throws software.frisby.core.validation.NumericValueOutsideRangeException if the value is outside
     *                                                                           the allowed range.
     */
    ClientLoggingConfigurationBuilder maxBodySize(int maxBodySize);

    /**
     * Returns a new {@link ClientLoggingConfiguration} instance based on the options
     * configured on this builder.
     *
     * @return A new {@link ClientLoggingConfiguration}; never {@code null}.
     */
    ClientLoggingConfiguration build();
}

