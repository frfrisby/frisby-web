package software.frisby.web.client;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import software.frisby.core.validation.DurationOutsideRangeException;
import software.frisby.core.validation.MissingElementsException;
import software.frisby.core.validation.NullValueException;
import software.frisby.core.validation.NumericValueOutsideRangeException;
import software.frisby.web.client.exception.BadGatewayException;
import software.frisby.web.client.exception.GatewayTimeoutException;
import software.frisby.web.client.exception.ServiceUnavailableException;
import software.frisby.web.client.exception.TooManyRequestsException;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link DefaultRetryPolicyBuilder}.
 * <p>
 * Tests cover: input validation and correct application of every builder knob.
 */
class DefaultRetryPolicyBuilderTest {
    private static final String NULL_CONDITIONS_MSG =
            "The 'conditions' value is invalid. The value must not be null.";
    private static final String EMPTY_CONDITIONS_MSG =
            "The 'conditions' value is invalid. The value must not be empty.";
    private static final String INVALID_MAX_ATTEMPTS_MSG =
            "The 'maxAttempts' value is invalid. The value must be greater than '0'.";
    private static final String NULL_DELAY_MSG =
            "The 'delay' value is invalid. The value must not be null.";
    private static final String NULL_MAX_WAIT_MSG =
            "The 'maxWait' value is invalid. The value must not be null.";

    // -------------------------------------------------------------------------
    // on(Collection<RetryOn>)
    // -------------------------------------------------------------------------

    @Nested
    class OnCollection {
        @Test
        void nullCollection_throwsNullValueException() {
            NullValueException ex = assertThrows(
                    NullValueException.class,
                    () -> RetryPolicy.builder().on((java.util.Collection<RetryOn>) null)
            );

            assertEquals(NULL_CONDITIONS_MSG, ex.getMessage());
        }

        @Test
        void emptyCollection_throwsMissingElementsException() {
            MissingElementsException ex = assertThrows(
                    MissingElementsException.class,
                    () -> RetryPolicy.builder().on(List.of())
            );

            assertEquals(EMPTY_CONDITIONS_MSG, ex.getMessage());
        }

        @Test
        void validCollection_conditionsApplied() {
            RetryPolicy policy = RetryPolicy.builder()
                    .on(List.of(RetryOn.BAD_GATEWAY, RetryOn.GATEWAY_TIMEOUT))
                    .build();

            assertTrue(policy.retryDelay(1, new BadGatewayException()).isPresent());
            assertTrue(policy.retryDelay(1, new GatewayTimeoutException()).isPresent());
            assertTrue(policy.retryDelay(1, new ServiceUnavailableException()).isEmpty());
        }

        @Test
        void multipleOnCollectionCalls_areAdditive() {
            RetryPolicy policy = RetryPolicy.builder()
                    .on(List.of(RetryOn.BAD_GATEWAY))
                    .on(List.of(RetryOn.SERVICE_UNAVAILABLE))
                    .build();

            assertTrue(policy.retryDelay(1, new BadGatewayException()).isPresent());
            assertTrue(policy.retryDelay(1, new ServiceUnavailableException()).isPresent());
        }

        @Test
        void onCollectionMixedWithVarargs_areAdditive() {
            RetryPolicy policy = RetryPolicy.builder()
                    .on(RetryOn.BAD_GATEWAY)
                    .on(List.of(RetryOn.SERVICE_UNAVAILABLE))
                    .build();

            assertTrue(policy.retryDelay(1, new BadGatewayException()).isPresent());
            assertTrue(policy.retryDelay(1, new ServiceUnavailableException()).isPresent());
        }
    }

    // -------------------------------------------------------------------------
    // on(RetryOn...)
    // -------------------------------------------------------------------------

    @Nested
    class OnVarargs {
        @Test
        void nullVarargs_throwsNullValueException() {
            NullValueException ex = assertThrows(
                    NullValueException.class,
                    () -> RetryPolicy.builder().on((RetryOn[]) null)
            );

            assertEquals(NULL_CONDITIONS_MSG, ex.getMessage());
        }

