package software.frisby.web.client;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import jakarta.ws.rs.core.UriInfo;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Minimal, hand-written SSE-shaped test resource used only by {@link ClientSseStreamTest}.
 * <p>
 * This is intentionally primitive — it exists to exercise {@link SseSpec#stream()} at the
 * raw-bytes level before the {@code client-sse} module (and its own {@code test-support}
 * resource, added separately) exist.  It does not use {@code jersey-media-sse} or model
 * any {@code server-sse} type.
 */
@Path("/sse-stream-test")
public final class SseStreamTestResource {
    private static final String TEXT_EVENT_STREAM = "text/event-stream";
    private static final String ACCEPT = "Accept";
    private static final String X_TEST_HEADER = "X-Test-Header";
    private static final String SESSION_COOKIE = "session";
    private static final String AUTHORIZATION = "Authorization";
    private static final String TAG_PARAMETER = "tag";

    /**
     * Echoes the {@code Accept} header value back as a single SSE {@code data:} line —
     * used to verify {@link SseSpec#stream()}'s default {@code Accept} header behavior.
     *
     * @param headers The injected request headers.
     * @return A one-event SSE response.
     */
    @GET
    @Path("/accept-echo")
    public Response acceptEcho(@Context HttpHeaders headers) {
        String accept = headers.getHeaderString(ACCEPT);

        StreamingOutput output = out -> {
            out.write(("data: " + accept + "\n\n").getBytes(StandardCharsets.UTF_8));
            out.flush();
        };

        return Response.ok(output).type(TEXT_EVENT_STREAM).build();
    }

    /**
     * Echoes the path parameter, all {@code tag} query parameter values, the
     * {@code X-Test-Header} header values, the {@code session} cookie value, and the
     * {@code Authorization} header value back as a single SSE {@code data:} line — used
     * to exercise every {@link SseSpec} navigation method
     * ({@code path()} (both overloads), {@code parameter()} (both overloads),
     * {@code header(String, String...)}, {@code cookie()}, and {@code security()}).
     *
     * @param id      The path parameter.
     * @param uriInfo Provides the query parameters.
     * @param headers Provides the request headers and cookies.
     * @return A one-event SSE response.
     */
    @GET
    @Path("/{id}/echo-all")
    public Response echoAll(@PathParam("id") String id,
                             @Context UriInfo uriInfo,
                             @Context HttpHeaders headers) {
        List<String> tags = uriInfo.getQueryParameters().getOrDefault(TAG_PARAMETER, List.of());
        List<String> testHeaderValues = headers.getRequestHeader(X_TEST_HEADER);
        Map<String, Cookie> cookies = headers.getCookies();
        Cookie sessionCookie = cookies.get(SESSION_COOKIE);
        String authorization = headers.getHeaderString(AUTHORIZATION);

        String data = "id=" + id
                + ";tags=" + String.join(",", tags)
                + ";header=" + (null == testHeaderValues ? "" : String.join(",", testHeaderValues))
                + ";cookie=" + (null == sessionCookie ? "" : sessionCookie.getValue())
                + ";auth=" + authorization;

        StreamingOutput output = out -> {
            out.write(("data: " + data + "\n\n").getBytes(StandardCharsets.UTF_8));
            out.flush();
        };

        return Response.ok(output).type(TEXT_EVENT_STREAM).build();
    }

    /**
     * Writes one event immediately, flushes, sleeps past a short client read timeout,
     * then writes a second event and flushes — used to verify that a long-held
     * {@link SseSpec#stream()} connection is not prematurely closed by
     * {@code ClientConfiguration.readTimeout()}.
     *
     * @return A two-event SSE response with an artificial delay between events.
     */
    @GET
    @Path("/slow")
    public Response slow() {
        StreamingOutput output = out -> {
            out.write("id: 1\nevent: first\ndata: hello\n\n".getBytes(StandardCharsets.UTF_8));
            out.flush();

            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            out.write("id: 2\nevent: second\ndata: world\n\n".getBytes(StandardCharsets.UTF_8));
            out.flush();
        };

        return Response.ok(output).type(TEXT_EVENT_STREAM).build();
    }

    /**
     * Returns a {@code 404} with a genuinely empty body (no entity at all) — used to
     * exercise the blank-body branch of {@link SseSpec#stream()}'s error mapping.
     * (Note: an unmapped path such as {@code /does-not-exist} also produces a blank
     * body in this embedded Jetty/Jersey stack — it does not exercise the other branch.)
     *
     * @return A {@code 404} response with no body.
     */
    @GET
    @Path("/empty-error")
    public Response emptyError() {
        return Response.status(Response.Status.NOT_FOUND).build();
    }

    /**
     * Returns a {@code 404} with a non-blank plain-text entity — used to exercise the
     * {@code body.isBlank() = false} branch of {@link SseSpec#stream()}'s error mapping,
     * confirming that the raw response body is preserved on the thrown exception.
     *
     * @return A {@code 404} response with a non-blank body.
     */
    @GET
    @Path("/error-with-body")
    public Response errorWithBody() {
        return Response.status(Response.Status.NOT_FOUND)
                .entity("stream not found")
                .type(MediaType.TEXT_PLAIN)
                .build();
    }
}



