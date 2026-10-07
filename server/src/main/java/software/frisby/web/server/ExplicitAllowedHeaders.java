package software.frisby.web.server;

import software.frisby.core.validation.StringSequences;

import java.util.List;

/**
 * The server advertises exactly the declared headers in the
 * {@code Access-Control-Allow-Headers} preflight response header.
 * <p>
 * Any header not in this list will be rejected by the browser during preflight.
 *
 * @param headers The allowed header names; an unmodifiable copy is kept.  May be empty
 *                if {@link AllowedHeaders#explicit(List)} was called with an empty list.
 * @see AllowedHeaders#explicit(List)
 */
record ExplicitAllowedHeaders(List<String> headers) implements AllowedHeaders {
    ExplicitAllowedHeaders {
        headers = List.copyOf(StringSequences.noBlankElements("headers", headers));
    }
}

