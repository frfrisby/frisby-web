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
 *         .on(RetryPolicy.GATEWAY_ERRORS)
 *         .on(RetryOn.TOO_MANY_REQUESTS)
 *         .delay(RetryDelay.exponential(Duration.ofSeconds(1)))
 *         .honorRetryAfterHeader(Duration.ofSeconds(60))
 *         .build();
 * }</pre>
 *
 * @see RetryPolicy
 * @see RetryDelay
 * @see RetryOn
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
     * Use with the convenience constants {@link RetryPolicy#GATEWAY_ERRORS} and
     * {@link RetryPolicy#TRANSPORT_ERRORS} to register groups in a single call:
     *
     * <pre>{@code
     * RetryPolicy.builder()
     *         .on(RetryPolicy.GATEWAY_ERRORS)
     *         .on(RetryPolicy.TRANSPORT_ERRORS)
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
     * <strong>Use with care.</strong>  Non-idempotent requests may have already been
     * processed by the server before the failure occurred.  Only enable this when you
     * are certain the target operation is safe to execute more than once (i.e., the
     * server itself is idempotent, or you accept the risk of duplicate processing).
     * <p>
     * Requests with a multipart form body are never retried regardless of this setting,
     * because the body is streamed and cannot be replayed after the first attempt.
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








