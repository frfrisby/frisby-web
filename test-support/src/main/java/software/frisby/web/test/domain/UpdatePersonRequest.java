package software.frisby.web.test.domain;

/**
 * Request body for {@code PUT /persons/{id}} and {@code PATCH /persons/{id}} — updates a person.
 *
 * @param name  The updated display name; may be {@code null} for a partial PATCH.
 * @param email The updated email address; may be {@code null} for a partial PATCH.
 */
public record UpdatePersonRequest(String name, String email) {
}

