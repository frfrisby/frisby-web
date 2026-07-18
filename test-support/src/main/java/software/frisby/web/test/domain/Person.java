package software.frisby.web.test.domain;

/**
 * A simple domain type used in integration tests for typed GET / POST / PUT / PATCH scenarios.
 *
 * @param id    The person's unique identifier.
 * @param name  The person's display name.
 * @param email The person's email address.
 */
public record Person(String id, String name, String email) {
}

