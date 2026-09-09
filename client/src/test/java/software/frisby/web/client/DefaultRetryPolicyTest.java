package software.frisby.web.client;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import software.frisby.core.validation.DurationOutsideRangeException;
import software.frisby.core.validation.NullValueException;
import software.frisby.web.client.exception.*;

import java.net.URI;
import java.net.http.HttpHeaders;
import java.time.Duration;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link DefaultRetryPolicy}.
 * <p>
 * Tests cover: {@code retryDelay} logic via {@link RetryContext}, per {@link RetryOn} value,
 * {@code Retry-After} header parsing, cap enforcement, non-idempotent method handling,
 * and replayable body checking.
 */
class DefaultRetryPolicyTest {
    private static final URI TEST_URI = URI.create("https://example.com/test");
    private static final String GET = "GET";
    private static final String POST = "POST";
    private static final RetryDelay FIXED_1S = RetryDelay.fixed(Duration.ofSeconds(1));

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static DefaultRetryPolicy policy(int maxAttempts, Set<RetryOn> retryOn) {
        return new DefaultRetryPolicy(
                maxAttempts,
                retryOn,
                FIXED_1S,
                false,
                null,
                false
        );
    }

    private static DefaultRetryPolicy policyWithRetryAfter(int maxAttempts,
                                                           Set<RetryOn> retryOn,
                                                           Duration cap) {
        return new DefaultRetryPolicy(
                maxAttempts,
                retryOn,
                FIXED_1S,
                true,
                cap,
                false
        );
    }

    private static DefaultRetryPolicy policyAllowingNonIdempotent(int maxAttempts,
                                                                  Set<RetryOn> retryOn) {
        return new DefaultRetryPolicy(
                maxAttempts,
                retryOn,
                FIXED_1S,
                false,
                null,
                true
        );
    }

    private static HttpHeaders headersWithRetryAfter(long seconds) {
        return HttpHeaders.of(
                Map.of("Retry-After", List.of(String.valueOf(seconds))),
                (k, v) -> true
        );
    }

    static RetryContext context(int attempt, Throwable failure, String method) {
        return new RetryContext(
                attempt,
                failure,
                method,
                TEST_URI,
                OptionalInt.empty(),
                true,
                RetryPhase.TRANSPORT
        );
    }

    static RetryContext context(int attempt, Throwable failure) {
        return context(attempt, failure, GET);
    }

    private static RetryContext contextHttpResponse(int attempt,
                                                    Throwable failure,
                                                    String method,
                                                    int statusCode) {
        return new RetryContext(
                attempt,
                failure,
                method,
                TEST_URI,
                OptionalInt.of(statusCode),
                true,
                RetryPhase.HTTP_RESPONSE
        );
    }

    private static RetryContext contextWithReplayability(int attempt,
                                                         Throwable failure,
                                                         String method,
                                                         boolean replayable) {
        return new RetryContext(
                attempt,
                failure,
                method,
                TEST_URI,
                OptionalInt.empty(),
                replayable,
                RetryPhase.TRANSPORT
        );
    }

    // -------------------------------------------------------------------------
    // isIdempotentMethod
    // -------------------------------------------------------------------------

    @Nested
    class IsIdempotentMethod {
        @Test
        void getMethod_returnsTrue() {
            assertTrue(DefaultRetryPolicy.isIdempotentMethod("GET"));
        }

        @Test
        void headMethod_returnsTrue() {
            assertTrue(DefaultRetryPolicy.isIdempotentMethod("HEAD"));
        }

        @Test
        void deleteMethod_returnsTrue() {
            assertTrue(DefaultRetryPolicy.isIdempotentMethod("DELETE"));
        }

        @Test
        void postMethod_returnsFalse() {
            assertFalse(DefaultRetryPolicy.isIdempotentMethod("POST"));
        }
    }

    // -------------------------------------------------------------------------
    // Attempt limit
    // -------------------------------------------------------------------------