        @Test
        void emptyVarargs_throwsMissingElementsException() {
            MissingElementsException ex = assertThrows(
                    MissingElementsException.class,
                    () -> RetryPolicy.builder().on(new RetryOn[]{})
            );

            assertEquals(EMPTY_CONDITIONS_MSG, ex.getMessage());
        }

        @Test
        void validVarargs_conditionsApplied() {
            RetryPolicy policy = RetryPolicy.builder()
                    .on(RetryOn.SERVICE_UNAVAILABLE, RetryOn.GATEWAY_TIMEOUT)
                    .build();

            assertTrue(policy.retryDelay(1, new ServiceUnavailableException()).isPresent());
            assertTrue(policy.retryDelay(1, new GatewayTimeoutException()).isPresent());
        }
    }

    // -------------------------------------------------------------------------
    // maxAttempts
    // -------------------------------------------------------------------------

    @Nested
    class MaxAttempts {
        @Test
        void zeroMaxAttempts_throwsNumericValueOutsideRangeException() {
            NumericValueOutsideRangeException ex = assertThrows(
                    NumericValueOutsideRangeException.class,
                    () -> RetryPolicy.builder().maxAttempts(0)
            );

            assertEquals(INVALID_MAX_ATTEMPTS_MSG, ex.getMessage());
        }

        @Test
        void negativeMaxAttempts_throwsNumericValueOutsideRangeException() {
            assertThrows(
                    NumericValueOutsideRangeException.class,
                    () -> RetryPolicy.builder().maxAttempts(-1)
            );
        }

        @Test
        void validMaxAttempts_appliedToPolicy() {
            RetryPolicy policy = RetryPolicy.builder()
                    .on(RetryOn.SERVICE_UNAVAILABLE)
                    .maxAttempts(5)
                    .build();

            assertTrue(policy.retryDelay(4, new ServiceUnavailableException()).isPresent());
            assertTrue(policy.retryDelay(5, new ServiceUnavailableException()).isEmpty());
        }
    }

    // -------------------------------------------------------------------------
    // delay
    // -------------------------------------------------------------------------

    @Nested
    class Delay {
        @Test
        void nullDelay_throwsNullValueException() {
            NullValueException ex = assertThrows(
                    NullValueException.class,
                    () -> RetryPolicy.builder().delay(null)
            );

            assertEquals(NULL_DELAY_MSG, ex.getMessage());
        }

        @Test
        void customDelay_appliedToPolicy() {
            RetryPolicy policy = RetryPolicy.builder()
                    .on(RetryOn.SERVICE_UNAVAILABLE)
                    .delay(RetryDelay.fixed(Duration.ofSeconds(5)))
                    .build();

            Duration delay = policy.retryDelay(1, new ServiceUnavailableException()).orElseThrow();

            assertEquals(Duration.ofSeconds(5), delay);
        }
    }

    // -------------------------------------------------------------------------
    // honorRetryAfterHeader
    // -------------------------------------------------------------------------

    @Nested
    class HonorRetryAfterHeader {
        @Test
        void honorRetryAfterHeader_noArg_setsDefaultCap() {
            // honorRetryAfterHeader() sets a 5-minute default cap — verify the policy
            // accepts a Retry-After value within that cap.
            RetryPolicy policy = RetryPolicy.builder()
                    .on(RetryOn.TOO_MANY_REQUESTS)
                    .honorRetryAfterHeader()
                    .build();

            java.net.http.HttpHeaders headers = java.net.http.HttpHeaders.of(
                    java.util.Map.of("Retry-After", List.of("30")),
                    (k, v) -> true
            );
            TooManyRequestsException ex = new TooManyRequestsException(
                    "GET",
                    java.net.URI.create("https://example.com"),
                    headers,
                    null
            );

            Duration delay = policy.retryDelay(1, ex).orElseThrow();

            assertEquals(Duration.ofSeconds(30), delay);
        }

