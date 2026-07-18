package software.frisby.web.test.resource;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import org.glassfish.jersey.media.multipart.FormDataBodyPart;
import org.glassfish.jersey.media.multipart.FormDataMultiPart;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * JAX-RS resource that echoes request bodies back as responses, enabling round-trip
 * serialization tests.
 *
 * <ul>
 *   <li>{@code POST /echo} — returns the raw JSON request body unchanged</li>
 *   <li>{@code POST /echo/form} — returns all received form fields as a JSON map
 *       ({@code application/x-www-form-urlencoded})</li>
 *   <li>{@code PUT /echo/form} — same as POST for form-urlencoded</li>
 *   <li>{@code POST /echo/multipart} — returns all received multipart text/JSON parts as a
 *       JSON map; each part value is returned as its raw string content</li>
 *   <li>{@code PUT /echo/multipart} — same as POST for multipart</li>
 * </ul>
 */
@Path("/echo")
public final class EchoResource {
    /**
     * Echoes the raw JSON body back to the caller.
     *
     * @param body The raw JSON string received in the request body.
     * @return HTTP 200 with the same body.
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response echo(String body) {
        return Response.ok(body).build();
    }

    /**
     * Echoes all form fields back to the caller as a {@code name → value} JSON map.
     *
     * @param formParams The URL-encoded form parameters.
     * @return HTTP 200 with a JSON object containing all received field names and their first values.
     */
    @POST
    @Path("/form")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, String> echoForm(MultivaluedMap<String, String> formParams) {
        return toMap(formParams);
    }

    /**
     * Same as {@link #echoForm(MultivaluedMap)} for PUT requests.
     *
     * @param formParams The URL-encoded form parameters.
     * @return HTTP 200 with a JSON object containing all received field names and their first values.
     */
    @PUT
    @Path("/form")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, String> echoFormPut(MultivaluedMap<String, String> formParams) {
        return toMap(formParams);
    }

    /**
     * Echoes all multipart text and JSON parts back to the caller as a {@code name → value}
     * JSON map.  Each part's string representation is returned verbatim — JSON parts are
     * returned as their serialized JSON string.
     *
     * @param form The multipart form data.
     * @return HTTP 200 with a JSON object containing all received part names and their string values.
     */
    @POST
    @Path("/multipart")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, String> echoMultipart(FormDataMultiPart form) {
        return toPartMap(form);
    }

    /**
     * Same as {@link #echoMultipart(FormDataMultiPart)} for PUT requests.
     *
     * @param form The multipart form data.
     * @return HTTP 200 with a JSON object containing all received part names and their string values.
     */
    @PUT
    @Path("/multipart")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, String> echoMultipartPut(FormDataMultiPart form) {
        return toPartMap(form);
    }

    private static Map<String, String> toMap(MultivaluedMap<String, String> formParams) {
        Map<String, String> result = new HashMap<>();

        for (Map.Entry<String, java.util.List<String>> entry : formParams.entrySet()) {
            result.put(entry.getKey(), entry.getValue().get(0));
        }

        return result;
    }

    private static Map<String, String> toPartMap(FormDataMultiPart form) {
        Map<String, String> result = new HashMap<>();

        for (Map.Entry<String, List<FormDataBodyPart>> entry : form.getFields().entrySet()) {
            result.put(entry.getKey(), entry.getValue().get(0).getEntityAs(String.class));
        }

        return result;
    }
}
