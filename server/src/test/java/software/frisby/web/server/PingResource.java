package software.frisby.web.server;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/**
 * Minimal JAX-RS resource used by server integration tests.
 * Must be {@code public} so Jersey can invoke its methods via reflection.
 */
@Path("/ping")
@Produces(MediaType.APPLICATION_JSON)
public final class PingResource {
    /**
     * A fully concrete, non-generic request/response POJO used to exercise
     * the {@code readFrom} and {@code writeTo} paths in {@link JsonMessageBodyProvider}
     * without any parameterized generic types.
     */
    public record PingRequest(String message) {
    }

    @GET
    public Map<String, String> ping() {
        return Map.of("message", "pong");
    }

    @GET
    @Path("/{id}")
    public Map<String, String> get(@PathParam("id") String id) {
        return Map.of("id", id);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Map<String, String> echo(Map<String, String> body) {
        return body;
    }

    /**
     * Accepts a typed {@link PingRequest} body and echoes it back.
     * Exercises {@link JsonMessageBodyProvider} with a plain POJO — no generic
     * type parameters — so {@code type.equals(genericType)} is {@code true} and
     * the simple {@code serializer.deserialize(body, type)} path is taken for both
     * reader and writer.
     */
    @POST
    @Path("/typed")
    @Consumes(MediaType.APPLICATION_JSON)
    public PingRequest typedEcho(PingRequest request) {
        return request;
    }

    /** Returns a binary octet-stream entity — exercises the InputStream branch in GZipResponseFilter. */
    @GET
    @Path("/bytes")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response bytes() {
        return Response
                .ok(new ByteArrayInputStream(new byte[]{1, 2, 3}))
                .type(MediaType.APPLICATION_OCTET_STREAM)
                .build();
    }

    /** Returns a plain-text response — exercises the non-JSON branch in GZipResponseFilter. */
    @GET
    @Path("/text")
    @Produces(MediaType.TEXT_PLAIN)
    public String text() {
        return "hello";
    }

    /**
     * Returns a {@code 500} response by building it directly — no exception is thrown.
     * Used to verify that a deliberate 5xx response with no associated exception is
     * logged at {@code WARNING}, not {@code ERROR} (since {@code requestException} is
     * {@code null}, the {@code null != cause} guard in {@code determineFailureLevel}
     * is {@code false} and the method falls through to {@code WARNING}).
     */
    @GET
    @Path("/server-error")
    public Response serverError() {
        return Response
                .status(Response.Status.INTERNAL_SERVER_ERROR)
                .type(MediaType.APPLICATION_JSON)
                .entity(Map.of("error", "deliberate"))
                .build();
    }

    /** Throws an unhandled exception — used to trigger the event-listener failure path. */
    @GET
    @Path("/fail")
    public Response fail() {
        throw new RuntimeException("Intentional test failure");
    }

    /**
     * Throws a {@link MappedException} — a non-{@link jakarta.ws.rs.WebApplicationException}
     * that is mapped to {@code 503} by {@link MappedExceptionMapper}.
     * <p>
     * Used to verify that a domain/infrastructure exception handled gracefully at the HTTP
     * layer via an {@link jakarta.ws.rs.ext.ExceptionMapper} is still logged at {@code ERROR},
     * because the exception represents a genuine application failure.
     */
    @GET
    @Path("/mapped-fail")
    public Response mappedFail() {
        throw new MappedException("Intentional mapped test failure");
    }

    /**
     * Throws an {@link jakarta.ws.rs.InternalServerErrorException} — a
     * {@link jakarta.ws.rs.WebApplicationException}-based 500 used to verify that
     * deliberate 5xx responses are logged at {@code WARNING}, not {@code ERROR}.
     */
    @GET
    @Path("/wae-fail")
    public Response waeFail() {
        throw new jakarta.ws.rs.InternalServerErrorException("Deliberate WAE 500");
    }

    /** Returns a 400 with a JSON error body — used to verify response body appears in failure logs. */
    @GET
    @Path("/bad-request")
    public Response badRequest() {
        return Response
                .status(Response.Status.BAD_REQUEST)
                .type(MediaType.APPLICATION_JSON)
                .entity(Map.of("error", "invalid-input", "detail", "The 'id' field is required."))
                .build();
    }

    /**
     * Returns a 400 with a {@code Set-Cookie} response header — used to verify the header
     * is masked in failure logs.
     */
    @GET
    @Path("/bad-request-with-cookie")
    public Response badRequestWithCookie() {
        return Response
                .status(Response.Status.BAD_REQUEST)
                .header("Set-Cookie", "session=secret-session-id; HttpOnly; Secure")
                .type(MediaType.APPLICATION_JSON)
                .entity(Map.of("error", "bad-request"))
                .build();
    }

    /**
     * Returns a 400 with multiple {@code Set-Cookie} response headers — used to verify
     * that each cookie is rendered on its own line with its value redacted.
     */
    @GET
    @Path("/bad-request-with-cookies")
    public Response badRequestWithCookies() {
        return Response
                .status(Response.Status.BAD_REQUEST)
                .header("Set-Cookie", "session=secret-session-id; Path=/; HttpOnly; Secure")
                .header("Set-Cookie", "token=secret-token-value; Path=/api; Secure; Max-Age=3600")
                .header("Set-Cookie", "pref=dark-mode-setting; Path=/; Max-Age=86400")
                .type(MediaType.APPLICATION_JSON)
                .entity(Map.of("error", "bad-request"))
                .build();
    }

    /**
     * Returns a 400 with a multi-value {@code Vary} response header — used to verify
     * that multiple values for a single header are joined cleanly in failure logs.
     */
    @GET
    @Path("/bad-request-multi-header")
    public Response badRequestMultiHeader() {
        return Response
                .status(Response.Status.BAD_REQUEST)
                .header("Vary", "Accept")
                .header("Vary", "Accept-Encoding")
                .type(MediaType.APPLICATION_JSON)
                .entity(Map.of("error", "bad-request"))
                .build();
    }

    /** Returns a 401 with a body — used to verify the body is stripped by the exception mapper. */
    @GET
    @Path("/unauthorized")
    public Response unauthorized() {
        throw new jakarta.ws.rs.NotAuthorizedException(
                Response.status(Response.Status.UNAUTHORIZED)
                        .type(MediaType.APPLICATION_JSON)
                        .entity(Map.of("error", "not-authorized", "user", "frank@example.com"))
                        .build()
        );
    }

    /** Returns a 403 with a body — used to verify the body is stripped by the exception mapper. */
    @GET
    @Path("/forbidden")
    public Response forbidden() {
        throw new jakarta.ws.rs.ForbiddenException(
                Response.status(Response.Status.FORBIDDEN)
                        .type(MediaType.APPLICATION_JSON)
                        .entity(Map.of("error", "forbidden", "role", "admin-required"))
                        .build()
        );
    }

    /** Throws an unhandled exception on POST — exercises the request-body capture + failure-log path. */
    @POST
    @Path("/fail")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response failPost(Map<String, String> body) {
        throw new RuntimeException("Intentional POST test failure");
    }

    /** Returns a 401 on POST — verifies the request body appears in the log while the response is stripped. */
    @POST
    @Path("/unauthorized")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response unauthorizedPost(Map<String, String> body) {
        throw new jakarta.ws.rs.NotAuthorizedException(
                Response.status(Response.Status.UNAUTHORIZED)
                        .type(MediaType.APPLICATION_JSON)
                        .entity(Map.of("error", "not-authorized"))
                        .build()
        );
    }

    /** Returns a 403 on POST — verifies the request body appears in the log while the response is stripped. */
    @POST
    @Path("/forbidden")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response forbiddenPost(Map<String, String> body) {
        throw new jakarta.ws.rs.ForbiddenException(
                Response.status(Response.Status.FORBIDDEN)
                        .type(MediaType.APPLICATION_JSON)
                        .entity(Map.of("error", "forbidden"))
                        .build()
        );
    }

    /**
     * Throws a 401 using a message-only constructor — no embedded response entity.
     * Exercises the path where {@code WebApplicationExceptionMapper} <em>is</em> invoked
     * (JAX-RS spec only bypasses the mapper when an entity is present in the embedded response).
     */
    @GET
    @Path("/unauthorized-message")
    public Response unauthorizedMessage() {
        throw new jakarta.ws.rs.NotAuthorizedException("Bearer realm=\"test\"");
    }

    /**
     * Throws a 403 using a message-only constructor — no embedded response entity.
     * Exercises the path where {@code WebApplicationExceptionMapper} <em>is</em> invoked.
     */
    @GET
    @Path("/forbidden-message")
    public Response forbiddenMessage() {
        throw new jakarta.ws.rs.ForbiddenException("Access denied");
    }

    /**
     * Accepts a {@code multipart/form-data} body as a raw {@link InputStream} and
     * returns the number of bytes received.  Used to verify that
     * {@code RequestBodyBufferingFilter} skipping multipart bodies does not corrupt
     * the stream before Jersey delivers it to the resource method.
     */
    @POST
    @Path("/upload")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response upload(InputStream body) throws IOException {
        int received = body.readAllBytes().length;

        return Response.ok(Map.of("received", String.valueOf(received))).build();
    }

    /**
     * Returns a 204 No Content response with no entity and no media type.
     * Used to exercise the {@code null == responseContext.getMediaType()} branch in
     * {@code GZipResponseFilter.shouldCompress()}.
     */
    @GET
    @Path("/no-content")
    public Response noContent() {
        return Response.noContent().build();
    }

    /**
     * Returns a 400 with a pre-serialized {@code String} entity.
     * Used to exercise the {@code entity instanceof String} branch in
     * {@code DefaultServer.serializeEntityForLog()}.
     */
    @GET
    @Path("/bad-request-string-entity")
    public Response badRequestStringEntity() {
        return Response
                .status(Response.Status.BAD_REQUEST)
                .type(MediaType.APPLICATION_JSON)
                .entity("{\"error\":\"string-entity-bad-request\"}")
                .build();
    }

    /**
     * Returns a 400 with an {@link InputStream} entity.
     * Used to exercise the {@code entity instanceof InputStream} branch in
     * {@code DefaultServer.serializeEntityForLog()} — the body must not appear in the log.
     */
    @GET
    @Path("/bad-request-stream")
    public Response badRequestStream() {
        return Response
                .status(Response.Status.BAD_REQUEST)
                .type(MediaType.APPLICATION_JSON)
                .entity(new ByteArrayInputStream("{\"error\":\"stream-entity\"}".getBytes()))
                .build();
    }

    /**
     * Accepts a binary {@code application/octet-stream} body and returns 400.
     * Used to exercise the {@code isBinaryBody=true} branch in
     * {@code DefaultServer.buildDetail()} — the request body must appear in the log
     * as {@code [application/octet-stream — body not logged]} rather than its raw bytes.
     */
    @POST
    @Path("/binary-fail")
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    public Response binaryFail(InputStream body) {
        return Response.status(Response.Status.BAD_REQUEST).build();
    }

    /**
     * Returns a 400 with an empty-string entity.
     * Used to exercise the {@code !responseBody.isBlank()} guard in
     * {@code DefaultServer.buildDetail()} — no {@code Response Body:} line should appear
     * in the log when the serialized entity is blank.
     */
    @GET
    @Path("/bad-request-empty-body")
    public Response badRequestEmptyBody() {
        return Response
                .status(Response.Status.BAD_REQUEST)
                .type(MediaType.APPLICATION_JSON)
                .entity("")
                .build();
    }

    /**
     * Returns a 400 with an {@code X-Api-Key} response header.
     * Used to verify that user-configured masked headers are redacted in failure logs
     * when they appear on the response side (not just the request side).
     */
    @GET
    @Path("/bad-request-with-api-key-header")
    public Response badRequestWithApiKeyHeader() {
        return Response
                .status(Response.Status.BAD_REQUEST)
                .header("X-Api-Key", "super-secret-api-key-12345")
                .type(MediaType.APPLICATION_JSON)
                .entity(Map.of("error", "bad-request"))
                .build();
    }
}



