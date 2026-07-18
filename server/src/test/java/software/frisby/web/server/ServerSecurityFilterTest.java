package software.frisby.web.server;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.Principal;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Integration tests for {@link SecurityRequestFilter} — verifies the first-accepts-wins
 * provider chain, 401 on no matching provider, health check bypass, and
 * {@link ServerSecurityContext} wiring.
 */
class ServerSecurityFilterTest {
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final String VALID_TOKEN = "valid-token";
    private static final String HEADER_X_TOKEN = "X-Token";

    // -------------------------------------------------------------------------
    // Single provider — token-based scheme
    // -------------------------------------------------------------------------

    @Nested
    class SingleProvider {
        private static Server server;
        private static int port;

        @BeforeAll
        static void startServer() {
            server = Server.builder()
                    .configuration(c -> c
                            .port(0)
                            .serializer(new TestJsonSerializer()))
                    .resources(new SecuredResource())
                    .healthCheck()
                    .authentication(new TokenAuthProvider())
                    .components(TestLogging.forClass(SingleProvider.class))
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
            HttpResponse<String> response = get(port, "/secured", VALID_TOKEN);

            assertEquals(200, response.statusCode());
        }

        @Test
        void missingToken_returns401() throws Exception {
            HttpResponse<String> response = getNoToken(port, "/secured");

            assertEquals(401, response.statusCode());
        }

        @Test
        void invalidToken_returns401() throws Exception {
            HttpResponse<String> response = get(port, "/secured", "bad-token");

            assertEquals(401, response.statusCode());
        }

        @Test
        void healthCheck_bypassesAuth() throws Exception {
            HttpResponse<String> response = getNoToken(port, "/health");

            assertEquals(200, response.statusCode());
        }
    }

    // -------------------------------------------------------------------------
    // Multi-provider — first-accepts-wins chain
    // -------------------------------------------------------------------------

    @Nested
    class MultiProvider {
        private static Server server;
        private static int port;

        @BeforeAll
        static void startServer() {
            server = Server.builder()
                    .configuration(c -> c
                            .port(0)
                            .serializer(new TestJsonSerializer()))
                    .resources(new SecuredResource())
                    .authentication(
                            new NeverAcceptProvider(),
                            new TokenAuthProvider()
                    )
                    .components(TestLogging.forClass(MultiProvider.class))
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
        void validToken_secondProviderAccepts_returns200() throws Exception {
            HttpResponse<String> response = get(port, "/secured", VALID_TOKEN);

            assertEquals(200, response.statusCode());
        }

        @Test
        void noProviderAccepts_returns401() throws Exception {
            HttpResponse<String> response = getNoToken(port, "/secured");

            assertEquals(401, response.statusCode());
        }
    }

    // -------------------------------------------------------------------------
    // Provider returns null SecurityContext
    // -------------------------------------------------------------------------

    @Nested
    class AuthenticateReturnsNull {
        private static Server server;
        private static int port;

        @BeforeAll
        static void startServer() {
            server = Server.builder()
                    .configuration(c -> c
                            .port(0)
                            .serializer(new TestJsonSerializer()))
                    .resources(new SecuredResource())
                    .authentication(new NullReturningProvider())
                    .components(TestLogging.forClass(AuthenticateReturnsNull.class))
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
        void nullSecurityContext_returns500() throws Exception {
            HttpResponse<String> response = get(port, "/secured", "any-token");

            assertEquals(500, response.statusCode());
        }
    }

    // -------------------------------------------------------------------------
    // Provider throws — exception wrapping
    // -------------------------------------------------------------------------

    @Nested
    class ProviderThrows {
        private static Server server;
        private static int port;

        @BeforeAll
        static void startServer() {
            server = Server.builder()
                    .configuration(c -> c
                            .port(0)
                            .serializer(new TestJsonSerializer()))
                    .resources(new SecuredResource())
                    .authentication(new ThrowingProvider())
                    .components(TestLogging.forClass(ProviderThrows.class))
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
        void unexpectedException_returns500() throws Exception {
            HttpResponse<String> response = get(port, "/secured", "any-token");

            assertEquals(500, response.statusCode());
        }
    }

