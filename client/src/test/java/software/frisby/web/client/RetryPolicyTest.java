package software.frisby.web.client;

import org.junit.jupiter.api.Test;
import software.frisby.core.validation.NullValueException;

import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RetryPolicyTest {
    private static final URI TEST_URI = URI.create("https://example.com/retry");

    @Test
    void nullPolicy_throwsNullValueException() {
        assertThrows(
                NullValueException.class,
                () -> RetryPolicy.of(null)
        );
    }

    @Test
    void customPolicy_returnsProvidedDelay() {
        RetryPolicy policy = RetryPolicy.of(context -> Optional.of(Duration.ofMillis(context.attempt())));

        RetryContext context = new RetryContext(
                3,
                new RuntimeException("boom"),
                "GET",
                TEST_URI,
                OptionalInt.empty(),
                true,
                RetryPhase.TRANSPORT
        );

        Optional<Duration> delay = policy.retryDelay(context);

        assertTrue(delay.isPresent());
        assertEquals(Duration.ofMillis(3), delay.get());
    }

    @Test
    void customPolicy_canInspectRetryContext() {
        AtomicReference<RetryContext> seen = new AtomicReference<>();

        RetryPolicy policy = RetryPolicy.of(context -> {
            seen.set(context);

            if ("POST".equals(context.method()) && context.uri().getPath().startsWith("/uploads/")) {
                return Optional.empty();
            }

            return Optional.of(Duration.ofSeconds(1));
        });

        RetryContext context = new RetryContext(
                1,
                new RuntimeException("transport"),
                "POST",
                URI.create("https://example.com/uploads/file-1"),
                OptionalInt.empty(),
                false,
                RetryPhase.PRE_FLIGHT
        );

        Optional<Duration> delay = policy.retryDelay(context);

        assertTrue(delay.isEmpty());
        assertSame(context, seen.get());
    }

    @Test
    void nonePolicy_alwaysReturnsEmpty() {
        RetryPolicy policy = RetryPolicy.none();

        RetryContext context = new RetryContext(
                2,
                new RuntimeException("failed"),
                "GET",
                TEST_URI,
                OptionalInt.of(503),
                true,
                RetryPhase.HTTP_RESPONSE
        );

        Optional<Duration> delay = policy.retryDelay(context);

        assertTrue(delay.isEmpty());
    }
}

