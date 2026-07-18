package software.frisby.web.server;

import software.frisby.core.validation.StringSequences;

import java.util.List;

/**
 * Describes how the server populates the {@code Access-Control-Allow-Headers} header
 * in CORS preflight responses.
 * <p>
 * Two variants exist:
 * <ul>
 *   <li>{@link Echo} — the server echoes whatever headers the browser requests via
 *       {@code Access-Control-Request-Headers} (permissive default).</li>
 *   <li>{@link Explicit} — the server advertises only the declared headers,
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
public sealed interface AllowedHeaders permits AllowedHeaders.Echo, AllowedHeaders.Explicit {
    /**
     * The server echoes the {@code Access-Control-Request-Headers} value sent by the
     * browser, permitting any headers the client chooses to include.
     * <p>
     * This is the permissive default — it is applied when
     * {@link CorsConfigurationBuilder#allowedHeaders(String...)} is never called.
     */
    final class Echo implements AllowedHeaders {
        private static final Echo INSTANCE = new Echo();

        private Echo() {
        }
    }

    /**
     * The server advertises exactly the declared headers in the
     * {@code Access-Control-Allow-Headers} preflight response header.
     * <p>
     * Any header not in this list will be rejected by the browser during preflight.
     */
    final class Explicit implements AllowedHeaders {
        private final List<String> headers;

        private Explicit(List<String> headers) {
            this.headers = List.copyOf(StringSequences.noBlankElements("headers", headers));
        }

        /**
         * Returns the configured allowed header names.
         *
         * @return An unmodifiable list; never {@code null}.  May be empty if
         * {@link AllowedHeaders#explicit(List)} was called with an empty list.
         */
        public List<String> headers() {
            return headers;
        }
    }

    /**
     * Returns the singleton {@link Echo} instance — the server echoes the browser's
     * {@code Access-Control-Request-Headers} value.
     *
     * @return The singleton {@link Echo} instance; never {@code null}.
     */
    static Echo echo() {
        return Echo.INSTANCE;
    }

    /**
     * Returns an {@link Explicit} instance advertising exactly the given headers.
     *
     * @param headers The header names to allow; must not be {@code null}, and each
     *                element must not be {@code null} or blank.  An empty list is
     *                accepted and results in no {@code Access-Control-Allow-Headers}
     *                header being sent.
     * @return A new {@link Explicit} instance; never {@code null}.
     * @throws software.frisby.core.validation.NullValueException   if {@code headers} is
     *                                                              {@code null}.
     * @throws software.frisby.core.validation.NullElementException if any element is
     *                                                              {@code null}.
     * @throws software.frisby.core.validation.BlankValueException  if any element is blank.
     */
    static Explicit explicit(List<String> headers) {
        return new Explicit(headers);
    }
}

