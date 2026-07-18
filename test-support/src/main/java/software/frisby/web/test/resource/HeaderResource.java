package software.frisby.web.test.resource;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;

import java.util.HashMap;
import java.util.Map;

/**
 * JAX-RS resource that returns select request headers as a JSON map, enabling tests to verify
 * that the client is forwarding headers correctly.
 *
 * <ul>
 *   <li>{@code GET /headers} — returns a JSON object containing the values of
 *       {@code Authorization}, {@code Content-Type}, and any headers whose names begin with
 *       {@code X-} that were present in the request</li>
 * </ul>
 */
@Path("/headers")
@Produces(MediaType.APPLICATION_JSON)
public final class HeaderResource {
    private static final String AUTHORIZATION = "Authorization";
    private static final String CONTENT_TYPE = "Content-Type";

    /**
     * Returns select request headers as a {@code name → value} map.
     * <p>
     * Includes {@code Authorization}, {@code Content-Type}, and all headers prefixed with
     * {@code X-}.  Only the first value is returned for multi-value headers.
     *
     * @param headers The injected JAX-RS request headers.
     * @return An immutable map of header name to first value.
     */
    @GET
    public Map<String, String> headers(@Context HttpHeaders headers) {
        Map<String, String> result = new HashMap<>();

        String authorization = headers.getHeaderString(AUTHORIZATION);

        if (null != authorization) {
            result.put(AUTHORIZATION, authorization);
        }

        String contentType = headers.getHeaderString(CONTENT_TYPE);

        if (null != contentType) {
            result.put(CONTENT_TYPE, contentType);
        }

        for (String name : headers.getRequestHeaders().keySet()) {
            if (name.startsWith("X-") || name.startsWith("x-")) {
                result.put(name, headers.getHeaderString(name));
            }
        }

        return result;
    }
}

