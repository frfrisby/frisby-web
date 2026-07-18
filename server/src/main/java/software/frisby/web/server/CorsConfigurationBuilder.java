package software.frisby.web.server;

/**
 * A builder for creating a {@link CorsConfiguration} instance.
 * <p>
 * Obtain a builder via {@link CorsConfiguration#builder()}.
 *
 * <pre>{@code
 * // Specific origins with credentials
 * CorsConfiguration cors = CorsConfiguration.builder()
 *         .allowedOrigins("https://app.example.com")
 *         .allowedMethods("GET", "POST", "PUT", "DELETE")
 *         .allowedHeaders("Authorization", "Content-Type")
 *         .allowCredentials()
 *         .build();
 *
 * // Wildcard — any browser origin permitted
 * CorsConfiguration cors = CorsConfiguration.builder()
 *         .allowedOrigins("*")
 *         .allowedMethods("GET", "POST")
 *         .build();
 * }</pre>
 *
 * @see CorsConfiguration
 */
public interface CorsConfigurationBuilder {
    /**
     * Adds one or more permitted origins for cross-origin requests.
     * <p>
     * Use the special value {@code "*"} to permit any browser origin.  When the
     * wildcard is configured, {@link #allowCredentials()} cannot also be set —
     * browsers reject {@code Access-Control-Allow-Credentials: true} combined with
     * {@code Access-Control-Allow-Origin: *}.  {@link #build()} enforces this rule.
     * <p>
     * Required.  {@link #build()} throws if no origin has been configured.
     * <p>
     * May be called multiple times; each call appends to the existing list.
     *
     * @param origins One or more origin strings; must not be {@code null} or empty, and
     *                each origin must not be blank.
     * @return This builder.
     * @throws software.frisby.core.validation.NullValueException       if {@code origins} is {@code null}.
     * @throws software.frisby.core.validation.MissingElementsException if {@code origins} is empty.
     * @throws software.frisby.core.validation.NullElementException     if any element is {@code null}.
     * @throws software.frisby.core.validation.BlankValueException      if any element is blank.
     */
    CorsConfigurationBuilder allowedOrigins(String... origins);

    /**
     * Adds one or more HTTP methods permitted in cross-origin requests.
     * <p>
     * These are sent as the {@code Access-Control-Allow-Methods} header in preflight
     * responses.
     * <p>
     * Required.  {@link #build()} throws if no method has been configured.
     * <p>
     * May be called multiple times; each call appends to the existing list.
     *
     * @param methods One or more HTTP method strings (e.g. {@code "GET"}, {@code "POST"});
     *                must not be {@code null} or empty, and each method must not be blank.
     * @return This builder.
     * @throws software.frisby.core.validation.NullValueException       if {@code methods} is {@code null}.
     * @throws software.frisby.core.validation.MissingElementsException if {@code methods} is empty.
     * @throws software.frisby.core.validation.NullElementException     if any element is {@code null}.
     * @throws software.frisby.core.validation.BlankValueException      if any element is blank.
     */
    CorsConfigurationBuilder allowedMethods(String... methods);

    /**
     * Adds one or more request headers permitted in cross-origin requests.
     * <p>
     * These are sent as the {@code Access-Control-Allow-Headers} header in preflight
     * responses.  When this method is never called, the server echoes the client's
     * {@code Access-Control-Request-Headers} value instead — this is the permissive
     * default that allows any header the client wants to send.
     * <p>
     * Optional.  May be called multiple times; each call appends to the existing list.
     *
     * @param headers One or more header name strings; must not be {@code null}, and
     *                each header name must not be blank.
     * @return This builder.
     * @throws software.frisby.core.validation.NullValueException   if {@code headers} is {@code null}.
     * @throws software.frisby.core.validation.NullElementException if any element is {@code null}.
     * @throws software.frisby.core.validation.BlankValueException  if any element is blank.
     */
    CorsConfigurationBuilder allowedHeaders(String... headers);

    /**
     * Permits credentials (cookies, HTTP authentication, client-side TLS certificates)
     * in cross-origin requests.
     * <p>
     * When set, the server adds {@code Access-Control-Allow-Credentials: true} to
     * responses.  Cannot be combined with a wildcard origin ({@code "*"}) —
     * {@link #build()} throws an {@link IllegalStateException} if both are configured.
     * <p>
     * Optional.  Defaults to {@code false}.
     *
     * @return This builder.
     */
    CorsConfigurationBuilder allowCredentials();

    /**
     * Returns a new {@link CorsConfiguration} instance based on the options
     * configured by calling the setter methods on this builder.
     *
     * @return A new {@link CorsConfiguration}; never {@code null}.
     * @throws software.frisby.core.validation.MissingElementsException if no origins or no methods have been configured.
     * @throws IllegalStateException                                    if {@link #allowCredentials()} is set together
     *                                                                  with a wildcard origin ({@code "*"}).
     */
    CorsConfiguration build();
}


