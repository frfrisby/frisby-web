package software.frisby.web.server;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import software.frisby.web.test.log.LogExpectation;
import software.frisby.web.test.log.SystemLogVerifier;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for {@link RequestLogger} covering log-level branches that cannot be
 * reached by the integration test suite.
 * <p>
 * The test environment sets the root JUL logger to {@code ALL}, which means TRACE is
 * always enabled.  As a result, conditional guards such as
 * {@code if (LOGGER.isLoggable(TRACE))} and {@code else if (LOGGER.isLoggable(INFO))}
 * always take the {@code true} / first branch.  The {@code false} / fallthrough branches
 * are never executed, which Sonar reports as uncovered.
 * <p>
 * Each test uses {@link SystemLogVerifier#builder()}.configure()} to narrow the logger
 * level for the duration of the test, driving the otherwise-unreachable branch.
 * Level restoration is handled automatically by {@link SystemLogVerifier#close()} via
 * try-with-resources.  Branch correctness — message content, attached exceptions —
 * is validated by the integration tests in {@link ServerFailureLoggingTest} and
 * {@link ServerRoutingTest}.
 */
class RequestLoggerTest {
    private final RequestLogger logger = new RequestLogger();

    // -------------------------------------------------------------------------
    // logRequest — else-if INFO branch and silent branch
    // -------------------------------------------------------------------------

    /**
     * Integration tests always run with TRACE (ALL) enabled, so only the TRACE branch
     * of {@code logRequest()} is reached.  These tests cover the remaining two branches.
     */
    @Nested
    class LogRequest {

        @Test
        void infoLevel_takesInfoBranch() {
            // TRACE (FINEST) is below INFO → isLoggable(TRACE) == false → else-if INFO branch.
            try (SystemLogVerifier verifier = SystemLogVerifier.builder()
                    .configure(RequestLogger.class, System.Logger.Level.INFO)
                    .expect(
                            LogExpectation.builder()
                                    .logger(RequestLogger.class)
                                    .level(System.Logger.Level.INFO)
                                    .predicate(e -> e.message().contains("GET") &&
                                            e.message().contains("/test") &&
                                            e.message().contains("200"))
                                    .failureMessage("Expected INFO one-liner for successful request.")
                                    .build()
                    )
                    .build()) {

                logger.logRequest("GET", "/test", 200, 5L, "");

                verifier.assertExpectations();
                assertEquals(1, verifier.infoCount());
                assertEquals(0, verifier.traceCount());
            }
        }

        @Test
        void loggingDisabled_takesSilentBranch() {
            // Both TRACE and INFO disabled → neither branch fires.
            try (SystemLogVerifier verifier = SystemLogVerifier.builder()
                    .configure(RequestLogger.class, System.Logger.Level.OFF)
                    .build()) {

                logger.logRequest("GET", "/test", 200, 5L, "");

                assertEquals(0, verifier.infoCount());
                assertEquals(0, verifier.traceCount());
            }
        }
    }

    // -------------------------------------------------------------------------
    // logHealthCheck — silent branch (health checks only log at TRACE)
    // -------------------------------------------------------------------------

    @Nested
    class LogHealthCheck {

        @Test
        void infoLevel_takesSilentBranch() {
            // Health check logging is TRACE-only; INFO → silent.
            try (SystemLogVerifier verifier = SystemLogVerifier.builder()
                    .configure(RequestLogger.class, System.Logger.Level.INFO)
                    .build()) {

                logger.logHealthCheck("GET", "/health", 200, 1L);

                assertEquals(0, verifier.traceCount());
                assertEquals(0, verifier.infoCount());
            }
        }
    }

    // -------------------------------------------------------------------------
    // logStarted / logStopped — silent branch (INFO guards)
    // -------------------------------------------------------------------------

    @Nested
    class LogStarted {

        @Test
        void loggingDisabled_takesSilentBranch() {
            try (SystemLogVerifier verifier = SystemLogVerifier.builder()
                    .configure(RequestLogger.class, System.Logger.Level.OFF)
                    .build()) {

                logger.logStarted("http://localhost:8080", "\n  port=8080");

                assertEquals(0, verifier.infoCount());
            }
        }
    }

    @Nested
    class LogStopped {

        @Test
        void loggingDisabled_takesSilentBranch() {
            try (SystemLogVerifier verifier = SystemLogVerifier.builder()
                    .configure(RequestLogger.class, System.Logger.Level.OFF)
                    .build()) {

                logger.logStopped("http://localhost:8080");

                assertEquals(0, verifier.infoCount());
            }
        }
    }

    // -------------------------------------------------------------------------
    // logCapacityRejection — silent branch (WARNING guard)
    // -------------------------------------------------------------------------

    @Nested
    class LogCapacityRejection {

        @Test
        void loggingDisabled_takesSilentBranch() {
            try (SystemLogVerifier verifier = SystemLogVerifier.builder()
                    .configure(RequestLogger.class, System.Logger.Level.OFF)
                    .build()) {

                logger.logCapacityRejection("GET", "/api/orders", 3L);

                assertEquals(0, verifier.warningCount());
            }
        }
    }

    // -------------------------------------------------------------------------
    // logFailureDetail — early-return branch (level not loggable)
    // -------------------------------------------------------------------------

    @Nested
    class LogFailureDetail {

        @Test
        void loggingDisabled_takesEarlyReturnBranch() {
            // When the resolved level (WARNING or ERROR) is not loggable, the method
            // returns immediately without building a log message.
            try (SystemLogVerifier verifier = SystemLogVerifier.builder()
                    .configure(RequestLogger.class, System.Logger.Level.OFF)
                    .build()) {

                logger.logFailureDetail("GET", "/api/orders", 404, 2L, "", null);

                assertEquals(0, verifier.warningCount());
                assertEquals(0, verifier.errorCount());
            }
        }
    }
}