    // -------------------------------------------------------------------------
    // SecurityContext propagation — principal and roles
    // -------------------------------------------------------------------------

    @Nested
    class SecurityContextPropagation {
        private static Server server;
        private static int port;

        @BeforeAll
        static void startServer() {
            server = Server.builder()
                    .configuration(c -> c
                            .port(0)
                            .serializer(new TestJsonSerializer()))
                    .resources(new WhoAmIResource())
                    .authentication(new TokenAuthProvider())
                    .components(TestLogging.forClass(SecurityContextPropagation.class))
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
        void securityContext_principalNameReturned() throws Exception {
            HttpResponse<String> response = get(port, "/whoami", VALID_TOKEN);

            assertEquals(200, response.statusCode());
            assertEquals("\"alice\"", response.body());
        }
    }

    // -------------------------------------------------------------------------
    // Helpers — HTTP
    // -------------------------------------------------------------------------

    private static HttpResponse<String> get(int port, String path, String token) throws Exception {
        return HTTP.send(
                HttpRequest.newBuilder()
                        .GET()
                        .uri(URI.create("http://localhost:" + port + path))
                        .header(HEADER_X_TOKEN, token)
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );
    }

    private static HttpResponse<String> getNoToken(int port, String path) throws Exception {
        return HTTP.send(
                HttpRequest.newBuilder()
                        .GET()
                        .uri(URI.create("http://localhost:" + port + path))
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );
    }

    // -------------------------------------------------------------------------
    // Test resources
    // -------------------------------------------------------------------------

    @Path("/secured")
    @Produces(MediaType.APPLICATION_JSON)
    public static final class SecuredResource {
        @GET
        public Response get() {
            return Response.ok("{\"message\":\"ok\"}").build();
        }
    }

    @Path("/whoami")
    @Produces(MediaType.APPLICATION_JSON)
    public static final class WhoAmIResource {
        @GET
        public Response get(@jakarta.ws.rs.core.Context jakarta.ws.rs.core.SecurityContext sc) {
            String name = sc.getUserPrincipal().getName();

            return Response.ok("\"" + name + "\"").build();
        }
    }

    // -------------------------------------------------------------------------
    // Test providers
    // -------------------------------------------------------------------------

    private static final class TokenAuthProvider implements AuthenticationProvider {
        private static final Principal ALICE = () -> "alice";

        @Override
        public boolean accepts(ContainerRequestContext context) {
            return null != context.getHeaderString(HEADER_X_TOKEN);
        }

        @Override
        public SecurityContext authenticate(ContainerRequestContext context) {
            String token = context.getHeaderString(HEADER_X_TOKEN);

            if (!VALID_TOKEN.equals(token)) {
                throw new NotAuthorizedException(
                        Response.status(Response.Status.UNAUTHORIZED).build()
                );
            }

            return ServerSecurityContext.of(ALICE, Set.of("USER"));
        }
    }

    private static final class NullReturningProvider implements AuthenticationProvider {
        @Override
        public boolean accepts(ContainerRequestContext context) {
            return true;
        }

        @Override
        public SecurityContext authenticate(ContainerRequestContext context) {
            return null;
        }
    }

    private static final class NeverAcceptProvider implements AuthenticationProvider {
        @Override
        public boolean accepts(ContainerRequestContext context) {
            return false;
        }

        @Override
        public SecurityContext authenticate(ContainerRequestContext context) {
            throw new IllegalStateException("accepts() returned false — authenticate() must not be called");
        }
    }

    private static final class ThrowingProvider implements AuthenticationProvider {
        @Override
        public boolean accepts(ContainerRequestContext context) {
            return true;
        }

        @Override
        public SecurityContext authenticate(ContainerRequestContext context) {
            throw new RuntimeException("Unexpected provider failure");
        }
    }
}



