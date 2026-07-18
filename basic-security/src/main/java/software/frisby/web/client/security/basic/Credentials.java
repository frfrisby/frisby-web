package software.frisby.web.client.security.basic;

import software.frisby.core.validation.Strings;

/**
 * Holds the username and password credentials used for HTTP Basic Authentication.
 * <p>
 * Create an instance via the {@link #of(String, String)} factory method or pass
 * username and password directly to
 * {@link BasicSecurityProviderBuilder#credentials(String, String)}.
 *
 * <pre>{@code
 * Credentials credentials = Credentials.of("alice", "s3cr3t");
 * }</pre>
 *
 * @param username The username to authenticate with.
 * @param password The password or secret associated with the username.
 */
public record Credentials(String username, String password) {
    private static final String USERNAME = "username";
    private static final String PASSWORD = "password";

    /**
     * Compact constructor — validates that neither field is blank.
     *
     * @throws software.frisby.core.validation.NullValueException  if {@code username} or
     *                                                             {@code password} is {@code null}.
     * @throws software.frisby.core.validation.BlankValueException if {@code username} or
     *                                                             {@code password} is blank.
     */
    public Credentials {
        Strings.notBlank(USERNAME, username);
        Strings.notBlank(PASSWORD, password);
    }

    /**
     * Returns a new {@link Credentials} instance with the given username and password.
     *
     * @param username The username; must not be blank.
     * @param password The password; must not be blank.
     * @return A new {@link Credentials} instance.
     * @throws software.frisby.core.validation.NullValueException  if {@code username} or
     *                                                             {@code password} is {@code null}.
     * @throws software.frisby.core.validation.BlankValueException if {@code username} or
     *                                                             {@code password} is blank.
     */
    public static Credentials of(String username, String password) {
        return new Credentials(username, password);
    }

    /**
     * Returns a redacted string representation — the password is never included.
     *
     * @return A string of the form {@code Credentials{username=alice, password=****}}.
     */
    @Override
    public String toString() {
        return "Credentials{username=" + username + ", password=****}";
    }
}
