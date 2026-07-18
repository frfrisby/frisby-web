package software.frisby.web.client.security.oauth2;

import software.frisby.web.serial.JsonSerializer;

import javax.net.ssl.SSLContext;
import java.net.URI;
import java.time.Duration;

/**
 * A builder for creating a {@link ClientCredentialsSecurityProvider} instance.
 * <p>
 * Obtain an instance via {@link ClientCredentialsSecurityProvider#builder()}.
 *
 * <pre>{@code
 * // Minimal -- no scope, default timeouts
 * ClientCredentialsSecurityProvider security = ClientCredentialsSecurityProvider.builder()
 *         .tokenEndpoint(URI.create("https://auth.example.com/oauth/token"))
 *         .credentials(ClientCredentials.of("my-client-id", "my-client-secret"))
 *         .serializer(myJsonSerializer)
 *         .build();
 *
 * // Fully configured
 * ClientCredentialsSecurityProvider security = ClientCredentialsSecurityProvider.builder()
 *         .tokenEndpoint(URI.create("https://auth.example.com/oauth/token"))
 *         .credentials("my-client-id", "my-client-secret")
 *         .serializer(myJsonSerializer)
 *         .scope("read", "write")
 *         .connectTimeout(Duration.ofSeconds(5))
 *         .requestTimeout(Duration.ofSeconds(15))
 *         .sslContext(myCustomSslContext)
 *         .eventListener(myTokenEventListener)
 *         .build();
 * }</pre>
 */
public interface ClientCredentialsSecurityProviderBuilder {
    /**
     * Sets the URI of the token endpoint that will be called to obtain access tokens.
     *
     * @param uri The fully qualified URI of the token endpoint; must not be {@code null}.
     * @return This builder instance.
     * @throws software.frisby.core.validation.NullValueException if {@code uri} is {@code null}.
     */
    ClientCredentialsSecurityProviderBuilder tokenEndpoint(URI uri);

    /**
     * Sets the client credentials using a pre-constructed {@link ClientCredentials} value object.
     *
     * @param credentials The client credentials; must not be {@code null}.
     * @return This builder instance.
     * @throws software.frisby.core.validation.NullValueException if {@code credentials} is {@code null}.
     */
    ClientCredentialsSecurityProviderBuilder credentials(ClientCredentials credentials);

    /**
     * Sets the client credentials directly from a client identifier and secret.
     * <p>
     * Convenience overload equivalent to calling
     * {@link #credentials(ClientCredentials) credentials(ClientCredentials.of(clientId, clientSecret))}.
     *
     * @param clientId     The client identifier; must not be blank.
     * @param clientSecret The client secret; must not be blank.
     * @return This builder instance.
     * @throws software.frisby.core.validation.NullValueException  if either value is {@code null}.
     * @throws software.frisby.core.validation.BlankValueException if either value is blank.
     */
    ClientCredentialsSecurityProviderBuilder credentials(String clientId, String clientSecret);

    /**
     * Sets the {@link JsonSerializer} used to deserialize the token endpoint response.
     * <p>
     * This is typically the same serializer registered with
     * {@link software.frisby.web.client.ClientConfigurationBuilder#serializer(JsonSerializer)}.
     *
     * @param serializer The JSON serializer; must not be {@code null}.
     * @return This builder instance.
     * @throws software.frisby.core.validation.NullValueException if {@code serializer} is {@code null}.
     */
    ClientCredentialsSecurityProviderBuilder serializer(JsonSerializer serializer);

    /**
     * Optionally sets the OAuth 2.0 scopes to request from the token endpoint.
     * <p>
     * When set, the scopes are joined with a single space and included as the
     * {@code scope} parameter in the token request body, per RFC 6749.
     * When not set, the scope parameter is omitted and the authorization server
     * assigns its default scopes.
     *
     * @param scopes One or more scope strings; must not be empty or contain blank values.
     * @return This builder instance.
     * @throws software.frisby.core.validation.NullValueException       if {@code scopes} is {@code null}.
     * @throws software.frisby.core.validation.MissingElementsException if {@code scopes} is empty.
     * @throws software.frisby.core.validation.NullElementException     if {@code scopes} contains any {@code null} element.
     * @throws software.frisby.core.validation.BlankValueException      if {@code scopes} contains any blank string element.
     */
    ClientCredentialsSecurityProviderBuilder scope(String... scopes);

