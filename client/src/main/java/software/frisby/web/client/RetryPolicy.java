package software.frisby.web.client;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;

/**
 * Determines whether and how long to wait before retrying a failed request.
 * <p>
 * The built-in builder covers the most common use cases:
 *
 * <pre>{@code
 * RetryPolicy policy = RetryPolicy.builder()
 *         .maxAttempts(3)
 *         .on(RetryPolicy.GATEWAY_ERRORS)
 *         .on(RetryOn.TOO_MANY_REQUESTS)
 *         .delay(RetryDelay.exponential(Duration.ofSeconds(1)))
 *         .honorRetryAfterHeader(Duration.ofSeconds(60))
 *         .build();
 *
 * Client client = Client.builder()
 *         .configuration(config)
 *         .retryPolicy(policy)
 *         .build();
 * }</pre>
 *
 * <h2>Custom implementation</h2>
 * <p>
 * If the builder does not cover your needs, implement this interface directly:
 *
 * <pre>{@code
 * public class MyRetryPolicy implements RetryPolicy {
 *     @Override
 *     public Optional<Duration> retryDelay(int attempt, RuntimeException failure) {
 *         if (attempt >= 3) return Optional.empty();
 *         if (failure instanceof ServiceUnavailableException) {
 *             return Optional.of(Duration.ofSeconds(attempt * 5L));
 *         }
 *         return Optional.empty();
 *     }
 * }
 * }</pre>
 *
 * <h2>Idempotency</h2>
 * <p>
 * By default, retries are only attempted for idempotent HTTP methods ({@code GET},
 * {@code HEAD}, {@code DELETE}).  Call {@link RetryPolicyBuilder#allowNonIdempotent()}
 * to also retry {@code POST}, {@code PUT}, and {@code PATCH} — only do this when you
     * are certain those operations are safe to execute more than once.  Requests with a
     * multipart form body are never retried regardless of this setting, because the body
     * is streamed and cannot be replayed after the first attempt.
 *
 * @see RetryPolicyBuilder
 * @see RetryDelay
 * @see RetryOn
 */
public interface RetryPolicy {

    /**
     * Convenience constant for the three gateway-level transient errors — the most
     * common targets for retry in load-balanced service-to-service communication.
     *
     * <p>Includes: {@link RetryOn#BAD_GATEWAY}, {@link RetryOn#SERVICE_UNAVAILABLE},
     * {@link RetryOn#GATEWAY_TIMEOUT}.
     */
    Set<RetryOn> GATEWAY_ERRORS = Set.of(
            RetryOn.BAD_GATEWAY,
            RetryOn.SERVICE_UNAVAILABLE,
            RetryOn.GATEWAY_TIMEOUT
    );

    /**
     * Convenience constant for transport-layer errors that do not involve an HTTP
     * response — connection refused, connect timeout, and read timeout.
     *
     * <p>Includes: {@link RetryOn#CONNECT_FAILURE}, {@link RetryOn#CONNECT_TIMEOUT},
     * {@link RetryOn#READ_TIMEOUT}.
     *
     * <p>{@link RetryOn#TRANSPORT_FAILURE} (SSL/TLS errors) is intentionally excluded —
     * SSL failures are often configuration issues rather than transient conditions.
     * Add it explicitly if your environment may produce transient SSL errors.
     */
    Set<RetryOn> TRANSPORT_ERRORS = Set.of(
            RetryOn.CONNECT_FAILURE,
            RetryOn.CONNECT_TIMEOUT,
            RetryOn.READ_TIMEOUT
    );

    // -------------------------------------------------------------------------
    // Core contract
    // -------------------------------------------------------------------------

    /**
     * Called by the client after each failed request execution to determine whether
     * to retry and how long to wait.
     * <p>
     * Return {@link Optional#of(Object)} with the delay to wait before the next attempt,
     * or {@link Optional#empty()} to stop retrying and propagate the exception to the
     * caller.
     * <p>
     * {@code attempt} is 1-based — it is the number of the execution that just failed.
     * After the first failure {@code attempt} is {@code 1}; after the second it is
     * {@code 2}, and so on.  To allow at most {@code N} total executions, return
     * {@code Optional.empty()} when {@code attempt >= N}.
     *
     * @param attempt The 1-based number of the execution that just failed.
     * @param failure The exception thrown by the failed execution.
     * @return The wait duration before the next attempt, or empty to stop retrying.
     */
    Optional<Duration> retryDelay(int attempt, RuntimeException failure);

    /**
     * Returns {@code true} if this policy permits retrying non-idempotent HTTP methods
     * ({@code POST}, {@code PUT}, {@code PATCH}).
     * <p>
     * Defaults to {@code false} — only idempotent methods ({@code GET}, {@code HEAD},
     * {@code DELETE}) are retried.  Custom implementations that want to allow
     * non-idempotent retries should override this method.
     *
     * @return {@code true} if non-idempotent methods may be retried.
     */
    default boolean allowNonIdempotent() {
        return false;
    }

    // -------------------------------------------------------------------------
    // Factories
    // -------------------------------------------------------------------------

    /**
     * Returns a policy that never retries.  This is the default when no retry policy
     * is configured on {@link ClientBuilder}.
     *
     * @return A no-op {@link RetryPolicy}; never {@code null}.
     */
    static RetryPolicy none() {
        return (attempt, failure) -> Optional.empty();
    }

    /**
     * Returns a new builder for constructing a {@link RetryPolicy}.
     *
     * @return A new {@link RetryPolicyBuilder}; never {@code null}.
     */
    static RetryPolicyBuilder builder() {
        return new DefaultRetryPolicyBuilder();
    }
}



