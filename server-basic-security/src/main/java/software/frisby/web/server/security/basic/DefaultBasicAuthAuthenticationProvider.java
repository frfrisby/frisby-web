package software.frisby.web.server.security.basic;

import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import software.frisby.web.server.AuthenticatedIdentity;
import software.frisby.web.server.ServerSecurityContext;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;

/**
 * Default implementation of {@link BasicAuthAuthenticationProvider}.
 * <p>
 * Accepts requests that carry an {@code Authorization: Basic <base64>} header.
 * Decodes the header value (Base64, UTF-8), splits on the first {@code :} to separate
 * username and password, then delegates to the caller-supplied {@link CredentialsValidator}.
 * <p>
 * <strong>Accepts:</strong> any request with an {@code Authorization} header whose value
 * starts with {@code "Basic "} (case-insensitive prefix check).
 * <p>
 * <strong>Authentication scheme:</strong> the returned {@link SecurityContext} reports
 * {@link SecurityContext#BASIC_AUTH}.
 * <p>
 * The password char array is zeroed immediately after the {@link CredentialsValidator}
 * returns or throws, so sensitive credential data is not held in memory longer than
 * necessary.
 */
final class DefaultBasicAuthAuthenticationProvider implements BasicAuthAuthenticationProvider {
    private static final String BASIC_PREFIX = "basic ";
    private static final String AUTHORIZATION_HEADER = "Authorization";

    private final CredentialsValidator validator;

    DefaultBasicAuthAuthenticationProvider(CredentialsValidator validator) {
        this.validator = validator;
    }

    @Override
    public boolean accepts(ContainerRequestContext context) {
        String header = context.getHeaderString(AUTHORIZATION_HEADER);
        if (null == header) {
            return false;
        }

        return header.toLowerCase(java.util.Locale.ROOT).startsWith(BASIC_PREFIX);
    }

    @Override
    public SecurityContext authenticate(ContainerRequestContext context) {
        String header = context.getHeaderString(AUTHORIZATION_HEADER);

        // Extract the Base64-encoded credentials part after "Basic "
        String encoded = header.substring(BASIC_PREFIX.length()).trim();
        if (encoded.isEmpty()) {
            throw new NotAuthorizedException(
                    Response.status(Response.Status.UNAUTHORIZED).build()
            );
        }

        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(encoded);
        } catch (IllegalArgumentException ex) {
            throw new NotAuthorizedException(
                    Response.status(Response.Status.UNAUTHORIZED).build()
            );
        }

        String credentials = new String(decoded, StandardCharsets.UTF_8);
        int colonIndex = credentials.indexOf(':');

        // colonIndex < 1  → no colon (-1) or empty username (0)
        // colonIndex == credentials.length() - 1  → empty password
        if (colonIndex < 1 || colonIndex == credentials.length() - 1) {
            throw new NotAuthorizedException(
                    Response.status(Response.Status.UNAUTHORIZED).build()
            );
        }

        String username = credentials.substring(0, colonIndex);
        char[] password = credentials.substring(colonIndex + 1).toCharArray();

        AuthenticatedIdentity identity;
        try {
            identity = validator.validate(username, password);
        } finally {
            Arrays.fill(password, '\0');
        }

        boolean isSecure = context.getSecurityContext().isSecure();

        return ServerSecurityContext.of(identity.principal(), identity.roles(), isSecure, SecurityContext.BASIC_AUTH);
    }
}




