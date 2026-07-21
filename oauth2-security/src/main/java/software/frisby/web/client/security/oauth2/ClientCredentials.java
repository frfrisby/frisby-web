package software.frisby.web.client.security.oauth2;

import software.frisby.core.validation.Strings;

/**
 * The OAuth 2.0 client credentials used to obtain an access token from a token endpoint.
 * <p>
 * These credentials identify the client application itself (not an end user) and are used
 * exclusively with the OAuth 2.0 client-credentials grant type.
 * <p>
 * Create an instance via the {@link #of(String, String)} factory method or via the
 * {@link ClientCredentialsSecurityProviderBuilder#credentials(String, String)} convenience
 * overload on the builder.
 *
 * <pre>{@code
 * ClientCredentials credentials = ClientCredentials.of("my-client-id", "my-client-secret");
 *
 * ClientCredentialsSecurityProvider security = ClientCredentialsSecurityProvider.builder()
 *         .tokenEndpoint(URI.create("https://auth.example.com/oauth/token"))
 *         .credentials(credentials)
 *         .serializer(myJsonSerializer)
 *         .build();
 * }</pre>
 *
 * @param clientId     The unique client identifier registered with the authorization server.
 * @param clientSecret The secret associated with the client identifier.
 */
public record ClientCredentials(String clientId, String clientSecret) {
    private static final String CLIENT_ID = "clientId";
    private static final String CLIENT_SECRET = "clientSecret";

    /**
     * Compact constructor — validates that neither field is blank.
     *
     * @param clientId     the client identifier; must not be blank
     * @param clientSecret the client secret; must not be blank
     * @throws software.frisby.core.validation.NullValueException  if either value is {@code null}.
     * @throws software.frisby.core.validation.BlankValueException if either value is blank.
     */
    public ClientCredentials {
        clientId = Strings.notBlank(CLIENT_ID, clientId);
        clientSecret = Strings.notBlank(CLIENT_SECRET, clientSecret);
    }

    /**
     * Creates a new {@link ClientCredentials} instance from the provided client identifier
     * and secret.
     *
     * @param clientId     The unique client identifier; must not be blank.
     * @param clientSecret The client secret; must not be blank.
     * @return A new {@link ClientCredentials} instance.
     * @throws software.frisby.core.validation.NullValueException  if either value is {@code null}.
     * @throws software.frisby.core.validation.BlankValueException if either value is blank.
     */
    public static ClientCredentials of(String clientId, String clientSecret) {
        return new ClientCredentials(clientId, clientSecret);
    }

    /**
     * Returns a string representation that redacts the client secret to prevent
     * accidental exposure in logs.
     *
     * @return A string of the form {@code ClientCredentials{clientId=my-client-id, clientSecret=***}}.
     */
    @Override
    public String toString() {
        return "ClientCredentials{clientId=" + clientId + ", clientSecret=***}";
    }
}