    @Nested
    class AttemptLimit {
        @Test
        void attemptEqualsMaxAttempts_returnsEmpty() {
            DefaultRetryPolicy p = policy(3, Set.of(RetryOn.SERVICE_UNAVAILABLE));

            Optional<Duration> result = p.retryDelay(context(3, new ServiceUnavailableException()));

            assertTrue(result.isEmpty());
        }

        @Test
        void attemptExceedsMaxAttempts_returnsEmpty() {
            DefaultRetryPolicy p = policy(3, Set.of(RetryOn.SERVICE_UNAVAILABLE));

            Optional<Duration> result = p.retryDelay(context(5, new ServiceUnavailableException()));

            assertTrue(result.isEmpty());
        }

        @Test
        void attemptBelowMaxAttempts_retryableFailure_returnsDelay() {
            DefaultRetryPolicy p = policy(3, Set.of(RetryOn.SERVICE_UNAVAILABLE));

            Optional<Duration> result = p.retryDelay(context(2, new ServiceUnavailableException()));

            assertTrue(result.isPresent());
            assertEquals(Duration.ofSeconds(1), result.get());
        }
    }

    // -------------------------------------------------------------------------
    // isRetryable — one test per RetryOn value
    // -------------------------------------------------------------------------

    @Nested
    class IsRetryable {
        @Test
        void requestTimeout_matchesRequestTimeoutException() {
            DefaultRetryPolicy p = policy(3, Set.of(RetryOn.REQUEST_TIMEOUT));

            Optional<Duration> result = p.retryDelay(context(1, new RequestTimeoutException()));

            assertTrue(result.isPresent());
        }

        @Test
        void tooManyRequests_matchesTooManyRequestsException() {
            DefaultRetryPolicy p = policy(3, Set.of(RetryOn.TOO_MANY_REQUESTS));

            Optional<Duration> result = p.retryDelay(context(1, new TooManyRequestsException()));

            assertTrue(result.isPresent());
        }

        @Test
        void badGateway_matchesBadGatewayException() {
            DefaultRetryPolicy p = policy(3, Set.of(RetryOn.BAD_GATEWAY));

            Optional<Duration> result = p.retryDelay(context(1, new BadGatewayException()));

            assertTrue(result.isPresent());
        }

        @Test
        void serviceUnavailable_matchesServiceUnavailableException() {
            DefaultRetryPolicy p = policy(3, Set.of(RetryOn.SERVICE_UNAVAILABLE));

            Optional<Duration> result = p.retryDelay(context(1, new ServiceUnavailableException()));

            assertTrue(result.isPresent());
        }

        @Test
        void gatewayTimeout_matchesGatewayTimeoutException() {
            DefaultRetryPolicy p = policy(3, Set.of(RetryOn.GATEWAY_TIMEOUT));

            Optional<Duration> result = p.retryDelay(context(1, new GatewayTimeoutException()));

            assertTrue(result.isPresent());
        }

        @Test
        void connectFailure_matchesConnectException() {
            DefaultRetryPolicy p = policy(3, Set.of(RetryOn.CONNECT_FAILURE));

            Optional<Duration> result = p.retryDelay(context(1, new ConnectException("refused", new RuntimeException())));

            assertTrue(result.isPresent());
        }

        @Test
        void connectTimeout_matchesConnectTimeoutException() {
            DefaultRetryPolicy p = policy(3, Set.of(RetryOn.CONNECT_TIMEOUT));

            Optional<Duration> result = p.retryDelay(context(1, new ConnectTimeoutException("timeout", new RuntimeException())));

            assertTrue(result.isPresent());
        }

        @Test
        void readTimeout_matchesReadTimeoutException() {
            DefaultRetryPolicy p = policy(3, Set.of(RetryOn.READ_TIMEOUT));

            Optional<Duration> result = p.retryDelay(context(1, new ReadTimeoutException("timeout", new RuntimeException())));

            assertTrue(result.isPresent());
        }

