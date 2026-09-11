package software.frisby.web.client;

import java.time.Duration;
import java.util.Collection;

/**
 * A builder for constructing a {@link RetryPolicy}.
 * <p>
 * Obtain via {@link RetryPolicy#builder()}.
 *
 * <pre>{@code
 * RetryPolicy policy = RetryPolicy.builder()
 *         .maxAttempts(3)
 *         .on(RetryOn.GATEWAY_ERRORS)
 *         .on(RetryOn.TOO_MANY_REQUESTS)
 *         .delay(RetryDelay.exponential(Duration.ofSeconds(1)))
 *         .honorRetryAfterHeader(Duration.ofSeconds(60))
 *         .build();
 * }</pre>
 *
 * <h2>Built-in retry algorithm</h2>
 *
 * <p>The built-in policy evaluates each failed request in this order:
 * <ol>
 *   <li>If {@code attempt >= maxAttempts}, stop.</li>
 *   <li>If {@code replayableBody == false}, stop (non-replayable bodies cannot be retried).</li>
 *   <li>If the HTTP method is non-idempotent and {@code allowNonIdempotent() == false}, stop.
 *       (Idempotent: {@code GET}, {@code HEAD}, {@code DELETE}; Non-idempotent:
 *       {@code POST}, {@code PUT}, {@code PATCH}.)</li>
 *   <li>If the failure/status does not match any of the configured {@link RetryOn} conditions, stop.</li>
 *   <li>If the failure occurred in the {@code HTTP_RESPONSE} phase and the server sent a
 *       {@code Retry-After} header within the configured cap, use that value.</li>
 *   <li>Otherwise, use the configured {@link RetryDelay} strategy.</li>
 * </ol>
 *
 * <h2>Customizing via {@link RetryPolicy#of(java.util.function.Function)}</h2>
 *
 * <p>For greater flexibility, bypass this builder and use {@link RetryPolicy#of(java.util.function.Function)}
 * to supply a custom decision function. Custom policies receive the full {@link RetryContext}
 * and can make arbitrary decisions without being constrained by the built-in algorithm above.
 *
 * @see RetryPolicy
 * @see RetryDelay
 * @see RetryOn
 * @see RetryContext
 */
public interface RetryPolicyBuilder {

    /**
     * Sets the maximum total number of executions, including the initial attempt.
     * <p>
     * With {@code maxAttempts(3)} the client will try at most 3 times: one initial
     * attempt and up to 2 retries.
     * <p>
     * Defaults to {@code 3}.
     *
     * @param maxAttempts The maximum number of total executions; must be {@code >= 1}.
     * @return This builder instance.
     * @throws software.frisby.core.validation.NumericValueOutsideRangeException if {@code maxAttempts < 1}.
     */
    RetryPolicyBuilder maxAttempts(int maxAttempts);

    /**
     * Registers one or more {@link RetryOn} conditions that will trigger a retry.
     * <p>
     * Calls are additive — each call adds to the set of retryable conditions.
     *
     * @param conditions The conditions to add; must not be {@code null} or empty.
     * @return This builder instance.
     * @throws software.frisby.core.validation.NullValueException       if {@code conditions} is {@code null}.
     * @throws software.frisby.core.validation.MissingElementsException if {@code conditions} is empty.
     * @throws software.frisby.core.validation.NullElementException     if {@code conditions} contains a {@code null} element.
     */
    RetryPolicyBuilder on(RetryOn... conditions);

    /**
     * Registers a collection of {@link RetryOn} conditions that will trigger a retry.
     * <p>
     * Use with the convenience constants {@link RetryOn#GATEWAY_ERRORS} and
     * {@link RetryOn#TRANSPORT_ERRORS} to register groups in a single call:
     *
     * <pre>{@code
     * RetryPolicy.builder()
     *         .on(RetryOn.GATEWAY_ERRORS)
     *         .on(RetryOn.TRANSPORT_ERRORS)
     *         ...
     * }</pre>
     *
     * @param conditions The conditions to add; must not be {@code null} or empty.
     * @return This builder instance.
     * @throws software.frisby.core.validation.NullValueException       if {@code conditions} is {@code null}.
     * @throws software.frisby.core.validation.MissingElementsException if {@code conditions} is empty.
     * @throws software.frisby.core.validation.NullElementException     if {@code conditions} contains a {@code null} element.
     */
    RetryPolicyBuilder on(Collection<RetryOn> conditions);

    /**
     * Sets the delay strategy that determines how long to wait between retry attempts.
     * <p>
     * Defaults to {@link RetryDelay#linear(Duration) linear(1 second)} when not set.
     *
     * @param delay The delay strategy; must not be {@code null}.
     * @return This builder instance.
     * @throws software.frisby.core.validation.NullValueException if {@code delay} is {@code null}.
     */
    RetryPolicyBuilder delay(RetryDelay delay);

    /**
     * Enables honoring the {@code Retry-After} response header for HTTP error responses.
     * <p>
     * When the server includes a {@code Retry-After: <seconds>} header and the value
     * does not exceed 5 minutes, the policy uses the server-supplied wait time instead
     * of the configured delay strategy.  Values exceeding 5 minutes fall back to the
     * configured delay.
     * <p>
     * This is equivalent to calling {@link #honorRetryAfterHeader(Duration)} with a
     * 5-minute cap.
     *
     * @return This builder instance.
     */
    RetryPolicyBuilder honorRetryAfterHeader();

    /**
     * Enables honoring the {@code Retry-After} response header for HTTP error responses,
     * with an explicit cap on the maximum accepted wait time.
     * <p>
     * When the server includes a {@code Retry-After: <seconds>} header and the value
     * does not exceed {@code maxWait}, the policy uses the server-supplied wait time.
     * Values exceeding {@code maxWait} fall back to the configured delay strategy.
     * <p>
     * Only the integer-seconds form of the {@code Retry-After} header is supported.
     * HTTP-date values are ignored and fall back to the configured delay.
     *
     * @param maxWait The maximum accepted {@code Retry-After} value; must be positive.
     * @return This builder instance.
     * @throws software.frisby.core.validation.NullValueException            if {@code maxWait} is {@code null}.
     * @throws software.frisby.core.validation.DurationOutsideRangeException if {@code maxWait} is zero or negative.
     */
    RetryPolicyBuilder honorRetryAfterHeader(Duration maxWait);

    /**
     * Permits retrying non-idempotent HTTP methods ({@code POST}, {@code PUT},
     * {@code PATCH}).
     * <p>
     * This flag only affects the built-in policy produced by this builder. Custom policies
     * created via {@link RetryPolicy#of(java.util.function.Function)} receive the full
     * {@link RetryContext} and make their own decisions about method idempotency and
     * replayability.
     * <p>
     * <strong>Use with care.</strong>  Non-idempotent requests may have already been
     * processed by the server before the failure occurred.  Only enable this when you
     * are certain the target operation is safe to execute more than once (i.e., the
     * server itself is idempotent, or you accept the risk of duplicate processing).
     * <p>
     * Requests with a multipart form body are never retried by the built-in policy
     * regardless of this setting, because the body is streamed and cannot be replayed
     * after the first attempt. Custom policies may also check {@link RetryContext#replayableBody()}
     * to gate their decisions.
     *
     * @return This builder instance.
     */
    RetryPolicyBuilder allowNonIdempotent();

    /**
     * Returns a new {@link RetryPolicy} configured by this builder.
     *
     * @return A new {@link RetryPolicy}; never {@code null}.
     */
    RetryPolicy build();
}
