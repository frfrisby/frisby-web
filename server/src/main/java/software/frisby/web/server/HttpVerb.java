package software.frisby.web.server;

/**
 * Standard HTTP methods defined by RFC 7231 and RFC 5789.
 * <p>
 * Use with {@link CorsConfigurationBuilder#allowedMethods(HttpVerb...)} to configure
 * which methods are permitted in cross-origin requests, and with
 * {@link HttpErrors#methodNotAllowed(HttpVerb...)} to populate the {@code Allow} response
 * header in {@code 405 Method Not Allowed} errors.
 * <p>
 * Using this enum instead of raw strings provides compile-time safety and ensures
 * that only valid, standard HTTP methods can be specified.  Duplicate values supplied
 * to either method are silently deduplicated.
 *
 * @see CorsConfigurationBuilder#allowedMethods(HttpVerb...)
 * @see HttpErrors#methodNotAllowed(HttpVerb...)
 */
public enum HttpVerb {
    /** RFC 7231 — retrieve a representation of the target resource. */
    GET,

    /** RFC 7231 — same as GET but transfers only the status line and headers. */
    HEAD,

    /** RFC 7231 — perform resource-specific processing on the request payload. */
    POST,

    /** RFC 7231 — replace the target resource with the request payload. */
    PUT,

    /** RFC 7231 — remove the target resource. */
    DELETE,

    /** RFC 7231 — describe the communication options for the target resource. */
    OPTIONS,

    /** RFC 5789 — apply partial modifications to the target resource. */
    PATCH,

    /** RFC 7231 — perform a message loop-back test to the target resource. */
    TRACE,

    /** RFC 7231 — establish a tunnel to the server for the target resource. */
    CONNECT
}

