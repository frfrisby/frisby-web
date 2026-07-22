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
 * // Wait grows linearly: 1 s, 2 s, 3 s, ...
 * RetryDelay.linear(Duration.ofSeconds(1))
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
     * @param delay The fixed wait duration; must not be {@code null}.
     * @return A {@link RetryDelay} that always returns {@code delay}.
     * @throws software.frisby.core.validation.NullValueException            if {@code delay} is {@code null}.
     * @throws software.frisby.core.validation.DurationOutsideRangeException if {@code delay} is zero or negative.
     */
    static RetryDelay fixed(Duration delay) {
        Durations.positive("delay", delay);

        return attempt -> delay;
    }

    // -------------------------------------------------------------------------
    // Built-in strategies
    // -------------------------------------------------------------------------

    /**
     * Returns a delay strategy that scales linearly with the attempt number.
     *
     * <pre>{@code
     * RetryDelay.linear(Duration.ofSeconds(1))
     * // attempt 1 → 1 s, attempt 2 → 2 s, attempt 3 → 3 s, ...
     * }</pre>
     *
     * @param baseDelay The base duration multiplied by the attempt number; must not be
     *                  {@code null}.
     * @return A {@link RetryDelay} that returns {@code baseDelay × attempt}.
     * @throws software.frisby.core.validation.NullValueException            if {@code baseDelay} is {@code null}.
     * @throws software.frisby.core.validation.DurationOutsideRangeException if {@code baseDelay} is zero or negative.
     */
    static RetryDelay linear(Duration baseDelay) {
        Durations.positive("baseDelay", baseDelay);

        return baseDelay::multipliedBy;
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
     * @param baseDelay The base duration for the first retry; must not be {@code null}.
     * @return A {@link RetryDelay} with exponential back-off and jitter.
     * @throws software.frisby.core.validation.NullValueException            if {@code baseDelay} is {@code null}.
     * @throws software.frisby.core.validation.DurationOutsideRangeException if {@code baseDelay} is zero or negative.
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
     * @param baseDelay The base duration for the first retry; must not be {@code null}.
     * @param maxDelay  The maximum duration between retries; must not be {@code null}.
     * @return A {@link RetryDelay} with exponential back-off, jitter, and a cap.
     * @throws software.frisby.core.validation.NullValueException            if either argument is {@code null}.
     * @throws software.frisby.core.validation.DurationOutsideRangeException if either argument is zero or negative.
     */
    static RetryDelay exponential(Duration baseDelay, Duration maxDelay) {
        Durations.positive("baseDelay", baseDelay);
        Durations.positive("maxDelay", maxDelay);

        return attempt -> {
            long baseMs = baseDelay.toMillis();
            long maxMs = maxDelay.toMillis();

            // 2^(attempt-1), shift capped to prevent overflow for large attempt counts
            int shift = Math.min(attempt - 1, 20);
            long exponentialMs = baseMs * (1L << shift);
            long cappedMs = Math.min(exponentialMs, maxMs);

            // Add up to 20% random jitter so concurrent clients don't all retry simultaneously
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
