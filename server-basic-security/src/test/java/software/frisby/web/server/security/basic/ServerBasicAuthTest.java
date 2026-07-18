package software.frisby.web.server.security.basic;

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
import software.frisby.web.server.ServerSecurityContext;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import software.frisby.web.server.AuthenticatedIdentity;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Integration tests for {@link BasicAuthAuthenticationProvider} via a real embedded server.
 * <p>
 * Verifies that:
 * <ul>
 *   <li>A valid {@code Authorization: Basic} header results in a {@code 200 OK}.</li>
 *   <li>A missing {@code Authorization} header results in {@code 401 Unauthorized}.</li>
 *   <li>An invalid password results in {@code 401 Unauthorized}.</li>
 *   <li>The validator throwing {@link ForbiddenException} results in {@code 403 Forbidden}.</li>
 *   <li>The health check endpoint bypasses authentication and returns {@code 200}.</li>
 * </ul>
 */
class ServerBasicAuthTest {
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final String VALID_USERNAME = "alice";
    private static final String VALID_PASSWORD = "s3cr3t";
    private static final String FORBIDDEN_USERNAME = "banned";
    private static final Principal ALICE = () -> "alice";

    private static Server server;
    private static int port;

    @BeforeAll
    static void startServer() {
        CredentialsValidator validator = (username, password) -> {
            if (FORBIDDEN_USERNAME.equals(username)) {
                throw new ForbiddenException();
            }

            if (VALID_USERNAME.equals(username) && VALID_PASSWORD.equals(new String(password))) {
                return AuthenticatedIdentity.of(ALICE);
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
                .authentication(BasicAuthAuthenticationProvider.of(validator))
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
    void validCredentials_returns200() throws Exception {
        HttpResponse<String> response = get(VALID_USERNAME, VALID_PASSWORD);

        assertEquals(200, response.statusCode());
    }

    @Test
    void missingAuthorizationHeader_returns401() throws Exception {
        HttpResponse<String> response = getNoAuth();

        assertEquals(401, response.statusCode());
    }

    @Test
    void wrongPassword_returns401() throws Exception {
        HttpResponse<String> response = get(VALID_USERNAME, "wrong-password");

        assertEquals(401, response.statusCode());
    }

    @Test
    void unknownUser_returns401() throws Exception {
        HttpResponse<String> response = get("nobody", "anypassword");

        assertEquals(401, response.statusCode());
    }

    @Test
    void forbiddenUser_returns403() throws Exception {
        HttpResponse<String> response = get(FORBIDDEN_USERNAME, "any-password");

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
        HttpResponse<String> response = get(FORBIDDEN_USERNAME, "any-password");

        assertEquals("", response.body());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static HttpResponse<String> get(String username, String password) throws Exception {
        String credentials = Base64.getEncoder().encodeToString(
                (username + ":" + password).getBytes(StandardCharsets.UTF_8)
        );

        return HTTP.send(
                HttpRequest.newBuilder()
                        .GET()
                        .uri(URI.create("http://localhost:" + port + "/protected"))
                        .header("Authorization", "Basic " + credentials)
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

