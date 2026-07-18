package software.frisby.web.test.domain;

/**
 * Request body for {@code POST /persons} — creates a new person.
 *
 * @param name  The person's display name.
 * @param email The person's email address.
 */
public record CreatePersonRequest(String name, String email) {
}

