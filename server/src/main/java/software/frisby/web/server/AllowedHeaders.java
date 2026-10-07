package software.frisby.web.server;

import java.util.List;

/**
 * Describes how the server populates the {@code Access-Control-Allow-Headers} header
 * in CORS preflight responses.
 * <p>
 * Two variants exist:
 * <ul>
 *   <li>{@code Echo} — the server echoes whatever headers the browser requests via
 *       {@code Access-Control-Request-Headers} (permissive default).</li>
 *   <li>{@code Explicit} — the server advertises only the declared headers,
 *       regardless of what the browser requests.</li>
 * </ul>
 * <p>
 * The permissive default is active when {@link CorsConfigurationBuilder#allowedHeaders}
 * is never called.  To restrict which headers are permitted, call
 * {@link CorsConfigurationBuilder#allowedHeaders(String...)} with the desired header names.
 *
 * @see CorsConfigurationBuilder#allowedHeaders(String...)
 * @see CorsConfiguration#allowedHeaders()
 */
public sealed interface AllowedHeaders permits EchoAllowedHeaders, ExplicitAllowedHeaders {
    /**
     * Returns the singleton echo instance — the server echoes the browser's
     * {@code Access-Control-Request-Headers} value.
     *
     * @return The singleton echo instance; never {@code null}.
     */
    static AllowedHeaders echo() {
        return EchoAllowedHeaders.INSTANCE;
    }

    /**
     * Returns an explicit instance advertising exactly the given headers.
     *
     * @param headers The header names to allow; must not be {@code null}, and each
     *                element must not be {@code null} or blank.  An empty list is
     *                accepted and results in no {@code Access-Control-Allow-Headers}
     *                header being sent.
     * @return A new explicit instance; never {@code null}.
     * @throws software.frisby.core.validation.NullValueException   if {@code headers} is
     *                                                              {@code null}.
     * @throws software.frisby.core.validation.NullElementException if any element is
     *                                                              {@code null}.
     * @throws software.frisby.core.validation.BlankValueException  if any element is blank.
     */
    static AllowedHeaders explicit(List<String> headers) {
        return new ExplicitAllowedHeaders(headers);
    }
}
