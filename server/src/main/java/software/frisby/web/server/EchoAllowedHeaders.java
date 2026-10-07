package software.frisby.web.server;

/**
 * The server echoes the {@code Access-Control-Request-Headers} value sent by the
 * browser, permitting any headers the client chooses to include.
 * <p>
 * This is the permissive default — it is applied when
 * {@link CorsConfigurationBuilder#allowedHeaders(String...)} is never called.
 *
 * @see AllowedHeaders#echo()
 */
final class EchoAllowedHeaders implements AllowedHeaders {
    static final EchoAllowedHeaders INSTANCE = new EchoAllowedHeaders();

    private EchoAllowedHeaders() {
    }
}

