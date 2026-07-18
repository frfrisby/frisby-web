package software.frisby.web.server;

import java.util.List;

/**
 * CORS (Cross-Origin Resource Sharing) policy applied to all responses when registered
 * via {@link ServerConfigurationBuilder#cors(CorsConfiguration)}.
 * <p>
 * Obtain a builder via the static {@link #builder()} factory method:
 *
 * <pre>{@code
 * CorsConfiguration cors = CorsConfiguration.builder()
 *         .allowedOrigins("https://app.example.com", "https://admin.example.com")
 *         .allowedMethods("GET", "POST", "PUT", "DELETE")
 *         .allowedHeaders("Authorization", "Content-Type")
 *         .allowCredentials()
 *         .build();
 *
 * // Wildcard origin — allows any browser origin (cannot be combined with allowCredentials())
 * CorsConfiguration cors = CorsConfiguration.builder()
 *         .allowedOrigins("*")
 *         .allowedMethods("GET", "POST")
 *         .build();
 * }</pre>
 *
 * @see CorsConfigurationBuilder
 * @see ServerConfigurationBuilder#cors(CorsConfiguration)
 */
public interface CorsConfiguration {
    /**
     * Returns a new {@link CorsConfigurationBuilder} instance.
     *
     * @return A new builder; never {@code null}.
     */
    static CorsConfigurationBuilder builder() {
        return new DefaultCorsConfigurationBuilder();
    }

    /**
     * Returns the set of origins that are permitted to make cross-origin requests.
     * <p>
     * When the list contains the special value {@code "*"}, all origins are permitted
     * and the response carries {@code Access-Control-Allow-Origin: *}.  In all other
     * cases, the incoming {@code Origin} header is matched against this list
     * (case-insensitively) and, if matched, echoed back as the
     * {@code Access-Control-Allow-Origin} response header; a {@code Vary: Origin}
     * header is also added so that proxies do not serve a cached response for the
     * wrong origin.
     *
     * @return The configured allowed origins; never {@code null} or empty.
     */
    List<String> allowedOrigins();

    /**
     * Returns the HTTP methods permitted in cross-origin requests.
     * <p>
     * Sent as the {@code Access-Control-Allow-Methods} header in preflight responses.
     *
     * @return The configured allowed methods; never {@code null} or empty.
     */
    List<String> allowedMethods();

    /**
     * Returns the headers policy for cross-origin requests.
     * <p>
     * When {@link AllowedHeaders.Echo}, the server echoes the client's
     * {@code Access-Control-Request-Headers} value in preflight responses — any header
     * the browser requests is permitted.
     * <p>
     * When {@link AllowedHeaders.Explicit}, the server sends only the declared headers
     * as the {@code Access-Control-Allow-Headers} preflight response header.
     *
     * @return The configured {@link AllowedHeaders} policy; never {@code null}.
     */
    AllowedHeaders allowedHeaders();

    /**
     * Returns {@code true} if cross-origin requests may include credentials
     * (cookies, HTTP authentication, client-side TLS certificates).
     * <p>
     * When {@code true}, the response carries
     * {@code Access-Control-Allow-Credentials: true}.  This cannot be combined with
     * a wildcard origin ({@code "*"}) — browsers reject that combination.
     *
     * @return {@code true} if credentials are allowed; {@code false} otherwise.
     */
    boolean allowCredentials();
}

