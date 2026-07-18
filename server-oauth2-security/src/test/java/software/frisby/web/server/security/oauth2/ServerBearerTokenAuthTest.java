package software.frisby.web.server.security.oauth2;

import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.frisby.web.server.Server;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.Principal;
import software.frisby.web.server.AuthenticatedIdentity;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Integration tests for {@link BearerTokenAuthenticationProvider} via a real embedded server.
 * <p>
 * Verifies that:
 * <ul>
 *   <li>A valid {@code Authorization: Bearer} header results in a {@code 200 OK}.</li>
 *   <li>A missing {@code Authorization} header results in {@code 401 Unauthorized}.</li>
 *   <li>An invalid token results in {@code 401 Unauthorized}.</li>
 *   <li>The validator throwing {@link ForbiddenException} results in {@code 403 Forbidden}.</li>
 *   <li>The health check endpoint bypasses authentication and returns {@code 200}.</li>
 * </ul>
 */
class ServerBearerTokenAuthTest {
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final String VALID_TOKEN = "valid.jwt.abc123";
    private static final String FORBIDDEN_TOKEN = "forbidden.token";
    private static final Principal BOB = () -> "bob";

    private static Server server;
    private static int port;

    @BeforeAll
    static void startServer() {
        BearerTokenValidator validator = token -> {
            if (FORBIDDEN_TOKEN.equals(token)) {
                throw new ForbiddenException();
            }

            if (VALID_TOKEN.equals(token)) {
                return AuthenticatedIdentity.of(BOB);
            }

            throw new NotAuthorizedException(
                    Response.status(Response.Status.UNAUTHORIZED).build()
            );
        };

        server = Server.builder()
                .configuration(c -> c
                        .port(0)
                        .serializer(new TestJsonSerializer()))
                .resources(new ProtectedResource())
                .healthCheck()
                .authentication(BearerTokenAuthenticationProvider.of(validator))
                .build();

        server.start();
        port = server.port();
    }

    @AfterAll
    static void stopServer() {
        if (null != server) {
            server.stop();
        }
    }

    @Test
    void validToken_returns200() throws Exception {
        HttpResponse<String> response = get(VALID_TOKEN);

        assertEquals(200, response.statusCode());
    }

    @Test
    void missingAuthorizationHeader_returns401() throws Exception {
        HttpResponse<String> response = getNoAuth();

        assertEquals(401, response.statusCode());
    }

    @Test
    void invalidToken_returns401() throws Exception {
        HttpResponse<String> response = get("bad.token.value");

        assertEquals(401, response.statusCode());
    }

    @Test
    void forbiddenToken_returns403() throws Exception {
        HttpResponse<String> response = get(FORBIDDEN_TOKEN);

        assertEquals(403, response.statusCode());
    }

    @Test
    void healthCheck_bypassesAuthentication_returns200() throws Exception {
        HttpResponse<String> response = HTTP.send(
                HttpRequest.newBuilder()
                        .GET()
                        .uri(URI.create("http://localhost:" + port + "/health"))
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );

        assertEquals(200, response.statusCode());
    }

    @Test
    void response401_hasNoBody() throws Exception {
        HttpResponse<String> response = getNoAuth();

        assertEquals("", response.body());
    }

    @Test
    void response403_hasNoBody() throws Exception {
        HttpResponse<String> response = get(FORBIDDEN_TOKEN);

        assertEquals("", response.body());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static HttpResponse<String> get(String token) throws Exception {
        return HTTP.send(
                HttpRequest.newBuilder()
                        .GET()
                        .uri(URI.create("http://localhost:" + port + "/protected"))
                        .header("Authorization", "Bearer " + token)
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );
    }

    private static HttpResponse<String> getNoAuth() throws Exception {
        return HTTP.send(
                HttpRequest.newBuilder()
                        .GET()
                        .uri(URI.create("http://localhost:" + port + "/protected"))
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );
    }

    // -------------------------------------------------------------------------
    // Test resource
    // -------------------------------------------------------------------------

    @Path("/protected")
    @Produces(MediaType.APPLICATION_JSON)
    public static final class ProtectedResource {
        @GET
        public Response get(@jakarta.ws.rs.core.Context jakarta.ws.rs.core.SecurityContext sc) {
            String name = sc.getUserPrincipal().getName();

            return Response.ok("{\"user\":\"" + name + "\"}").build();
        }
    }
}

