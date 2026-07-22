package software.frisby.web.client.security.oauth2;

import software.frisby.core.util.StopWatch;
import software.frisby.core.validation.Durations;
import software.frisby.core.validation.Sequences;
import software.frisby.core.validation.Values;
import software.frisby.web.client.exception.AbortedException;
import software.frisby.web.client.exception.ConnectTimeoutException;
import software.frisby.web.client.exception.ReadTimeoutException;
import software.frisby.web.client.exception.TransportException;
import software.frisby.web.client.security.RequestContext;
import software.frisby.web.serial.GenericType;
import software.frisby.web.serial.JsonSerializer;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Package-private implementation of {@link ClientCredentialsSecurityProvider}.
 * <p>
 * Uses the JDK {@link HttpClient} directly to call the token endpoint -- deliberately
 * independent of the {@code client} module's {@code HttpEngine} to avoid any circular
 * concern between the HTTP client and its security provider.
 * <p>
 * Thread safety: token acquisition and refresh are guarded by a {@code synchronized}
 * block on a private lock object.  Exactly one thread performs the token fetch; all
 * others wait and then reuse the result.
 */
final class DefaultClientCredentialsSecurityProvider implements ClientCredentialsSecurityProvider {
    private static final System.Logger LOGGER =
            System.getLogger(DefaultClientCredentialsSecurityProvider.class.getName());

    private static final String AUTHORIZATION = "Authorization";
    private static final String BASIC_PREFIX = "Basic ";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String CONTENT_TYPE = "Content-Type";
    private static final String FORM_URLENCODED = "application/x-www-form-urlencoded";
    private static final String GRANT_TYPE = "grant_type";
    private static final String GRANT_VALUE = "client_credentials";
    private static final String CLIENT_ID_FIELD = "client_id";
    private static final String CLIENT_SECRET_FIELD = "client_secret";
    private static final String SCOPE_FIELD = "scope";
    private static final String ACCESS_TOKEN_FIELD = "access_token";
    private static final String EXPIRES_IN_FIELD = "expires_in";
    private static final String POST = "POST";

    private final URI tokenEndpoint;
    private final String requestBody;
    private final String basicAuthHeader;
    private final long expiryBufferSeconds;
    private final Duration requestTimeout;
    private final JsonSerializer serializer;
    private final TokenEventListener eventListener;
    private final HttpClient httpClient;
    private final Object syncRoot = new Object();

    private AccessToken token;

    @SuppressWarnings("java:S107") // all parameters are required; the builder pattern on the public API keeps call sites clean
    DefaultClientCredentialsSecurityProvider(URI tokenEndpoint,
                                             ClientCredentials credentials,
                                             JsonSerializer serializer,
                                             List<String> scopes,
                                             Duration connectTimeout,
                                             Duration requestTimeout,
                                             SSLContext sslContext,
                                             TokenEventListener eventListener,
                                             boolean basicAuth,
                                             Duration expiryBuffer) {
        this.tokenEndpoint = Values.notNull("tokenEndpoint", tokenEndpoint);
        this.serializer = Values.notNull("serializer", serializer);
        this.requestBody = buildRequestBody(Values.notNull("credentials", credentials), Sequences.notNull("scopes", scopes), basicAuth);
        this.basicAuthHeader = basicAuth
                ? BASIC_PREFIX + Base64.getEncoder().encodeToString(
                        (credentials.clientId() + ":" + credentials.clientSecret()).getBytes(StandardCharsets.UTF_8))
                : null;
        this.expiryBufferSeconds = Durations.positive("expiryBuffer", expiryBuffer).getSeconds();
        this.requestTimeout = Durations.positive("requestTimeout", requestTimeout);
        this.eventListener = Values.notNull("eventListener", eventListener);
        this.httpClient = buildHttpClient(Durations.positive("connectTimeout", connectTimeout), sslContext);

        this.token = null;
    }

    private static HttpClient buildHttpClient(Duration connectTimeout, SSLContext sslContext) {
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(connectTimeout);

        if (null != sslContext) {
            builder.sslContext(sslContext);
        }

        return builder.build();
    }

