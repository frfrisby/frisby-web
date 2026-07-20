package software.frisby.web.client;

import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;

/**
 * Bundles a {@link HttpRequest} with an optional body snapshot for logging.
 * <p>
 * The body snapshot is the raw UTF-8 bytes of the request body, captured before
 * the body publisher is created so they are available for log output:
 * <ul>
 *   <li>JSON entity bodies — the serialized JSON bytes (pre-compression for gzip requests);
 *       shared with the {@link HttpRequest.BodyPublisher} for non-compressed bodies,
 *       so no extra allocation is required</li>
 *   <li>Form-urlencoded bodies — the URL-encoded bytes; also shared with the publisher</li>
 *   <li>Multipart form-data bodies — {@link #MULTIPART_SNAPSHOT} (streamed; cannot be reproduced)</li>
 *   <li>Bodiless requests (GET, HEAD, DELETE, etc.) — {@code null}</li>
 * </ul>
 * <p>
 * {@link RequestLogger} converts the bytes to a {@link String} only at log time and
 * only when the relevant log level is active — avoiding a redundant String allocation
 * on every request in production.
 */
@SuppressWarnings("java:S6218") // array equality is intentionally unused — this is an internal logging carrier, not a value type
record OutboundRequest(HttpRequest request, byte[] bodySnapshot) {
    /**
     * Placeholder used as the body snapshot for {@code multipart/form-data} requests.
     */
    static final byte[] MULTIPART_SNAPSHOT =
            "[multipart/form-data — body not logged]".getBytes(StandardCharsets.UTF_8);

    /**
     * Factory for bodiless requests (GET, DELETE, HEAD).
     */
    static OutboundRequest of(HttpRequest request) {
        return new OutboundRequest(request, null);
    }

    /**
     * Factory for requests that carry a loggable body snapshot.
     */
    static OutboundRequest of(HttpRequest request, byte[] bodySnapshot) {
        return new OutboundRequest(request, bodySnapshot);
    }
}