    /**
     * Sets the maximum time to wait for a TCP connection to be established to the
     * token endpoint.
     * <p>
     * Optional; defaults to {@code 10} seconds.
     *
     * @param timeout The connect timeout; must not be {@code null} and must be positive.
     * @return This builder instance.
     * @throws software.frisby.core.validation.NullValueException            if {@code timeout} is {@code null}.
     * @throws software.frisby.core.validation.DurationOutsideRangeException if {@code timeout} is zero or negative.
     */
    ClientCredentialsSecurityProviderBuilder connectTimeout(Duration timeout);

    /**
     * Sets the maximum time to wait for a complete response from the token endpoint
     * after the request has been sent.
     * <p>
     * Optional; defaults to {@code 30} seconds.
     *
     * @param timeout The request timeout; must not be {@code null} and must be positive.
     * @return This builder instance.
     * @throws software.frisby.core.validation.NullValueException            if {@code timeout} is {@code null}.
     * @throws software.frisby.core.validation.DurationOutsideRangeException if {@code timeout} is zero or negative.
     */
    ClientCredentialsSecurityProviderBuilder requestTimeout(Duration timeout);

    /**
     * Sets a custom {@link SSLContext} for non-standard TLS configurations such as
     * a private CA trust store or mutual TLS client certificate.
     * <p>
     * Optional; defaults to the JDK default {@link SSLContext}.
     *
     * @param sslContext The SSL context to use; must not be {@code null}.
     * @return This builder instance.
     * @throws software.frisby.core.validation.NullValueException if {@code sslContext} is {@code null}.
     */
    ClientCredentialsSecurityProviderBuilder sslContext(SSLContext sslContext);

    /**
     * Configures the provider to send client credentials as an HTTP Basic Auth header
     * ({@code Authorization: Basic base64(clientId:clientSecret)}) instead of as
     * {@code client_id} and {@code client_secret} form body parameters.
     * <p>
     * Use this when the token endpoint requires the {@code client_secret_basic}
     * authentication method (RFC 6749 §2.3.1).
     * <p>
     * Optional; defaults to {@code client_secret_post} (credentials in the request body).
     *
     * @return This builder instance.
     */
    ClientCredentialsSecurityProviderBuilder basicAuth();

    /**
     * Sets how early to treat a cached token as expired and trigger a proactive refresh.
     * <p>
     * A token with {@code expires_in = N} seconds is treated as expired after
     * {@code N - buffer} seconds, guarding against clock skew between the client
     * and the authorization server and against tokens that expire mid-flight.
     * <p>
     * Optional; defaults to {@code 30} seconds.
     *
     * @param buffer The expiry buffer; must be positive and not {@code null}.
     * @return This builder instance.
     * @throws software.frisby.core.validation.NullValueException            if {@code buffer} is {@code null}.
     * @throws software.frisby.core.validation.DurationOutsideRangeException if {@code buffer} is zero or negative.
     */
    ClientCredentialsSecurityProviderBuilder expiryBuffer(Duration buffer);

    /**
     * Sets the {@link TokenEventListener} that receives notifications after every
     * token fetch attempt.
     * <p>
     * Optional; defaults to a no-op listener.
     *
     * @param listener The event listener; must not be {@code null}.
     * @return This builder instance.
     */
    ClientCredentialsSecurityProviderBuilder eventListener(TokenEventListener listener);

    /**
     * Returns a new {@link ClientCredentialsSecurityProvider} configured with the
     * supplied options.
     *
     * @return A new {@link ClientCredentialsSecurityProvider} instance.
     * @throws IllegalStateException if {@code tokenEndpoint}, {@code credentials}, or
     *                               {@code serializer} were not provided.
     */
    ClientCredentialsSecurityProvider build();
}
