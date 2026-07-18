package software.frisby.web.test.log;

/**
 * A builder for creating an instance of {@link SystemLogVerifier}.
 * Obtain via {@link SystemLogVerifier#builder()}.
 */
public interface SystemLogVerifierBuilder {
    /**
     * Temporarily overrides the log level of the logger bound to {@code clazz} for the
     * duration of the test.  The original level is restored when {@link SystemLogVerifier#close()}
     * is called.
     * <p>
     * Use {@link System.Logger.Level#OFF} to drive the {@code isLoggable()} guards inside the
     * class under test to {@code false}, enabling branch coverage of the level-guard paths.
     *
     * @param clazz The class whose logger level will be overridden.
     * @param level The level to apply for the duration of the test.
     * @return This builder.
     */
    SystemLogVerifierBuilder configure(Class<?> clazz, System.Logger.Level level);

    /**
     * Temporarily overrides the log level of the logger bound to {@code loggerName} for the
     * duration of the test.  The original level is restored when {@link SystemLogVerifier#close()}
     * is called.
     * <p>
     * Use {@link System.Logger.Level#OFF} to drive the {@code isLoggable()} guards inside the
     * class under test to {@code false}, enabling branch coverage of the level-guard paths.
     *
     * @param loggerName The name of the logger whose level will be overridden.
     * @param level      The level to apply for the duration of the test.
     * @return This builder.
     */
    SystemLogVerifierBuilder configure(String loggerName, System.Logger.Level level);

    /**
     * Registers one or more {@link LogExpectation} instances that the verifier will evaluate
     * against captured log events during the test run.
     *
     * @param expectations One or more expectations to register; must not be {@code null}.
     * @return This builder.
     */
    SystemLogVerifierBuilder expect(LogExpectation... expectations);

    /**
     * Returns a new {@link SystemLogVerifier} configured by this builder, and immediately
     * registers it as the active verifier so that log events are routed to it.
     *
     * @return A new, active {@link SystemLogVerifier}; never {@code null}.
     */
    SystemLogVerifier build();
}

