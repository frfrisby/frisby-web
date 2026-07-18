package software.frisby.web.client;

import software.frisby.web.client.exception.UriSyntaxException;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Assembles a fully-qualified request {@link URI} from a base URI, a path template,
 * path parameter substitutions, and query parameters.
 * <p>
 * Path parameter values and query parameter names/values are percent-encoded so that
 * special characters (spaces, ampersands, etc.) in caller-supplied values produce a
 * valid URI rather than silently corrupting the request.
 */
final class UriBuilder {
    private static final String CONTEXT_FORMAT =
            "The 'uri' value is invalid.  Context{base='%s', path='%s', query='%s'}.";
    private static final String PLACEHOLDER_FORMAT =
            "The 'path' value is invalid.  The placeholder '{%s}' is not present in the template '%s'.";

    private UriBuilder() {
    }

    /**
     * Assembles a fully-qualified {@link URI} for a request.
     * <p>
     * Path parameter substitution is performed eagerly at the {@code path()} call site
     * on {@code RequestState}; by the time {@code resolve()} is called the path string
     * is fully assembled and ready to use.
     *
     * @param base            The base URI from {@link ClientConfiguration#uri()}.
     * @param path            The resource path; may be {@code null} or blank.
     * @param queryParameters Query parameter name-value pairs; multivalued parameters
     *                        appear as separate entries with the same key; may be empty.
     * @return The assembled URI.
     * @throws UriSyntaxException if the resulting URI is syntactically invalid.
     */
    static URI resolve(URI base,
                       String path,
                       List<Map.Entry<String, String>> queryParameters) {
        String resolvedPath = canonicalizePath(base.getRawPath()) + canonicalizePath(path);
        String query = buildQuery(queryParameters);

        StringBuilder sb = new StringBuilder();

        sb.append(base.getScheme()).append("://").append(base.getRawAuthority());
        sb.append(resolvedPath);

        if (null != query) {
            sb.append("?").append(query);
        }

        String fragment = base.getRawFragment();

        if (null != fragment && !fragment.isBlank()) {
            sb.append("#").append(fragment);
        }

        try {
            return new URI(sb.toString());
        } catch (URISyntaxException ex) {
            throw new UriSyntaxException(
                    String.format(CONTEXT_FORMAT, base, path, query),
                    ex
            );
        }
    }

    /**
     * Substitutes all {@code {name}} placeholders in {@code path} with the corresponding
     * percent-encoded values from {@code parameters}.
     * <p>
     * Package-private to support unit testing.
     */
    static String substitutePath(String path, List<PathParameter> parameters) {
        if (null == path ||
                path.isBlank() ||
                null == parameters ||
                parameters.isEmpty()) {
            return path;
        }

        String result = path;
        for (PathParameter param : parameters) {
            String placeholder = "{" + param.id() + "}";

            if (!result.contains(placeholder)) {
                throw new UriSyntaxException(
                        String.format(PLACEHOLDER_FORMAT, param.id(), path)
                );
            }

            result = result.replace(placeholder, encodePath(param.value()));
        }

        return result;
    }

    /**
     * Normalizes a path segment by ensuring a leading {@code /} and no trailing {@code /}.
     * Returns an empty string for blank input.
     * <p>
     * Package-private to support unit testing.
     */
    static String canonicalizePath(String path) {
        if (null == path ||
                path.isBlank()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();

        if (!path.startsWith("/")) {
            sb.append("/");
        }

        if (path.endsWith("/")) {
            sb.append(path, 0, path.length() - 1);
        } else {
            sb.append(path);
        }

        return sb.toString();
    }

    private static String buildQuery(List<Map.Entry<String, String>> parameters) {
        if (null == parameters ||
                parameters.isEmpty()) {
            return null;
        }

        StringBuilder sb = new StringBuilder();

        for (Map.Entry<String, String> entry : parameters) {
            if (!sb.isEmpty()) {
                sb.append("&");
            }

            sb.append(encodeParam(entry.getKey()))
                    .append("=")
                    .append(encodeParam(entry.getValue()));
        }

        return sb.toString();
    }

    /**
     * Percent-encodes a path segment value.
     * Spaces are encoded as {@code %20} (not {@code +}, which is only correct in
     * {@code application/x-www-form-urlencoded} contexts).
     */
    private static String encodePath(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /**
     * Percent-encodes a query parameter name or value.
     * Spaces are encoded as {@code %20} for RFC 3986 compliance.
     */
    private static String encodeParam(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