    private static String buildRequestBody(ClientCredentials credentials, List<String> scopes, boolean basicAuth) {
        StringJoiner joiner = new StringJoiner("&");

        joiner.add(encode(GRANT_TYPE) + "=" + encode(GRANT_VALUE));

        if (!basicAuth) {
            joiner.add(encode(CLIENT_ID_FIELD) + "=" + encode(credentials.clientId()));
            joiner.add(encode(CLIENT_SECRET_FIELD) + "=" + encode(credentials.clientSecret()));
        }

        if (!scopes.isEmpty()) {
            String scopeValue = String.join(" ", scopes);

            joiner.add(encode(SCOPE_FIELD) + "=" + encode(scopeValue));
        }

        return joiner.toString();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    @Override
    public void secure(RequestContext request) {
        request.addHeader(AUTHORIZATION, BEARER_PREFIX + currentToken().value);
    }

    private AccessToken currentToken() {
        Instant now = Instant.now();

        synchronized (syncRoot) {
            if (null == token || now.isAfter(token.expiresAt)) {
                token = fetchToken(now);
            }
        }

        return token;
    }

    @SuppressWarnings("java:S1141") // inner try is intentional: fires event listener before rethrowing parseAccessToken failures
    private AccessToken fetchToken(Instant now) {
        HttpRequest request = buildHttpRequest();
        StopWatch watch = StopWatch.start();

        try {
            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );

            watch.stop();

            Duration latency = watch.duration();

            if (response.statusCode() != 200) {
                TokenRequestLogger.logTokenError(request, response, latency);

                TokenEndpointException ex = new TokenEndpointException(
                        tokenEndpoint,
                        response.statusCode(),
                        response.body()
                );

                fireTokenFetchFailed(latency, ex);
                throw ex;
            }

            TokenRequestLogger.logSuccess(request, response, latency);

            AccessToken result;

            try {
                result = parseAccessToken(response.body(), now);
            } catch (RuntimeException ex) {
                fireTokenFetchFailed(latency, ex);
                throw ex;
            }

            fireTokenFetched(latency);
            return result;
        } catch (HttpConnectTimeoutException ex) {
            watch.stop();

            Duration latency = watch.duration();
            ConnectTimeoutException wrapped = new ConnectTimeoutException(ex, POST, tokenEndpoint);

            TokenRequestLogger.logTransportError(request, wrapped);
            fireTokenFetchFailed(latency, wrapped);

            throw wrapped;
        } catch (HttpTimeoutException ex) {
            watch.stop();

            Duration latency = watch.duration();
            ReadTimeoutException wrapped = new ReadTimeoutException(ex, POST, tokenEndpoint);

            TokenRequestLogger.logTransportError(request, wrapped);
            fireTokenFetchFailed(latency, wrapped);

            throw wrapped;
        } catch (java.net.ConnectException ex) {
            watch.stop();

            Duration latency = watch.duration();
            software.frisby.web.client.exception.ConnectException wrapped =
                    new software.frisby.web.client.exception.ConnectException(ex, POST, tokenEndpoint);

            TokenRequestLogger.logTransportError(request, wrapped);
            fireTokenFetchFailed(latency, wrapped);

            throw wrapped;
        } catch (IOException ex) {
            watch.stop();

            Duration latency = watch.duration();
            TransportException wrapped = new TransportException(ex, POST, tokenEndpoint);

            TokenRequestLogger.logTransportError(request, wrapped);
            fireTokenFetchFailed(latency, wrapped);

            throw wrapped;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();

            watch.stop();

            Duration latency = watch.duration();
            AbortedException wrapped = new AbortedException(ex, POST, tokenEndpoint);

            TokenRequestLogger.logTransportError(request, wrapped);
            fireTokenFetchFailed(latency, wrapped);

            throw wrapped;
        } finally {
            watch.stop();
        }
    }

    private HttpRequest buildHttpRequest() {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(tokenEndpoint)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .header(CONTENT_TYPE, FORM_URLENCODED)
                .timeout(requestTimeout);

        if (null != basicAuthHeader) {
            builder.header(AUTHORIZATION, basicAuthHeader);
        }

        return builder.build();
    }

    private AccessToken parseAccessToken(String responseBody, Instant now) {
        Map<String, Object> body = serializer.deserialize(
                responseBody.getBytes(StandardCharsets.UTF_8),
                new GenericType<>() {
                }
        );

        String accessToken = (String) body.get(ACCESS_TOKEN_FIELD);

        if (null == accessToken || accessToken.isBlank()) {
            throw new TokenEndpointException(
                    tokenEndpoint,
                    0,
                    "The response did not contain an 'access_token' field."
            );
        }

        Number expiresIn = (Number) body.get(EXPIRES_IN_FIELD);
        long expirySeconds = null != expiresIn ? expiresIn.longValue() : 0L;

        Instant expiresAt = expirySeconds > expiryBufferSeconds
                ? now.plusSeconds(expirySeconds - expiryBufferSeconds)
                : now.plusSeconds(expirySeconds);

        return new AccessToken(expiresAt, accessToken);
    }

    private void fireTokenFetched(Duration latency) {
        try {
            eventListener.onTokenFetched(latency);
        } catch (Exception ex) {
            LOGGER.log(
                    System.Logger.Level.WARNING,
                    "TokenEventListener.onTokenFetched threw an unexpected exception",
                    ex
            );
        }
    }

    private void fireTokenFetchFailed(Duration latency, Throwable cause) {
        try {
            eventListener.onTokenFetchFailed(latency, cause);
        } catch (Exception ex) {
            LOGGER.log(
                    System.Logger.Level.WARNING,
                    "TokenEventListener.onTokenFetchFailed threw an unexpected exception",
                    ex
            );
        }
    }

    private record AccessToken(Instant expiresAt, String value) {
    }
}
