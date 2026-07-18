package software.frisby.web.test.resource;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * JAX-RS resource that returns any requested HTTP status code, enabling client error-mapping
 * integration tests.
 *
 * <ul>
 *   <li>{@code GET /status/{code}} — returns the given status code with a JSON body
 *       {@code {"status":<code>}}, allowing tests to verify that the client maps each
 *       HTTP status to the correct exception subtype</li>
 * </ul>
 */
@Path("/status")
@Produces(MediaType.APPLICATION_JSON)
public final class StatusResource {
    /**
     * Returns the given HTTP status code with a minimal JSON body.
     *
     * @param code The HTTP status code to return (e.g. {@code 400}, {@code 404}, {@code 500}).
     * @return A response with the given status code and a JSON body.
     */
    @GET
    @Path("/{code}")
    public Response status(@PathParam("code") int code) {
        return Response
                .status(code)
                .entity("{\"status\":" + code + "}")
                .type(MediaType.APPLICATION_JSON)
                .build();
    }
}

