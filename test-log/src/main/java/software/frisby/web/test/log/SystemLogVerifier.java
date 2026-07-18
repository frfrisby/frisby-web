package software.frisby.web.test.log;

import java.time.Duration;

/**
 * Allows unit tests to capture and assert on {@link System.Logger} messages written by the class
 * under test.
 * <p>
 * Works by attaching a custom handler to the root {@code java.util.logging} logger at
 * {@link java.util.logging.Level#ALL}, which is the backend that {@link System.Logger} delegates
 * to by default.  This means {@code isLoggable()} guards inside the class under test are exercised
 * at full fidelity — no mocking or reflection required.
 * <p>
 * Always use in a try-with-resources block so that the active verifier is deregistered after each
 * test:
 *
 * <pre>{@code
 * try (SystemLogVerifier verifier = SystemLogVerifier.builder()
 *         .expect(LogExpectation.builder()
 *                 .logger(RequestLogger.class)
 *                 .level(System.Logger.Level.INFO)
 *                 .predicate(e -> e.message().contains("GET /orders"))
 *                 .build()
 *         )
 *         .build()) {
 *
 *     // ... exercise the code under test ...
 *
 *     verifier.assertExpectations();
 *     assertEquals(1, verifier.infoCount());
 * }
 * }</pre>
 *
 * @see LogExpectation
 * @see SystemLogVerifierBuilder
 */
public interface SystemLogVerifier extends AutoCloseable {
    /**
     * Returns a new {@link SystemLogVerifierBuilder} for constructing a verifier.
     *
     * @return A new builder; never {@code null}.
     */
    static SystemLogVerifierBuilder builder() {
        return new DefaultSystemLogVerifierBuilder();
    }

    /**
     * Returns the total number of {@link System.Logger.Level#ERROR} messages captured since this
     * verifier was created.
     *
     * @return The error message count.
     */
    int errorCount();

    /**
     * Returns the total number of {@link System.Logger.Level#WARNING} messages captured since this
     * verifier was created.
     *
     * @return The warning message count.
     */
    int warningCount();

    /**
     * Returns the total number of {@link System.Logger.Level#INFO} messages captured since this
     * verifier was created.
     *
     * @return The info message count.
     */
    int infoCount();

    /**
     * Returns the total number of {@link System.Logger.Level#DEBUG} messages captured since this
     * verifier was created.
     *
     * @return The debug message count.
     */
    int debugCount();

    /**
     * Returns the total number of {@link System.Logger.Level#TRACE} messages captured since this
     * verifier was created.
     *
     * @return The trace message count.
     */
    int traceCount();

    /**
     * Asserts that all registered {@link LogExpectation} instances have been satisfied immediately,
     * without waiting.  Suitable for synchronous code where the log call is guaranteed to have
     * occurred before this method is invoked.
     *
     * @throws AssertionError if any expectation has not been satisfied.
     */
    void assertExpectations();

    /**
     * Waits up to {@code timeout} for all registered {@link LogExpectation} instances to be
     * satisfied.  Use this overload when the class under test logs from a background thread.
     *
     * @param timeout The maximum time to wait.
     * @throws AssertionError if any expectation is not satisfied within the timeout.
     */
    void assertExpectations(Duration timeout);

    /**
     * Deregisters this verifier and prints a summary of captured message counts to standard
     * output.  Always call via try-with-resources to guarantee cleanup.
     */
    @Override
    void close();
}

