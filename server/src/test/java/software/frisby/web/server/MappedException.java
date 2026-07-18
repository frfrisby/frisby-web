package software.frisby.web.server;

/**
 * A non-{@link jakarta.ws.rs.WebApplicationException} exception used by
 * {@link MappedExceptionMapper} in failure-logging integration tests.
 * <p>
 * Simulates a domain/infrastructure exception (e.g. {@code DatabaseException})
 * that is handled gracefully at the HTTP layer via an {@link jakarta.ws.rs.ext.ExceptionMapper}
 * but still represents a genuine application failure — and must therefore be
 * logged at {@code ERROR}, not {@code WARNING}.
 */
final class MappedException extends RuntimeException {
    MappedException(String message) {
        super(message);
    }
}

