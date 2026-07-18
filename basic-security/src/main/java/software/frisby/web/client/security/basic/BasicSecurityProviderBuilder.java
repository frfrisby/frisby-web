package software.frisby.web.client.security.basic;

/**
 * A builder for creating a {@link BasicSecurityProvider} instance.
 * <p>
 * Obtain an instance via {@link BasicSecurityProvider#builder()}.
 *
 * <pre>{@code
 * // Using a Credentials value object
 * BasicSecurityProvider security = BasicSecurityProvider.builder()
 *         .credentials(Credentials.of("alice", "s3cr3t"))
 *         .build();
 *
 * // Using the convenience overload
 * BasicSecurityProvider security = BasicSecurityProvider.builder()
 *         .credentials("alice", "s3cr3t")
 *         .build();
 * }</pre>
 */
public interface BasicSecurityProviderBuilder {
    /**
     * Sets the credentials using a pre-constructed {@link Credentials} value object.
     *
     * @param credentials The credentials to use for authentication.
     * @return This builder instance.
     */
    BasicSecurityProviderBuilder credentials(Credentials credentials);

    /**
     * Sets the credentials directly from a username and password.
     * <p>
     * Convenience overload equivalent to calling
     * {@link #credentials(Credentials) credentials(Credentials.of(username, password))}.
     *
     * @param username The username; must not be blank.
     * @param password The password; must not be blank.
     * @return This builder instance.
     * @throws software.frisby.core.validation.NullValueException  if {@code username} or
     *                                                             {@code password} is {@code null}.
     * @throws software.frisby.core.validation.BlankValueException if {@code username} or
     *                                                             {@code password} is blank.
     */
    BasicSecurityProviderBuilder credentials(String username, String password);

    /**
     * Returns a new {@link BasicSecurityProvider} configured with the supplied credentials.
     *
     * @return A new {@link BasicSecurityProvider} instance.
     * @throws IllegalStateException if no credentials were provided.
     */
    BasicSecurityProvider build();
}

