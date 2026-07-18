package software.frisby.web.test;

import org.glassfish.jersey.logging.LoggingFeature;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Factory for a Jersey {@link LoggingFeature} scoped to a test class logger.
 * <p>
 * Configured at {@code FINEST / PAYLOAD_ANY} so every request and response —
 * headers, body, and status — is captured under the test class's logger name.
 * Because the logger is at {@code FINEST}, output is silent in CI unless that
 * specific logger is explicitly enabled; it is always available when debugging.
 *
 * <p>Add to any integration test server via:
 * <pre>{@code
 * .components(TestLogging.forClass(MyTest.class))
 * }</pre>
 */
public final class TestLogging {
    private TestLogging() {
    }

    /**
     * Returns a {@link LoggingFeature} whose output is scoped to
     * {@code testClass.getName()}.
     *
     * @param testClass The test class whose name becomes the logger name.
     * @return A configured {@link LoggingFeature}; never {@code null}.
     */
    public static LoggingFeature forClass(Class<?> testClass) {
        return LoggingFeature.builder()
                .withLogger(Logger.getLogger(testClass.getName()))
                .level(Level.FINEST)
                .verbosity(LoggingFeature.Verbosity.PAYLOAD_ANY)
                .maxEntitySize(4096)
                .build();
    }
}

