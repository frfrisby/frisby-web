package software.frisby.web.client;

import software.frisby.core.validation.Durations;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Determines how long to wait before the next retry attempt.
 * <p>
 * {@code RetryDelay} is a {@link FunctionalInterface} — callers may supply a lambda
 * for custom delay logic.  The built-in factory methods cover the most common
 * strategies:
 *
 * <pre>{@code
 * // Always wait the same amount
 * RetryDelay.fixed(Duration.ofSeconds(2))
 *
 * // Wait grows linearly, capped at 30 s (default): 1 s, 2 s, 3 s, ..., 30 s, 30 s, ...
 * RetryDelay.linear(Duration.ofSeconds(1))
 *
 * // Linear with a custom cap
 * RetryDelay.linear(Duration.ofSeconds(1), Duration.ofSeconds(10))
 *
 * // Wait grows exponentially with jitter, capped at 30 s (default)
 * RetryDelay.exponential(Duration.ofSeconds(1))
 *
 * // Exponential with a custom cap
 * RetryDelay.exponential(Duration.ofMillis(500), Duration.ofSeconds(60))
 * }</pre>
 *
 * @see RetryPolicy
 * @see RetryPolicyBuilder#delay(RetryDelay)
 */
@FunctionalInterface
public interface RetryDelay {
    /**
     * Returns a delay strategy that always waits the same fixed duration.
     *
     * <pre>{@code
     * RetryDelay.fixed(Duration.ofSeconds(5))
     * // attempt 1 → 5 s, attempt 2 → 5 s, attempt 3 → 5 s, ...
     * }</pre>
     *
     * @param delay The fixed wait duration; must be at least 1 millisecond.
     * @return A {@link RetryDelay} that always returns {@code delay}.
     * @throws software.frisby.core.validation.NullValueException            if {@code delay} is {@code null}.
     * @throws software.frisby.core.validation.DurationOutsideRangeException if {@code delay} is less than 1 millisecond.
     */
    static RetryDelay fixed(Duration delay) {
        // A minimum of 1 ms, matching linear()/exponential(), rather than merely
        // "positive" — fixed() doesn't truncate via toMillis() internally, so a
        // sub-millisecond delay wouldn't misbehave mathematically, but allowing one
        // strategy to accept nanosecond precision while the other two reject it would be
        // a surprising inconsistency; a delay that small is not a meaningful retry delay.
        Durations.min("delay", delay, Duration.ofMillis(1));

        return attempt -> delay;
    }

    // -------------------------------------------------------------------------
    // Built-in strategies
    // -------------------------------------------------------------------------

    /**
     * Returns a delay strategy that scales linearly with the attempt number, capped at
     * 30 seconds.
     * <p>
     * The wait grows as {@code baseDelay × attempt}, capped at {@code 30 s} — without a
     * cap, an unexpectedly high {@code attempt} count (e.g. a caller-configured
     * {@code maxAttempts} far larger than intended, or a reconnect loop that keeps
     * escalating for a long time) would otherwise grow the wait without bound.
     *
     * <pre>{@code
     * RetryDelay.linear(Duration.ofSeconds(1))
     * // attempt 1 → 1 s, attempt 2 → 2 s, attempt 3 → 3 s, ... (capped at 30 s)
     * }</pre>
     *
     * @param baseDelay The base duration multiplied by the attempt number; must be at
     *                  least 1 millisecond.
     * @return A {@link RetryDelay} that returns {@code baseDelay × attempt}, capped at
     * 30 seconds.
     * @throws software.frisby.core.validation.NullValueException            if {@code baseDelay} is {@code null}.
     * @throws software.frisby.core.validation.DurationOutsideRangeException if {@code baseDelay} is less than 1 millisecond.
     */
    static RetryDelay linear(Duration baseDelay) {
        return linear(baseDelay, Duration.ofSeconds(30));
    }

    /**
     * Returns a delay strategy that scales linearly with the attempt number, capped at a
     * custom maximum.
     * <p>
     * The wait grows as {@code baseDelay × attempt}, capped at {@code maxDelay}.
     *
     * <pre>{@code
     * RetryDelay.linear(Duration.ofSeconds(1), Duration.ofSeconds(10))
     * // attempt 1 → 1 s, attempt 2 → 2 s, ..., attempt 10 → 10 s, attempt 11 → 10 s, ...
     * }</pre>
     *
     * @param baseDelay The base duration multiplied by the attempt number; must be at
     *                  least 1 millisecond.
     * @param maxDelay  The maximum duration between retries; must be at least 1
     *                  millisecond.
     * @return A {@link RetryDelay} that returns {@code baseDelay × attempt}, capped at
     * {@code maxDelay}.
     * @throws software.frisby.core.validation.NullValueException            if either argument is {@code null}.
     * @throws software.frisby.core.validation.DurationOutsideRangeException if either argument is less than 1 millisecond.
     */
    static RetryDelay linear(Duration baseDelay, Duration maxDelay) {
        // A minimum of 1 ms (rather than merely "positive") is required because both
        // values are converted to milliseconds below via toMillis(); a sub-millisecond
        // Duration (e.g. Duration.ofNanos(500)) is genuinely positive but truncates to 0,
        // which would silently make this strategy a permanent no-op delay instead of
        // failing fast at configuration time.
        Durations.min("baseDelay", baseDelay, Duration.ofMillis(1));
        Durations.min("maxDelay", maxDelay, Duration.ofMillis(1));

        return attempt -> {
            long baseMs = baseDelay.toMillis();
            long maxMs = maxDelay.toMillis();

            // Guarding with a division check before multiplying — rather than multiplying
            // first and clamping the result — avoids ever computing baseMs * attempt when
            // that product could overflow a long for a very large attempt count; once
            // attempt exceeds maxMs / baseMs, the true (unbounded) product is already at or
            // beyond maxMs, so the capped result is maxMs regardless of the exact product.
            long linearMs = attempt > maxMs / baseMs ? maxMs : Math.min(baseMs * attempt, maxMs);

            return Duration.ofMillis(linearMs);
        };
    }

