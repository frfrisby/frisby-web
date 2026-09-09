package software.frisby.web.client;

import software.frisby.core.validation.Values;

import java.time.Duration;
import java.util.Optional;
import java.util.function.Function;

/**
 * Determines whether and how long to wait before retrying a failed request.
 * <p>
 * The built-in builder covers the most common use cases:
 *
 * <pre>{@code
 * RetryPolicy policy = RetryPolicy.builder()
 *         .maxAttempts(3)
 *         .on(RetryOn.GATEWAY_ERRORS)
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
 * If the builder does not cover your needs, use {@link #of(Function)} to supply a custom
 * decision function, or implement this interface directly:
 *
 * <pre>{@code
 * RetryPolicy custom = RetryPolicy.of(context -> {
 *     if (context.attempt() >= 3) return Optional.empty();
 *     if (context.failure() instanceof ServiceUnavailableException) {
 *         return Optional.of(Duration.ofSeconds(context.attempt() * 5L));
 *     }
 *     return Optional.empty();
 * });
 *
 * // Or, for more complex logic:
 * public class MyRetryPolicy implements RetryPolicy {
 *     @Override
 *     public Optional<Duration> retryDelay(RetryContext context) {
 *         if (context.attempt() >= 3) return Optional.empty();
 *         if (!context.replayableBody()) return Optional.empty();
 *         if (context.statusCode().stream().anyMatch(c -> c == 429)) {
 *             return Optional.of(Duration.ofSeconds(30));
 *         }
 *         return Optional.empty();
 *     }
 * }
 * }</pre>
 *
 * <h2>Retry eligibility and the built-in policy</h2>
 * <p>
 * By default, the built-in policy (created via {@link #builder()}) retries only idempotent
 * HTTP methods ({@code GET}, {@code HEAD}, {@code DELETE}). Call
 * {@link RetryPolicyBuilder#allowNonIdempotent()} to also consider {@code POST}, {@code PUT},
 * and {@code PATCH} — only do this when you are certain those operations are safe to execute
 * more than once. Requests with a multipart form body are never retried by the built-in
 * policy, because the body is streamed and cannot be replayed after the first attempt.
 * <p>
 * Custom policies using {@link #of(Function)} have total control and are not subject to
 * these restrictions — the decision is entirely theirs, driven by the {@link RetryContext}
 * which includes {@code replayableBody()} and the HTTP method for inspection.
 *
 * @see RetryPolicyBuilder
 * @see RetryDelay
 * @see RetryOn
 * @see RetryContext
 */
public interface RetryPolicy {

    // -------------------------------------------------------------------------
    // Core contract
    // -------------------------------------------------------------------------

    /**
     * Returns a policy that never retries.  This is the default when no retry policy
     * is configured on {@link ClientBuilder}.
     *
     * @return A no-op {@link RetryPolicy}; never {@code null}.
     */
    static RetryPolicy none() {
        return context -> Optional.empty();
    }

    /**
     * Returns a new builder for constructing a {@link RetryPolicy}.
     *
     * @return A new {@link RetryPolicyBuilder}; never {@code null}.
     */
    static RetryPolicyBuilder builder() {
        return new DefaultRetryPolicyBuilder();
    }

    /**
     * Returns a {@link RetryPolicy} that delegates to the supplied decision function.
     * <p>
     * Useful for custom policies that do not require the full complexity of implementing
     * the interface:
     *
     * <pre>{@code
     * RetryPolicy policy = RetryPolicy.of(context -> {
     *     if (context.attempt() >= 3) return Optional.empty();
     *     if (!context.replayableBody()) return Optional.empty();
     *     if (context.statusCode().isPresent() && context.statusCode().getAsInt() == 429) {
     *         return Optional.of(Duration.ofSeconds(30));
     *     }
     *     return Optional.empty();
     * });
     * }</pre>
     *
     * @param policy A function that accepts a {@link RetryContext} and returns the retry
     *               delay, or empty to stop retrying; must not be {@code null}.
     * @return A new {@link RetryPolicy} that delegates to the supplied function;
     * never {@code null}.
     * @throws software.frisby.core.validation.NullValueException if {@code policy} is {@code null}.
     */
    static RetryPolicy of(Function<RetryContext, Optional<Duration>> policy) {
        Values.notNull("policy", policy);
        return policy::apply;
    }

    /**
     * Called by the client after each failed request execution to determine whether
     * to retry and how long to wait.
     * <p>
     * Return {@link Optional#of(Object)} with the delay to wait before the next attempt,
     * or {@link Optional#empty()} to stop retrying and propagate the exception to the
     * caller.
     * <p>
     * The {@link RetryContext} carries:
     * <ul>
     *   <li>{@code attempt} — the 1-based number of the execution that just failed</li>
     *   <li>{@code failure} — the exception thrown</li>
     *   <li>{@code method}/{@code uri} — the HTTP method and fully-resolved request URI</li>
     *   <li>{@code statusCode} — present only for HTTP-response failures; empty for
     *       pre-flight or transport failures</li>
     *   <li>{@code replayableBody} — whether the request body can be sent more than once</li>
     *   <li>{@code phase} — the lifecycle phase in which the failure occurred
     *       ({@link RetryPhase#PRE_FLIGHT}, {@link RetryPhase#TRANSPORT}, or
     *       {@link RetryPhase#HTTP_RESPONSE})</li>
     * </ul>
     *
     * @param context The full context of the failed execution.
     * @return The wait duration before the next attempt, or empty to stop retrying.
     */
    Optional<Duration> retryDelay(RetryContext context);
}



