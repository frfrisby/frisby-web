package software.frisby.web.client;

import software.frisby.web.client.exception.*;

import java.time.Duration;
import java.util.OptionalLong;
import java.util.Optional;
import java.util.Set;

/**
 * Package-private implementation of {@link RetryPolicy}.
 */
final class DefaultRetryPolicy implements RetryPolicy {
    private final int maxAttempts;
    private final Set<RetryOn> retryOn;
    private final RetryDelay delay;
    private final boolean honorRetryAfterHeader;
    private final Duration retryAfterCap;
    private final boolean allowNonIdempotent;

    DefaultRetryPolicy(int maxAttempts,
                       Set<RetryOn> retryOn,
                       RetryDelay delay,
                       boolean honorRetryAfterHeader,
                       Duration retryAfterCap,
                       boolean allowNonIdempotent) {
        this.maxAttempts = maxAttempts;
        this.retryOn = retryOn;
        this.delay = delay;
        this.honorRetryAfterHeader = honorRetryAfterHeader;
        this.retryAfterCap = retryAfterCap;
        this.allowNonIdempotent = allowNonIdempotent;
    }

    @Override
    public Optional<Duration> retryDelay(int attempt, RuntimeException failure) {
        if (attempt >= maxAttempts) {
            return Optional.empty();
        }

        if (!isRetryable(failure)) {
            return Optional.empty();
        }

        if (honorRetryAfterHeader && failure instanceof HttpResponseException httpEx) {
            OptionalLong headerSeconds = httpEx.headers().firstValueAsLong("Retry-After");

            if (headerSeconds.isPresent()) {
                Duration headerDelay = Duration.ofSeconds(headerSeconds.getAsLong());

                if (null == retryAfterCap || headerDelay.compareTo(retryAfterCap) <= 0) {
                    return Optional.of(headerDelay);
                }
            }
        }

        return Optional.of(delay.delayFor(attempt));
    }

    @Override
    public boolean allowNonIdempotent() {
        return allowNonIdempotent;
    }

    private boolean isRetryable(RuntimeException failure) {
        for (RetryOn condition : retryOn) {
            if (matches(condition, failure)) {
                return true;
            }
        }

        return false;
    }

    private static boolean matches(RetryOn condition, RuntimeException failure) {
        return switch (condition) {
            case REQUEST_TIMEOUT -> failure instanceof RequestTimeoutException;
            case TOO_MANY_REQUESTS -> failure instanceof TooManyRequestsException;
            case BAD_GATEWAY -> failure instanceof BadGatewayException;
            case SERVICE_UNAVAILABLE -> failure instanceof ServiceUnavailableException;
            case GATEWAY_TIMEOUT -> failure instanceof GatewayTimeoutException;
            case CONNECT_FAILURE -> failure instanceof ConnectException;
            case CONNECT_TIMEOUT -> failure instanceof ConnectTimeoutException;
            case READ_TIMEOUT -> failure instanceof ReadTimeoutException;
            case TRANSPORT_FAILURE -> failure instanceof TransportException;
        };
    }
}