    /**
     * Returns an exponential back-off strategy with random jitter, capped at 30 seconds.
     * <p>
     * The wait grows as {@code baseDelay × 2^(attempt−1)}, capped at {@code 30 s}, with
     * up to 20% random jitter added to spread concurrent retries across clients.
     *
     * <pre>{@code
     * RetryDelay.exponential(Duration.ofSeconds(1))
     * // attempt 1 → ~1 s, attempt 2 → ~2 s, attempt 3 → ~4 s, ...  (capped at ~30 s)
     * }</pre>
     *
     * @param baseDelay The base duration for the first retry; must be at least 1
     *                  millisecond.
     * @return A {@link RetryDelay} with exponential back-off and jitter.
     * @throws software.frisby.core.validation.NullValueException            if {@code baseDelay} is {@code null}.
     * @throws software.frisby.core.validation.DurationOutsideRangeException if {@code baseDelay} is less than 1 millisecond.
     */
    static RetryDelay exponential(Duration baseDelay) {
        return exponential(baseDelay, Duration.ofSeconds(30));
    }

    /**
     * Returns an exponential back-off strategy with random jitter and a custom cap.
     * <p>
     * The wait grows as {@code baseDelay × 2^(attempt−1)}, capped at {@code maxDelay},
     * with up to 20% random jitter added to spread concurrent retries across clients.
     *
     * <pre>{@code
     * RetryDelay.exponential(Duration.ofMillis(500), Duration.ofSeconds(60))
     * // attempt 1 → ~0.5 s, attempt 2 → ~1 s, attempt 3 → ~2 s, ... (capped at ~60 s)
     * }</pre>
     *
     * @param baseDelay The base duration for the first retry; must be at least 1
     *                  millisecond.
     * @param maxDelay  The maximum duration between retries; must be at least 1
     *                  millisecond.
     * @return A {@link RetryDelay} with exponential back-off, jitter, and a cap.
     * @throws software.frisby.core.validation.NullValueException            if either argument is {@code null}.
     * @throws software.frisby.core.validation.DurationOutsideRangeException if either argument is less than 1 millisecond.
     */
    static RetryDelay exponential(Duration baseDelay, Duration maxDelay) {
        // A minimum of 1 ms (rather than merely "positive") is required because both
        // values are converted to milliseconds below via toMillis(); a sub-millisecond
        // Duration (e.g. Duration.ofNanos(500)) is genuinely positive but truncates to 0,
        // which would silently make this strategy a permanent no-op delay instead of
        // failing fast at configuration time.
        Durations.min("baseDelay", baseDelay, Duration.ofMillis(1));
        Durations.min("maxDelay", maxDelay, Duration.ofMillis(1));

        return attempt -> {
            long baseMs = baseDelay.toMillis();
            long maxMs = maxDelay.toMillis();

            // 2^(attempt-1), shift capped to prevent overflow for large attempt counts
            int shift = Math.min(attempt - 1, 20);
            long exponentialMs = baseMs * (1L << shift);
            long cappedMs = Math.min(exponentialMs, maxMs);

            // Add up to 20% random jitter so concurrent clients don't all retry simultaneously.
            // nextDouble() is intentional: nextLong((long)(cappedMs * 0.2d)) would throw
            // IllegalArgumentException when cappedMs is small enough that the bound rounds to 0.
            @SuppressWarnings("java:S2140")
            long jitterMs = (long) (cappedMs * 0.2d * ThreadLocalRandom.current().nextDouble());

            return Duration.ofMillis(cappedMs + jitterMs);
        };
    }

    /**
     * Returns the duration to wait before the next attempt.
     * <p>
     * {@code attempt} is the 1-based number of the attempt that just failed — so
     * after the first failure {@code attempt} is {@code 1}, after the second
     * failure it is {@code 2}, and so on.
     *
     * @param attempt The 1-based attempt number that just failed; always {@code >= 1}.
     * @return The duration to wait; never {@code null} or negative.
     */
    Duration delayFor(int attempt);
}
