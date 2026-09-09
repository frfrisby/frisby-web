package software.frisby.web.client;

import software.frisby.web.client.exception.*;

import java.time.Duration;
import java.util.Optional;
import java.util.OptionalLong;
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

    private static boolean matches(RetryOn condition, RetryContext context) {
        return switch (condition) {
            case REQUEST_TIMEOUT -> context.failure() instanceof RequestTimeoutException;
            case TOO_MANY_REQUESTS -> context.failure() instanceof TooManyRequestsException;
            case BAD_GATEWAY -> context.failure() instanceof BadGatewayException;
            case SERVICE_UNAVAILABLE -> context.failure() instanceof ServiceUnavailableException;
            case GATEWAY_TIMEOUT -> context.failure() instanceof GatewayTimeoutException;
            case CONNECT_FAILURE -> context.failure() instanceof ConnectException;
            case CONNECT_TIMEOUT -> context.failure() instanceof ConnectTimeoutException;
            case READ_TIMEOUT -> context.failure() instanceof ReadTimeoutException;
            case TRANSPORT_FAILURE -> context.failure() instanceof TransportException;
        };
    }

    static boolean isIdempotentMethod(String method) {
        return "GET".equals(method) || "HEAD".equals(method) || "DELETE".equals(method);
    }

    @Override
    public Optional<Duration> retryDelay(RetryContext context) {
        if (context.attempt() >= maxAttempts) {
            return Optional.empty();
        }

        if (!isRetryable(context)) {
            return Optional.empty();
        }

        if (honorRetryAfterHeader && context.failure() instanceof HttpResponseException httpEx) {
            OptionalLong headerSeconds = httpEx.headers().firstValueAsLong("Retry-After");

            if (headerSeconds.isPresent()) {
                Duration headerDelay = Duration.ofSeconds(headerSeconds.getAsLong());

                if (null == retryAfterCap || headerDelay.compareTo(retryAfterCap) <= 0) {
                    return Optional.of(headerDelay);
                }
            }
        }

        return Optional.of(delay.delayFor(context.attempt()));
    }

    boolean allowNonIdempotent() {
        return allowNonIdempotent;
    }

    private boolean isRetryable(RetryContext context) {
        if (RetryPhase.PRE_FLIGHT != context.phase()) {
            if (!context.replayableBody()) {
                return false;
            }

            if (!allowNonIdempotent && !isIdempotentMethod(context.method())) {
                return false;
            }
        }

        for (RetryOn condition : retryOn) {
            if (matches(condition, context)) {
                return true;
            }
        }

        return false;
    }
}
