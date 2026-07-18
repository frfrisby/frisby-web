package software.frisby.web.server.security.basic;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.*;

import java.io.InputStream;
import java.net.URI;
import java.util.*;

/**
 * Minimal stub implementation of {@link ContainerRequestContext} for unit tests.
 * Only the methods needed by {@link DefaultBasicAuthAuthenticationProvider} are implemented.
 */
final class MockRequestContext implements ContainerRequestContext {
    private final Map<String, String> headers;

    private MockRequestContext(Map<String, String> headers) {
        this.headers = headers;
    }

    static MockRequestContext withHeader(String name, String value) {
        return new MockRequestContext(Map.of(name, value));
    }

    static MockRequestContext noHeaders() {
        return new MockRequestContext(Map.of());
    }

    @Override
    public String getHeaderString(String name) {
        // Case-insensitive header lookup
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(name)) {
                return entry.getValue();
            }
        }

        return null;
    }

    @Override
    public SecurityContext getSecurityContext() {
        return new SecurityContext() {
            @Override
            public java.security.Principal getUserPrincipal() {
                return null;
            }

            @Override
            public boolean isUserInRole(String role) {
                return false;
            }

            @Override
            public boolean isSecure() {
                return false;
            }

            @Override
            public String getAuthenticationScheme() {
                return null;
            }
        };
    }

    // Unimplemented stubs — not needed for these tests.

    @Override public Object getProperty(String name) { return null; }
    @Override public Collection<String> getPropertyNames() { return List.of(); }
    @Override public void setProperty(String name, Object object) { }
    @Override public void removeProperty(String name) { }
    @Override public UriInfo getUriInfo() { return null; }
    @Override public void setRequestUri(URI requestUri) { }
    @Override public void setRequestUri(URI baseUri, URI requestUri) { }
    @Override public Request getRequest() { return null; }
    @Override public String getMethod() { return "GET"; }
    @Override public void setMethod(String method) { }
    @Override public MultivaluedMap<String, String> getHeaders() { return new MultivaluedHashMap<>(); }
    @Override public Date getDate() { return null; }
    @Override public Locale getLanguage() { return null; }
    @Override public MediaType getMediaType() { return null; }
    @Override public List<MediaType> getAcceptableMediaTypes() { return List.of(); }
    @Override public List<Locale> getAcceptableLanguages() { return List.of(); }
    @Override public Map<String, Cookie> getCookies() { return Map.of(); }
    @Override public boolean hasEntity() { return false; }
    @Override public InputStream getEntityStream() { return null; }
    @Override public void setEntityStream(InputStream input) { }
    @Override public void setSecurityContext(SecurityContext context) { }
    @Override public void abortWith(Response response) { }
    @Override public int getLength() { return -1; }
}