        @Test
        void honorRetryAfterHeader_withCap_nullCap_throwsNullValueException() {
            NullValueException ex = assertThrows(
                    NullValueException.class,
                    () -> RetryPolicy.builder().honorRetryAfterHeader(null)
            );

            assertEquals(NULL_MAX_WAIT_MSG, ex.getMessage());
        }

        @Test
        void honorRetryAfterHeader_withCap_zeroDuration_throwsDurationOutsideRangeException() {
            assertThrows(
                    DurationOutsideRangeException.class,
                    () -> RetryPolicy.builder().honorRetryAfterHeader(Duration.ZERO)
            );
        }

        @Test
        void honorRetryAfterHeader_withCap_applied() {
            RetryPolicy policy = RetryPolicy.builder()
                    .on(RetryOn.TOO_MANY_REQUESTS)
                    .honorRetryAfterHeader(Duration.ofSeconds(10))
                    .build();

            java.net.http.HttpHeaders headers = java.net.http.HttpHeaders.of(
                    java.util.Map.of("Retry-After", List.of("5")),
                    (k, v) -> true
            );
            TooManyRequestsException ex = new TooManyRequestsException(
                    "GET",
                    java.net.URI.create("https://example.com"),
                    headers,
                    null
            );

            assertEquals(Duration.ofSeconds(5), policy.retryDelay(1, ex).orElseThrow());
        }
    }

    // -------------------------------------------------------------------------
    // allowNonIdempotent
    // -------------------------------------------------------------------------

    @Nested
    class AllowNonIdempotent {
        @Test
        void notSet_defaultFalse() {
            DefaultRetryPolicy policy = (DefaultRetryPolicy) RetryPolicy.builder()
                    .on(RetryOn.SERVICE_UNAVAILABLE)
                    .build();

            assertFalse(policy.allowNonIdempotent());
        }

        @Test
        void allowNonIdempotent_setsFlag() {
            DefaultRetryPolicy policy = (DefaultRetryPolicy) RetryPolicy.builder()
                    .on(RetryOn.SERVICE_UNAVAILABLE)
                    .allowNonIdempotent()
                    .build();

            assertTrue(policy.allowNonIdempotent());
        }
    }

    // -------------------------------------------------------------------------
    // Convenience sets
    // -------------------------------------------------------------------------

    @Nested
    class ConvenienceSets {
        @Test
        void gatewayErrors_containsExpectedValues() {
            assertTrue(RetryPolicy.GATEWAY_ERRORS.contains(RetryOn.BAD_GATEWAY));
            assertTrue(RetryPolicy.GATEWAY_ERRORS.contains(RetryOn.SERVICE_UNAVAILABLE));
            assertTrue(RetryPolicy.GATEWAY_ERRORS.contains(RetryOn.GATEWAY_TIMEOUT));
        }

        @Test
        void transportErrors_containsExpectedValues() {
            assertTrue(RetryPolicy.TRANSPORT_ERRORS.contains(RetryOn.CONNECT_FAILURE));
            assertTrue(RetryPolicy.TRANSPORT_ERRORS.contains(RetryOn.CONNECT_TIMEOUT));
            assertTrue(RetryPolicy.TRANSPORT_ERRORS.contains(RetryOn.READ_TIMEOUT));
        }

        @Test
        void onGatewayErrors_usesConvenienceSet() {
            RetryPolicy policy = RetryPolicy.builder()
                    .on(RetryPolicy.GATEWAY_ERRORS)
                    .build();

            assertTrue(policy.retryDelay(1, new BadGatewayException()).isPresent());
            assertTrue(policy.retryDelay(1, new ServiceUnavailableException()).isPresent());
            assertTrue(policy.retryDelay(1, new GatewayTimeoutException()).isPresent());
        }
    }
}