        @Test
        void transportFailure_matchesTransportException() {
            DefaultRetryPolicy p = policy(3, Set.of(RetryOn.TRANSPORT_FAILURE));

            Optional<Duration> result = p.retryDelay(context(1, new TransportException("ssl error", new RuntimeException())));

            assertTrue(result.isPresent());
        }

        @Test
        void conditionNotInSet_returnsEmpty() {
            DefaultRetryPolicy p = policy(3, Set.of(RetryOn.SERVICE_UNAVAILABLE));

            Optional<Duration> result = p.retryDelay(context(1, new BadGatewayException()));

            assertTrue(result.isEmpty());
        }

        @Test
        void emptyRetryOnSet_alwaysReturnsEmpty() {
            DefaultRetryPolicy p = policy(3, Set.of());

            Optional<Duration> result = p.retryDelay(context(1, new ServiceUnavailableException()));

            assertTrue(result.isEmpty());
        }
    }

    // -------------------------------------------------------------------------
    // Retry-After header
    // -------------------------------------------------------------------------

    @Nested
    class RetryAfterHeader {
        @Test
        void headerPresent_withinCap_returnsHeaderDelay() {
            DefaultRetryPolicy p = policyWithRetryAfter(
                    3,
                    Set.of(RetryOn.TOO_MANY_REQUESTS),
                    Duration.ofSeconds(60)
            );
            TooManyRequestsException ex = new TooManyRequestsException(
                    "GET",
                    TEST_URI,
                    headersWithRetryAfter(10),
                    null
            );

            Optional<Duration> result = p.retryDelay(context(1, ex, "GET"));

            assertTrue(result.isPresent());
            assertEquals(Duration.ofSeconds(10), result.get());
        }

        @Test
        void headerPresent_equalsToCap_returnsHeaderDelay() {
            DefaultRetryPolicy p = policyWithRetryAfter(
                    3,
                    Set.of(RetryOn.TOO_MANY_REQUESTS),
                    Duration.ofSeconds(10)
            );
            TooManyRequestsException ex = new TooManyRequestsException(
                    "GET",
                    TEST_URI,
                    headersWithRetryAfter(10),
                    null
            );

            Optional<Duration> result = p.retryDelay(context(1, ex, "GET"));

            assertTrue(result.isPresent());
            assertEquals(Duration.ofSeconds(10), result.get());
        }

        @Test
        void headerPresent_exceedsCap_fallsBackToConfiguredDelay() {
            DefaultRetryPolicy p = policyWithRetryAfter(
                    3,
                    Set.of(RetryOn.TOO_MANY_REQUESTS),
                    Duration.ofSeconds(5)
            );
            TooManyRequestsException ex = new TooManyRequestsException(
                    "GET",
                    TEST_URI,
                    headersWithRetryAfter(30),
                    null
            );

            Optional<Duration> result = p.retryDelay(context(1, ex, "GET"));

            assertTrue(result.isPresent());
            assertEquals(Duration.ofSeconds(1), result.get());
        }

        @Test
        void headerAbsent_fallsBackToConfiguredDelay() {
            DefaultRetryPolicy p = policyWithRetryAfter(
                    3,
                    Set.of(RetryOn.TOO_MANY_REQUESTS),
                    Duration.ofSeconds(60)
            );
            TooManyRequestsException ex = new TooManyRequestsException();

            Optional<Duration> result = p.retryDelay(context(1, ex));

            assertTrue(result.isPresent());
            assertEquals(Duration.ofSeconds(1), result.get());
        }

        @Test
        void honorRetryAfterDisabled_headerIgnored_usesConfiguredDelay() {
            DefaultRetryPolicy p = policy(3, Set.of(RetryOn.TOO_MANY_REQUESTS));
            TooManyRequestsException ex = new TooManyRequestsException(
                    "GET",
                    TEST_URI,
                    headersWithRetryAfter(30),
                    null
            );

            Optional<Duration> result = p.retryDelay(context(1, ex, "GET"));

            assertTrue(result.isPresent());
            assertEquals(Duration.ofSeconds(1), result.get());
        }

