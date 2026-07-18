package software.frisby.web.test.resource;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import software.frisby.web.test.domain.CreatePersonRequest;
import software.frisby.web.test.domain.Person;
import software.frisby.web.test.domain.UpdatePersonRequest;

import java.util.List;

/**
 * JAX-RS resource that provides predictable responses for client integration tests.
 * <p>
 * Responses are deterministic and do not maintain state — the same request always produces
 * the same response, making assertions straightforward.
 *
 * <ul>
 *   <li>{@code GET /persons} — returns two hardcoded persons (tests {@code GenericType<List>})</li>
 *   <li>{@code GET /persons/{id}} — returns a person using the path {@code id}; returns 404
 *       for {@code id == "not-found"} (tests 404 error mapping)</li>
 *   <li>{@code POST /persons} — echoes {@code name}/{@code email} back as a new person
 *       with a fixed id (tests POST with typed response)</li>
 *   <li>{@code PUT /persons/{id}} — echoes the body back with the path id (tests PUT)</li>
 *   <li>{@code PATCH /persons/{id}} — same as PUT (tests PATCH)</li>
 *   <li>{@code DELETE /persons/{id}} — returns 204 No Content (tests void DELETE)</li>
 *   <li>{@code HEAD /persons/{id}} — automatically served by JAX-RS from the GET handler</li>
 * </ul>
 */
@Path("/persons")
@Produces(MediaType.APPLICATION_JSON)
public final class PersonResource {
    private static final String FIXED_ID = "person-1";
    private static final String CREATED_ID = "created-1";

    /**
     * Returns two hardcoded persons.
     *
     * @return A list of two persons.
     */
    @GET
    public List<Person> list() {
        return List.of(
                new Person("person-1", "Alice", "alice@example.com"),
                new Person("person-2", "Bob", "bob@example.com")
        );
    }

    /**
     * Returns a single person by id.  Returns HTTP 404 when {@code id} is {@code "not-found"}.
     *
     * @param id The path parameter.
     * @return A person, or HTTP 404.
     */
    @GET
    @Path("/{id}")
    public Response get(@PathParam("id") String id) {
        if ("not-found".equals(id)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        return Response.ok(new Person(id, "Test Person", "test@example.com")).build();
    }

    /**
     * Creates a new person from the request body.
     *
     * @param request The creation request.
     * @return HTTP 201 with the created person.
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response create(CreatePersonRequest request) {
        Person created = new Person(CREATED_ID, request.name(), request.email());
        return Response.status(Response.Status.CREATED).entity(created).build();
    }

    /**
     * Replaces a person with the request body.
     *
     * @param id      The path parameter.
     * @param request The update request.
     * @return The updated person.
     */
    @PUT
    @Path("/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Person replace(@PathParam("id") String id, UpdatePersonRequest request) {
        return new Person(id, request.name(), request.email());
    }

    /**
     * Partially updates a person with the request body.
     *
     * @param id      The path parameter.
     * @param request The partial update request.
     * @return The updated person.
     */
    @PATCH
    @Path("/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Person patch(@PathParam("id") String id, UpdatePersonRequest request) {
        return new Person(id, request.name(), request.email());
    }

    /**
     * Deletes a person by id.
     *
     * @param id The path parameter.
     * @return HTTP 204 No Content.
     */
    @DELETE
    @Path("/{id}")
    public Response delete(@PathParam("id") String id) {
        return Response.noContent().build();
    }
}

