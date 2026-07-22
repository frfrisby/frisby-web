package software.frisby.web.client;

import software.frisby.core.validation.Durations;
import software.frisby.core.validation.Numbers;
import software.frisby.core.validation.Sequences;
import software.frisby.core.validation.Values;

import java.time.Duration;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Package-private implementation of {@link RetryPolicyBuilder}.
 */
final class DefaultRetryPolicyBuilder implements RetryPolicyBuilder {
    private static final String MAX_ATTEMPTS_ARGUMENT_NAME = "maxAttempts";
    private static final String CONDITIONS_ARGUMENT_NAME = "conditions";
    private static final String DELAY_ARGUMENT_NAME = "delay";
    private static final String MAX_WAIT_ARGUMENT_NAME = "maxWait";

    private static final int DEFAULT_MAX_ATTEMPTS = 3;
    private static final Duration DEFAULT_RETRY_AFTER_CAP = Duration.ofMinutes(5);

    private int maxAttempts;
    private final Set<RetryOn> retryOn;
    private RetryDelay delay;
    private boolean honorRetryAfterHeader;
    private Duration retryAfterCap;
    private boolean allowNonIdempotent;

    DefaultRetryPolicyBuilder() {
        this.maxAttempts = DEFAULT_MAX_ATTEMPTS;
        this.retryOn = new LinkedHashSet<>();
        this.delay = RetryDelay.linear(Duration.ofSeconds(1));
        this.honorRetryAfterHeader = false;
        this.retryAfterCap = null;
        this.allowNonIdempotent = false;
    }

    @Override
    public RetryPolicyBuilder maxAttempts(int maxAttempts) {
        this.maxAttempts = Numbers.positive(MAX_ATTEMPTS_ARGUMENT_NAME, maxAttempts);

        return this;
    }

    @Override
    public RetryPolicyBuilder on(RetryOn... conditions) {
        Sequences.notEmpty(CONDITIONS_ARGUMENT_NAME, conditions);

        for (RetryOn condition : conditions) {
            this.retryOn.add(condition);
        }

        return this;
    }

    @Override
    public RetryPolicyBuilder on(Collection<RetryOn> conditions) {
        Sequences.notEmpty(CONDITIONS_ARGUMENT_NAME, conditions);

        for (RetryOn condition : conditions) {
            this.retryOn.add(condition);
        }

        return this;
    }

    @Override
    public RetryPolicyBuilder delay(RetryDelay delay) {
        this.delay = Values.notNull(DELAY_ARGUMENT_NAME, delay);

        return this;
    }

    @Override
    public RetryPolicyBuilder honorRetryAfterHeader() {
        this.honorRetryAfterHeader = true;
        this.retryAfterCap = DEFAULT_RETRY_AFTER_CAP;

        return this;
    }

    @Override
    public RetryPolicyBuilder honorRetryAfterHeader(Duration maxWait) {
        this.honorRetryAfterHeader = true;
        this.retryAfterCap = Durations.positive(MAX_WAIT_ARGUMENT_NAME, maxWait);

        return this;
    }

    @Override
    public RetryPolicyBuilder allowNonIdempotent() {
        this.allowNonIdempotent = true;

        return this;
    }

    @Override
    public RetryPolicy build() {
        return new DefaultRetryPolicy(
                maxAttempts,
                Set.copyOf(retryOn),
                delay,
                honorRetryAfterHeader,
                retryAfterCap,
                allowNonIdempotent
        );
    }
}








