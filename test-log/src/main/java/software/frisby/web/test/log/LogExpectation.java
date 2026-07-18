package software.frisby.web.test.log;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

/**
 * Declares an expected {@link System.Logger} event that the class under test should record during
 * a test run.
 * <p>
 * Create instances via the nested {@link Builder}:
 *
 * <pre>{@code
 * LogExpectation expectation = LogExpectation.builder()
 *         .logger(RequestLogger.class)
 *         .level(System.Logger.Level.INFO)
 *         .predicate(e -> e.message().contains("GET /orders → 200"))
 *         .build();
 * }</pre>
 *
 * @see SystemLogVerifier
 * @see SystemLogVerifierBuilder
 */
public final class LogExpectation {
    private final String loggerName;
    private final System.Logger.Level level;
    private final String failureMessage;
    private final Predicate<SystemLogEvent> predicate;
    private final CountDownLatch latch;

    LogExpectation(String loggerName,
                   System.Logger.Level level,
                   String failureMessage,
                   Predicate<SystemLogEvent> predicate) {
        this.loggerName = loggerName;
        this.level = level;
        this.failureMessage = failureMessage;
        this.predicate = predicate;
        this.latch = new CountDownLatch(1);
    }

    /**
     * Returns a new {@link Builder} for constructing a {@link LogExpectation}.
     *
     * @return A new builder; never {@code null}.
     */
    public static Builder builder() {
        return new Builder();
    }

    String loggerName() {
        return loggerName;
    }

    System.Logger.Level level() {
        return level;
    }

    void test(SystemLogEvent event) {
        if (this.latch.getCount() > 0 && this.predicate.test(event)) {
            this.latch.countDown();
        }
    }

    void await(Duration timeout) {
        boolean satisfied;

        try {
            satisfied = this.latch.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            satisfied = false;
        }

        if (!satisfied) {
            throw new AssertionError(
                    String.format(
                            "%s  Info{logger='%s', level=%s, timeout=%s}",
                            this.failureMessage,
                            this.loggerName,
                            this.level.name(),
                            timeout
                    )
            );
        }
    }

    /**
     * A builder for creating an instance of {@link LogExpectation}.
     * Obtain via {@link LogExpectation#builder()}.
     */
    public static final class Builder {
        private String loggerName;
        private System.Logger.Level level;
        private String failureMessage;
        private Predicate<SystemLogEvent> predicate;

        Builder() {
            this.failureMessage = "The expected log message was not recorded within the timeout period.";
        }

        /**
         * Scopes this expectation to the logger bound to the provided class.
         *
         * @param clazz The class whose logger this expectation targets.
         * @return This builder.
         */
        public Builder logger(Class<?> clazz) {
            return logger(clazz.getName());
        }

        /**
         * Scopes this expectation to the logger with the provided fully-qualified name.
         *
         * @param loggerName The fully-qualified logger name.
         * @return This builder.
         */
        public Builder logger(String loggerName) {
            this.loggerName = loggerName;
            return this;
        }

        /**
         * Sets the log level this expectation targets.
         *
         * @param level The {@link System.Logger.Level} to match.
         * @return This builder.
         */
        public Builder level(System.Logger.Level level) {
            this.level = level;
            return this;
        }

        /**
         * Sets the predicate used to match captured events.  The latch is counted down the first
         * time the predicate returns {@code true}.
         *
         * @param predicate A predicate over {@link SystemLogEvent}.
         * @return This builder.
         */
        public Builder predicate(Predicate<SystemLogEvent> predicate) {
            this.predicate = predicate;
            return this;
        }

        /**
         * Overrides the default failure message shown when the expectation is not satisfied
         * within the timeout period.
         *
         * @param failureMessage A user-friendly description of what was expected.
         * @return This builder.
         */
        public Builder failureMessage(String failureMessage) {
            this.failureMessage = failureMessage;
            return this;
        }

        /**
         * Returns a new {@link LogExpectation} configured by this builder.
         *
         * @return A new {@link LogExpectation}; never {@code null}.
         */
        public LogExpectation build() {
            return new LogExpectation(
                    this.loggerName,
                    this.level,
                    this.failureMessage,
                    this.predicate
            );
        }
    }
}

