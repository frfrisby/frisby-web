package software.frisby.web.server.security.oauth2;

import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import software.frisby.web.server.AuthenticatedIdentity;
import software.frisby.web.server.ServerSecurityContext;

import java.util.Locale;

/**
 * Default implementation of {@link BearerTokenAuthenticationProvider}.
 */
final class DefaultBearerTokenAuthenticationProvider implements BearerTokenAuthenticationProvider {
    private static final String BEARER_PREFIX = "bearer ";
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_SCHEME = "BEARER";

    private final BearerTokenValidator validator;

    DefaultBearerTokenAuthenticationProvider(BearerTokenValidator validator) {
        this.validator = validator;
    }

    @Override
    public boolean accepts(ContainerRequestContext context) {
        String header = context.getHeaderString(AUTHORIZATION_HEADER);
        if (null == header) {
            return false;
        }

        return header.toLowerCase(Locale.ROOT).startsWith(BEARER_PREFIX);
    }

    @Override
    public SecurityContext authenticate(ContainerRequestContext context) {
        String header = context.getHeaderString(AUTHORIZATION_HEADER);

        String[] fields = header.trim().split("\\s+");
        if (fields.length != 2) {
            throw new NotAuthorizedException(
                    String.format("The '%s' header value is not properly formatted.", AUTHORIZATION_HEADER),
                    Response.status(Response.Status.UNAUTHORIZED).build()
            );
        }

        String token = fields[1];

        AuthenticatedIdentity identity = validator.validate(token);

        boolean isSecure = context.getSecurityContext().isSecure();

        return ServerSecurityContext.of(identity.principal(), identity.roles(), isSecure, BEARER_SCHEME);
    }
}
