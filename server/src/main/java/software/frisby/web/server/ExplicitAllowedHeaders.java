package software.frisby.web.server;

import software.frisby.core.validation.StringSequences;

import java.util.List;

/**
 * The server advertises exactly the declared headers in the
 * {@code Access-Control-Allow-Headers} preflight response header.
 * <p>
 * Any header not in this list will be rejected by the browser during preflight.
 *
 * @see AllowedHeaders#explicit(List)
 */
final class ExplicitAllowedHeaders implements AllowedHeaders {
    private final List<String> headers;

    ExplicitAllowedHeaders(List<String> headers) {
        this.headers = List.copyOf(StringSequences.noBlankElements("headers", headers));
    }

    /**
     * Returns the configured allowed header names.
     *
     * @return An unmodifiable list; never {@code null}.  May be empty if
     * {@link AllowedHeaders#explicit(List)} was called with an empty list.
     */
    List<String> headers() {
        return headers;
    }
}

