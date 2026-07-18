package software.frisby.web.client;

import software.frisby.core.validation.Sequences;
import software.frisby.core.validation.Strings;
import software.frisby.core.validation.Values;
import software.frisby.web.client.exception.HttpResponseException;
import software.frisby.web.client.exception.UriSyntaxException;
import software.frisby.web.client.security.RequestContext;
import software.frisby.web.client.security.SecurityProvider;

import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Holds the mutable navigation state shared by all HTTP verb request implementations
 * and provides the common request-building logic.
 * <p>
 * Each verb class ({@link GetRequest}, {@link DeleteRequest}, etc.) holds one
 * {@code RequestState} instance and delegates all navigation calls to it, keeping
 * the verb classes focused on their execution-specific behavior.
 */
final class RequestState {
    private static final String COOKIE_HEADER = "Cookie";
    private static final String PATH = "path";
    private static final String PARAMETERS = "parameters";
    private static final String NAME = "name";
    private static final String VALUE = "value";
    private static final String VALUES = "values";
    private static final String COOKIE = "cookie";
    private static final String PROVIDER = "provider";

    private static final Set<String> RESTRICTED_HEADERS = Set.of(
            "accept",
            "accept-encoding",
            "content-type",
            "content-length",
            "content-encoding",
            "transfer-encoding"
    );

    private final SecurityProvider defaultSecurity;
    private final List<Map.Entry<String, String>> queryParameters;
    private final List<Map.Entry<String, String>> headers;
    private final List<HttpCookie> cookies;
    private String path;
    private SecurityProvider security;

    RequestState(SecurityProvider defaultSecurity) {
        this.defaultSecurity = defaultSecurity;
        this.path = null;
        this.queryParameters = new ArrayList<>();
        this.headers = new ArrayList<>();
        this.cookies = new ArrayList<>();
        this.security = null;
    }

    /**
     * Returns a {@link HttpResponse.BodyHandler} for {@code Void} responses.
     * <p>
     * On error ({@code 4xx} / {@code 5xx}), reads the body as a string and throws the
     * appropriate {@link HttpResponseException}.
     * On success, discards the response body.
     *
     * @param method The HTTP method, used to populate the exception context.
     * @param uri    The request URI, used to populate the exception context.
     * @return A body handler that produces {@code Void}.
     */
    static HttpResponse.BodyHandler<Void> voidBodyHandler(String method, URI uri) {
        return responseInfo -> {
            if (ExceptionFactory.isError(responseInfo.statusCode())) {
                return HttpResponse.BodySubscribers.mapping(
                        HttpResponse.BodySubscribers.ofString(StandardCharsets.UTF_8),
                        (Function<String, Void>) body -> {
                            String errorBody = body.isBlank() ? null : body;

                            throw ExceptionFactory.create(
                                    errorBody,
                                    method,
                                    uri,
                                    responseInfo.statusCode(),
                                    responseInfo.headers()
                            );
                        }
                );
            }

            return HttpResponse.BodySubscribers.discarding();
        };
    }

    private static void checkNotRestricted(String name) {
        if (RESTRICTED_HEADERS.contains(name.toLowerCase())) {
            throw new IllegalArgumentException(
                    "The 'header' value of '" + name + "' is invalid." +
                            "  This header is managed by the client and cannot be set directly."
            );
        }
    }

    void path(String path) {
        this.path = Strings.notBlank(PATH, path);
    }

    /**
     * @throws UriSyntaxException if {@code parameterId} does not correspond
     *                            to a {@code {name}} placeholder in {@code path}.
     */
    void path(String path, String parameterId, String parameterValue) {
        path(path, PathParameter.of(parameterId, parameterValue));
    }

    /**
     * @throws UriSyntaxException if any element of {@code parameters} does not
     *                            correspond to a {@code {name}} placeholder in {@code path}.
     */
    void path(String path, PathParameter... parameters) {
        Values.notNull(PARAMETERS, parameters);

        if (parameters.length > 0) {
            this.path = UriBuilder.substitutePath(
                    Strings.notBlank(PATH, path),
                    List.of(parameters)
            );
        } else {
            this.path = Strings.notBlank(PATH, path);
        }
    }

    void parameter(String name, String value) {
        this.queryParameters.add(Map.entry(Strings.notBlank(NAME, name), Strings.notBlank(VALUE, value)));
    }

    void parameter(String name, String... values) {
        Strings.notBlank(NAME, name);
        Sequences.notEmpty(VALUES, values);

        for (String value : values) {
            this.queryParameters.add(Map.entry(name, Strings.notBlank(VALUE, value)));
        }
    }

    void header(String name, String value) {
        checkNotRestricted(Strings.notBlank(NAME, name));
        this.headers.add(Map.entry(name, Strings.notBlank(VALUE, value)));
    }