        @Test
        void nullCap_headerAcceptedRegardlessOfValue() {
            DefaultRetryPolicy p = new DefaultRetryPolicy(
                    3,
                    Set.of(RetryOn.TOO_MANY_REQUESTS),
                    FIXED_1S,
                    true,
                    null,   // no cap
                    false
            );
            TooManyRequestsException ex = new TooManyRequestsException(
                    "GET",
                    TEST_URI,
                    headersWithRetryAfter(3600),
                    null
            );

            Optional<Duration> result = p.retryDelay(context(1, ex, "GET"));

            assertTrue(result.isPresent());
            assertEquals(Duration.ofSeconds(3600), result.get());
        }

        @Test
        void nonHttpResponseException_honorRetryAfterEnabled_usesConfiguredDelay() {
            DefaultRetryPolicy p = policyWithRetryAfter(
                    3,
                    Set.of(RetryOn.READ_TIMEOUT),
                    Duration.ofSeconds(60)
            );
            ReadTimeoutException ex = new ReadTimeoutException("timeout", new RuntimeException());

            Optional<Duration> result = p.retryDelay(context(1, ex));

            assertTrue(result.isPresent());
            assertEquals(Duration.ofSeconds(1), result.get());
        }
    }

    // -------------------------------------------------------------------------
    // allowNonIdempotent
    // -------------------------------------------------------------------------

    @Nested
    class AllowNonIdempotent {
        @Test
        void defaultPolicy_allowNonIdempotentReturnsFalse() {
            DefaultRetryPolicy p = policy(3, Set.of(RetryOn.SERVICE_UNAVAILABLE));

            assertFalse(p.allowNonIdempotent());
        }

        @Test
        void allowNonIdempotentEnabled_returnsTrue() {
            DefaultRetryPolicy p = new DefaultRetryPolicy(
                    3,
                    Set.of(RetryOn.SERVICE_UNAVAILABLE),
                    FIXED_1S,
                    false,
                    null,
                    true
            );

            assertTrue(p.allowNonIdempotent());
        }
    }

    // -------------------------------------------------------------------------
    // Delay strategy
    // -------------------------------------------------------------------------

    @Nested
    class DelayStrategy {
        @Test
        void fixedDelay_alwaysReturnsSameValue() {
            RetryDelay fixed5s = RetryDelay.fixed(Duration.ofSeconds(5));
            DefaultRetryPolicy p = new DefaultRetryPolicy(
                    5,
                    Set.of(RetryOn.SERVICE_UNAVAILABLE),
                    fixed5s,
                    false,
                    null,
                    false
            );

            assertEquals(Duration.ofSeconds(5), p.retryDelay(context(1, new ServiceUnavailableException())).orElseThrow());
            assertEquals(Duration.ofSeconds(5), p.retryDelay(context(2, new ServiceUnavailableException())).orElseThrow());
            assertEquals(Duration.ofSeconds(5), p.retryDelay(context(3, new ServiceUnavailableException())).orElseThrow());
        }

        @Test
        void linearDelay_scalesWithAttempt() {
            RetryDelay linear = RetryDelay.linear(Duration.ofSeconds(1));
            DefaultRetryPolicy p = new DefaultRetryPolicy(
                    5,
                    Set.of(RetryOn.SERVICE_UNAVAILABLE),
                    linear,
                    false,
                    null,
                    false
            );

            assertEquals(Duration.ofSeconds(1), p.retryDelay(context(1, new ServiceUnavailableException())).orElseThrow());
            assertEquals(Duration.ofSeconds(2), p.retryDelay(context(2, new ServiceUnavailableException())).orElseThrow());
            assertEquals(Duration.ofSeconds(3), p.retryDelay(context(3, new ServiceUnavailableException())).orElseThrow());
        }
    }

    // -------------------------------------------------------------------------
    // RetryDelay.exponential — factory, delegation, and cap behaviour
    // -------------------------------------------------------------------------

