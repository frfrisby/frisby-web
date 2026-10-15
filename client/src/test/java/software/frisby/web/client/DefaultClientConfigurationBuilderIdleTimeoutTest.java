package software.frisby.web.client;

import org.junit.jupiter.api.Test;
import software.frisby.core.validation.DurationOutsideRangeException;
import software.frisby.core.validation.NullValueException;
import software.frisby.web.serial.JsonSerializer;

import java.net.URI;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Validates {@link ClientConfigurationBuilder#idleTimeout(Duration)} and {@link
 * ClientConfiguration#idleTimeout()} — mirroring {@code connectTimeout}/{@code
 * readTimeout}'s own validation shape (null/zero/negative/valid), plus the
 * optional-with-fallback behavior specific to {@code idleTimeout}: it defaults to
 * {@code readTimeout()}'s configured value when never explicitly set.
 */
class DefaultClientConfigurationBuilderIdleTimeoutTest {
    private static final URI BASE_URI = URI.create("https://api.example.com");
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(30);
    private static final JsonSerializer SERIALIZER = new TestJsonSerializer();

    private static ClientConfigurationBuilder baseBuilder() {
        return ClientConfiguration.builder()
                .uri(BASE_URI)
                .connectTimeout(CONNECT_TIMEOUT)
                .readTimeout(READ_TIMEOUT)
                .serializer(SERIALIZER);
    }

    @Test
    void unset_fallsBackToReadTimeout() {
        ClientConfiguration configuration = baseBuilder().build();

        assertEquals(READ_TIMEOUT, configuration.idleTimeout());
    }

    @Test
    void explicitlySet_overridesReadTimeoutFallback() {
        Duration idleTimeout = Duration.ofSeconds(90);

        ClientConfiguration configuration = baseBuilder()
                .idleTimeout(idleTimeout)
                .build();

        assertEquals(idleTimeout, configuration.idleTimeout());
        assertEquals(READ_TIMEOUT, configuration.readTimeout());
    }

    @Test
    void nullValue_throwsNullValueException() {
        ClientConfigurationBuilder builder = baseBuilder();

        assertThrows(NullValueException.class, () -> builder.idleTimeout(null));
    }

    @Test
    void zeroValue_throwsDurationOutsideRangeException() {
        ClientConfigurationBuilder builder = baseBuilder();

        assertThrows(DurationOutsideRangeException.class, () -> builder.idleTimeout(Duration.ZERO));
    }

    @Test
    void negativeValue_throwsDurationOutsideRangeException() {
        ClientConfigurationBuilder builder = baseBuilder();

        assertThrows(DurationOutsideRangeException.class, () -> builder.idleTimeout(Duration.ofSeconds(-1)));
    }

    @Test
    void validValue_returnsBuilderInstance() {
        ClientConfigurationBuilder builder = baseBuilder();

        assertEquals(builder, builder.idleTimeout(Duration.ofSeconds(10)));
    }
}