    void header(String name, String... values) {
        checkNotRestricted(Strings.notBlank(NAME, name));
        Sequences.notEmpty(VALUES, values);

        for (String value : values) {
            this.headers.add(Map.entry(name, Strings.notBlank(VALUE, value)));
        }
    }

    void cookie(HttpCookie cookie) {
        this.cookies.add(Values.notNull(COOKIE, cookie));
    }

    void security(SecurityProvider provider) {
        this.security = Values.notNull(PROVIDER, provider);
    }

    /**
     * Resolves the final request URI from the base URI, the accumulated path,
     * and query parameters.
     *
     * @param baseUri The base URI from {@link ClientConfiguration#uri()}.
     * @return The fully resolved request URI.
     */
    URI resolveUri(URI baseUri) {
        return UriBuilder.resolve(baseUri, path, queryParameters);
    }

    /**
     * Assembles an {@link HttpRequest.Builder} from the accumulated state, ready for any
     * additional headers (e.g. {@code Content-Type}) to be applied before calling
     * {@link HttpRequest.Builder#build()}.
     * <p>
     * Body-bearing verb implementations ({@code PostRequest}, {@code PutRequest},
     * {@code PatchRequest}) use this method so they can add content headers before
     * finalizing the request.  Bodiless verb implementations call the convenience wrapper
     * {@link #buildRequest} instead.
     *
     * @param uri            The fully resolved request URI.
     * @param method         The HTTP method string (e.g. {@code "GET"}, {@code "POST"}).
     * @param bodyPublisher  The request body publisher; use
     *                       {@link HttpRequest.BodyPublishers#noBody()} for bodiless requests.
     * @param addAcceptJson  Whether to add {@code Accept: application/json}.
     * @param acceptEncoding The {@code Accept-Encoding} header value to add, or {@code null}
     *                       to omit the header.
     * @param readTimeout    The per-request read timeout from {@link ClientConfiguration#readTimeout()}.
     * @return A configured {@link HttpRequest.Builder}; call {@link HttpRequest.Builder#build()}
     * to produce the final request.
     */
    HttpRequest.Builder prepareBuilder(URI uri,
                                       String method,
                                       HttpRequest.BodyPublisher bodyPublisher,
                                       boolean addAcceptJson,
                                       String acceptEncoding,
                                       Duration readTimeout) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(uri)
                .method(method, bodyPublisher)
                .timeout(readTimeout)
                .header(Headers.USER_AGENT, "");

        if (addAcceptJson) {
            builder.header(Headers.ACCEPT, "application/json");
        }

        if (null != acceptEncoding) {
            builder.header(Headers.ACCEPT_ENCODING, acceptEncoding);
        }

        for (Map.Entry<String, String> entry : headers) {
            builder.header(entry.getKey(), entry.getValue());
        }

        List<HttpCookie> allCookies = new ArrayList<>(cookies);

        SecurityProvider effectiveSecurity = null != security ? security : defaultSecurity;

        if (null != effectiveSecurity) {
            effectiveSecurity.secure(new DefaultRequestContext(builder, allCookies));
        }

        if (!allCookies.isEmpty()) {
            String cookieValue = allCookies.stream()
                    .map(c -> c.getName() + "=" + c.getValue())
                    .collect(Collectors.joining("; "));

            builder.header(COOKIE_HEADER, cookieValue);
        }

        return builder;
    }

    /**
     * Assembles the {@link HttpRequest} from the accumulated state.
     * <p>
     * Convenience wrapper around {@link #prepareBuilder} for bodiless verb
     * implementations that do not need to add content headers.
     *
     * @param uri            The fully resolved request URI.
     * @param method         The HTTP method string (e.g. {@code "GET"}, {@code "POST"}).
     * @param bodyPublisher  The request body publisher; use
     *                       {@link HttpRequest.BodyPublishers#noBody()} for bodiless requests.
     * @param addAcceptJson  Whether to add {@code Accept: application/json}.
     * @param acceptEncoding The {@code Accept-Encoding} header value to add, or {@code null}
     *                       to omit the header.
     * @param readTimeout    The per-request read timeout from {@link ClientConfiguration#readTimeout()}.
     * @return The fully configured {@link HttpRequest}.
     */
    HttpRequest buildRequest(URI uri,
                             String method,
                             HttpRequest.BodyPublisher bodyPublisher,
                             boolean addAcceptJson,
                             String acceptEncoding,
                             Duration readTimeout) {
        return prepareBuilder(uri, method, bodyPublisher, addAcceptJson, acceptEncoding, readTimeout)
                .build();
    }

    private record DefaultRequestContext(HttpRequest.Builder builder,
                                         List<HttpCookie> cookies) implements RequestContext {
        @Override
        public void addHeader(String name, String value) {
            builder.header(name, value);
        }

        @Override
        public void addCookie(HttpCookie cookie) {
            cookies.add(cookie);
        }
    }
}