    @Nested
    class Exponential {
        @Test
        void nullBaseDelay_throwsNullValueException() {
            assertThrows(NullValueException.class, () -> RetryDelay.exponential(null));
        }

        @Test
        void zeroBaseDelay_throwsDurationOutsideRangeException() {
            assertThrows(DurationOutsideRangeException.class, () -> RetryDelay.exponential(Duration.ZERO));
        }

        @Test
        void nullBaseDelay_twoArg_throwsNullValueException() {
            assertThrows(NullValueException.class, () -> RetryDelay.exponential(null, Duration.ofSeconds(30)));
        }

        @Test
        void nullMaxDelay_twoArg_throwsNullValueException() {
            assertThrows(NullValueException.class, () -> RetryDelay.exponential(Duration.ofSeconds(1), null));
        }

        @Test
        void zeroMaxDelay_twoArg_throwsDurationOutsideRangeException() {
            assertThrows(DurationOutsideRangeException.class, () -> RetryDelay.exponential(Duration.ofSeconds(1), Duration.ZERO));
        }

        @Test
        void noCapArg_delegatesTo30SecondCap_attempt1WithinExpectedRange() {
            // exponential(base) delegates to exponential(base, 30s).
            // Attempt 1: base × 2^0 = base; plus up to 20% jitter → [base, base × 1.2].
            RetryDelay delay = RetryDelay.exponential(Duration.ofSeconds(1));

            Duration result = delay.delayFor(1);

            assertTrue(result.compareTo(Duration.ofSeconds(1)) >= 0,
                    "Delay must be at least 1 s");
            assertTrue(result.compareTo(Duration.ofMillis(1200)) <= 0,
                    "Delay must not exceed 1 s + 20% jitter (1200 ms)");
        }

        @Test
        void noCapArg_largeAttempt_cappedAt30Seconds() {
            // Large attempt numbers should be capped at 30 s (+ up to 20% jitter = 36 s).
            RetryDelay delay = RetryDelay.exponential(Duration.ofSeconds(1));

            Duration result = delay.delayFor(100);

            assertTrue(result.compareTo(Duration.ofSeconds(30)) >= 0,
                    "Delay must be at least 30 s at the cap");
            assertTrue(result.compareTo(Duration.ofMillis(36_000)) <= 0,
                    "Delay must not exceed 30 s + 20% jitter (36 000 ms)");
        }

        @Test
        void withCap_attempt1WithinExpectedRange() {
            RetryDelay delay = RetryDelay.exponential(Duration.ofSeconds(1), Duration.ofSeconds(5));

            Duration result = delay.delayFor(1);

            assertTrue(result.compareTo(Duration.ofSeconds(1)) >= 0);
            assertTrue(result.compareTo(Duration.ofMillis(1200)) <= 0);
        }

        @Test
        void withCap_largeAttempt_cappedAtMaxDelay() {
            // Cap is 5 s; large attempt should be capped there (+ up to 20% jitter = 6 s).
            RetryDelay delay = RetryDelay.exponential(Duration.ofSeconds(1), Duration.ofSeconds(5));

            Duration result = delay.delayFor(100);

            assertTrue(result.compareTo(Duration.ofSeconds(5)) >= 0,
                    "Delay must be at least the cap (5 s)");
            assertTrue(result.compareTo(Duration.ofMillis(6_000)) <= 0,
                    "Delay must not exceed cap + 20% jitter (6 000 ms)");
        }

        @Test
        void withCap_intermediateAttempt_scalesBeforeCap() {
            // Base 1 s, cap 60 s: attempt 3 = 1 × 2^2 = 4 s (well below cap, no jitter issue).
            RetryDelay delay = RetryDelay.exponential(Duration.ofSeconds(1), Duration.ofSeconds(60));

            Duration result = delay.delayFor(3);

            assertTrue(result.compareTo(Duration.ofSeconds(4)) >= 0);
            assertTrue(result.compareTo(Duration.ofMillis(4_800)) <= 0);
        }
    }
}
